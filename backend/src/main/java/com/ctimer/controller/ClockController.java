package com.ctimer.controller;

import com.ctimer.model.ClockPressRequest;
import com.ctimer.model.CreateRoomRequest;
import com.ctimer.model.GameOverRequest;
import com.ctimer.model.JoinRoomRequest;
import com.ctimer.model.PlayerColor;
import com.ctimer.model.ReconnectRoomRequest;
import com.ctimer.model.RoomSessionResponse;
import com.ctimer.model.RoomSnapshot;
import com.ctimer.model.StartGameRequest;
import com.ctimer.service.ChessClockService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes REST and WebSocket handlers for room-based chess clock interaction.
 */
@RestController
@RequestMapping
public class ClockController {

    private final ChessClockService chessClockService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the controller with required dependencies.
     *
     * @param chessClockService authoritative room timer service
     * @param messagingTemplate broker template for socket broadcasts
     */
    public ClockController(ChessClockService chessClockService, SimpMessagingTemplate messagingTemplate) {
        this.chessClockService = chessClockService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Creates a room and assigns the host color.
     *
     * @param request create room request
     * @return room session response
     */
    @PostMapping("/api/rooms/create")
    public RoomSessionResponse createRoom(@RequestBody CreateRoomRequest request) {
        final PlayerColor hostColor = PlayerColor.fromValue(request.hostColor());
        final RoomSessionResponse response = chessClockService.createRoom(
                request.initialMinutes(),
                request.incrementSeconds(),
                hostColor
        );
        broadcastSnapshot(response.snapshot());
        return response;
    }

    /**
     * Joins a room and assigns the remaining color.
     *
     * @param request join room request
     * @return room session response
     */
    @PostMapping("/api/rooms/join")
    public RoomSessionResponse joinRoom(@RequestBody JoinRoomRequest request) {
        final RoomSessionResponse response = chessClockService.joinRoom(request.roomCode());
        broadcastSnapshot(response.snapshot());
        return response;
    }

    /**
     * Reconnects a player to a room after a page refresh.
     *
     * @param request reconnect request
     * @return room session response
     */
    @PostMapping("/api/rooms/reconnect")
    public RoomSessionResponse reconnectRoom(@RequestBody ReconnectRoomRequest request) {
        final RoomSessionResponse response = chessClockService.reconnectRoom(
                request.roomCode(),
                PlayerColor.fromValue(request.player())
        );
        return response;
    }

    /**
     * Starts a room game. Only BLACK is allowed to start.
     *
     * @param request start game request
     * @return updated room snapshot
     */
    @PostMapping("/api/rooms/start")
    public RoomSnapshot startRoom(@RequestBody StartGameRequest request) {
        final RoomSnapshot updated = chessClockService.startGame(
                request.roomCode(),
                PlayerColor.fromValue(request.player())
        );
        broadcastSnapshot(updated);
        return updated;
    }

    /**
     * Ends a room game manually and marks it as complete.
     *
     * @param request game over request
     * @return updated room snapshot
     */
    @PostMapping("/api/rooms/game-over")
    public RoomSnapshot gameOver(@RequestBody GameOverRequest request) {
        final RoomSnapshot updated = chessClockService.endGame(
                request.roomCode(),
                PlayerColor.fromValue(request.player())
        );
        broadcastSnapshot(updated);
        return updated;
    }

    /**
     * Returns the latest room snapshot over REST.
     *
     * @param roomCode room code path parameter
     * @return room snapshot
     */
    @GetMapping("/api/rooms/{roomCode}/state")
    public RoomSnapshot getRoomState(@PathVariable String roomCode) {
        return chessClockService.getRoomState(roomCode);
    }

    /**
     * Handles clock press events from WebSocket clients and broadcasts updates.
     *
     * @param request socket press payload
     */
    @MessageMapping("/clock.press")
    public void pressClock(ClockPressRequest request) {
        final RoomSnapshot updated = chessClockService.pressClock(
                request.roomCode(),
                PlayerColor.fromValue(request.player())
        );
        broadcastSnapshot(updated);
    }

    /**
     * Handles invalid room or request errors with a 400 response.
     *
     * @param exception exception describing the client error
     * @return error message payload
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    /**
     * Broadcasts a room snapshot to the room-specific topic.
     *
     * @param snapshot room snapshot to broadcast
     */
    private void broadcastSnapshot(RoomSnapshot snapshot) {
        messagingTemplate.convertAndSend("/topic/room/" + snapshot.roomCode() + "/clock", snapshot);
    }

    /**
     * Simple error response payload.
     *
     * @param message error details
     */
    public record ErrorResponse(String message) {
    }
}
