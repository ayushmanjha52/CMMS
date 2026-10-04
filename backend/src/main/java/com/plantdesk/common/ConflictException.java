package com.plantdesk.common;

/** 409: the request is well-formed but the current state of the resource forbids it. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
