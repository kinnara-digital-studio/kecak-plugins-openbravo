package com.kinnara.kecakplugins.openbravo;

import com.kinnara.kecakplugins.openbravo.commons.RestMixin;
import com.kinnara.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.displaytag.util.LookupUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Openbravo Record Activation DataList Action
 *
 * Activate / deactivate openbravo record(s)
 */
public class OpenbravoRecordActivationDataListAction extends DataListActionDefault implements RestMixin {
    @Override
    public String getLinkLabel() {
        String label = getPropertyString("label");
        if (label == null || label.isEmpty()) {
            label = "Activate";
        }
        return label;
    }

    @Override
    public String getHref() {
        return getPropertyString("href");
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn");
    }

    @Override
    public String getConfirmation() {
        String confirm = getPropertyString("confirmation");
        if (confirm == null || confirm.isEmpty()) {
            confirm = "Please Confirm";
        }
        return confirm;
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        final String primaryKeyColumn = dataList.getBinder().getPrimaryKeyColumnName();
        final DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");

        // only allow POST
        final HttpServletRequest servletRequest = WorkflowUtil.getHttpServletRequest();
        if (servletRequest != null && !"POST".equalsIgnoreCase(servletRequest.getMethod())) {
            return null;
        }

        if (rowKeys != null && rowKeys.length > 0) {
            final StringBuilder url = new StringBuilder(getApiEndPoint(getPropertyBaseUrl(), getPropertyTableEntity()));
            final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getPropertyUsername(), getPropertyPassword()));

            final DataListCollection<Map<String, String>> sortedDataListCollection = dataList.getRows();
            sortedDataListCollection.sort(Comparator.comparing(m -> m.getOrDefault(primaryKeyColumn, "")));

            for (String rowKey : rowKeys) {
                try {
                    final FormRow row = new FormRow();
                    row.setId(rowKey);

                    if("toggle".equalsIgnoreCase(getPropertyActivationMode())) {
                        final boolean isCurrentlyActive = "true".equalsIgnoreCase(getValue(sortedDataListCollection, primaryKeyColumn, rowKey, "active"));
                        row.setProperty("active", String.valueOf(!isCurrentlyActive));
                    } else {
                        row.setProperty("active", String.valueOf("activate".equalsIgnoreCase(getPropertyActivationMode())));
                    }

                    final HttpUriRequest request = getHttpRequest(null, url.toString(), "PUT", headers, row);
                    final HttpClient client = getHttpClient(isIgnoreCertificateError());
                    final HttpResponse response = client.execute(request);

                    final int statusCode = getResponseStatus(response);
                    if (getStatusGroupCode(statusCode) != 200) {
                        throw new OpenbravoClientException("Response code [" + statusCode + "] is not 200 (Success)");
                    } else if (statusCode != 200) {
                        LogUtil.warn(getClassName(), "Response code [" + statusCode + "] is considered as success");
                    }

                    if (!isJsonResponse(response)) {
                        throw new OpenbravoClientException("Content type is not JSON");
                    }

                    try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                        final JSONObject jsonResponseBody = new JSONObject(br.lines().collect(Collectors.joining())).getJSONObject("response");
                        final int status = jsonResponseBody.getInt("status");
                        if (status != 0) {
                            throw new OpenbravoClientException(jsonResponseBody.getJSONObject("error").getString("message"));
                        }
                    }
                } catch (OpenbravoClientException | IOException | JSONException e) {
                    LogUtil.error(getClassName(), e, e.getMessage());
                }

                if(isRowAction()) {
                    break;
                }
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "Openbravo Activation";
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
        return "Openbravo Activation";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/OpenbravoRecordActivationDataListAction.json", null, false, "/messages/Openbravo");
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

    protected String getAuthenticationHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    }

    protected String getApiEndPoint(String baseUrl, String tableEntity) {
        return baseUrl + "/org.openbravo.service.json.jsonrest/" + tableEntity;
    }

    protected String getPropertyActivationMode() {
        return getPropertyString("mode");
    }

    protected String getValue(@SuppressWarnings("rawtypes") DataListCollection<Map<String, String>> sortedRows, String primaryKeyColumnName, String key, String columnName) {
        final Map<String, String> searchKey = new HashMap<>();
        searchKey.put(primaryKeyColumnName, key);

        final int index = Collections.binarySearch(sortedRows, searchKey, Comparator.comparing(m -> m.getOrDefault(primaryKeyColumnName, "")));
        if(index >= 0) {
            return sortedRows.get(index).getOrDefault(columnName, "");
        }

        return "";
    }

    protected boolean isRowAction() {
        return "true".equalsIgnoreCase(getPropertyString("isRowAction"));
    }
}
