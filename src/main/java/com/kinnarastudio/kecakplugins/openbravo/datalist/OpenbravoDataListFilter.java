package com.kinnarastudio.kecakplugins.openbravo.datalist;

import com.kinnarastudio.kecakplugins.openbravo.model.OpenbravoDataListQueryObject;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.lib.TextFieldDataListFilterType;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;

import java.util.Arrays;
import java.util.ResourceBundle;

public class OpenbravoDataListFilter extends TextFieldDataListFilterType {
    public final static String LABEL = "Openbravo Custom DataList Filter";

    @Override
    public String getTemplate(DataList datalist, String name, String label) {
        return super.getTemplate(datalist, name, label);
    }

    @Override
    public DataListFilterQueryObject getQueryObject(DataList datalist, String name) {
        final String value = getValue(datalist, name);

        if (value == null) {
            return null;
        }

        final String[] arguments = Arrays.stream(getValues(datalist, name))
                .filter(s -> !s.isEmpty())
                .map(s -> s.split(";"))
                .flatMap(Arrays::stream)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (arguments.length > 0) {
            DataListFilterQueryObject queryObject = new OpenbravoDataListQueryObject() {{
                final String escapedName = StringUtil.escapeRegex(name.replaceAll("\\$_identifier$", ".name"));
                final String query = OpenbravoDataListFilter.this.getCondition().replaceAll("(^\\$\\B)|(\\B\\$\\B)|(\\B\\$$)", escapedName);

                setQuery(query);
                setValues(arguments);
                setDatalist(datalist);
                setOr("OR".equalsIgnoreCase(OpenbravoDataListFilter.this.getOperator()));
            }};

            LogUtil.info(getClassName(), "operator [" + queryObject.getOperator() + "] query [" + queryObject.getQuery() + "] arguments [" + String.join(";", queryObject.getValues()) + "]");

            return queryObject;
        } else {
            return null;
        }
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
        return AppUtil.readPluginResource(getClassName(), "/properties/datalist/OpenbravoDataListFilter.json", null, true, "/messages/Openbravo");
    }

    protected String getCondition() {
        return getPropertyString("query");
    }

    protected String getOperator() {
        return getPropertyString("operator");
    }
}
