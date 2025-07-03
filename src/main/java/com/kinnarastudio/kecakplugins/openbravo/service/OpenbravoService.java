package com.kinnarastudio.kecakplugins.openbravo.service;

import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.commons.jsonstream.model.JSONObjectEntry;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoCreateRecordException;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.RestClientException;
import com.kinnarastudio.obclient.annotation.ObEntity;
import com.kinnarastudio.obclient.annotation.ObField;
import org.apache.http.HttpResponse;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Use {@link com.kinnarastudio.obclient.service.OpenbravoService}
 */
@Deprecated
public class OpenbravoService {
    public final static DateFormat DF = new SimpleDateFormat("yyyy-MM-dd");

    private static OpenbravoService instance = null;
    Exception cutCircuitCause = null;
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

    public Map<String, String> delete(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String primaryKey, @Nonnull String username, @Nonnull String password) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "delete : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] primaryKey [" + primaryKey + "] username [" + username + "]");

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

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getBasicAuthenticationHeader(username, password));
            final HttpResponse response = restService.doDelete(url.toString(), headers);

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

    public <T> Optional<T> get(Class<T> clazz, @Nonnull String baseUrl, @Nonnull String username, @Nonnull String password, @Nonnull String primaryKey) throws OpenbravoClientException {
        return Arrays.stream(get(clazz, baseUrl, username, password, Collections.singletonMap("id", primaryKey)))
                .map(o -> (T) o)
                .findFirst();
    }


    @Nonnull
    public Map<String, String> get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nonnull String primaryKey) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "get : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] primaryKey [" + primaryKey + "] username [" + username + "] password [" + password + "]");

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

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getBasicAuthenticationHeader(username, password));
            final HttpResponse response = restService.doGet(url.toString(), headers);

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

    public <T> Object[] get(Class<T> clazz, @Nonnull String baseUrl, @Nonnull String username, @Nonnull String password, Map<String, String> filter) throws OpenbravoClientException {
        final String where = getFilterWhereCondition(filter);
        return get(clazz, baseUrl, username, password, where, null, null, null, null, null);
    }

    public Map<String, Object>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, Map<String, String> filter) throws OpenbravoClientException {
        final String where = getFilterWhereCondition(filter);
        return get(baseUrl, tableEntity, username, password, null, where, null, null, null, null, null);
    }

    /**
     * @param clazz
     * @param baseUrl
     * @param username
     * @param password
     * @param condition
     * @param arguments
     * @param sort
     * @param desc
     * @param startRow
     * @param endRow
     * @param <T>
     * @return arrays of T[]
     * @throws OpenbravoClientException
     */
    public <T> Object[] get(@Nonnull Class<T> clazz, @Nonnull String baseUrl, @Nonnull String username, @Nonnull String password, @Nullable String condition, Object[] arguments, @Nullable String sort, @Nullable Boolean desc, @Nullable Integer startRow, @Nullable Integer endRow) throws OpenbravoClientException {
        final String tableEntity = getTableEntity(clazz);
        final String[] fields = getFields(clazz);
        final Map<String, Object>[] records = get(baseUrl, tableEntity, username, password, fields, condition, arguments, sort, desc, startRow, endRow);

        return Arrays.stream(records)
                .map(Try.onFunction(m -> {
                    try {
                        return clazz.getConstructor(Map.class).newInstance(m);
                    } catch (NoSuchMethodException ignored) {
                        final T instance = clazz.getConstructor().newInstance();

                        Optional.of(clazz)
                                .map(Class::getDeclaredFields)
                                .stream()
                                .flatMap(Arrays::stream)
                                .forEach(Try.onConsumer(field -> {
                                    final String jsonKey = Optional.of(field)
                                            .map(f -> f.getAnnotation(ObField.class))
                                            .map(ObField::value)
                                            .orElseGet(field::getName);
                                    final String classAttribute = field.getName();
                                    final String setterName = "set" + classAttribute.substring(0, 1).toUpperCase() + classAttribute.substring(1);
                                    final Object value = m.get(jsonKey);
                                    if (value == null) return;

                                    try {
                                        clazz.getDeclaredMethod(setterName, value.getClass()).invoke(instance, value);
                                    } catch (NoSuchMethodException e) {
                                        clazz.getDeclaredMethod(setterName, String.class).invoke(instance, String.valueOf(value));
                                    }
                                }));

                        return instance;
                    }
                }))
                .toArray();
    }

    /**
     * @param baseUrl
     * @param tableEntity
     * @param username
     * @param password
     * @param fields
     * @param condition
     * @param arguments
     * @param sort
     * @param desc
     * @param startRow
     * @param endRow
     * @return
     * @throws OpenbravoClientException
     */
    public Map<String, Object>[] get(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nullable String[] fields, @Nullable String condition, Object[] arguments, @Nullable String sort, @Nullable Boolean desc, @Nullable Integer startRow, @Nullable Integer endRow) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "get : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "]");

        try {
            final RestService restService = RestService.getInstance();
            restService.setIgnoreCertificate(ignoreCertificateError);
            restService.setDebug(isDebug);

            final StringBuilder url = new StringBuilder()
                    .append(baseUrl)
                    .append("/org.openbravo.service.json.jsonrest/")
                    .append(tableEntity);

            if (fields != null && fields.length > 0) {
                addUrlParameter(url, "_selectedProperties", String.join(",", fields));
            }

            if (noFilterActive) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            if (startRow != null) {
                addUrlParameter(url, "_startRow", startRow.toString());
            }

            if (endRow != null) {
                addUrlParameter(url, "_endRow", endRow.toString());
            }

            if (condition != null && !condition.isEmpty()) {
                final String where = arguments == null ? condition : formatArguments(condition, arguments);
                addUrlParameter(url, "_where", URLEncoder.encode(where));
            }

            if (sort != null && !sort.isEmpty()) {
                if (desc != null && desc) {
                    sort += " desc";
                }
                addUrlParameter(url, "_orderBy", URLEncoder.encode(sort.replaceAll("\\$", ".")));
            }

            if (isDebug) {
                LogUtil.info(getClass().getName(), "get : url [" + url + "]");
            }

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getBasicAuthenticationHeader(username, password));
            final HttpResponse response = restService.doGet(url.toString(), headers);

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

    protected String formatArguments(String condition, Object[] arguments) {
        final Pattern p = Pattern.compile("\\?");
        final Matcher m = p.matcher(condition);

        final StringBuilder sb = new StringBuilder();
        final List<Object> args = new ArrayList<>();
        if (arguments != null) {
            for (int i = 0; i < arguments.length && m.find(); i++) {
                final Object argument = arguments[i];

                final String replacement;
                if (argument instanceof Integer || argument instanceof Long) {
                    replacement = "%d";
                    args.add(argument);
                } else if (argument instanceof Float || argument instanceof Double) {
                    replacement = "%.2f";
                    args.add(argument);
                } else if (argument instanceof Date) {
                    replacement = "'%s'";
                    args.add(DF.format(argument));
                } else {
                    replacement = "'%s'";
                    args.add(String.valueOf(argument).replaceAll(",", "''"));
                }

                m.appendReplacement(sb, replacement);
            }
        }

        m.appendTail(sb);

        return String.format(sb.toString(), args.toArray(new Object[0]));
    }

    public int count(@Nonnull String baseUrl, @Nonnull String tableEntity, @Nonnull String username, @Nonnull String password, @Nullable String condition, @Nullable Object[] arguments) throws OpenbravoClientException {
        LogUtil.info(getClass().getName(), "count : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "]");

        try {
            final RestService restService = RestService.getInstance();
            restService.setIgnoreCertificate(ignoreCertificateError);
            restService.setDebug(isDebug);

            final StringBuilder url = new StringBuilder()
                    .append(baseUrl)
                    .append("/ws/com.kinnarastudio.openbravo.kecakadapter.RecordCount/")
                    .append(tableEntity);

            if (noFilterActive) {
                addUrlParameter(url, "_noActiveFilter", "true");
            }

            if (condition != null && !condition.isEmpty()) {
                final String where = arguments == null ? condition : formatArguments(condition, arguments);
                addUrlParameter(url, "_where", URLEncoder.encode(where));
            }

            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getBasicAuthenticationHeader(username, password));
            final HttpResponse response = restService.doGet(url.toString(), headers);
            ;

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
        LogUtil.info(getClass().getName(), "post : baseUrl [" + baseUrl + "] tableEntity [" + tableEntity + "] username [" + username + "]");

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
            final Map<String, String> headers = Collections.singletonMap("Authorization", restService.getBasicAuthenticationHeader(username, password));

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

                            final HttpResponse response = restService.doPost(url.toString(), headers, jsonBody);

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
                                        throw new OpenbravoCreateRecordException(errors);
                                    } else if (status == -1) {
                                        throw new OpenbravoClientException(jsonResponse.getJSONObject("error").getString("message"));
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
                                return data;
                            }
                        } catch (OpenbravoClientException | RestClientException | IOException | JSONException |
                                 OpenbravoCreateRecordException e) {
                            LogUtil.error(getClass().getName(), e, e.getMessage());
                            if (shortCircuit) {
                                cutCircuit = true;
                                cutCircuitCause = e;
                                return null;
                            }

                            return Collections.<String, Object>emptyMap();
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(Map[]::new);

            if (cutCircuit) {
                throw cutCircuitCause instanceof OpenbravoClientException
                        ? (OpenbravoClientException) cutCircuitCause
                        : new OpenbravoClientException(cutCircuitCause);
            }

            if (rows.length != result.length)
                throw new OpenbravoClientException("Request length [" + rows.length + "] and response length [" + result.length + "] are different");

            return (Map<String, Object>[]) result;
        } catch (RestClientException e) {
            throw new OpenbravoClientException(e);
        }
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

    protected String getTableEntity(Class<?> clazz) {
        return Optional.of(clazz)
                .map(c -> c.getAnnotation(ObEntity.class))
                .map(ObEntity::value)
                .orElseGet(clazz::getSimpleName);
    }

    protected String[] getFields(Class<?> clazz) {
        return Optional.of(clazz)
                .stream()
                .map(Class::getDeclaredFields)
                .flatMap(Arrays::stream)
                .map(field -> Optional.of(field)
                        .map(f -> f.getAnnotation(ObField.class))
                        .map(ObField::value)
                        .orElseGet(field::getName))
                .map(s -> s.replaceAll("\\$.*$", ""))
                .toArray(String[]::new);
    }

}
