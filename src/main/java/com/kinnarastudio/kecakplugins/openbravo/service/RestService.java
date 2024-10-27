package com.kinnarastudio.kecakplugins.openbravo.service;

import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import com.kinnarastudio.kecakplugins.openbravo.exceptions.RestClientException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public class RestService {

    private static RestService instance = null;

    private boolean isDebug = false;

    private boolean ignoreCertificate = false;

    private RestService() {
    }

    public static synchronized RestService getInstance() {
        if (instance == null) instance = new RestService();
        return instance;
    }

    public HttpClient getHttpClient() throws RestClientException {
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
            throw new RestClientException(e);
        }
    }

    public int getResponseStatus(@Nonnull HttpResponse response) throws RestClientException {
        return Optional.of(response)
                .map(HttpResponse::getStatusLine)
                .map(StatusLine::getStatusCode)
                .orElseThrow(() -> new RestClientException("Error getting status code"));
    }

    public boolean isJsonResponse(@Nonnull HttpResponse response) throws OpenbravoClientException {
        return getResponseContentType(response).contains("json");
    }

    public String getResponseContentType(@Nonnull HttpResponse response) throws OpenbravoClientException {
        return Optional.of(response)
                .map(HttpResponse::getEntity)
                .map(HttpEntity::getContentType)
                .map(Header::getValue)
                .orElseGet(() -> {
                    LogUtil.warn(getClass().getName(), "Empty header content-type");
                    return "";
                });
    }

    public int getStatusGroupCode(int status) {
        if (isDebug) {
            LogUtil.info(getClass().getName(), "getStatusGroupCode : status [" + status + "]");
        }

        return status - (status % 100);
    }

    public HttpUriRequest getHttpRequest(@Nonnull String url, @Nonnull Map<String, String> headers) throws OpenbravoClientException, RestClientException {
        return getHttpRequest(url, "GET", headers, null);
    }

    public HttpUriRequest getHttpRequest(@Nonnull String url, @Nonnull String method, @Nonnull Map<String, String> headers, @Nullable JSONObject bodyPayload) throws OpenbravoClientException, RestClientException {
        if (isDebug) {
            LogUtil.info(getClass().getName(), "getHttpRequest url [" + url + "] method [" + method + "] bodyPayload [" + bodyPayload + "]");
        }

        @Nullable HttpEntity httpEntity;
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method) || bodyPayload == null) {
            httpEntity = null;
        } else {
            httpEntity = new StringEntity(bodyPayload.toString(), ContentType.APPLICATION_JSON);
        }

        final HttpRequestBase request;

        if ("GET".equalsIgnoreCase(method)) {
            request = new HttpGet(url);
        } else if ("POST".equalsIgnoreCase(method)) {
            request = new HttpPost(url);
        } else if ("PUT".equalsIgnoreCase(method)) {
            request = new HttpPut(url);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            request = new HttpDelete(url);
        } else {
            throw new RestClientException("Method [" + method + "] not supported");
        }

        headers.forEach(request::addHeader);

        if (httpEntity != null && request instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) request).setEntity(httpEntity);
        }

        return request;
    }

    protected String getAuthenticationHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    }

    public void setIgnoreCertificate(boolean ignoreCertificate) {
        this.ignoreCertificate = ignoreCertificate;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }
}
