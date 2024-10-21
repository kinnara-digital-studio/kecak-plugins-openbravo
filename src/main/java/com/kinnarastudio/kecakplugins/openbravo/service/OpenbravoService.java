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
import java.util.*;
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
        if (isDebug) {
            LogUtil.info(getClass().getName(), "get : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] primaryKey [" + primaryKey + "] username [" + username + "] password [" + password + "]");
        }

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

                return JSONStream.of(jsonResponse.getJSONObject("data"), Try.onBiFunction(JSONObject::getString))
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

    public Map<String, String>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, Map<String, String> filter) throws OpenbravoClientException {
        if (isDebug) {
            LogUtil.info(getClass().getName(), "post : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "] password [" + password + "]");
        }

        try {
            final RestService restService = RestService.getInstance();
            restService.setIgnoreCertificate(ignoreCertificateError);
            restService.setDebug(isDebug);

            final StringBuilder url = new StringBuilder().append(baseUrl).append("/org.openbravo.service.json.jsonrest/")
                    .append(tableEntity);

            if (noFilterActive) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getAuthenticationHeader(username, password));
            final HttpClient client = restService.getHttpClient();

            Optional.ofNullable(filter)
                    .map(Map::entrySet)
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(e -> addUrlParameter(url, e.getKey(), e.getValue()));

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

                return new Map[]{JSONStream.of(jsonResponse.getJSONObject("data"), Try.onBiFunction(JSONObject::getString))
                        .peek(e -> {
                            if (isDebug && "_identifier".equals(e.getKey())) {
                                LogUtil.info(getClass().getName(), "get : identifier [" + e.getValue() + "]");
                            }
                        })
                        .collect(Collectors.toUnmodifiableMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue))};
            }
        } catch (RestClientException | JSONException | IOException e) {
            throw new OpenbravoClientException(e);
        }
    }

    public synchronized Map<String, Object>[] post(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nonnull Map<String, Object>[] rows) throws OpenbravoClientException {
        if (isDebug) {
            LogUtil.info(getClass().getName(), "post : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "] password [" + password + "]");
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
                    .map(Try.onFunction(row -> {
                        if (cutCircuit) return null;

                        if (isDebug) {
                            LogUtil.info(getClass().getName(), "post : row [" + row.entrySet().stream().map(e -> String.join("->", e.getKey(), String.valueOf(e.getValue()))).collect(Collectors.joining(";")) + "]");
                        }

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
                                    LogUtil.info(getClass().getName(), "put : responsePayload [" + responsePayload + "]");
                                }
                                final JSONObject jsonResponse = new JSONObject(responsePayload)
                                        .getJSONObject("response");

                                final int status = jsonResponse.getInt("status");
                                if (status != 0) {
                                    if (status == -4) {
                                        final Map<String, String> errors = JSONStream.of(jsonResponse.getJSONObject("errors"), Try.onBiFunction(JSONObject::getString))
                                                .collect(Collectors.toUnmodifiableMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue));
                                        throw new OpenbravoClientException(errors);
                                    } else {
                                        throw new OpenbravoClientException(responsePayload);
                                    }
                                }

                                Map<String, String> data = JSONStream.of(jsonResponse.getJSONArray("data"), Try.onBiFunction(JSONArray::getJSONObject))
                                        .findFirst()
                                        .stream()
                                        .flatMap(json -> JSONStream.of(json, Try.onBiFunction(JSONObject::getString)))
                                        .collect(Collectors.toUnmodifiableMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue));

                                LogUtil.info(getClass().getName(), "post : data posted [" + data.get("_identifier") + "]");
                                return data;
                            }
                        } catch (OpenbravoClientException | RestClientException | IOException | JSONException e) {
                            LogUtil.error(getClass().getName(), e, e.getMessage());
                            if (shortCircuit) {
                                cutCircuit = true;
                                return null;
                            }

                            return Collections.emptyMap();
                        }
                    }))
                    .filter(Objects::nonNull)
                    .toArray(Map[]::new);

            if (isDebug) {
                for (Map<String, String> row : result) {
                    LogUtil.info(getClass().getName(), "post : result row [" + row.entrySet().stream().map(e -> e.getKey() + "->" + e.getValue()).collect(Collectors.joining(";")) + "]");
                }
            }

            if (rows.length != result.length)
                throw new OpenbravoClientException("Request length and response length are different");

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

    protected void errorHandler(JSONObject jsonResponse) throws OpenbravoClientException, JSONException {
        int status = jsonResponse.getInt("status");
        if (status == -1) {
            throw new OpenbravoClientException("");
        }
    }
}
