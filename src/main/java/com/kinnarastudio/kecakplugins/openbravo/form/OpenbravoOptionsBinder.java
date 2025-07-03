package com.kinnarastudio.kecakplugins.openbravo.form;

import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import com.kinnarastudio.obclient.service.OpenbravoService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class OpenbravoOptionsBinder extends FormBinder implements FormLoadOptionsBinder, RestMixin, FormAjaxOptionsBinder {
    final public static String LABEL = "Openbravo Options Binder";

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        final String valueColumn = getPropertyString("valueColumn");
        final String labelColumn = getPropertyString("labelColumn");

        try {
            final OpenbravoService openbravoService = OpenbravoService.getInstance();
            final String[] fields = null;
            final Map<String, Object>[] records = openbravoService.get(getPropertyBaseUrl(), getPropertyTableEntity(), getPropertyUsername(), getPropertyPassword(), fields, getWhereCondition(), null, null, null, null, null);
            final FormRowSet rowSet = Optional.ofNullable(records)
                    .stream()
                    .flatMap(Arrays::stream)
                    .map(m -> {
                        final String value = String.valueOf(m.getOrDefault(valueColumn, ""));
                        final String label = String.valueOf(m.getOrDefault(labelColumn, ""));

                        return new FormRow() {{
                            setProperty("value", value);
                            setProperty("label", label.isEmpty() ? value : label);
                        }};
                    })
                    .collect(Collectors.toCollection(FormRowSet::new));

            rowSet.setMultiRow(true);

            return rowSet;
        } catch (OpenbravoClientException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
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

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/form/OpenbravoOptionBinder.json", null, false, "/messages/Openbravo");
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

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
    public FormRowSet loadAjaxOptions(@Nullable String[] depedencyVal) {
        return load(null, null, null);
    }

    @Override
    public boolean useAjax() {
        return true;
    }

    protected String getWhereCondition() {
        return getPropertyString("customWhereCondition");
    }
}
