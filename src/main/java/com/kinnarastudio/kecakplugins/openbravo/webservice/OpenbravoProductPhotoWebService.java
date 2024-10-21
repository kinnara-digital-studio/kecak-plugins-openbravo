package com.kinnarastudio.kecakplugins.openbravo.webservice;

import com.kinnarastudio.kecakplugins.openbravo.exceptions.RestClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.dao.PluginDefaultPropertiesDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PluginDefaultProperties;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.service.PropertyUtil;
import org.kecak.apps.exception.ApiException;

import javax.net.ssl.SSLContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class OpenbravoProductPhotoWebService extends DefaultApplicationPlugin implements PluginWebSupport {
    public final static String LABEL = "Openbravo Product Photo";
    public final static int BUFFER_LENGTH = 4096;

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
    public Object execute(Map map) {
        return null;
    }

    @Override
    public void webService(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException {
        try {
            final String method = servletRequest.getMethod();
            if (!"GET".equalsIgnoreCase(method)) {
                throw new ApiException(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method [" + method + "] is not supported");
            }

            final String imageId = getRequiredParameter(servletRequest, "imageId");
            final String baseUrl = getPropertyString("baseUrl");
            if (baseUrl.isEmpty()) {
                throw new ApiException(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Missing service configuration");
            }
            final String username = getPropertyString("username");
            final String password = getPropertyString("password");
            final boolean ignoreCertificate = "true".equalsIgnoreCase(getPropertyString("ignoreCertificate"));
            final String urlString = baseUrl + "/utility/ShowImage?id=" + imageId;

            final HttpClient client = getHttpClient(ignoreCertificate);
            final HttpUriRequest request = new HttpGet(urlString);
            request.setHeader("Authorization", getAuthenticationHeader(username, password));

            final HttpResponse response = client.execute(request);
            servletResponse.setContentType(response.getEntity().getContentType().getValue());
            try (InputStream inputStream = response.getEntity().getContent()) {
                final OutputStream outputStream = servletResponse.getOutputStream();
                byte[] buffer = new byte[BUFFER_LENGTH];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

//            final URL url = new URL(urlString);
////            final URLConnection urlConnection = url.openConnection();
//            httpServletResponse.setContentType("image/jpg");
//            final OutputStream outputStream = httpServletResponse.getOutputStream();
//            try (InputStream inputStream = url.openStream()) {
//                byte[] buffer = new byte[BUFFER_LENGTH];
//
//                int bytesRead;
//                while ((bytesRead = inputStream.read(buffer)) >= 0) {
//                    outputStream.write(buffer, 0, bytesRead);
//                }
//                outputStream.flush();
//            }
        } catch (ApiException e) {
            servletResponse.sendError(e.getErrorCode(), e.getMessage());
        } catch (RestClientException e) {
            servletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return OpenbravoProductPhotoWebService.class.getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/webservice/OpenbravoProductPhotoWebService.json", null, true, "/messages/Openbravo");
    }

    protected String getRequiredParameter(HttpServletRequest request, String parameterName) throws ApiException {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Parameter [" + parameterName + "] is not supplied"));

    }

    protected HttpClient getHttpClient(boolean ignoreCertificate) throws RestClientException {
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

    @Override
    public Map<String, Object> getProperties() {
        PluginDefaultPropertiesDao pluginDefaultPropertiesDao = (PluginDefaultPropertiesDao) AppUtil.getApplicationContext().getBean("pluginDefaultPropertiesDao");
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        return Optional.ofNullable(pluginDefaultPropertiesDao.loadById(OpenbravoProductPhotoWebService.class.getName(), appDefinition))
                .map(PluginDefaultProperties::getPluginProperties)
                .map(s -> AppUtil.processHashVariable(s, null, StringUtil.TYPE_JSON, null))
                .map(PropertyUtil::getPropertiesValueFromJson)
                .orElseGet(Collections::emptyMap);
    }

    @Override
    public String getPropertyString(String property) {
        Map<String, Object> properties = getProperties();
        String value = properties != null && properties.get(property) != null ? (String) properties.get(property) : "";
        return value;
    }

    protected String getAuthenticationHeader(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    }
}
