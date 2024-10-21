package com.kinnarastudio.kecakplugins.openbravo.exceptions;

import java.util.Collections;
import java.util.Map;

/**
 * @author aristo
 *
 * Rest Client Exception
 */
public class OpenbravoClientException extends Exception {
    private final Map<String, String> errors;

    public OpenbravoClientException(String message, Throwable cause) {
        super(message, cause);
        errors = Collections.emptyMap();
    }

    public OpenbravoClientException(Throwable cause) {
        super(cause);
        errors = Collections.emptyMap();
    }

    public OpenbravoClientException(String message) {
        super(message);
        errors = Collections.emptyMap();
    }

    public OpenbravoClientException(Map<String,String> errors) {
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
