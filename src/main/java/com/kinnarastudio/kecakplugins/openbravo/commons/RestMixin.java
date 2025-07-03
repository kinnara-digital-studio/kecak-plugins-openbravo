package com.kinnarastudio.kecakplugins.openbravo.commons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE;

/**
 * @author aristo
 */
public interface RestMixin extends PropertyEditable, Unclutter {
    Map<String, Form> formCache = new HashMap<>();

    /**
     * Get property "method"
     *
     * @return
     */
    default String getPropertyMethod() {
        return ifEmptyThen(getPropertyString("method"), "GET");
    }

    /**
     * Get property "headers"
     *
     * @return
     */
    default Map<String, String> getPropertyHeaders() {
        return getPropertyHeaders(null);
    }

    /**
     * Get property "headers"
     *
     * @return
     */
    default Map<String, String> getPropertyHeaders(WorkflowAssignment assignment) {
        return getKeyValueProperty(assignment, "headers");
    }

    /**
     * Get parameter as String
     *
     * @param assignment
     * @return
     */
    @Nonnull
    default String getParameterString(@Nullable WorkflowAssignment assignment) {
        return getParameters()
                .stream()
                .map(throwableFunction(m -> String.format("%s=%s", m.get("key"), URLEncoder.encode(AppUtil.processHashVariable(m.get("value"), assignment, null, null), "UTF-8"))))
                .collect(Collectors.joining("&"));
    }

