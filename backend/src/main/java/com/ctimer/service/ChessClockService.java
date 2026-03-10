package com.ctimer.service;

import com.ctimer.model.ClockSnapshot;
import com.ctimer.model.PlayerColor;
import org.springframework.stereotype.Service;

/**
 * Manages the authoritative chess clock state and transitions.
 */
@Service
public class ChessClockService {

    private static final long DEFAULT_INITIAL_MINUTES = 5;
    private static final long DEFAULT_INCREMENT_SECONDS = 0;
    private static final long MILLIS_PER_MINUTE = 60_000;
    private static final long MILLIS_PER_SECOND = 1_000;

    private ClockSnapshot state;

    /**
     * Creates a service with a default timer configuration.
     */
    public ChessClockService() {
        this.state = newGame(DEFAULT_INITIAL_MINUTES, DEFAULT_INCREMENT_SECONDS, System.currentTimeMillis());
    }

    /**
     * Returns the current authoritative clock snapshot.
     *
     * @return current clock snapshot
     */
    public synchronized ClockSnapshot getState() {
        state = applyElapsed(state, System.currentTimeMillis());
        return state;
    }

    /**
     * Applies a clock press for the provided player.
     *
     * @param player player pressing the clock
     * @return updated clock snapshot
     */
    public synchronized ClockSnapshot pressClock(PlayerColor player) {
        final long now = System.currentTimeMillis();
        final ClockSnapshot elapsedSnapshot = applyElapsed(state, now);

        if (!elapsedSnapshot.running()) {
            state = elapsedSnapshot;
            return state;
        }

        if (elapsedSnapshot.activePlayer() != player) {
            state = withStatus(elapsedSnapshot, "Ignored: only the active player can press the clock.");
            return state;
        }

        long white = elapsedSnapshot.whiteRemainingMs();
        long black = elapsedSnapshot.blackRemainingMs();

        if (player == PlayerColor.WHITE) {
            white += elapsedSnapshot.incrementMs();
        } else {
            black += elapsedSnapshot.incrementMs();
        }

        state = new ClockSnapshot(
                white,
                black,
                player.opposite(),
                now,
                true,
                elapsedSnapshot.incrementMs(),
                player.opposite() + " to move"
        );
        return state;
    }

    /**
     * Recreates the game with user-defined timing options.
     *
     * @param initialMinutes initial minutes per side
     * @param incrementSeconds increment in seconds
     * @return reset clock snapshot
     */
    public synchronized ClockSnapshot resetGame(long initialMinutes, long incrementSeconds) {
        final long now = System.currentTimeMillis();
        final long safeMinutes = initialMinutes > 0 ? initialMinutes : DEFAULT_INITIAL_MINUTES;
        final long safeIncrement = Math.max(incrementSeconds, 0);
        this.state = newGame(safeMinutes, safeIncrement, now);
        return state;
    }

    /**
     * Builds a new game snapshot.
     *
     * @param initialMinutes initial minutes per side
     * @param incrementSeconds increment in seconds
     * @param now current epoch timestamp
     * @return initial clock snapshot
     */
    private ClockSnapshot newGame(long initialMinutes, long incrementSeconds, long now) {
        final long initialMs = initialMinutes * MILLIS_PER_MINUTE;
        return new ClockSnapshot(
                initialMs,
                initialMs,
                PlayerColor.WHITE,
                now,
                true,
                incrementSeconds * MILLIS_PER_SECOND,
                "WHITE to move"
        );
    }

    /**
     * Applies elapsed time to the currently running side.
     *
     * @param snapshot source snapshot
     * @param now current epoch timestamp
     * @return snapshot with elapsed time deducted
     */
    private ClockSnapshot applyElapsed(ClockSnapshot snapshot, long now) {
        if (!snapshot.running()) {
            return snapshot;
        }

        final long elapsed = Math.max(0, now - snapshot.lastSwitchEpochMs());
        long white = snapshot.whiteRemainingMs();
        long black = snapshot.blackRemainingMs();

        if (snapshot.activePlayer() == PlayerColor.WHITE) {
            white -= elapsed;
        } else {
            black -= elapsed;
        }

        if (white <= 0 || black <= 0) {
            white = Math.max(0, white);
            black = Math.max(0, black);
            final String status = white == 0 ? "WHITE flagged" : "BLACK flagged";
            return new ClockSnapshot(
                    white,
                    black,
                    snapshot.activePlayer(),
                    now,
                    false,
                    snapshot.incrementMs(),
                    status
            );
        }

        return new ClockSnapshot(
                white,
                black,
                snapshot.activePlayer(),
                now,
                true,
                snapshot.incrementMs(),
                snapshot.statusMessage()
        );
    }

    /**
     * Creates a copy of a snapshot with an updated status message.
     *
     * @param snapshot source snapshot
     * @param statusMessage status message to set
     * @return snapshot copy with updated status
     */
    private ClockSnapshot withStatus(ClockSnapshot snapshot, String statusMessage) {
        return new ClockSnapshot(
                snapshot.whiteRemainingMs(),
                snapshot.blackRemainingMs(),
                snapshot.activePlayer(),
                snapshot.lastSwitchEpochMs(),
                snapshot.running(),
                snapshot.incrementMs(),
                statusMessage
        );
    }
}
