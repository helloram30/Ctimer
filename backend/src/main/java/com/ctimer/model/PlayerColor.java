package com.ctimer.model;

import java.util.Locale;

/**
 * Represents a chess side color.
 */
public enum PlayerColor {
    WHITE,
    BLACK;

    /**
     * Returns the opposite chess side.
     *
     * @return opposite player color
     */
    public PlayerColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    /**
     * Parses a player value to an enum constant.
     *
     * @param value user-provided color string
     * @return matching player color
     */
    public static PlayerColor fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Player must be provided.");
        }
        return PlayerColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
