package com.kinnarastudio.kecakplugins.openbravo.exceptions;

import java.util.Collections;
import java.util.Map;

/**
 * @author aristo
 *
 * Rest Client Exception
 */
public class OpenbravoClientException extends Exception {

    public OpenbravoClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenbravoClientException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public OpenbravoClientException(String message) {
        super(message);
    }
}
