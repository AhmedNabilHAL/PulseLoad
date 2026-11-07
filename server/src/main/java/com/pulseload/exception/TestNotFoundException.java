package com.pulseload.exception;

import java.util.UUID;

public class TestNotFoundException extends RuntimeException {

    public TestNotFoundException(UUID id) {
        super("Test not found: " + id);
    }
}
