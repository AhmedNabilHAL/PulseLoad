package com.pulseload.exception;

import java.util.UUID;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(UUID id) {
        super("Run not found: " + id);
    }
}
