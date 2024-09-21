package com.kinnarastudio.kecakplugins.openbravo.form;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class OpenbravoOptionsBinder extends FormBinder implements FormLoadOptionsBinder, RestMixin, FormAjaxOptionsBinder {
    final public static String LABEL = "Openbravo Options Binder";

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        final WorkflowAssignment workflowAssignment = Optional.ofNullable(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);
        try {
            final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getPropertyUsername(), getPropertyPassword()));

            final String url = getApiEndPoint(getPropertyBaseUrl(), getPropertyTableEntity(), 1000, getWhereCondition());
            final HttpUriRequest request = getHttpRequest(workflowAssignment, url, "GET", headers);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpResponse response = client.execute(request);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final String responseString = br.lines().collect(Collectors.joining());
                final JSONObject jsonResponseObject = new JSONObject(responseString);

                JSONArray jsonData = jsonResponseObject
                        .getJSONObject("response")
                        .getJSONArray("data");

                final FormRowSet rowSet = JSONStream.of(jsonData, Try.onBiFunction(JSONArray::getJSONObject))
                        .map(Try.onFunction(t -> {
                            FormRow formRow = new FormRow();
                            final String value = t.getString(getPropertyString("valueColumn"));
                            final String label = t.getString(getPropertyString("labelColumn"));

                            formRow.setProperty("value", value);
                            formRow.setProperty("label", label.isEmpty() ? value : label);

                            return formRow;
                        }))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(FormRowSet::new));

                rowSet.setMultiRow(true);

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

    protected String getApiEndPoint(String baseUrl, String tableEntity, int endRow, String where) {

        StringBuilder url = new StringBuilder(baseUrl + "/org.openbravo.service.json.jsonrest/" + tableEntity + "?");

        if(endRow > 0) {
            url.append("_endRow=").append(endRow).append("&");
        }

        if(where != null && !where.isEmpty()) {
            url.append("_where=").append(where).append("&");
        }

        return url.toString();
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
