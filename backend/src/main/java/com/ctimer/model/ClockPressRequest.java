package com.ctimer.model;

/**
 * Incoming clock press payload sent by a client.
 *
 * @param roomCode room code that identifies the game session
 * @param player side that pressed the clock
 */
public record ClockPressRequest(String roomCode, String player) {
}
