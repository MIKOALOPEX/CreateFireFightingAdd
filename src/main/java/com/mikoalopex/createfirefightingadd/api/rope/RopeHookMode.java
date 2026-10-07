package com.mikoalopex.createfirefightingadd.api.rope;

/** Stable mode identifiers, independent of the hook block's registry name and model. */
public enum RopeHookMode {
    LOCKED("locked", false),
    SLIDING("sliding", true),
    ARRESTING("arresting", true);

    private final String id;
    private final boolean transfersLength;

    RopeHookMode(String id, boolean transfersLength) {
        this.id = id;
        this.transfersLength = transfersLength;
    }

    public String id() { return id; }
    public boolean transfersLength() { return transfersLength; }

    public static RopeHookMode fromId(String id) {
        for (RopeHookMode mode : values()) if (mode.id.equals(id)) return mode;
        return LOCKED;
    }
}
