package com.kinnarastudio.kecakplugins.openbravo.form;

import com.kinnarastudio.kecakplugins.openbravo.service.KecakService;
import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import com.kinnarastudio.obclient.service.OpenbravoService;
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
        obService.setIgnoreCertificateError(isIgnoringCertificateError());
        obService.setNoFilterActive(isNoFilterActive());

        try {
            final Map<String, String> filter = Collections.singletonMap(getForeignKey(), primaryKey);
            return Arrays.stream(obService.get(getBaseUrl(), getTableEntity(), getUsername(), getPassword(), filter))
                    .map(m -> m.entrySet()
                            .stream()
                            .collect(Collectors.toMap(e -> e.getKey().replaceAll("[^a-zA-Z0-9_]", ""), Map.Entry::getValue, (accept, ignore) -> accept, FormRow::new)))
                    .collect(Collectors.toCollection(() -> new FormRowSet() {{
                        setMultiRow(true);
                    }}));
        } catch (com.kinnarastudio.obclient.exceptions.OpenbravoClientException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        try {
            final KecakService kecakService = KecakService.getInstance();
            final OpenbravoService obService = OpenbravoService.getInstance();
            obService.setIgnoreCertificateError(isIgnoringCertificateError());
            obService.setShortCircuit(true);
            obService.setNoFilterActive(isNoFilterActive());

            final Form parentForm = FormUtil.findRootForm(element);
            FormStoreBinder parentStoreBinder = parentForm.getStoreBinder();
            FormRowSet storeBinderData = formData.getStoreBinderData(parentStoreBinder);
            FormRowSet parentRowSet = parentStoreBinder.store(parentForm, storeBinderData, formData);

            final Map<String, String> formErrors = formData.getFormErrors();
            if (formErrors != null && !formErrors.isEmpty()) {
                return null;
            }

            Optional.ofNullable(parentRowSet)
                    .stream()
                    .flatMap(Collection::stream)
                    .findFirst()
                    .map(FormRow::getId)
                    .ifPresent(formData::setPrimaryKeyValue);

            final String foreignKey = getForeignKey();

            final Form form = kecakService.generateForm(getFormDefId());
            String foreignKeyValue = parentForm.getPrimaryKeyValue(formData);

            if (foreignKeyValue == null) {
                throw new OpenbravoClientException("Foreign key [" + foreignKey + "] is NULL");
            }

            final String gridElementId = element.getPropertyString("id");

            final Map[] rows = Optional.ofNullable(rowSet)
                    .stream()
                    .flatMap(FormRowSet::stream)
//                    .filter(row -> row.keySet().stream().noneMatch(gridElementId::equals))
                    .map(row -> row.entrySet()
                            .stream()
                            .filter(e -> !"id".equalsIgnoreCase(String.valueOf(e.getKey())))
                            .filter(e -> !gridElementId.equals(String.valueOf(e.getKey())))
                            .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> {
                                final String elementId = String.valueOf(e.getKey());
                                final Element elmn = FormUtil.findElement(elementId, form, formData);
                                final boolean isNumeric = Optional.ofNullable(elmn)
                                        .map(Element::getValidator)
                                        .map(v -> v.getPropertyString("type"))
                                        .map("numeric"::equalsIgnoreCase)
                                        .orElse(false);

                                try {
                                    return isNumeric ? new BigDecimal(String.valueOf(e.getValue())) : String.valueOf(e.getValue());
                                } catch (NumberFormatException ex) {
                                    LogUtil.error(getClassName(), ex, "[" + e.getValue() + "] is not a number");
                                    return String.valueOf(e.getValue());
                                }
                            })))
                    .filter(m -> !m.isEmpty())
                    .toArray(Map[]::new);

            Arrays.stream(rows).forEach(m -> m.put(foreignKey, foreignKeyValue));

            final Map<String, Object>[] result = obService.post(getBaseUrl(), getTableEntity(), getUsername(), getPassword(), rows);
            return Arrays.stream(result)
                    .map(m -> m.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (accept, ignore) -> accept, FormRow::new)))
                    .collect(Collectors.toCollection(FormRowSet::new));
        } catch (Exception e) {
            final String elementId = element.getPropertyString("id");
            formData.addFormError(elementId, e.getMessage());
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
        return AppUtil.readPluginResource(getClassName(), "/properties/form/OpenbravoGridBinder.json", null, true, "/messages/Openbravo");
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

    public boolean isDebugging() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
//        return true;
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
