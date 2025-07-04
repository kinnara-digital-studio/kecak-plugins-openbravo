package com.kinnarastudio.kecakplugins.openbravo.datalist;

import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.model.OpenbravoDataListQueryObject;
import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import com.kinnarastudio.obclient.service.OpenbravoService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Openbravo DataList Binder
 */
public class OpenbravoDataListBinder extends DataListBinderDefault implements RestMixin {
    @Override
    public DataListColumn[] getColumns() {
        return Optional.ofNullable(getData(null, null, null, null, null, null, 1))
                .stream()
                .flatMap(Collection::stream)
                .findFirst()
                .map(Map::keySet)
                .stream()
                .flatMap(Collection::stream)
                .map(s -> new DataListColumn() {{
                    setName(s);
                    setLabel(s);
                }})
                .toArray(DataListColumn[]::new);
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection<Map<String, String>> getData(@Nullable DataList dataList, @Nullable Map properties, @Nullable DataListFilterQueryObject[] filterQueryObjects, String sort, @Nullable Boolean desc, @Nullable Integer start, @Nullable Integer rows) {
        final OpenbravoService obService = OpenbravoService.getInstance();
        obService.setIgnoreCertificateError(isIgnoreCertificateError());
        obService.setNoFilterActive(isNoFilterActive());

        final String baseUrl = getBaseUrl();
        final String tableEntity = getTableEntity();
        final String username = getUsername();
        final String password = getPassword();

        final String filterWhereCondition = getFilterWhereCondition(filterQueryObjects);
        final String customWhereCondition = getCustomWhereCondition();
        final String whereCondition;
        if (customWhereCondition.isEmpty()) {
            whereCondition = filterWhereCondition;
        } else {
            whereCondition = String.format("(%s) AND (%s)", filterWhereCondition, customWhereCondition);
        }

        try {
            final Integer startRow = start == null ? 0 : start;
            final Integer endRow = rows == null ? null : (startRow + rows);
            final DataListCollection<Map<String, String>> result = Arrays.stream(obService.get(baseUrl, tableEntity, username, password, null, whereCondition, null, sort, desc, startRow, endRow))
                    .map(m -> m.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                    .collect(Collectors.toCollection(DataListCollection::new));

            return result;
        } catch (OpenbravoClientException e) {
            LogUtil.error(getClassName(), e, "getData : dataList [" + Optional.ofNullable(dataList).map(DataList::getId).orElse("") + "]");
            return null;
        }
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map map, DataListFilterQueryObject[] filterQueryObjects) {
        final com.kinnarastudio.kecakplugins.openbravo.service.OpenbravoService obService = com.kinnarastudio.kecakplugins.openbravo.service.OpenbravoService.getInstance();
        obService.setIgnoreCertificateError(isIgnoreCertificateError());
        obService.setNoFilterActive(isNoFilterActive());

        final String baseUrl = getBaseUrl();
        final String tableEntity = getTableEntity();
        final String username = getUsername();
        final String password = getPassword();
        final String filterWhereCondition = getFilterWhereCondition(filterQueryObjects);
        final String customWhereCondition = getCustomWhereCondition();
        final String whereCondition;
        if (customWhereCondition.isEmpty()) {
            whereCondition = filterWhereCondition;
        } else {
            whereCondition = String.format("(%s) AND (%s)", filterWhereCondition, customWhereCondition);
        }

        try {
            final int result = obService.count(baseUrl, tableEntity, username, password, whereCondition, null);
            return result;
        } catch (OpenbravoClientException e) {
            LogUtil.info(getClassName(), "getDataTotalRowCount : dataList [" + dataList.getId() + "]");
            LogUtil.error(getClassName(), e, e.getMessage());
            return 0;
        }
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "Openbravo DataList Binder";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/datalist/OpenbravoDataListBinder.json", null, false, "/messages/Openbravo");
    }

    protected String getBaseUrl() {
        return AppUtil.processHashVariable(getPropertyString("baseUrl"), null, null, null);
    }

    protected String getTableEntity() {
        return AppUtil.processHashVariable(getPropertyString("tableEntity"), null, null, null);
    }

    protected String getUsername() {
        return AppUtil.processHashVariable(getPropertyString("username"), null, null, null);
    }

    protected String getPassword() {
        return AppUtil.processHashVariable(getPropertyString("password"), null, null, null);
    }

    protected boolean isNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    protected String getFilterWhereCondition(DataListFilterQueryObject[] filterQueryObjects) {
        // fix operator value, don't know why the operators are always 'AND'
        // even though the filter plugins already set to 'OR'
        Optional.ofNullable(filterQueryObjects)
                .stream()
                .flatMap(Arrays::stream)
                .forEach(queryObject -> {
                    final String operator;
                    if (queryObject instanceof OpenbravoDataListQueryObject) {
                        operator = ((OpenbravoDataListQueryObject) queryObject).isOr() ? "OR" : "AND";
                    } else {
                        operator = ifEmptyThen(queryObject.getOperator(), "AND");
                    }
                    queryObject.setOperator(operator);
                });

        final Stack<DataListFilterQueryObject> orStack = new Stack<>();
        final Queue<DataListFilterQueryObject> orQueue = new ArrayDeque<>();

        final List<DataListFilterQueryObject> packedQueryObject = Optional.ofNullable(filterQueryObjects)
                .stream()
                .flatMap(Arrays::stream)
                .peek(queryObject -> {
                    final String operator = queryObject.getOperator();
                    if ("OR".equalsIgnoreCase(operator)) {
                        orStack.push(queryObject);
                        orQueue.add(queryObject);
                    }
                })
                .filter(queryObject -> "AND".equalsIgnoreCase(queryObject.getOperator()))
                .collect(Collectors.toList());

        orStack.stream()
                .reduce((q1, q2) -> new DataListFilterQueryObject() {{
                    final String query = q1.getQuery() + " OR " + q2.getQuery();
                    final String[] values = Stream.concat(nullableArraysStream(q1.getValues()), nullableArraysStream(q2.getValues()))
                                    .toArray(String[]::new);

                    setOperator("AND");
                    setQuery(query);
                    setValues(values);
                }})
                .ifPresent(packedQueryObject::add);
        packedQueryObject.add(new DataListFilterQueryObject() {{
            setOperator("AND");
            setQuery(orStack.stream().map(DataListFilterQueryObject::getQuery).collect(Collectors.joining(" OR ")));
            setValues(orStack.stream().map(DataListFilterQueryObject::getValues).flatMap(Arrays::stream).toArray(String[]::new));
        }});

        return packedQueryObject
                .stream()
                .filter(Objects::nonNull)
                .filter(f -> f.getQuery() != null && !f.getQuery().isEmpty())
                .map(queryObject -> {
                    final String operator = queryObject.getOperator();
                    final String condition = getCondition(queryObject);

                    return operator + " (" + condition + ")";
                })
                .collect(Collectors.joining(" ", "1=1 ", ""));
    }

    protected String getCondition(DataListFilterQueryObject filterQueryObject) {
        final Pattern p = Pattern.compile("\\?");
        final String[] values = filterQueryObject.getValues();

        final StringBuilder condition = new StringBuilder();
        final String query = filterQueryObject.getQuery().replaceAll("\\$_identifier", ".name");
        final Matcher m = p.matcher(query);
        int i = 0;
        while (m.find()) {
            if (i < values.length) {
                final String value = values[i].replaceAll("'", "''");
                m.appendReplacement(condition, "'" + value + "'");
            }
            i++;
        }
        m.appendTail(condition);
        return condition.toString();
    }

    protected String getCustomWhereCondition() {
        return getPropertyString("customWhereCondition");
    }

    protected <T> Stream<T> nullableArraysStream(@Nullable T[] array) {
        return Optional.ofNullable(array)
                .stream()
                .flatMap(Arrays::stream);
    }
}
