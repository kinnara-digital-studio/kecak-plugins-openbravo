package com.kinnarastudio.kecakplugins.openbravo.hashvariable;

import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
import com.kinnarastudio.kecakplugins.openbravo.service.OpenbravoService;
import org.joget.apps.app.dao.PluginDefaultPropertiesDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DefaultHashVariablePlugin;
import org.joget.apps.app.model.PluginDefaultProperties;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.service.PropertyUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Load from ADUser
 */
public class OpenbravoFindWhere extends DefaultHashVariablePlugin {
    public final static String LABEL = "Openbravo Find Using Where";

    @Override
    public String getPrefix() {
        return "openbravoFindWhere";
    }

    @Override
    public String processHashVariable(String key) {
        final boolean isDebug = isDebug();

        final String[] split = key.replaceAll("\\[.+]", "").split("\\.", 2);
        final String tableEntity = Arrays.stream(split).findFirst().orElse("");
        final String field = Arrays.stream(split).skip(1).findFirst().orElse("");

        final Matcher matcher = Pattern.compile("(?<=\\[).*(?=])").matcher(key);
        final String where = matcher.find() ? matcher.group() : "1=1";

        final String baseUrl = getBaseUrl();
        final String username = getUsername();
        final String password = getPassword();
        try {
            final OpenbravoService obService = OpenbravoService.getInstance();
            obService.setDebug(isDebug);
            obService.setIgnoreCertificateError(getIgnoreCertificateError());

            final Map<String, Object>[] result = obService.get(baseUrl, tableEntity, username, password, new String[] {field}, where, null, null);
            final String value = Optional.ofNullable(result)
                    .stream()
                    .flatMap(Arrays::stream)
                    .findFirst()
                    .map(m -> m.getOrDefault(field, ""))
                    .map(String::valueOf)
                    .orElse("");

            if (isDebug()) {
                LogUtil.info(getClassName(), "processHashVariable : key [" + key + "] result [" + value + "] ");
            }

            return value;

        } catch (OpenbravoClientException e) {
            LogUtil.error(getClassName(), e, "Error loading table [" + tableEntity + "] name [" + where + "] field [" + field + "]");
            return "";
        }
    }

    @Override
    public Collection<String> availableSyntax() {
        Collection<String> list = new ArrayList<>();
        list.add(getPrefix() + ".TABLE_ENTITY[WHERE].GET_FIELD");
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
