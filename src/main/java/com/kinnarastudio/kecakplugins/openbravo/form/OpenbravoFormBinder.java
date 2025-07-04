package com.kinnarastudio.kecakplugins.openbravo.form;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoCreateRecordException;
import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import com.kinnarastudio.obclient.service.OpenbravoService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Openbravo Form Binder
 */
public class OpenbravoFormBinder extends FormBinder implements FormLoadElementBinder, FormStoreElementBinder, FormDeleteBinder, RestMixin {
    public final static String LABEL = "Openbravo Form Binder";

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        if (primaryKey == null || primaryKey.isEmpty()) return null;

        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final WorkflowAssignment workflowAssignment = Optional.of(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);

        final Form form = FormUtil.findRootForm(element);
        String tableEntity = getPropertyString("tableEntity");
        final StringBuilder url = new StringBuilder(getApiEndPoint(getBaseUrl(), tableEntity, primaryKey));
        if (getPropertyNoFilterActive()) {
            addUrlParameter(url, "_noActiveFilter", "true");
        }

        final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getUsername(), getPassword()));

        try {
            final HttpUriRequest request = getHttpRequest(workflowAssignment, url.toString(), "GET", headers);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpResponse response = client.execute(request);

            final int statusCode = getResponseStatus(response);
            if (statusCode == 404) {
                LogUtil.debug(getClassName(), "ID [" + primaryKey + "] : No record");
                return null;
            } else if (getStatusGroupCode(statusCode) != 200) {
                throw new OpenbravoClientException("ID [" + primaryKey + "] : Response code [" + statusCode + "] is not 200 (Success) url [" + url + "]");
            } else if (statusCode != 200) {
                LogUtil.warn(getClassName(), "ID [" + primaryKey + "] : Response code [" + statusCode + "] is considered as success");
            }

            if (!isJsonResponse(response)) {
                throw new OpenbravoClientException("Content type is not JSON");
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final JSONObject jsonResponseBody = new JSONObject(br.lines().collect(Collectors.joining()));
                return new FormRowSet() {{
                    final FormRow row = convertJson(jsonResponseBody);
                    add(row);
                }};
            }
        } catch (OpenbravoClientException | IOException | JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    @Override
    public FormRowSet store(Element form, FormRowSet rowSet, FormData formData) {
        final Boolean isStored = (Boolean) form.getProperty("_stored");
        if (isStored != null && isStored) {
//            return rowSet;
            return Optional.of(form)
                    .map(Element::getStoreBinder)
                    .map(formData::getStoreBinderData)
                    .orElse(rowSet);
        }

        form.setProperty("_stored", true);

        final boolean isDebugging = isDebugging();

        final OpenbravoService obService = OpenbravoService.getInstance();
        obService.setShortCircuit(true);
        obService.setNoFilterActive(isNoFilterActive());
        obService.setIgnoreCertificateError(isIgnoreCertificateError());

        String tableEntity = getTableEntity();

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
            final FormRow result = Arrays.stream(obService.post(getBaseUrl(), tableEntity, getUsername(), getPassword(), new Map[]{row}))
                    .map(Map<String, Object>::entrySet)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toMap(e -> e.getKey().replaceAll("[^a-zA-Z0-9_]", ""), Map.Entry::getValue, (accept, reject) -> accept, FormRow::new));

            FormRowSet resultRowSet = new FormRowSet() {{
                add(result);
            }};

            formData.setStoreBinderData(form.getStoreBinder(), resultRowSet);
            formData.setPrimaryKeyValue(result.getId());

            return resultRowSet;

        } catch (com.kinnarastudio.obclient.exceptions.OpenbravoClientException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof OpenbravoCreateRecordException) {
                final Map<String, String> errors = ((OpenbravoCreateRecordException) cause).getErrors();
                Optional.ofNullable(errors).map(Map::entrySet).stream().flatMap(Collection::stream)
                        .forEach(entry -> formData.addFormError(entry.getKey(), entry.getValue()));
            }

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
        return AppUtil.readPluginResource(getClassName(), "/properties/form/OpenbravoFormBinder.json", null, false, "/messages/Openbravo");
    }

    protected String getBaseUrl() {
        return AppUtil.processHashVariable(getPropertyString("baseUrl"), null, null, null);
    }

    protected String getUsername() {
        return AppUtil.processHashVariable(getPropertyString("username"), null, null, null);
    }

    protected String getPassword() {
        return AppUtil.processHashVariable(getPropertyString("password"), null, null, null);
    }

    protected String getAuthenticationHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    }

    protected String getApiEndPoint(String baseUrl, String tableEntity, String primaryKey) {
        return baseUrl + "/org.openbravo.service.json.jsonrest/" + tableEntity + "/" + primaryKey;
    }

    protected FormRow convertJson(JSONObject json) {
        return JSONStream.of(json, Try.onBiFunction(JSONObject::getString))
                .collect(FormRow::new, (row, e) -> row.setProperty(e.getKey().replaceAll("[^a-zA-Z0-9_]", ""), e.getValue()), FormRow::putAll);
    }

    protected boolean getPropertyNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    protected String getTableEntity() {
        return AppUtil.processHashVariable(getPropertyString("tableEntity"), null, null, null);
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

    @Override
    public boolean isDebugging() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }

    @Override
    public void delete(Element element, FormRowSet rowSet, FormData formData, boolean deleteGrid, boolean deleteSubform, boolean abortProcess, boolean deleteFiles, boolean hardDelete) {
        final boolean isDebugging = isDebugging();

        final OpenbravoService obService = OpenbravoService.getInstance();
        obService.setShortCircuit(false);
        obService.setNoFilterActive(isNoFilterActive());
        obService.setIgnoreCertificateError(isIgnoreCertificateError());

        final String baseUrl = getBaseUrl();
        final String tableEntity = getTableEntity();
        final String username = getUsername();
        final String password = getPassword();

        Optional.ofNullable(rowSet)
                .stream()
                .flatMap(Collection::stream)
                .map(FormRow::getId)
                .forEach(Try.onConsumer(key -> {
                    final Map<String, Object> result = obService.delete(baseUrl, tableEntity, key, username, password);
                    if (isDebugging) {
                        LogUtil.info(getClassName(), "ID [" + result.get("id") + "] from entity [" + tableEntity + "] has been deleted");
                    }
                }));
    }
}
