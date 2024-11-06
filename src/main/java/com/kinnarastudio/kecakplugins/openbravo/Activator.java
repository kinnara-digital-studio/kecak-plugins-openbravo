package com.kinnarastudio.kecakplugins.openbravo;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnarastudio.kecakplugins.openbravo.datalist.OpenbravoDataListBinder;
import com.kinnarastudio.kecakplugins.openbravo.datalist.OpenbravoDeleteListDataListAction;
import com.kinnarastudio.kecakplugins.openbravo.datalist.OpenbravoRecordActivationDataListAction;
import com.kinnarastudio.kecakplugins.openbravo.form.*;

import com.kinnarastudio.kecakplugins.openbravo.hashvariable.OpenbravoAssignmentHashVariable;
import com.kinnarastudio.kecakplugins.openbravo.hashvariable.OpenbravoCurrentUserVariable;
import com.kinnarastudio.kecakplugins.openbravo.hashvariable.OpenbravoFindWhere;
import com.kinnarastudio.kecakplugins.openbravo.process.OpenbravoTool;
import com.kinnarastudio.kecakplugins.openbravo.webservice.OpenbravoProductPhotoWebService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        // Form Binders
        registrationList.add(context.registerService(OpenbravoFormBinder.class.getName(), new OpenbravoFormBinder(), null));
        registrationList.add(context.registerService(OpenbravoGridBinder.class.getName(), new OpenbravoGridBinder(), null));

        // Form Options Binders
        registrationList.add(context.registerService(OpenbravoOptionsBinder.class.getName(), new OpenbravoOptionsBinder(), null));

        // DataList Binders
        registrationList.add(context.registerService(OpenbravoDataListBinder.class.getName(), new OpenbravoDataListBinder(), null));

        // DataList Actions
        registrationList.add(context.registerService(OpenbravoRecordActivationDataListAction.class.getName(), new OpenbravoRecordActivationDataListAction(), null));
        registrationList.add(context.registerService(OpenbravoDeleteListDataListAction.class.getName(), new OpenbravoDeleteListDataListAction(), null));

        // Process Tools
        registrationList.add(context.registerService(OpenbravoTool.class.getName(), new OpenbravoTool(), null));

        // Hash Variables
        registrationList.add(context.registerService(OpenbravoCurrentUserVariable.class.getName(), new OpenbravoCurrentUserVariable(), null));
        registrationList.add(context.registerService(OpenbravoFindWhere.class.getName(), new OpenbravoFindWhere(), null));
        registrationList.add(context.registerService(OpenbravoAssignmentHashVariable.class.getName(), new OpenbravoAssignmentHashVariable(), null));

        // Web Services
        registrationList.add(context.registerService(OpenbravoProductPhotoWebService.class.getName(), new OpenbravoProductPhotoWebService(), null));

    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}