package com.kinnarastudio.kecakplugins.openbravo.service;

import com.kinnarastudio.kecakplugins.openbravo.exceptions.OpenbravoClientException;
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
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class KecakService {
    private static KecakService instance = null;

    private KecakService(){}

    public static KecakService getInstance() {
        if(instance == null) {
            instance = new KecakService();
        }

        return instance;
    }

    public Form generateForm(String formDefId) throws Exception {
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

        throw new Exception("Error generating form [" + formDefId + "]");
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

    protected String processHashVariable(String content, @Nullable WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(content, assignment, null, null);
    }
}
