package com.kinnarastudio.kecakplugins.openbravo.form;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadOptionsBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;

public class OpenbravoOptionsBinder extends FormBinder implements FormLoadOptionsBinder, RestMixin{
    final public static String LABEL = "Open Bravo Options Binder";

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        final WorkflowAssignment workflowAssignment = Optional.of(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);
        try {
            final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getPropertyUsername(), getPropertyPassword()));

            final StringBuilder url = new StringBuilder(getApiEndPoint(getPropertyBaseUrl(), getPropertyTableEntity()));

            final HttpUriRequest request = getHttpRequest(workflowAssignment, url.toString(), "GET", headers);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());

            final HttpResponse response = client.execute(request);

            final int statusCode = getResponseStatus(response);
            if(statusCode == 404) {
                LogUtil.debug(getClassName(), "ID [" + primaryKey + "] : No record");
                return null;
            } else if (getStatusGroupCode(statusCode) != 200) {
                throw new OpenbravoClientException("ID [" + primaryKey + "] : Response code [" + statusCode + "] is not 200 (Success)");
            } else if (statusCode != 200) {
                LogUtil.warn(getClassName(), "ID [" + primaryKey + "] : Response code [" + statusCode + "] is considered as success");
            }

            if (!isJsonResponse(response)) {
                throw new OpenbravoClientException("Content type is not JSON");
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final JSONObject jsonResponseObject = new JSONObject(br.lines().collect(Collectors.joining()));
                final FormRowSet rowSet = new FormRowSet();
                JSONArray jsonData = jsonResponseObject.getJSONObject("response").getJSONArray("data");
                
                JSONStream.of(jsonData, Try.onBiFunction(JSONArray::getJSONObject))
                .forEach(t -> {
                    FormRow formRow = new FormRow();
                    try {
                        formRow.setProperty("value", t.getString(getPropertyString("valueColumn")));
                        formRow.setProperty("label", t.getString(getPropertyString("labelColumn")));
                        rowSet.add(formRow);
                    } catch (JSONException e) {
                        LogUtil.error(getClassName(), e, e.getMessage());
                    }
                });

                return rowSet;
            }
        } catch (OpenbravoClientException | IOException | JSONException e) {
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

    protected String getApiEndPoint(String baseUrl, String tableEntity) {
        return baseUrl + "/org.openbravo.service.json.jsonrest/" + tableEntity + "?_endRow=1000";
    }
    
    protected String getPropertyUsername() {
        return AppUtil.processHashVariable(getPropertyString("username"), null, null, null);
    }

    protected FormRow convertJson(JSONObject json) {
        return JSONStream.of(json, Try.onBiFunction(JSONObject::getString))
                .collect(FormRow::new, (row, e) -> row.setProperty(e.getKey(), e.getValue()), FormRow::putAll);
    }

    protected String getPropertyPassword() {
        return AppUtil.processHashVariable(getPropertyString("password"), null, null, null);
    }

    protected String getAuthenticationHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
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
        return AppUtil.readPluginResource(getClassName(), "/properties/OpenbravoOptionBinder.json", null, false, "/messages/Openbravo");
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
}
