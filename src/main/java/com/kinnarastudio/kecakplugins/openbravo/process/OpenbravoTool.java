package com.kinnarastudio.kecakplugins.openbravo.process;

import com.kinnarastudio.kecakplugins.openbravo.service.KecakService;
import com.kinnarastudio.kecakplugins.openbravo.service.OpenbravoService;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;

import java.util.*;
import java.util.stream.Collectors;

public class OpenbravoTool extends DefaultApplicationPlugin {
    public final static String LABEL = "Openbravo Tool";

    @Override
    public String getName() {
        return LABEL;
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
    public Object execute(Map map) {
        try {
            final OpenbravoService openbravoService = OpenbravoService.getInstance();
            openbravoService.setDebug(isDebug());
            openbravoService.setIgnoreCertificateError(ignoreCertificateError());

            final KecakService kecakService = KecakService.getInstance();

            final WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");
            final String dataListId = getDataListId();
            final Map<String, String[]> filters = getDataListFilter();
            final DataList dataList = kecakService.generateDataList(dataListId, filters, workflowAssignment);
            final String primaryKeyField = dataList.getBinder().getPrimaryKeyColumnName();

            final String baseUrl = getBaseUrl();
            final String tableEntity = getTableEntity();
            final String username = getUsername();
            final String password = getPassword();

            if (isDebug()) {
                LogUtil.info(getClassName(), "baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "]");
            }
            final Map<String, String> jsonKeyToDataListFieldMap = getDataListFieldMapping();
            final Map<String, Object>[] rows = Optional.ofNullable(dataList.getRows())
                    .stream()
                    .flatMap(Collection<Map<String, String>>::stream)
                    .map(row -> jsonKeyToDataListFieldMap.entrySet()
                            .stream()
                            .collect(Collectors.toUnmodifiableMap(e -> e.getKey(), e -> row.getOrDefault(e.getValue(), ""))))
                    .toArray(Map[]::new);

            final Map<String, Object>[] postResult = openbravoService.post(baseUrl, tableEntity, username, password, rows);
            assert rows.length == postResult.length;

            final String formDefId = getFormDefId();
            if (formDefId.isEmpty()) {
                if (isDebug()) {
                    LogUtil.info(getClassName(), "Ignoring response");
                }
                return null;
            }

            final Form form = kecakService.generateForm(formDefId);
            final AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");

            final Map<String, String> elementToJsonMap = getFormFieldMapping();

            for (int i = 0; i < postResult.length; i++) {
                final Map<String, Object> resultRow = postResult[i];
                final Map<String, Object> row = rows[i];

                final String primaryKey = String.valueOf(row.get(primaryKeyField));
                final FormData formData = new FormData() {{
                    setPrimaryKeyValue(primaryKey);
                }};

                elementToJsonMap.forEach((elementId, jsonKey) -> {
                    final Element element = FormUtil.findElement(elementId, form, formData);
                    if (element == null) return;

                    final String parameterName = FormUtil.getElementParameterName(element);

                    final Object value = resultRow.get(jsonKey);
                    if (value == null) return;

                    formData.addRequestParameterValues(parameterName, new String[]{String.valueOf(value)});
                });

                final boolean ignoreValidation = true;
                appService.submitForm(form, formData, ignoreValidation);
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/process/OpenbravoTool.json", null, true, "/messages/Openbravo");
    }

    public String getDataListId() {
        return getPropertyString("dataListId");
    }

    public Map<String, String[]> getDataListFilter() {
        return Arrays.stream(getPropertyGrid("dataListFilter"))
                .collect(Collectors.toMap(m -> m.get("name"), m -> Optional.ofNullable(m.get("value"))
                        .map(s -> s.split(";")).stream()
                        .flatMap(Arrays::stream)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new)));
    }

    public String getBaseUrl() {
        return getPropertyString("baseUrl");
    }

    public String getUsername() {
        return getPropertyString("username");
    }

    public String getPassword() {
        return getPropertyString("password");
    }

    public String getTableEntity() {
        return getPropertyString("tableEntity");
    }

    public String getFormDefId() {
        return getPropertyString("formDefId");
    }

    public Map<String, String> getDataListFieldMapping() {
        return Arrays.stream(getPropertyGrid("dataListFieldMapping"))
                .collect(Collectors.toUnmodifiableMap(m -> m.get("jsonKey"), m -> m.get("dataListField")));

    }

    public Map<String, String> getFormFieldMapping() {
        return Arrays.stream(getPropertyGrid("formFieldMapping"))
                .collect(Collectors.toUnmodifiableMap(m -> m.get("formField"), m -> m.get("jsonKey")));
    }

    public boolean isDebug() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }

    protected boolean ignoreCertificateError() {
        return "true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"));
    }
}
