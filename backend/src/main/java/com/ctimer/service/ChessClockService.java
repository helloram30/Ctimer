package com.ctimer.service;

import com.ctimer.model.PlayerColor;
import com.ctimer.model.RoomSessionResponse;
import com.ctimer.model.RoomSnapshot;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages room-based chess clock sessions and transitions.
 */
@Service
public class ChessClockService {

    private static final long DEFAULT_INITIAL_MINUTES = 5;
    private static final long DEFAULT_INCREMENT_SECONDS = 0;
    private static final long MILLIS_PER_MINUTE = 60_000;
    private static final long MILLIS_PER_SECOND = 1_000;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, RoomState> rooms;
    private final SecureRandom random;

    /**
     * Creates the room service with in-memory state.
     */
    public ChessClockService() {
        this.rooms = new ConcurrentHashMap<>();
        this.random = new SecureRandom();
    }

    /**
     * Creates a room and assigns the requested creator color.
     *
     * @param initialMinutes initial minutes per side
     * @param incrementSeconds increment in seconds
     * @param hostColor creator-selected color
     * @return room session response containing assigned color and snapshot
     */
    public synchronized RoomSessionResponse createRoom(long initialMinutes, long incrementSeconds, PlayerColor hostColor) {
        final long now = System.currentTimeMillis();
        final long safeMinutes = initialMinutes > 0 ? initialMinutes : DEFAULT_INITIAL_MINUTES;
        final long safeIncrement = Math.max(incrementSeconds, DEFAULT_INCREMENT_SECONDS);
        final String roomCode = generateRoomCode();

        final RoomState state = new RoomState();
        state.roomCode = roomCode;
        state.whiteRemainingMs = safeMinutes * MILLIS_PER_MINUTE;
        state.blackRemainingMs = safeMinutes * MILLIS_PER_MINUTE;
        state.activePlayer = PlayerColor.WHITE;
        state.lastSwitchEpochMs = now;
        state.running = false;
        state.incrementMs = safeIncrement * MILLIS_PER_SECOND;

        if (hostColor == PlayerColor.WHITE) {
            state.whiteJoined = true;
            state.blackJoined = false;
        } else {
            state.whiteJoined = false;
            state.blackJoined = true;
        }

        state.statusMessage = "Waiting for opponent to join";
        rooms.put(roomCode, state);

        return new RoomSessionResponse(roomCode, hostColor, toSnapshot(state));
    }

    /**
     * Joins a room and assigns the remaining available color.
     *
     * @param roomCode room code to join
     * @return room session response containing assigned color and snapshot
     */
    public synchronized RoomSessionResponse joinRoom(String roomCode) {
        final RoomState state = getRequiredRoom(roomCode);
        final PlayerColor assignedColor;

        if (!state.whiteJoined) {
            state.whiteJoined = true;
            assignedColor = PlayerColor.WHITE;
        } else if (!state.blackJoined) {
            state.blackJoined = true;
            assignedColor = PlayerColor.BLACK;
        } else {
            throw new IllegalArgumentException("Room is full.");
        }

        state.statusMessage = state.whiteJoined && state.blackJoined
                ? "Both players joined. BLACK press Start"
                : "Waiting for opponent to join";

        return new RoomSessionResponse(state.roomCode, assignedColor, toSnapshot(state));
    }

    /**
     * Reconnects an existing client to a room using the previously assigned color.
     *
     * @param roomCode room code to reconnect
     * @param player previously assigned color
     * @return room session response containing assigned color and snapshot
     */
    public synchronized RoomSessionResponse reconnectRoom(String roomCode, PlayerColor player) {
        final RoomState state = getRequiredRoom(roomCode);
        if (player == PlayerColor.WHITE && !state.whiteJoined) {
            throw new IllegalArgumentException("WHITE is not assigned in this room.");
        }
        if (player == PlayerColor.BLACK && !state.blackJoined) {
            throw new IllegalArgumentException("BLACK is not assigned in this room.");
        }
        return new RoomSessionResponse(state.roomCode, player, toSnapshot(state));
    }

    /**
     * Returns the current room snapshot.
     *
     * @param roomCode room code to read
     * @return current room snapshot
     */
    public synchronized RoomSnapshot getRoomState(String roomCode) {
        final RoomState state = getRequiredRoom(roomCode);
        applyElapsed(state, System.currentTimeMillis());
        return toSnapshot(state);
    }

    /**
     * Starts a room game and begins WHITE countdown. Only BLACK can start.
     *
     * @param roomCode room code to start
     * @param player player requesting start
     * @return updated room snapshot
     */
    public synchronized RoomSnapshot startGame(String roomCode, PlayerColor player) {
        final RoomState state = getRequiredRoom(roomCode);

        if (state.gameOver) {
            state.statusMessage = "Game is over. Create a new room.";
            return toSnapshot(state);
        }

        if (player != PlayerColor.BLACK) {
            state.statusMessage = "Only BLACK can start the game";
            return toSnapshot(state);
        }

        if (!state.whiteJoined || !state.blackJoined) {
            state.statusMessage = "Waiting for both players to join";
            return toSnapshot(state);
        }

        if (state.running) {
            state.statusMessage = "Game already started";
            return toSnapshot(state);
        }

        final long now = System.currentTimeMillis();
        state.running = true;
        state.activePlayer = PlayerColor.WHITE;
        state.lastSwitchEpochMs = now;
        state.statusMessage = "WHITE to move";
        return toSnapshot(state);
    }

