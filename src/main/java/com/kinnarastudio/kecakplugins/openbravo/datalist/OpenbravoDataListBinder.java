package com.kinnarastudio.kecakplugins.openbravo.datalist;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.kecakplugins.openbravo.commons.RestMixin;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Openbravo DataList Binder
 */
public class OpenbravoDataListBinder extends DataListBinderDefault implements RestMixin {
    @Override
    public DataListColumn[] getColumns() {
        return Optional.ofNullable(getData(null, null, null, null, null, null, 1))
                .stream()
                .flatMap(Collection::stream)
                .findFirst()
                .map(Map::keySet)
                .stream()
                .flatMap(Collection::stream)
                .map(s -> new DataListColumn() {{
                    setName(s);
                    setLabel(s);
                }})
                .toArray(DataListColumn[]::new);
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection<Map<String, String>> getData(@Nullable DataList dataList, @Nullable Map properties, @Nullable DataListFilterQueryObject[] filterQueryObjects, String sort, @Nullable Boolean desc, @Nullable Integer start, @Nullable Integer rows) {
        try {
            StringBuilder url = new StringBuilder(getApiEndPoint(getPropertyBaseUrl(), getPropertyTableEntity()));

            if (!getPropertyString("sortBy").isEmpty()) {
                addUrlParameter(url, "_sortBy", getPropertyString("sortBy"));
            }

            if (getPropertyNoFilterActive()) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            if (sort != null && !sort.isEmpty()) {
                addUrlParameter(url, "_sortBy", sort);
            }

            if (start != null) {
                addUrlParameter(url, "_startRow", String.valueOf(start));
            }

            if (rows != null) {
                addUrlParameter(url, "_endRow", String.valueOf((start == null ? 0 : start) + rows));
            }

            final String filterWhereCondition = getFilterWhereCondition(filterQueryObjects);
            final String customWhereCondition = getCustomWhereCondition();
            final String whereCondition;
            if (customWhereCondition.isEmpty()) {
                whereCondition = filterWhereCondition;
            } else {
                whereCondition = String.format("(%s) AND (%s)", filterWhereCondition, customWhereCondition);
            }
            addUrlParameter(url, "_where", URLEncoder.encode(whereCondition));
            final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getPropertyUsername(), getPropertyPassword()));
            final HttpUriRequest request = getHttpRequest(url.toString(), "GET", headers);

            // kirim request ke server
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
                final String responseBody = br.lines().collect(Collectors.joining());
                final JSONObject jsonResponseBody = new JSONObject(responseBody);

                return Optional.of(jsonResponseBody)
                        .map(Try.onFunction(j -> j.getJSONObject("response")))
                        .map(Try.onFunction(j -> j.getJSONArray("data")))
                        .map(j -> JSONStream.of(j, Try.onBiFunction(JSONArray::getJSONObject)))
                        .orElseGet(Stream::empty)
                        .map(this::convertJson)
                        .collect(Collectors.toCollection(DataListCollection::new));
            }
        } catch (IOException | OpenbravoClientException | JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return null;
        }
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map map, DataListFilterQueryObject[] filterQueryObjects) {
        try {
            final StringBuilder url = new StringBuilder(getCountApiEndPoint(getPropertyBaseUrl(), getPropertyTableEntity()));
            LogUtil.info(getClassName(), "new get total row count");
//            addUrlParameter(url, "_selectedProperties", "id");

            final int fetchLimit = getPropertyFetchLimit();
            if (fetchLimit > 0) {
                addUrlParameter(url, "_endRow", String.valueOf(fetchLimit));
            }

            if (getPropertyNoFilterActive()) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            final String filterWhereCondition = getFilterWhereCondition(filterQueryObjects);
            final String customWhereCondition = getCustomWhereCondition();
            final String whereCondition;
            if (customWhereCondition.isEmpty()) {
                whereCondition = filterWhereCondition;
            } else {
                whereCondition = String.format("(%s) AND (%s)", filterWhereCondition, customWhereCondition);
            }
            addUrlParameter(url, "_where", URLEncoder.encode(whereCondition));

            final Map<String, String> headers = Collections.singletonMap("Authorization", getAuthenticationHeader(getPropertyUsername(), getPropertyPassword()));
            LogUtil.info(getClassName(), "getDataTotalRowCount url [" + url + "]");
            final HttpUriRequest request = getHttpRequest(url.toString(), "GET", headers);

            // kirim request ke server
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
                final String responseBody = br.lines().collect(Collectors.joining());
                final JSONObject jsonResponseBody = new JSONObject(responseBody);
                int totalRows = jsonResponseBody.getJSONObject("response").getInt("count");
                return totalRows;
            }
        } catch (IOException | OpenbravoClientException | JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return 0;
        }
    }

    @Override
    public String getName() {
        return getLabel();
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
        return "Openbravo DataList Binder";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/OpenbravoDataListBinder.json", null, false, "/messages/Openbravo");
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

    protected String getCountApiEndPoint(String baseUrl, String tableEntity) {
        return baseUrl + "/ws/com.kinnarastudio.openbravo.kecakadapter.RecordCount/" + tableEntity;
    }

    protected Map<String, String> convertJson(JSONObject json) {
        return JSONStream.of(json, Try.onBiFunction(JSONObject::getString))
                .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
    }

    protected boolean getPropertyNoFilterActive() {
        return "true".equalsIgnoreCase(getPropertyString("noFilterActive"));
    }

    protected int getPropertyFetchLimit() {
        return Integer.parseInt(getPropertyString("fetchLimit"));
    }

    protected String getFilterWhereCondition(DataListFilterQueryObject[] filterQueryObjects) {
        final Pattern p = Pattern.compile("\\?");
        String whereCondition = Optional.ofNullable(filterQueryObjects)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(filterQueryObject -> {
                    final String operator = ifEmptyThen(filterQueryObject.getOperator(), "AND");
                    final String query = filterQueryObject.getQuery().replaceAll("\\$_identifier", "\\.name");
                    final String[] values = filterQueryObject.getValues();

                    final StringBuilder condition = new StringBuilder();
                    final Matcher m = p.matcher(query);
                    int i = 0;
                    while (m.find()) {
                        if (i < values.length) {
                            m.appendReplacement(condition, "'" + values[i] + "'");
                        }
                        i++;
                    }
                    m.appendTail(condition);

                    return operator + " " + condition;
                })
                .collect(Collectors.joining(" ", "1=1 ", ""));
        return whereCondition;
    }

    protected String getCustomWhereCondition() {
        return getPropertyString("customWhereCondition");
    }
}
