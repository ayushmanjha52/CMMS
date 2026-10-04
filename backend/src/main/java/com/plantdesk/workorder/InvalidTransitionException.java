package com.plantdesk.workorder;

import com.plantdesk.common.ConflictException;

public class InvalidTransitionException extends ConflictException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
