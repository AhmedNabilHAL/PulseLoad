package com.pulseload.engine.executor;

public enum ExecutionOutcome {
    SUCCESS_2XX,
    REDIRECT_3XX,
    CLIENT_ERROR_4XX,
    SERVER_ERROR_5XX,
    TIMEOUT,
    CONNECT_ERROR
}
