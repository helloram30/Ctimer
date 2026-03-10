package com.ctimer.model;

/**
 * Response sent to clients after create or join actions.
 *
 * @param roomCode room code that identifies the game session
 * @param assignedColor color assigned to the current client
 * @param snapshot current room snapshot
 */
public record RoomSessionResponse(String roomCode, PlayerColor assignedColor, RoomSnapshot snapshot) {
}
