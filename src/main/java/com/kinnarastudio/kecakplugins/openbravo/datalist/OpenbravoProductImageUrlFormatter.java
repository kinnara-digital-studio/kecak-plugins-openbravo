package com.kinnarastudio.kecakplugins.openbravo.datalist;

import com.kinnarastudio.kecakplugins.openbravo.webservice.OpenbravoProductPhotoWebService;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.plugin.base.PluginManager;

import java.util.Map;
import java.util.ResourceBundle;

public class OpenbravoProductImageUrlFormatter extends DataListColumnFormatDefault {
    public final static String LABEL = "Openbravo Product Image Url Formatter";
    
    @Override
    public String format(DataList dataList, DataListColumn dataListColumn, Object row, Object value) {
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final String imageId = ((Map<String, String>)row).get("image");
        return String.format("%s/web/json/app/%s/plugin/%s/service?imageId=%s", getLocalHost(), appDefinition.getAppId(), OpenbravoProductPhotoWebService.class.getName(), imageId);
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
        return AppUtil.readPluginResource(getClassName(), "/properties/datalist/OpenbravoProductImageUrlFormatter.json");
    }

    protected String getLocalHost() {
        return getPropertyString("localHost");
    }
}
