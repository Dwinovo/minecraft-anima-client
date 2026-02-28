package com.dwinovo.anima.telemetry.model;

public enum EventVerb {
    ATTACKED("ATTACKED"),
    KILLED("KILLED"),
    HELPED("HELPED");

    private final String value;

    EventVerb(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
