package com.ctimer.model;

/**
 * Immutable clock snapshot sent to clients.
 *
 * @param whiteRemainingMs white remaining time in milliseconds
 * @param blackRemainingMs black remaining time in milliseconds
 * @param activePlayer side currently counting down
 * @param lastSwitchEpochMs epoch timestamp when active player started running
 * @param running true when the game is live
 * @param incrementMs increment added after each valid press
 * @param statusMessage UI-friendly status details
 */
public record ClockSnapshot(
        long whiteRemainingMs,
        long blackRemainingMs,
        PlayerColor activePlayer,
        long lastSwitchEpochMs,
        boolean running,
        long incrementMs,
        String statusMessage
) {
}
