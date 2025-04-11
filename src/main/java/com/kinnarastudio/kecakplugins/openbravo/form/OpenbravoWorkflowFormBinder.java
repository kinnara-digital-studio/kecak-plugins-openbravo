package com.kinnarastudio.kecakplugins.openbravo.form;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Openbravo Workflow Form Binder
 */
public class OpenbravoWorkflowFormBinder extends OpenbravoFormBinder {
    public final static String LABEL = "Openbravo Workflow Form Binder";

    @Override
    public String getName() {
        return LABEL;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        FormRowSet result = rows;
        if (rows != null && !rows.isEmpty()) {
            // store form data to DB
            result = super.store(element, rows, formData);

            // handle workflow variables
            if (!rows.isMultiRow()) {
                String activityId = formData.getActivityId();
                String processId = formData.getProcessId();
                if (activityId != null || processId != null) {
                    WorkflowManager workflowManager = (WorkflowManager) WorkflowUtil.getApplicationContext().getBean("workflowManager");

                    // recursively find element(s) mapped to workflow variable
                    FormRow row = rows.iterator().next();
                    Map<String, String> variableMap = new HashMap<String, String>();
                    variableMap = storeWorkflowVariables(element, row, variableMap);

                    if (activityId != null) {
                        workflowManager.activityVariables(activityId, variableMap);
                    } else {
                        workflowManager.processVariables(processId, variableMap);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Recursive into elements to retrieve workflow variable values to be stored.
     * @param element
     * @param row The current row of data
     * @param variableMap The variable name=value pairs to be stored.
     * @return
     */
    protected Map<String, String> storeWorkflowVariables(Element element, FormRow row, Map<String, String> variableMap) {
        String variableName = element.getPropertyString(AppUtil.PROPERTY_WORKFLOW_VARIABLE);
        if (variableName != null && !variableName.trim().isEmpty()) {
            String id = element.getPropertyString(FormUtil.PROPERTY_ID);
            String value = (String) row.get(id);
            if (value != null) {
                variableMap.put(variableName, value);
            }
        }
        for (Iterator<Element> i = element.getChildren().iterator(); i.hasNext();) {
            Element child = i.next();
            storeWorkflowVariables(child, row, variableMap);
        }
        return variableMap;
    }
}
