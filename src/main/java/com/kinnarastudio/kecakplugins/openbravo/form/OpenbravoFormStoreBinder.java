package com.kinnarastudio.kecakplugins.openbravo.form;

import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import com.kinnarastudio.kecakplugins.openbravo.service.OpenbravoService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Openbravo Form Binder
 */
@Deprecated
public class OpenbravoFormStoreBinder extends FormBinder implements FormStoreElementBinder, RestMixin {

    @Override
    public FormRowSet store(Element form, FormRowSet rowSet, FormData formData) {
        if(Boolean.parseBoolean(String.valueOf(form.getProperty("_stored")))) {
            return rowSet;
        }

        final OpenbravoService obService = OpenbravoService.getInstance();
        obService.setShortCircuit(true);
        obService.setDebug(isDebug());
        obService.setNoFilterActive(isNoFilterActive());
        obService.setIgnoreCertificateError(isIgnoreCertificateError());

        String tableEntity = getPropertyTableEntity(FormUtil.findRootForm(form));

        final Map<String, Object> row = Optional.ofNullable(rowSet)
                .stream()
                .flatMap(FormRowSet::stream)
                .findFirst()
                .map(FormRow::entrySet)
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableMap(e -> String.valueOf(e.getKey()), entry -> {
                    final String elementId = String.valueOf(entry.getKey());
                    final Element element = FormUtil.findElement(elementId, form, formData);
                    final boolean isNumeric = Optional.ofNullable(element)
                            .map(Element::getValidator)
                            .map(v -> v.getPropertyString("type"))
                            .map("numeric"::equalsIgnoreCase)
                            .orElse(false);

                    try {
                        return isNumeric ? new BigDecimal(String.valueOf(entry.getValue())) : String.valueOf(entry.getValue());
                    } catch (NumberFormatException ex) {
                        LogUtil.error(getClassName(), ex, "[" + entry.getValue() + "] is not a number");
                        return entry.getValue();
                    }
                }));

        try {
            final FormRow result = Arrays.stream(obService.post(getPropertyBaseUrl(), tableEntity, getPropertyUsername(), getPropertyPassword(), new Map[]{row}))
                    .map(Map<String, String>::entrySet)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (accept, reject) -> accept, FormRow::new));

            formData.setPrimaryKeyValue(result.getId());

            return new FormRowSet() {{
                add(result);
            }};

        } catch (OpenbravoClientException e) {
            Map<String, String> errors = e.getErrors();
            errors.forEach((field, message) -> LogUtil.warn(getClassName(), message));
            errors.forEach(formData::addFormError);

            LogUtil.error(getClassName(), e, e.getMessage());

            return rowSet;
        }
    }

    @Override
    public String getName() {
        return "Openbravo Form Store Binder";
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
        return "Deprecated Openbravo Form Store Binder";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/form/OpenbravoFormBinder.json", null, false, "/messages/Openbravo");
    }

    protected String getPropertyBaseUrl() {
        return AppUtil.processHashVariable(getPropertyString("baseUrl"), null, null, null);
    }

    protected String getPropertyTableEntity(Form form) {
//        return form.getPropertyString(FormUtil.PROPERTY_TABLE_NAME);
        return AppUtil.processHashVariable(getPropertyString("tableEntity"), null, null, null);
    }

    protected String getPropertyUsername() {
        return AppUtil.processHashVariable(getPropertyString("username"), null, null, null);
    }

    protected String getPropertyPassword() {
        return AppUtil.processHashVariable(getPropertyString("password"), null, null, null);
    }

    protected boolean isNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    protected boolean isNewRecord(FormData formData) {
        return Optional.of(formData)
                .map(FormData::getLoadBinderMap)
                .map(Map::isEmpty)
                .orElse(true);
    }
}
