package com.ctimer.controller;

import com.ctimer.model.ClockPressRequest;
import com.ctimer.model.ClockResetRequest;
import com.ctimer.model.ClockSnapshot;
import com.ctimer.model.PlayerColor;
import com.ctimer.service.ChessClockService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes REST and WebSocket handlers for chess clock interaction.
 */
@RestController
@RequestMapping
public class ClockController {

    private final ChessClockService chessClockService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates the controller with required dependencies.
     *
     * @param chessClockService authoritative timer service
     * @param messagingTemplate broker template for socket broadcasts
     */
    public ClockController(ChessClockService chessClockService, SimpMessagingTemplate messagingTemplate) {
        this.chessClockService = chessClockService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Returns the current snapshot over REST.
     *
     * @return current clock snapshot
     */
    @GetMapping("/api/clock/state")
    public ClockSnapshot getState() {
        return chessClockService.getState();
    }

    /**
     * Resets the timer with supplied settings and broadcasts the new state.
     *
     * @param request reset payload
     * @return updated clock snapshot
     */
    @PostMapping("/api/clock/reset")
    public ClockSnapshot resetClock(@RequestBody ClockResetRequest request) {
        final ClockSnapshot updated = chessClockService.resetGame(request.initialMinutes(), request.incrementSeconds());
        messagingTemplate.convertAndSend("/topic/clock", updated);
        return updated;
    }

    /**
     * Handles clock press events from WebSocket clients and broadcasts updates.
     *
     * @param request socket press payload
     */
    @MessageMapping("/clock.press")
    public void pressClock(ClockPressRequest request) {
        final PlayerColor player = PlayerColor.fromValue(request.player());
        final ClockSnapshot updated = chessClockService.pressClock(player);
        messagingTemplate.convertAndSend("/topic/clock", updated);
    }
}
