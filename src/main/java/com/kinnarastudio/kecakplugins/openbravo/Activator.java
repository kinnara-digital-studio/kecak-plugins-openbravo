package com.kinnarastudio.kecakplugins.openbravo;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnarastudio.kecakplugins.openbravo.datalist.OpenbravoDataListBinder;
import com.kinnarastudio.kecakplugins.openbravo.datalist.OpenbravoDeleteListDataListAction;
import com.kinnarastudio.kecakplugins.openbravo.datalist.OpenbravoRecordActivationDataListAction;
import com.kinnarastudio.kecakplugins.openbravo.form.OpenbravoFormLoadBinder;
import com.kinnarastudio.kecakplugins.openbravo.form.OpenbravoFormStoreBinder;
import com.kinnarastudio.kecakplugins.openbravo.form.OpenbravoOptionsBinder;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(OpenbravoFormLoadBinder.class.getName(), new OpenbravoFormLoadBinder(), null));
        registrationList.add(context.registerService(OpenbravoFormStoreBinder.class.getName(), new OpenbravoFormStoreBinder(), null));
        registrationList.add(context.registerService(OpenbravoDataListBinder.class.getName(), new OpenbravoDataListBinder(), null));
        registrationList.add(context.registerService(OpenbravoRecordActivationDataListAction.class.getName(), new OpenbravoRecordActivationDataListAction(), null));
        registrationList.add(context.registerService(OpenbravoOptionsBinder.class.getName(), new OpenbravoOptionsBinder(), null));
        registrationList.add(context.registerService(OpenbravoDeleteListDataListAction.class.getName(), new OpenbravoDeleteListDataListAction(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}