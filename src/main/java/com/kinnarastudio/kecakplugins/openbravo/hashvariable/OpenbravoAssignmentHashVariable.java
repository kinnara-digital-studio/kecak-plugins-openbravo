package com.kinnarastudio.kecakplugins.openbravo.hashvariable;

import org.joget.apps.app.model.DefaultHashVariablePlugin;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;

public class OpenbravoAssignmentHashVariable extends DefaultHashVariablePlugin {
    public final static String LABEL = "Openbravo Assignment Hash Variable";

    @Override
    public String getPrefix() {
        return "openbravoAssignment";
    }

    @Override
    public String processHashVariable(String key) {
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final WorkflowAssignment workflowAssignment = (WorkflowAssignment) getProperty("workflowAssignment");

        if(workflowAssignment == null) {
            LogUtil.warn(getClassName(), "Workflow assignment is NULL");
            return "";
        }

        final WorkflowProcess workflowProcess = workflowManager.getRunningProcessById(workflowAssignment.getProcessId());

        String recordId = workflowProcess.getInstanceId();
        WorkflowProcessLink link = workflowManager.getWorkflowProcessLink(recordId);
        if (link != null) {
            recordId = link.getOriginProcessId();
        }

        return recordId;
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
        return "";
    }

    @Override
    public Collection<String> availableSyntax() {
        Collection<String> list = new ArrayList<>();
        list.add(getPrefix() + ".recordId");
        return list;
    }
}
