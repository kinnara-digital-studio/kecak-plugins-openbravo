package com.kinnarastudio.kecakplugins.openbravo.exceptions;

import java.util.Map;

public class OpenbravoCreateRecordException extends Exception {
    private final Map<String, String> errors;

    public OpenbravoCreateRecordException(Map<String, String> errors) {
        super("One or more fields contain illegal values, check the errors of each field");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
