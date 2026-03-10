package com.ctimer.model;

/**
 * Incoming reset payload for creating a new timer configuration.
 *
 * @param initialMinutes initial minutes per side
 * @param incrementSeconds increment per move in seconds
 */
public record ClockResetRequest(long initialMinutes, long incrementSeconds) {
}
