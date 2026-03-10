package com.ctimer.model;

/**
 * Incoming payload for reconnecting to an existing room after refresh.
 *
 * @param roomCode room code that identifies the game session
 * @param player previously assigned player color
 */
public record ReconnectRoomRequest(String roomCode, String player) {
}
