package com.ctimer.model;

/**
 * Incoming clock press payload sent by a client.
 *
 * @param player side that pressed the clock
 */
public record ClockPressRequest(String player) {
}