    /**
     * Marks a room game as over by player request.
     *
     * @param roomCode room code to end
     * @param player side requesting game over
     * @return updated room snapshot
     */
    public synchronized RoomSnapshot endGame(String roomCode, PlayerColor player) {
        final RoomState state = getRequiredRoom(roomCode);
        if (!isPlayerAssigned(state, player)) {
            throw new IllegalArgumentException("Player is not assigned to this room.");
        }
        if (state.gameOver) {
            return toSnapshot(state);
        }
        state.running = false;
        state.gameOver = true;
        state.statusMessage = "Game over by " + player;
        return toSnapshot(state);
    }

    /**
     * Applies a clock press to a room.
     *
     * @param roomCode room code to update
     * @param player side pressing the clock
     * @return updated room snapshot
     */
    public synchronized RoomSnapshot pressClock(String roomCode, PlayerColor player) {
        final long now = System.currentTimeMillis();
        final RoomState state = getRequiredRoom(roomCode);

        applyElapsed(state, now);
        if (state.gameOver) {
            return toSnapshot(state);
        }

        if (!state.running) {
            if (state.whiteJoined && state.blackJoined) {
                state.statusMessage = "Game not started. BLACK press Start";
            }
            return toSnapshot(state);
        }

        if (state.activePlayer != player) {
            state.statusMessage = "Ignored: only the active player can press the clock";
            return toSnapshot(state);
        }

        if (player == PlayerColor.WHITE) {
            state.whiteRemainingMs += state.incrementMs;
        } else {
            state.blackRemainingMs += state.incrementMs;
        }

        state.activePlayer = player.opposite();
        state.lastSwitchEpochMs = now;
        state.statusMessage = state.activePlayer + " to move";
        return toSnapshot(state);
    }

    /**
     * Finds a room by code and throws when missing.
     *
     * @param roomCode room code provided by client
     * @return room state
     */
    private RoomState getRequiredRoom(String roomCode) {
        final String normalized = normalizeRoomCode(roomCode);
        final RoomState state = rooms.get(normalized);
        if (state == null) {
            throw new IllegalArgumentException("Room not found.");
        }
        return state;
    }

    /**
     * Applies elapsed time to the currently active side.
     *
     * @param state mutable room state
     * @param now current epoch timestamp
     */
    private void applyElapsed(RoomState state, long now) {
        if (!state.running) {
            return;
        }

        final long elapsed = Math.max(0, now - state.lastSwitchEpochMs);
        if (state.activePlayer == PlayerColor.WHITE) {
            state.whiteRemainingMs -= elapsed;
        } else {
            state.blackRemainingMs -= elapsed;
        }

        state.lastSwitchEpochMs = now;

        if (state.whiteRemainingMs <= 0 || state.blackRemainingMs <= 0) {
            state.whiteRemainingMs = Math.max(0, state.whiteRemainingMs);
            state.blackRemainingMs = Math.max(0, state.blackRemainingMs);
            state.running = false;
            state.gameOver = true;
            state.statusMessage = state.whiteRemainingMs == 0 ? "WHITE flagged" : "BLACK flagged";
        }
    }

    /**
     * Converts mutable room state into an immutable snapshot.
     *
     * @param state mutable room state
     * @return immutable room snapshot
     */
    private RoomSnapshot toSnapshot(RoomState state) {
        return new RoomSnapshot(
                state.roomCode,
                state.whiteRemainingMs,
                state.blackRemainingMs,
                state.activePlayer,
                state.lastSwitchEpochMs,
                state.running,
                state.gameOver,
                state.whiteJoined && state.blackJoined && !state.running && !state.gameOver,
                state.incrementMs,
                state.whiteJoined,
                state.blackJoined,
                state.statusMessage
        );
    }

    /**
     * Checks if a player color is assigned in the room.
     *
     * @param state room state
     * @param player player color
     * @return true when player is assigned in room
     */
    private boolean isPlayerAssigned(RoomState state, PlayerColor player) {
        return player == PlayerColor.WHITE ? state.whiteJoined : state.blackJoined;
    }

    /**
     * Generates a unique room code.
     *
     * @return unique room code
     */
    private String generateRoomCode() {
        String candidate = randomCode();
        while (rooms.containsKey(candidate)) {
            candidate = randomCode();
        }
        return candidate;
    }

    /**
     * Generates one random room code candidate.
     *
     * @return random room code candidate
     */
    private String randomCode() {
        final StringBuilder builder = new StringBuilder(ROOM_CODE_LENGTH);
        for (int index = 0; index < ROOM_CODE_LENGTH; index++) {
            final int position = random.nextInt(ROOM_CODE_CHARS.length());
            builder.append(ROOM_CODE_CHARS.charAt(position));
        }
        return builder.toString();
    }

    /**
     * Normalizes a room code for map lookups.
     *
     * @param roomCode user-provided room code
     * @return normalized room code
     */
    private String normalizeRoomCode(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new IllegalArgumentException("Room code is required.");
        }
        return roomCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Mutable in-memory representation of one room.
     */
    private static class RoomState {
        private String roomCode;
        private long whiteRemainingMs;
        private long blackRemainingMs;
        private PlayerColor activePlayer;
        private long lastSwitchEpochMs;
        private boolean running;
        private boolean gameOver;
        private long incrementMs;
        private boolean whiteJoined;
        private boolean blackJoined;
        private String statusMessage;
    }
}
