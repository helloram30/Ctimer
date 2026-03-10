package com.ctimer.model;

/**
 * Incoming payload for joining an existing room.
 *
 * @param roomCode room code entered by the joining player
 */
public record JoinRoomRequest(String roomCode) {
}
