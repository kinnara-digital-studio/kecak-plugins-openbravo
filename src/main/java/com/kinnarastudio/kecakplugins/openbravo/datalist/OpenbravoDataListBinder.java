package com.kinnarastudio.kecakplugins.openbravo.datalist;

import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import com.kinnarastudio.kecakplugins.openbravo.service.OpenbravoService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        obService.setDebug(isDebuging());
        obService.setIgnoreCertificateError(isIgnoreCertificateError());
        obService.setNoFilterActive(getPropertyNoFilterActive());

        final String baseUrl = getPropertyBaseUrl();
        final String tableEntity = getPropertyTableEntity();
        final String username = getPropertyUsername();
        final String password = getPropertyPassword();
        final String filterWhereCondition = getFilterWhereCondition(filterQueryObjects);
        final String customWhereCondition = getCustomWhereCondition();
        final String whereCondition;
        if (customWhereCondition.isEmpty()) {
            whereCondition = filterWhereCondition;
        } else {
            whereCondition = String.format("(%s) AND (%s)", filterWhereCondition, customWhereCondition);
        }

        try {


            final DataListCollection<Map<String, String>> result = Arrays.stream(obService.get(baseUrl, tableEntity, username, password, whereCondition, sort, desc))
                    .map(m -> m.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                    .collect(Collectors.toCollection(DataListCollection::new));

            return result;
        } catch (OpenbravoClientException e) {
            LogUtil.info(getClassName(), "getData : dataList [" + dataList.getId() + "]");
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map map, DataListFilterQueryObject[] filterQueryObjects) {
        final OpenbravoService obService = OpenbravoService.getInstance();
        obService.setDebug(isDebuging());
        obService.setIgnoreCertificateError(isIgnoreCertificateError());
        obService.setNoFilterActive(getPropertyNoFilterActive());

        final String baseUrl = getPropertyBaseUrl();
        final String tableEntity = getPropertyTableEntity();
        final String username = getPropertyUsername();
        final String password = getPropertyPassword();
        final String filterWhereCondition = getFilterWhereCondition(filterQueryObjects);
        final String customWhereCondition = getCustomWhereCondition();
        final String whereCondition;
        if (customWhereCondition.isEmpty()) {
            whereCondition = filterWhereCondition;
        } else {
            whereCondition = String.format("(%s) AND (%s)", filterWhereCondition, customWhereCondition);
        }

        try {
            final int result = obService.count(baseUrl, tableEntity, username, password, whereCondition);
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

    protected String getPropertyBaseUrl() {
        return AppUtil.processHashVariable(getPropertyString("baseUrl"), null, null, null);
    }

    protected String getPropertyTableEntity() {
        return AppUtil.processHashVariable(getPropertyString("tableEntity"), null, null, null);
    }

    protected String getPropertyUsername() {
        return AppUtil.processHashVariable(getPropertyString("username"), null, null, null);
    }

    protected String getPropertyPassword() {
        return AppUtil.processHashVariable(getPropertyString("password"), null, null, null);
    }

    protected boolean getPropertyNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    protected String getFilterWhereCondition(DataListFilterQueryObject[] filterQueryObjects) {
        final Pattern p = Pattern.compile("\\?");
        String whereCondition = Optional.ofNullable(filterQueryObjects)
                .stream()
                .flatMap(Arrays::stream)
                .map(filterQueryObject -> {
                    final String operator = ifEmptyThen(filterQueryObject.getOperator(), "AND");
                    final String query = filterQueryObject.getQuery().replaceAll("\\$_identifier", "\\.name");
                    final String[] values = filterQueryObject.getValues();

                    final StringBuilder condition = new StringBuilder();
                    final Matcher m = p.matcher(query);
                    int i = 0;
                    while (m.find()) {
                        if (i < values.length) {
                            m.appendReplacement(condition, "'" + values[i] + "'");
                        }
                        i++;
                    }
                    m.appendTail(condition);

                    return operator + " " + condition;
                })
                .collect(Collectors.joining(" ", "1=1 ", ""));
        return whereCondition;
    }

    protected String getCustomWhereCondition() {
        return getPropertyString("customWhereCondition");
    }
}
