package com.ctimer.model;

/**
 * Incoming payload for starting a created room.
 *
 * @param roomCode room code that identifies the game session
 * @param player side requesting game start
 */
public record StartGameRequest(String roomCode, String player) {
}
