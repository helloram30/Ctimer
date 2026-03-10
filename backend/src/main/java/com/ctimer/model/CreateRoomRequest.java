package com.ctimer.model;

/**
 * Incoming payload for creating a new room.
 *
 * @param initialMinutes initial minutes per side
 * @param incrementSeconds increment per move in seconds
 * @param hostColor side selected by the room creator
 */
public record CreateRoomRequest(long initialMinutes, long incrementSeconds, String hostColor) {
}
