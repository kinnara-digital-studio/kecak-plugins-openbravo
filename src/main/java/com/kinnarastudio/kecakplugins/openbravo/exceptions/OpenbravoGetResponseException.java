package com.kinnarastudio.kecakplugins.openbravo.exceptions;

public class OpenbravoGetResponseException extends Exception {
    private final int status;
    private final String message;
    private final String messageType;
    private final String title;

    public OpenbravoGetResponseException(int status, String message, String messageType, String title) {
        this.status = status;
        this.message = message;
        this.messageType = messageType;
        this.title = title;
    }
}
