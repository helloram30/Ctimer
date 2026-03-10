package com.ctimer.model;

/**
 * Incoming payload for ending a room game manually.
 *
 * @param roomCode room code that identifies the game session
 * @param player player requesting game termination
 */
public record GameOverRequest(String roomCode, String player) {
}
