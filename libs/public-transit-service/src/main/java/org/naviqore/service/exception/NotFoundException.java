package org.naviqore.service.exception;

public class NotFoundException extends Exception {

    public NotFoundException(String type, String id) {
        super(type + " " + id + " not found.");
    }

}