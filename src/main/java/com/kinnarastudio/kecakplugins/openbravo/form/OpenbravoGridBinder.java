package com.kinnarastudio.kecakplugins.openbravo.form;

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

public class OpenbravoGridBinder extends FormBinder
        implements FormLoadBinder,
        FormStoreBinder,
        FormLoadMultiRowElementBinder,
        FormStoreMultiRowElementBinder {

    public final static String LABEL = "Openbravo Grid Binder";

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        if (primaryKey == null || primaryKey.isEmpty()) return null;

        final OpenbravoService obService = OpenbravoService.getInstance();
        obService.setDebug(isDebuging());

        try {
            final Map<String, String> filter = Collections.singletonMap(getForeignKey(), primaryKey);
            return Arrays.stream(obService.get(getBaseUrl(), getTableEntity(), getUsername(), getPassword(), filter))
                    .map(m -> m.entrySet()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (accept, ignore) -> accept, FormRow::new)))
                    .collect(Collectors.toCollection(() -> new FormRowSet() {{
                        setMultiRow(true);
                    }}));
        } catch (OpenbravoClientException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        try {
            final OpenbravoService obService = OpenbravoService.getInstance();
            obService.setDebug(isDebuging());
            obService.setIgnoreCertificateError(isIgnoringCertificateError());
            obService.setShortCircuit(true);
            obService.setNoFilterActive(isNoFilterActive());

            Form parentForm = FormUtil.findRootForm(element);
            FormStoreBinder parentStoreBinder = parentForm.getStoreBinder();
            FormRowSet storeBinderData = formData.getStoreBinderData(parentStoreBinder);
            FormRowSet parentRowSet = parentStoreBinder.store(parentForm, storeBinderData, formData);

            Optional.ofNullable(parentRowSet)
                    .stream()
                    .flatMap(Collection::stream)
                    .findFirst()
                    .ifPresent(r -> {
                        formData.setPrimaryKeyValue(r.getId());
                        parentForm.setProperty("_stored", Boolean.TRUE);
                    });

            final Form form = obService.generateForm(getFormDefId());
            String primaryKeyValue = parentForm.getPrimaryKeyValue(formData);


//        FormRowSet originalRowSet = this.load(element, primaryKeyValue, formData);

            final String foreignKey = getForeignKey();
            final Map<String, Object>[] rows = Optional.ofNullable(rowSet)
                    .stream()
                    .flatMap(FormRowSet::stream)
                    .peek(row -> row.setProperty(foreignKey, primaryKeyValue))
                    .map(r -> ((Map<Object, Object>) r.getCustomProperties())
                            .entrySet()
                            .stream()
                            .filter(e -> {
                                final String key = e.getKey().toString();
                                return !key.isEmpty() && !"id".equalsIgnoreCase(key);
                            })
                            .collect(Collectors.toUnmodifiableMap(e -> e.getKey().toString(), entry -> {
                                final String elementId = String.valueOf(entry.getKey());
                                final Element elmn = FormUtil.findElement(elementId, form, formData);
                                final boolean isNumeric = Optional.ofNullable(elmn)
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
                            })))
                    .toArray(Map[]::new);


            Map<String, Object>[] result = obService.post(getBaseUrl(), getTableEntity(), getUsername(), getPassword(), rows);
            return Arrays.stream(result)
                    .map(m -> new FormRow() {{
                        putAll(m);
                    }})
                    .collect(Collectors.toCollection(FormRowSet::new));
        } catch (OpenbravoClientException e) {
            final Map<String, String> errors = e.getErrors();
            errors.forEach((field, message) -> LogUtil.warn(getClassName(), message));
            errors.forEach(formData::addFormError);

            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
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
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
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
        return AppUtil.readPluginResource(getClassName(), "/properties/form/OpenbravoGridBinder.json");
    }

    public String getBaseUrl() {
        return getPropertyString("baseUrl");
    }

    public String getTableEntity() {
        return getPropertyString("tableEntity");
    }

    public String getUsername() {
        return getPropertyString("username");
    }

    public String getPassword() {
        return getPropertyString("password");
    }

    public String getForeignKey() {
        return getPropertyString("foreignKey");
    }

    public boolean isDebuging() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }

    public boolean isIgnoringCertificateError() {
        return "true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"));
    }

    public boolean isNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    public String getFormDefId() {
        return getPropertyString("formDefId");
    }
}
