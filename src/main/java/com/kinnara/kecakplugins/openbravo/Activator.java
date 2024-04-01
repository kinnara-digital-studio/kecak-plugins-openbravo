package com.kinnara.kecakplugins.openbravo;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnara.kecakplugins.openbravo.datalist.OpenbravoDataListBinder;
import com.kinnara.kecakplugins.openbravo.datalist.OpenbravoRecordActivationDataListAction;
import com.kinnara.kecakplugins.openbravo.form.OpenbravoFormLoadBinder;
import com.kinnara.kecakplugins.openbravo.form.OpenbravoFormStoreBinder;
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
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}