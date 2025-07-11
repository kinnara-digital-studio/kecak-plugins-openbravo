package com.kinnarastudio.kecakplugins.openbravo.form;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Openbravo Form Binder
 */
public class OpenbravoCompositeFormStoreBinder extends FormBinder implements FormStoreElementBinder, RestMixin {

    public final static String LABEL = "Openbravo Composite Form Store Binder";

    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final WorkflowAssignment workflowAssignment = Optional.of(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);

        final Form form = FormUtil.findRootForm(element);
        String tableEntity = getPropertyString("tableEntity");
        final String url = getApiEndPoint(getPropertyBaseUrl(), tableEntity);
        final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getPropertyUsername(), getPropertyPassword()));
        final FormRow row = rowSet.get(0);
        if (!isNewRecord(formData)) {
            final String primaryKey = Optional.of(formData)
                    .map(FormData::getProcessId)
                    .map(workflowManager::getWorkflowProcessLink)
                    .map(WorkflowProcessLink::getOriginProcessId)
                    .orElse(formData.getPrimaryKeyValue().replaceAll("-", ""));
            row.setId(primaryKey);
        }

        try {
            final HttpUriRequest request = getHttpRequest(workflowAssignment, url, "POST", headers, row);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpResponse response = client.execute(request);

            final int statusCode = getResponseStatus(response);
            if (getStatusGroupCode(statusCode) != 200) {
                throw new OpenbravoClientException("Response code [" + statusCode + "] is not 200 (Success) url [" + url + "]");
            } else if (statusCode != 200) {
                LogUtil.warn(getClassName(), "Response code [" + statusCode + "] is considered as success");
            }

            if (!isJsonResponse(response)) {
                throw new OpenbravoClientException("Content type is not JSON");
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final JSONObject jsonResponseBody = new JSONObject(br.lines().collect(Collectors.joining())).getJSONObject("response");
                LogUtil.info(getClassName(), "JSON Response Body Store Binder: " + jsonResponseBody.toString());
                final int status = jsonResponseBody.getInt("status");
                if (status != 0) {
                    throw new OpenbravoClientException(jsonResponseBody.getJSONObject("response").getJSONObject("error").getString("message"));
                }
            }
        } catch (OpenbravoClientException | IOException | JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            formData.addFormError("", e.getMessage());
        }

        return rowSet;
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
        return AppUtil.readPluginResource(getClassName(), "/properties/form/OpenbravoCompositeFormStoreBinder.json", null, false, "/messages/Openbravo");
    }

    protected String getPropertyBaseUrl() {
        return AppUtil.processHashVariable(getPropertyString("baseUrl"), null, null, null);
    }

    protected String getPropertyTableEntity(Form form) {
        return form.getPropertyString(FormUtil.PROPERTY_TABLE_NAME);
//        return AppUtil.processHashVariable(getPropertyString("tableEntity"), null, null, null);
    }

    protected String getPropertyUsername() {
        return AppUtil.processHashVariable(getPropertyString("username"), null, null, null);
    }

    protected String getPropertyPassword() {
        return AppUtil.processHashVariable(getPropertyString("password"), null, null, null);
    }

    protected String getAuthenticationHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    }

    protected String getApiEndPoint(String baseUrl, String tableEntity) {
        return baseUrl + "/org.openbravo.service.json.jsonrest/" + tableEntity;
    }

    protected String getApiEndPoint(String baseUrl, String tableEntity, String primaryKey) {
        return baseUrl + "/org.openbravo.service.json.jsonrest/" + tableEntity + "/" + primaryKey;
    }

    protected FormRow convertJson(JSONObject json) {
        return JSONStream.of(json, Try.onBiFunction(JSONObject::getString))
                .collect(FormRow::new, (row, e) -> row.setProperty(e.getKey(), e.getValue()), FormRow::putAll);
    }

    protected boolean getPropertyNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    protected boolean isNewRecord(FormData formData) {
        return Optional.of(formData)
                .map(FormData::getLoadBinderMap)
                .map(Map::isEmpty)
                .orElse(true);
    }
}
