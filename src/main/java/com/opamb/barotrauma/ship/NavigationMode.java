package com.opamb.barotrauma.ship;

/** The server's current driving mode, also displayed by the navigation terminal. */
public enum NavigationMode {
    MANUAL,
    TRANSIT,
    HOLD,
    BLOCKED,
    SETTLING;

    public static NavigationMode byId(int id) {
        NavigationMode[] modes = values();
        return id >= 0 && id < modes.length ? modes[id] : MANUAL;
    }

    public boolean automatic() {
        return this != MANUAL;
    }
}
