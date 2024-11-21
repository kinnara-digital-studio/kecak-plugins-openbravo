package com.kinnarastudio.kecakplugins.openbravo.service;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.commons.jsonstream.model.JSONObjectEntry;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.RestClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OpenbravoService {
    private static OpenbravoService instance = null;

    private boolean ignoreCertificateError = false;

    private boolean isDebug = false;

    private boolean shortCircuit = false;

    private boolean noFilterActive = false;

    private boolean cutCircuit = false;

    private OpenbravoService() {
    }

    public static synchronized OpenbravoService getInstance() {
        if (instance == null) instance = new OpenbravoService();
        instance.shortCircuit = false;
        instance.cutCircuit = false;
        instance.isDebug = false;
        instance.ignoreCertificateError = false;
        instance.noFilterActive = false;
        return instance;
    }

    @Nonnull
    public Map<String, String> get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String primaryKey, @Nonnull String username, @Nonnull String password) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "get : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] primaryKey [" + primaryKey + "] username [" + username + "] password [" + (isDebug ? "*" : password) + "]");

        try {
            final RestService restService = RestService.getInstance();
            restService.setIgnoreCertificate(ignoreCertificateError);
            restService.setDebug(isDebug);

            final StringBuilder url = new StringBuilder()
                    .append(baseUrl)
                    .append("/org.openbravo.service.json.jsonrest/")
                    .append(tableEntity)
                    .append("/")
                    .append(primaryKey);

            if (noFilterActive) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getAuthenticationHeader(username, password));
            final HttpClient client = restService.getHttpClient();

            final HttpUriRequest request = restService.getHttpRequest(url.toString(), headers);

            final HttpResponse response = client.execute(request);

            final int statusCode = restService.getResponseStatus(response);
            if (restService.getStatusGroupCode(statusCode) != 200) {
                throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success) url [" + url + "]");
            } else if (statusCode != 200) {
                LogUtil.warn(getClass().getName(), "Response code [" + statusCode + "] is considered as success");
            }

            if (!restService.isJsonResponse(response)) {
                throw new RestClientException("Content type is not JSON");
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final String responsePayload = br.lines().collect(Collectors.joining());

                if (isDebug) {
                    LogUtil.info(getClass().getName(), "get : responsePayload [" + responsePayload + "]");
                }

                final JSONObject jsonResponse = new JSONObject(responsePayload)
                        .getJSONObject("response");


                final int status = jsonResponse.optInt("status", -1);
                if (status != 0) {
                    throw new OpenbravoClientException(responsePayload);
                }

                final JSONObject jsonData = jsonResponse.getJSONObject("data");
                return JSONStream.of(jsonData, Try.onBiFunction(JSONObject::getString))
                        .peek(e -> {
                            if (isDebug && "_identifier".equals(e.getKey())) {
                                LogUtil.info(getClass().getName(), "get : identifier [" + e.getValue() + "]");
                            }
                        })
                        .collect(Collectors.toUnmodifiableMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue));
            }
        } catch (RestClientException | JSONException | IOException e) {
            throw new OpenbravoClientException(e);
        }
    }

    public Map<String, Object>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, Map<String, String> filter) throws OpenbravoClientException {
        return get(baseUrl, tableEntity, username, password, filter, null, null, null, null);
    }

    public Map<String, Object>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, Map<String, String> filter, @Nullable String sort, @Nullable Boolean desc, @Nullable Integer start, @Nullable Integer end) throws OpenbravoClientException {
        final String where = getFilterWhereCondition(filter);
        return get(baseUrl, tableEntity, username, password, where, null, sort, desc, start, end);
    }

    public Map<String, Object>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nullable String condition, @Nullable Object[] arguments, @Nullable String sort, @Nullable Boolean desc, @Nullable Integer start, @Nullable Integer end) throws OpenbravoClientException {
        return get(baseUrl, tableEntity, username, password, null, condition, arguments, sort, desc, start, end);
    }

    public Map<String, Object>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nullable String[] fields, @Nullable String condition, Object[] arguments, @Nullable String sort, @Nullable Boolean desc, @Nullable Integer start, @Nullable Integer end) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "get : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "] password [" + (isDebug ? "*" : password) + "]");

        try {
            final RestService restService = RestService.getInstance();
            restService.setIgnoreCertificate(ignoreCertificateError);
            restService.setDebug(isDebug);

            final StringBuilder url = new StringBuilder()
                    .append(baseUrl)
                    .append("/org.openbravo.service.json.jsonrest/")
                    .append(tableEntity);

            if(fields != null && fields.length > 0) {
                addUrlParameter(url, "_selectedProperties", String.join(",", fields));
            }

            if (noFilterActive) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            if(start != null) {
                addUrlParameter(url, "_startRow", start.toString());
            }

            if(end != null) {
                addUrlParameter(url, "_endRow", end.toString());
            }

            if (condition != null && !condition.isEmpty()) {
//                final Pattern p = Pattern.compile("\\?");
//                final Matcher m = p.matcher(condition);
//                final StringBuilder sb = new StringBuilder();
//                for (int i = 0; m.find(); i++) {
//                    final Object argument = arguments[i];
//
//                    final String replacement;
//                    if(argument instanceof Integer || argument instanceof Long) {
//                        replacement = "%d";
//                    } else if(argument instanceof Float || argument instanceof Double) {
//                        replacement = "%f";
//                    } else if(argument instanceof Date) {
//                        replacement = "TO_DATE('%s', 'YYYY-MM-DD')";
//                    } else {
//                        replacement = "'%s'";
//                    }
//
//                    m.appendReplacement(sb, replacement);
//                }

                final String where = arguments == null ? condition : String.format(condition.replaceAll("\\?", "'%s'"), arguments);
                addUrlParameter(url, "_where", URLEncoder.encode(where));
            }

            if (sort != null && !sort.isEmpty()) {
                if (desc != null && desc) {
                    sort += " desc";
                }
                addUrlParameter(url, "", URLEncoder.encode(sort));
            }

            if (isDebug) {
                LogUtil.info(getClass().getName(), "get : url [" + url + "]");
            }

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getAuthenticationHeader(username, password));
            final HttpUriRequest request = restService.getHttpRequest(url.toString(), headers);

            final HttpClient client = restService.getHttpClient();
            final HttpResponse response = client.execute(request);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final String responsePayload = br.lines().collect(Collectors.joining());

                if (isDebug) {
                    LogUtil.info(getClass().getName(), "get : responsePayload [" + responsePayload + "]");
                }

                final int statusCode = restService.getResponseStatus(response);
                if (restService.getStatusGroupCode(statusCode) != 200) {
                    throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success) url [" + url + "]");
                } else if (statusCode != 200) {
                    LogUtil.warn(getClass().getName(), "Response code [" + statusCode + "] is considered as success");
                }

                if (!restService.isJsonResponse(response)) {
                    throw new RestClientException("Content type is not JSON");
                }

                final JSONObject jsonResponse = new JSONObject(responsePayload)
                        .getJSONObject("response");

                final int status = jsonResponse.optInt("status", -1);
                if (status != 0) {
                    throw new OpenbravoClientException(responsePayload);
                }

                final JSONArray jsonData = jsonResponse.getJSONArray("data");
                return JSONStream.of(jsonData, Try.onBiFunction(JSONArray::getJSONObject))
                        .map(json -> JSONStream.of(json, Try.onBiFunction(JSONObject::get))
                                .collect(Collectors.toMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue)))
                        .toArray(Map[]::new);
            }
        } catch (RestClientException | JSONException | IOException e) {
            throw new OpenbravoClientException(e);
        }
    }

    public int count(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nullable String where) throws OpenbravoClientException {
        final RestService restService = RestService.getInstance();
        restService.setIgnoreCertificate(ignoreCertificateError);
        restService.setDebug(isDebug);

        try {
            final StringBuilder url = new StringBuilder()
                    .append(baseUrl)
                    .append("/ws/com.kinnarastudio.openbravo.kecakadapter.RecordCount/")
                    .append(tableEntity);

            if (noFilterActive) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            if (where != null && !where.isEmpty()) {
                addUrlParameter(url, "_where", URLEncoder.encode(where));
            }

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getAuthenticationHeader(username, password));
            final HttpUriRequest request = restService.getHttpRequest(url.toString(), headers);

            // kirim request ke server
            final HttpClient client = restService.getHttpClient();
            final HttpResponse response = client.execute(request);

            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                final String responsePayload = br.lines().collect(Collectors.joining());

                if (isDebug) {
                    LogUtil.info(getClass().getName(), "count : responsePayload [" + responsePayload + "]");
                }

                final int statusCode = restService.getResponseStatus(response);
                if (restService.getStatusGroupCode(statusCode) != 200) {
                    throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success) url [" + url + "]");
                } else if (statusCode != 200) {
                    LogUtil.warn(getClass().getName(), "Response code [" + statusCode + "] is considered as success");
                }

                if (!restService.isJsonResponse(response)) {
                    throw new RestClientException("Content type is not JSON");
                }

                final JSONObject jsonResponse = new JSONObject(responsePayload)
                        .getJSONObject("response");

                return jsonResponse.getInt("count");
            }
        } catch (RestClientException | JSONException | IOException e) {
            throw new OpenbravoClientException(e);
        }
    }

    public synchronized Map<String, Object>[] post(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nonnull Map<String, Object>[] rows) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "post : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "] password [" + (isDebug ? "*" : password) + "]");

        if (isDebug) {
            for (Map<String, Object> row : rows) {
                LogUtil.info(getClass().getName(), "post : rows [" + row + "]");
            }
        }
        try {
            final RestService restService = RestService.getInstance();
            restService.setIgnoreCertificate(ignoreCertificateError);
            restService.setDebug(isDebug);

            final StringBuilder url = new StringBuilder().append(baseUrl).append("/org.openbravo.service.json.jsonrest/").append(tableEntity);
            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getAuthenticationHeader(username, password));
            final HttpClient client = restService.getHttpClient();

            cutCircuit = false;


            final Map[] result = Arrays.stream(rows)
                    .map(row -> {
                        if (cutCircuit) return null;
                        try {
                            final JSONObject jsonBody = new JSONObject() {{
                                put("data", row.entrySet()
                                        .stream()
                                        .collect(JSONCollectors.toJSONObject(Map.Entry::getKey, Map.Entry::getValue)));
                            }};

                            final HttpUriRequest request = restService.getHttpRequest(url.toString(), "POST", headers, jsonBody);

                            final HttpResponse response = client.execute(request);

                            final int statusCode = restService.getResponseStatus(response);
                            if (restService.getStatusGroupCode(statusCode) != 200) {
                                throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success) url [" + url + "]");
                            } else if (statusCode != 200) {
                                LogUtil.warn(getClass().getName(), "Response code [" + statusCode + "] is considered as success");
                            }

                            if (!restService.isJsonResponse(response)) {
                                throw new RestClientException("Content type is not JSON");
                            }

                            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                                final String responsePayload = br.lines().collect(Collectors.joining());
                                if (isDebug) {
                                    LogUtil.info(getClass().getName(), "post : responsePayload [" + responsePayload + "]");
                                }
                                final JSONObject jsonResponse = new JSONObject(responsePayload)
                                        .getJSONObject("response");

                                final int status = jsonResponse.getInt("status");
                                if (status != 0) {
                                    if (status == -4) {
                                        final JSONObject jsonErrors = jsonResponse.getJSONObject("errors");
                                        final Map<String, String> errors = JSONStream.of(jsonErrors, Try.onBiFunction(JSONObject::getString))
                                                .collect(Collectors.toUnmodifiableMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue));
                                        throw new OpenbravoClientException(errors);
                                    } else {
                                        throw new OpenbravoClientException(responsePayload);
                                    }
                                }

                                final JSONArray jsonData = jsonResponse.getJSONArray("data");
                                final Map<String, Object> data = JSONStream.of(jsonData, Try.onBiFunction(JSONArray::getJSONObject))
                                        .findFirst()
                                        .stream()
                                        .flatMap(json -> JSONStream.of(json, Try.onBiFunction(JSONObject::get)))
                                        .collect(Collectors.toUnmodifiableMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue));

                                if (isDebug) {
                                    LogUtil.info(getClass().getName(), "post : data result posted [" + data + "]");
                                }
                                LogUtil.info(getClass().getName(), "post : data posted [" + data.get("id") + "][" + data.get("_identifier") + "]");
                                return data;
                            }
                        } catch (OpenbravoClientException | RestClientException | IOException | JSONException e) {
                            LogUtil.error(getClass().getName(), e, e.getMessage());
                            if (shortCircuit) {
                                cutCircuit = true;
                                return null;
                            }

                            return Collections.<String, String>emptyMap();
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(Map[]::new);

            if (rows.length != result.length)
                throw new OpenbravoClientException("Request length [" + rows.length + "] and response length [" + result.length + "] are different");

            return (Map<String, Object>[]) result;
        } catch (RestClientException e) {
            throw new OpenbravoClientException(e);
        }
    }

    public DataList generateDataList(String datalistId, Map<String, String[]> filters, WorkflowAssignment workflowAssignment) throws OpenbravoClientException, OpenbravoClientException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        org.joget.apps.datalist.service.DataListService dataListService = (org.joget.apps.datalist.service.DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        DataList dataList = Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(s -> processHashVariable(s, workflowAssignment))
                .map(dataListService::fromJson)
                .orElseThrow(() -> new OpenbravoClientException("DataList [" + datalistId + "] not found"));

        Optional.of(dataList)
                .map(DataList::getFilters)
                .stream()
                .flatMap(Arrays::stream)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> l.length > 0)
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.getFilterQueryObjects();
        dataList.setFilters(null);

        return dataList;
    }

    public Form generateForm(String formDefId) throws OpenbravoClientException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");

        // proceed without cache
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef != null && formDefId != null && !formDefId.isEmpty()) {
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                String json = formDef.getJson();
                Form form = (Form) formService.createElementFromJson(json);

                // put in cache if possible

                return form;
            }
        }

        throw new OpenbravoClientException("Error generating form [" + formDefId + "]");
    }

    protected String processHashVariable(String content, @Nullable WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(content, assignment, null, null);
    }

    protected void addUrlParameter(@Nonnull final StringBuilder url, String parameterName, String parameterValue) {
        url.append(String.format("%s%s=%s", (url.toString().contains("?") ? "&" : "?"), parameterName, parameterValue));
    }

    public void setIgnoreCertificateError(boolean ignoreCertificateError) {
        this.ignoreCertificateError = ignoreCertificateError;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

    public void setShortCircuit(boolean shortCircuit) {
        this.shortCircuit = shortCircuit;
    }

    public void setNoFilterActive(boolean noFilterActive) {
        this.noFilterActive = noFilterActive;
    }

    protected String getFilterWhereCondition(Map<String, String> filter) {
        return Optional.ofNullable(filter)
                .map(Map::entrySet)
                .stream()
                .flatMap(Collection::stream)
                .map(e -> e.getKey() + "='" + e.getValue() + "'")
                .collect(Collectors.joining(") AND (", "(", ")"));

    }

    protected void errorHandler(JSONObject jsonResponse) throws OpenbravoClientException, JSONException {
        int status = jsonResponse.getInt("status");
        if (status == -1) {
            throw new OpenbravoClientException("");
        }
    }
}
