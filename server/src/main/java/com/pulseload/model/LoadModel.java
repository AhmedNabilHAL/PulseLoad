package com.pulseload.model;

public enum LoadModel {
    /** Arrival-rate controlled: dispatch requests to hit a target RPS. */
    OPEN,
    /** Concurrency controlled: run a target number of virtual users in a loop. */
    CLOSED
}
