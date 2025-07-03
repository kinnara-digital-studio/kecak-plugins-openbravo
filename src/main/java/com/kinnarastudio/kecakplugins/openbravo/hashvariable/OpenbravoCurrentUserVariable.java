package com.kinnarastudio.kecakplugins.openbravo.hashvariable;

import com.kinnarastudio.obclient.exceptions.OpenbravoClientException;
import com.kinnarastudio.obclient.service.OpenbravoService;
import org.joget.apps.app.dao.PluginDefaultPropertiesDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DefaultHashVariablePlugin;
import org.joget.apps.app.model.PluginDefaultProperties;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.service.PropertyUtil;
import org.joget.workflow.util.WorkflowUtil;

import java.util.*;

/**
 * Load from ADUser
 */
public class OpenbravoCurrentUserVariable extends DefaultHashVariablePlugin {
    public final static String LABEL = "Openbravo User Variable";

    @Override
    public String getPrefix() {
        return "openbravoCurrentUser";
    }

    @Override
    public String processHashVariable(String key) {
        final String field = key;
        final String obUser = WorkflowUtil.getCurrentUsername();

        final String baseUrl = getBaseUrl();
        final String username = getUsername();
        final String password = getPassword();
        try {
            final OpenbravoService obService = OpenbravoService.getInstance();
            final Map<String, Object>[] result = obService.get(baseUrl, "ADUser", username, password, Collections.singletonMap("name", obUser));
            return Optional.ofNullable(result)
                    .stream()
                    .flatMap(Arrays::stream)
                    .findFirst()
                    .map(m -> m.getOrDefault(key, ""))
                    .map(String::valueOf)
                    .orElse("");
        } catch (OpenbravoClientException e) {
            LogUtil.error(getClassName(), e, "Error loading user [" + obUser + "] field [" + field + "]");
            return "";
        }
    }

    @Override
    public Collection<String> availableSyntax() {
        Collection<String> list = new ArrayList<>();
        list.add(getPrefix() + ".FIELD");
        return list;
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
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/hashvariable/OpenbravoHashVariable.json", null, true, "/messages/Openbravo");
    }

    public String getUsername() {
        return String.valueOf(getDefaultProperties().get("username"));
    }

    public String getPassword() {
        return String.valueOf(getDefaultProperties().get("password"));
    }

    public String getBaseUrl() {
        return String.valueOf(getDefaultProperties().get("baseUrl"));
    }

    public boolean getIgnoreCertificateError() {
        return "true".equalsIgnoreCase(String.valueOf(getDefaultProperties().get("ignoreCertificateError")));
    }

    public boolean isDebug() {
        return "true".equalsIgnoreCase(String.valueOf(getDefaultProperties().get("isDebug")));
    }

    @Override
    public Object execute(Map map) {
        return null;
    }

    public Map<String, Object> getDefaultProperties() {
        PluginDefaultPropertiesDao pluginDefaultPropertiesDao = (PluginDefaultPropertiesDao) AppUtil.getApplicationContext().getBean("pluginDefaultPropertiesDao");
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        return Optional.ofNullable(pluginDefaultPropertiesDao.loadById(getClassName(), appDefinition))
                .map(PluginDefaultProperties::getPluginProperties)
                .map(s -> AppUtil.processHashVariable(s, null, StringUtil.TYPE_JSON, null))
                .map(PropertyUtil::getPropertiesValueFromJson)
                .orElseGet(Collections::emptyMap);
    }
}
