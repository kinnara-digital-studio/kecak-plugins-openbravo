package com.kinnarastudio.kecakplugins.openbravo.model;

import org.joget.apps.datalist.model.DataListFilterQueryObject;

public class OpenbravoDataListQueryObject extends DataListFilterQueryObject {
    private boolean or;

    public boolean isOr() {
        return or;
    }

    public void setOr(boolean or) {
        this.or = or;
    }
}
