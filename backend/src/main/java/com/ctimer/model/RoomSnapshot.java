package com.ctimer.model;

/**
 * Immutable room snapshot sent to clients.
 *
 * @param roomCode room code that identifies the game session
 * @param whiteRemainingMs white remaining time in milliseconds
 * @param blackRemainingMs black remaining time in milliseconds
 * @param activePlayer side currently counting down
 * @param lastSwitchEpochMs epoch timestamp when active player started running
 * @param running true when the game is live
 * @param gameOver true when the room game is finished
 * @param readyToStart true when both players are present and start is allowed
 * @param incrementMs increment added after each valid press
 * @param whiteJoined true when a white-side player is connected to the room
 * @param blackJoined true when a black-side player is connected to the room
 * @param statusMessage UI-friendly status details
 */
public record RoomSnapshot(
        String roomCode,
        long whiteRemainingMs,
        long blackRemainingMs,
        PlayerColor activePlayer,
        long lastSwitchEpochMs,
        boolean running,
        boolean gameOver,
        boolean readyToStart,
        long incrementMs,
        boolean whiteJoined,
        boolean blackJoined,
        String statusMessage
) {
}
