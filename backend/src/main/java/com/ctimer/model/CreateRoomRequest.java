package com.ctimer.model;

/**
 * Incoming payload for creating a new room.
 *
 * @param whiteInitialMinutes initial minutes for white
 * @param blackInitialMinutes initial minutes for black
 * @param whiteIncrementSeconds white increment per move in seconds
 * @param blackIncrementSeconds black increment per move in seconds
 * @param hostColor side selected by the room creator
 */
public record CreateRoomRequest(
        long whiteInitialMinutes,
        long blackInitialMinutes,
        long whiteIncrementSeconds,
        long blackIncrementSeconds,
        String hostColor
) {
}
