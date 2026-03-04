package io.quarkus.bean.validation.impl;

import java.time.Clock;

import jakarta.validation.ClockProvider;

public class DefaultClockProvider implements ClockProvider {

    @Override
    public Clock getClock() {
        return Clock.systemDefaultZone();
    }
}
