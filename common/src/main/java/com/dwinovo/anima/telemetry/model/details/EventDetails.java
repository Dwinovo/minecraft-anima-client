package com.dwinovo.anima.telemetry.model.details;

import java.util.Map;

@FunctionalInterface
public interface EventDetails {
    Map<String, Object> toMap();
}
