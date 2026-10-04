package com.plantdesk.common;

/**
 * 404. Also what a cross-tenant id produces — deliberately indistinguishable from "does not
 * exist", so probing ids reveals nothing about other plants.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " not found");
    }
}