    /**
     * Get property "parameters"
     *
     * @return
     */
    @Nonnull
    default List<Map<String, String>> getParameters() {
        return Optional.of("parameters")
                .map(this::getProperty)
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, String>) o)
                .collect(Collectors.toList());
    }

    /**
     * Get property "url" and "parameters" combined
     *
     * @return
     */
    default String getPropertyUrl() {
        return getPropertyUrl(null);
    }

    /**
     * Get property "url" and "parameters" combined
     *
     * @param assignment WorkflowAssignment
     * @return
     */
    default String getPropertyUrl(@Nullable WorkflowAssignment assignment) {
        String url = AppUtil.processHashVariable(getPropertyString("url"), assignment, null, null);
        String parameter = getParameterString(assignment).trim();

        if (!parameter.isEmpty()) {
            url += (url.trim().matches("https?://.+\\?.*") ? "&" : "?") + parameter;
        }

        // buat apa?
        return url.replaceAll("#", ":");
    }

    @Nonnull
    default Map<String, String> getPropertyFormData(WorkflowAssignment assignment) {
        return getKeyValueProperty(assignment, "formData");
    }

    /**
     * Get grid property that contains "key" and "value"
     *
     * @param assignment   WorkflowAssignment
     * @param propertyName String
     * @return
     */
    default Map<String, String> getKeyValueProperty(WorkflowAssignment assignment, String propertyName) {
        return Optional.of(propertyName)
                .map(this::getProperty)
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElse(Stream.empty())
                .filter(Objects::nonNull)
                .map(o -> (Map<String, String>) o)
                .collect(Collectors.toMap(
                        m -> processHashVariable(m.getOrDefault("key", ""), assignment),
                        m -> processHashVariable(m.getOrDefault("value", ""), assignment)));
    }

    default String processHashVariable(String content, @Nullable WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(content, assignment, null, null);
    }

    /**
     * Get property "ignoreCertificateError"
     *
     * @return
     */
    default boolean isIgnoreCertificateError() {
        return "true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"));
    }

    /**
     * Get property "recordPath"
     *
     * @return
     */
    default String getPropertyRecordPath() {
        return getPropertyRecordPath(null);
    }

    default String getPropertyRecordPath(WorkflowAssignment workflowAssignment) {
        return processHashVariable(getPropertyString("recordPath"), workflowAssignment);
    }

    /**
     * Get property "debug"
     *
     * @return
     */
    default boolean isDebugging() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }

    /**
     * Get JSON request body
     *
     * @param entity     String
     * @param assignment WorkflowAssignment
     * @return
     * @throws OpenbravoClientException
     */
    default HttpEntity getJsonRequestEntity(String entity, @Nullable WorkflowAssignment assignment) throws OpenbravoClientException {
        final String body = AppUtil.processHashVariable(entity, assignment, null, null);
        if (isJsonObject(body) || isJsonArray(body)) {
            return new StringEntity(body, ContentType.APPLICATION_JSON);
        } else {
            throw new OpenbravoClientException("Invalid json : " + entity);
        }
    }

    /**
     * Verify input string is JSON
     *
     * @param inputString
     * @return
     * @throws OpenbravoClientException
     */
    default String verifyJsonString(String inputString) throws OpenbravoClientException {
        try {
            return new JSONObject(inputString).toString();
        } catch (JSONException jsonException) {
            try {
                return new JSONArray(inputString).toString();
            } catch (JSONException jsonArrayException) {
                if (isDebugging()) {
                    throw new OpenbravoClientException("Invalid json : " + inputString);
                } else {
                    throw new OpenbravoClientException("Invalid json");
                }
            }
        }
    }

    default boolean isJsonObject(String inputString) {
        try {
            new JSONObject(inputString);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    default boolean isJsonArray(String inputString) {
        try {
            new JSONArray(inputString);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Get multipart request body
     *
     * @param multipart
     * @param assignment
     * @return
     */
    default HttpEntity getMultipartRequestEntity(Map<String, String> multipart, @Nullable WorkflowAssignment assignment) {
        final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        builder.setMode(BROWSER_COMPATIBLE);

        multipart.forEach(builder::addTextBody);

        return builder.build();
    }

    /**
     * Get HTTP Client
     *
     * @param ignoreCertificate
     * @return
     * @throws OpenbravoClientException
     */
    default HttpClient getHttpClient(boolean ignoreCertificate) throws OpenbravoClientException {
        try {
            if (ignoreCertificate) {
                SSLContext sslContext = new SSLContextBuilder()
                        .loadTrustMaterial(null, (certificate, authType) -> true).build();
                return HttpClients.custom().setSSLContext(sslContext)
                        .setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .build();
            } else {
                return HttpClientBuilder.create().build();
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new OpenbravoClientException(e);
        }
    }

    default HttpUriRequest getHttpRequest(String url, String method, Map<String, String> headers) throws OpenbravoClientException {
        return getHttpRequest(null, url, method, headers);
    }

    /**
     * Get HTTP request
     *
     * @param assignment
     * @param url
     * @param method
     * @param headers
     * @return
     * @throws OpenbravoClientException
     */
    default HttpUriRequest getHttpRequest(@Nullable WorkflowAssignment assignment, String url, String method, Map<String, String> headers) throws OpenbravoClientException {
        return getHttpRequest(assignment, url, method, headers, new FormRow());
    }

    default HttpUriRequest getHttpRequest(@Nullable WorkflowAssignment assignment, String url, String method, Map<String, String> headers, @Nonnull FormRow row) throws OpenbravoClientException {
        @Nullable HttpEntity httpEntity;
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            httpEntity = null;
        } else {
            try {
                final JSONObject jsonData = Optional.of(row)
                        .map(FormRow::entrySet)
                        .stream()
                        .flatMap(Collection::stream)
                        .collect(JSONCollectors.toJSONObject(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue())));

                final JSONObject jsonRequest = new JSONObject();
                jsonRequest.put("data", jsonData);

                httpEntity = getJsonRequestEntity(jsonRequest.toString(), assignment);
            } catch (JSONException e) {
                throw new OpenbravoClientException(e);
            }
        }
        return getHttpRequest(assignment, url, method, headers, httpEntity);
    }

    /**
     * Get HTTP request
     *
     * @param assignment
     * @param url
     * @param method
     * @param headers
     * @param httpEntity
     * @return
     * @throws OpenbravoClientException
     */
    default HttpUriRequest getHttpRequest(WorkflowAssignment assignment, String url, String method, Map<String, String> headers, @Nullable HttpEntity httpEntity) throws OpenbravoClientException {
        final HttpRequestBase request;

        if ("GET".equals(method)) {
            request = new HttpGet(url);
        } else if ("POST".equals(method)) {
            request = new HttpPost(url);
        } else if ("PUT".equals(method)) {
            request = new HttpPut(url);
        } else if ("DELETE".equals(method)) {
            request = new HttpDelete(url);
        } else {
            throw new OpenbravoClientException("Method [" + method + "] not supported");
        }

        headers.forEach((k, v) -> request.addHeader(k, AppUtil.processHashVariable(v, assignment, null, null)));

        if (httpEntity != null && request instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) request).setEntity(httpEntity);
        }

        if (isDebugging()) {
            LogUtil.info(getClassName(), "getHttpRequest : url [" + request.getURI() + "] method [" + request.getMethod() + "]");

            if (request instanceof HttpEntityEnclosingRequestBase) {
                HttpEntityEnclosingRequestBase entityEnclosingRequest = (HttpEntityEnclosingRequestBase) request;
                String requestContentType = Optional.of(entityEnclosingRequest)
                        .map(HttpEntityEnclosingRequestBase::getEntity)
                        .map(HttpEntity::getContentType)
                        .map(Header::getValue)
                        .orElse("");
                String requestMethod = Optional.of(entityEnclosingRequest)
                        .map(HttpEntityEnclosingRequestBase::getMethod)
                        .orElse("");

                Optional.of(entityEnclosingRequest)
                        .map(HttpEntityEnclosingRequestBase::getEntity)
                        .map(throwableFunction(HttpEntity::getContent)).ifPresent(inputStream -> {
                            try (BufferedReader br = new BufferedReader(new InputStreamReader(entityEnclosingRequest.getEntity().getContent()))) {
                                String bodyContent = br.lines().collect(Collectors.joining());
                                if (isDebugging()) {
                                    LogUtil.info(getClassName(), "getHttpRequest : Content-Type [" + requestContentType + "] method [" + requestMethod + "] bodyContent [" + bodyContent + "]");
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        return request;
    }

    /**
     * Get primary key
     *
     * @param properties
     * @return
     */
    default String getPrimaryKey(Map properties) {
        return Optional.of(properties)
                .map(m -> m.get("recordId"))
                .map(String::valueOf)
                .orElseGet(() -> {
                    PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
                    WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
                    AppService appService = (AppService) pluginManager.getBean("appService");

                    return Optional.of(workflowAssignment)
                            .map(WorkflowAssignment::getProcessId)
                            .map(s -> appService.getOriginProcessId(s))
                            .orElse("");
                });
    }

    /**
     * Get status code
     *
     * @param response
     * @return
     * @throws OpenbravoClientException
     */
    default int getResponseStatus(@Nonnull HttpResponse response) throws OpenbravoClientException {
        return Optional.of(response)
                .map(HttpResponse::getStatusLine)
                .map(StatusLine::getStatusCode)
                .orElseThrow(() -> new OpenbravoClientException("Error getting status code"));
    }

    /**
     * Get content type from response
     *
     * @param response
     * @return
     * @throws OpenbravoClientException
     */
    default String getResponseContentType(@Nonnull HttpResponse response) throws OpenbravoClientException {
        return Optional.of(response)
                .map(HttpResponse::getEntity)
                .map(HttpEntity::getContentType)
                .map(Header::getValue)
                .orElseGet(() -> {
                    LogUtil.warn(getClassName(), "Empty header content-type");
                    return "";
                });
    }

    /**
     * Get body payload
     *
     * @param response
     * @return
     * @throws OpenbravoClientException
     */
    default String getResponseBody(@Nonnull HttpResponse response) throws OpenbravoClientException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
            return br.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new OpenbravoClientException(e);
        }
    }

    /**
     * Returns 200ish, 300ish, 400ish, or 500ish
     *
     * @param status
     * @return
     */
    default int getStatusGroupCode(int status) {
        return status - (status % 100);
    }

    /**
     * Response is JSON
     *
     * @param response HttpResponse
     * @return boolean
     * @throws OpenbravoClientException
     */
    default boolean isJsonResponse(@Nonnull HttpResponse response) throws OpenbravoClientException {
        return getResponseContentType(response).contains("json");
    }

    /**
     * Response is XML
     *
     * @param response HttpResponse
     * @return
     * @throws OpenbravoClientException
     */
    default boolean isXmlResponse(@Nonnull HttpResponse response) throws OpenbravoClientException {
        return getResponseContentType(response).contains("xml");
    }

    /**
     * Handle JSON response
     *
     * @param response
     * @return
     * @throws OpenbravoClientException
     */
    default FormRowSet handleJsonResponse(@Nonnull HttpResponse response, Object[] mapping) throws OpenbravoClientException {
        Pattern recordPattern = Pattern.compile(getPropertyRecordPath().replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);

        FormRowSet jsonResult = Optional.of(response)
                .map(HttpResponse::getEntity)
                .map(throwableFunction(HttpEntity::getContent))
                .map(throwableFunction(is -> {
                    try (InputStreamReader streamReader = new InputStreamReader(is);
                         JsonReader reader = new JsonReader(streamReader)) {

                        JsonParser parser = new JsonParser();
                        JsonElement jsonElement = parser.parse(reader);

                        if (isDebugging()) {
                            LogUtil.info(getClass().getName(), "handleJsonResponse : jsonElement [" + jsonElement.toString() + "]");
                        }

                        JsonHandler handler = new JsonHandler(jsonElement, recordPattern);
                        return handler.parse(1);
                    }
                })).orElseThrow(() -> new OpenbravoClientException("Error parsing JSON response"));

        FormRowSet result = new FormRowSet();
        for (FormRow formRow : jsonResult) {
            FormRow newRow = new FormRow();
            if (mapping != null) {
                for (Object obj : mapping) {
                    final Map<String, String> row = (Map<String, String>) obj;
                    String restProperties = row.get("restProperties");
                    String formField = row.get("formField");
                    if (formRow.getProperty(restProperties) != null) {
                        newRow.setProperty(formField, formRow.getProperty(restProperties));
                        result.add(newRow);
                    }
                }
            }
        }
        result.addAll(jsonResult);
        return result;
    }

    /**
     * Handle XML response
     *
     * @param response
     * @return
     * @throws OpenbravoClientException
     */
    default FormRowSet handleXmlResponse(@Nonnull HttpResponse response) throws OpenbravoClientException {
        try {
            FormRowSet result = new FormRowSet();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(response.getEntity().getContent(),
                    new LoadBinderSaxHandler(
                            Pattern.compile(getPropertyRecordPath().replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
                            result
                    ));

            return result;
        } catch (UnsupportedOperationException | SAXException | ParserConfigurationException | IOException e) {
            throw new OpenbravoClientException(e);
        }
    }

    /**
     * Handle response
     *
     * @param response
     * @return
     * @throws OpenbravoClientException
     */
    @Nullable
    default FormRowSet handleResponse(@Nonnull HttpResponse response, Object[] mapping) throws OpenbravoClientException {
        int statusCode = getResponseStatus(response);
        String responseContentType = getResponseContentType(response);

        if (isDebugging()) {
            LogUtil.info(getClass().getName(), "handleResponse : Status [" + statusCode + "] Content-Type [" + responseContentType + "]");
        }

        if (statusCode != HttpServletResponse.SC_OK) {
            LogUtil.warn(getClassName(), "Response status [" + getResponseStatus(response) + "] message [" + getResponseBody(response) + "]");
            return null;
        }

        if (isJsonResponse(response)) {
            return handleJsonResponse(response, mapping);
        } else if (isXmlResponse(response)) {
            return handleXmlResponse(response);
        } else {
            if (isDebugging()) {
                LogUtil.info(getClassName(), "handleResponse : response [" + getResponseBody(response) + "]");
            }

            LogUtil.warn(getClassName(), "Unsupported response content type [" + responseContentType + "], assume JSON response");
            return handleJsonResponse(response, mapping);
        }
    }

//    /**
//     * Set HTTP entity
//     *
//     * @param request
//     * @param entity
//     * @throws RestClientException
//     */
//    default void setHttpEntity(HttpEntityEnclosingRequestBase request, String entity) throws RestClientException {
//        try {
//            setHttpEntity(request, new JSONObject(entity));
//        } catch (JSONException e) {
//            throw new RestClientException(e);
//        }
//    }
//
//    /**
//     * Set http entity
//     *
//     * @param request
//     * @param entity
//     * @throws RestClientException
//     */
//    default void setHttpEntity(HttpEntityEnclosingRequestBase request, @Nonnull HttpEntity entity) {
//        String method = request.getMethod();
//
//        if(("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
//            LogUtil.warn(getClass().getName(), "Body request will be ignored for method [" + method + "]");
//            return;
//        }
//
//        request.setEntity(entity);
//    }
//
//    default void setHttpEntity(HttpEntityEnclosingRequestBase request, JSONObject jsonObject) throws RestClientException {
//        try {
//            setHttpEntity(request, new StringEntity(jsonObject.toString()));
//        } catch (UnsupportedEncodingException e) {
//            throw new RestClientException(e);
//        }
//    }

    /**
     * Get grid properties
     *
     * @param properties
     * @param propertyName
     * @return
     */
    default List<Map<String, String>> getGridProperties(Map<String, Object> properties, String propertyName) {
        return Optional.of(propertyName)
                .map(properties::get)
                .filter(o -> o instanceof Object[])
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(o -> o instanceof Map)
                .map(o -> (Map<String, Object>) o)
                .map(m -> m.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                .collect(Collectors.toList());
    }

    /**
     * Get property "dataListFilter"
     *
     * @param obj
     * @return
     */
    default Map<String, List<String>> getPropertyDataListFilter(PropertyEditable obj, WorkflowAssignment workflowAssignment) {
        final Map<String, List<String>> filters = Optional.of("dataListFilter")
                .map(obj::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, Object>) o)
                .map(m -> {
                    Map<String, List<String>> map = new HashMap<>();
                    String name = String.valueOf(m.get("name"));
                    String value = Optional.of("value")
                            .map(m::get)
                            .map(String::valueOf)
                            .map(s -> processHashVariable(s, workflowAssignment))
                            .orElse("");

                    map.put(name, Collections.singletonList(value));
                    return map;
                })
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> {
                            List<String> result = new ArrayList<>(e1);
                            result.addAll(e2);
                            return result;
                        })
                );

        return filters;
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws OpenbravoClientException
     */
    @Nonnull
    default DataList generateDataList(String datalistId) throws OpenbravoClientException {
        return generateDataList(datalistId, null);
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws OpenbravoClientException
     */
    @Nonnull
    default DataList generateDataList(String datalistId, WorkflowAssignment workflowAssignment) throws OpenbravoClientException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(s -> processHashVariable(s, workflowAssignment))
                .map(dataListService::fromJson)
                .orElseThrow(() -> new OpenbravoClientException("DataList [" + datalistId + "] not found"));
    }

    default void addUrlParameter(@Nonnull final StringBuilder url, @Nonnull String parameterName, @Nonnull String parameterValue) {
        url.append(String.format("%s%s=%s", (url.toString().contains("?") ? "&" : "?"), parameterName, parameterValue));
    }

    /**
     * Get DataList row as JSONObject
     *
     * @param dataListId
     * @return
     */

    @Nonnull
    default JSONObject getDataListRow(String dataListId, @Nonnull final Map<String, List<String>> filters) throws OpenbravoClientException {
        DataList dataList = generateDataList(dataListId);

        getCollectFilters(dataList, filters);

        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        if (rows == null) {
            throw new OpenbravoClientException("Error retrieving row from dataList [" + dataListId + "]");
        }

        JSONArray jsonArrayData = Optional.of(dataList)
                .map(DataList::getRows)
                .map(r -> (DataListCollection<Map<String, Object>>) r)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(m -> formatRow(dataList, m))
                .map(JSONObject::new)
                .collect(Collector.of(JSONArray::new, JSONArray::put, JSONArray::put));

        JSONObject jsonResult = new JSONObject();
        try {
            jsonResult.put("data", jsonArrayData);
        } catch (JSONException e) {
            throw new OpenbravoClientException(e);
        }

        return jsonResult;
    }

    /**
     * Get collect filters
     *
     * @param dataList Input/Output parameter
     */
    default void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Optional.of(dataList)
                .map(DataList::getFilters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> !l.isEmpty())
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.getFilterQueryObjects();
        dataList.setFilters(null);
    }

    /**
     * Format Row
     *
     * @param dataList
     * @param row
     * @return
     */
    @Nonnull
    default Map<String, String> formatRow(@Nonnull DataList dataList, @Nonnull final Map<String, Object> row) {
        return Optional.of(dataList)
                .map(DataList::getColumns)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(not(DataListColumn::isHidden))
                .map(DataListColumn::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(s -> s, s -> formatValue(dataList, row, s)));
    }

    /**
     * Format
     *
     * @param dataList DataList
     * @param row      Row
     * @param field    Field
     * @return
     */
    @Nonnull
    default String formatValue(@Nonnull final DataList dataList, @Nonnull final Map<String, Object> row, String field) {
        String value = Optional.of(field)
                .map(row::get)
                .map(String::valueOf)
                .orElse("");

        return Optional.of(dataList)
                .map(DataList::getColumns)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(c -> field.equals(c.getName()))
                .findFirst()
                .map(column -> Optional.of(column)
                        .map(throwableFunction(DataListColumn::getFormats))
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(f -> f.format(dataList, column, row, value))
                        .map(s -> s.replaceAll("<[^>]*>", ""))
                        .orElse(value))
                .orElse(value);
    }

    /**
     * @param formDefId
     * @return
     */
    @Nonnull
    default Form generateForm(String formDefId) throws OpenbravoClientException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");

        // check in cache
        if (formCache.containsKey(formDefId))
            return formCache.get(formDefId);

        // proceed without cache
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef != null && formDefId != null && !formDefId.isEmpty()) {
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                String json = formDef.getJson();
                Form form = (Form) formService.createElementFromJson(json);

                // put in cache if possible
                formCache.put(formDefId, form);

                return form;
            }
        }

        throw new OpenbravoClientException("Error generating form [" + formDefId + "]");
    }

    default FormRow generateFormRow(DataList dataList, Map<String, Object> input) {
        String idField = Optional.of(dataList)
                .map(DataList::getBinder)
                .map(DataListBinder::getPrimaryKeyColumnName)
                .orElse("id");

        return Optional.ofNullable(input)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .collect(() -> {
                    FormRow row = new FormRow();
                    row.setId(input.getOrDefault(idField, "").toString());
                    return row;
                }, (r, e) -> r.setProperty(e.getKey(), e.getValue().toString()), FormRow::putAll);
    }

    /**
     * @param path
     * @param element
     * @param recordPattern
     * @param fieldPattern
     * @param isLookingForRecordPattern
     * @param rowSet
     * @param row
     * @param foreignKeyField
     * @param primaryKey
     */
    default void parseJson(String path, @Nonnull JsonElement element, @Nonnull Pattern recordPattern, @Nonnull Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, @Nonnull final FormRowSet rowSet, @Nullable FormRow row, final String foreignKeyField, final String primaryKey) {
        Matcher matcher = recordPattern.matcher(path);
        boolean isRecordPath = matcher.find() && isLookingForRecordPattern && element.isJsonObject();

        if (isRecordPath) {
            // start looking for value and label pattern
            row = new FormRow();
        }

        if (element.isJsonObject()) {
            parseJsonObject(path, (JsonObject) element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
            if (isRecordPath) {
                if (foreignKeyField != null && !foreignKeyField.isEmpty())
                    row.setProperty(foreignKeyField, primaryKey);
                rowSet.add(row);
            }
        } else if (element.isJsonArray()) {
            parseJsonArray(path, (JsonArray) element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
            if (isRecordPath) {
                if (foreignKeyField != null && !foreignKeyField.isEmpty())
                    row.setProperty(foreignKeyField, primaryKey);
                rowSet.add(row);
            }
        } else if (element.isJsonPrimitive() && !isLookingForRecordPattern) {
            for (Map.Entry<String, Pattern> entry : fieldPattern.entrySet()) {
                setRow(entry.getValue().matcher(path), entry.getKey(), element.getAsString(), row);
            }
        }
    }

    default void setRow(Matcher matcher, String key, String value, FormRow row) {
        if (matcher.find() && row != null && row.getProperty(key) == null) {
            row.setProperty(key, value);
        }
    }

    default void parseJsonObject(String path, JsonObject json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row, String foreignKeyField, String primaryKey) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            parseJson(path + "." + entry.getKey(), entry.getValue(), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
        }
    }

    default void parseJsonArray(String path, JsonArray json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row, String foreignKeyField, String primaryKey) {
        for (int i = 0, size = json.size(); i < size; i++) {
            parseJson(path, json.get(i), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
        }
    }

    default Optional<String> getJsonResultVariableValue(String variablePath, JsonElement element) {
        if (variablePath == null || element == null) {
            return Optional.empty();
        }

        JsonElement currentElement = element;
        for (String variable : variablePath.split("\\.")) {
            if (currentElement == null) {
                break;
            }
            currentElement = getJsonResultVariable(variable, currentElement);
        }

        return Optional.ofNullable(currentElement).map(JsonElement::getAsString);
    }

    /**
     * @param variable : variable name to search
     * @param element  : element to search for variable
     * @return
     */
    default JsonElement getJsonResultVariable(@Nonnull String variable, @Nonnull JsonElement element) {
        if (element.isJsonObject())
            return getJsonResultVariableFromObject(variable, element.getAsJsonObject());
        else if (element.isJsonArray())
            return getJsonResultVariableFromArray(variable, element.getAsJsonArray());
        else if (element.isJsonPrimitive())
            return element;
        return null;
    }

    default JsonElement getJsonResultVariableFromObject(String variable, JsonObject object) {
        return object.get(variable);
    }

    default JsonElement getJsonResultVariableFromArray(String variable, JsonArray array) {
        for (JsonElement item : array) {
            JsonElement result = getJsonResultVariable(variable, item);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
