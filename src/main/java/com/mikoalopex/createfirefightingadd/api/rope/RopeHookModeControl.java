package com.mikoalopex.createfirefightingadd.api.rope;

/** Optional block-entity contract; callers do not need Sable or a specific hook registry name. */
public interface RopeHookModeControl {
    RopeHookMode getMode();

    /** Call on the owning server thread. Returns false if the mode cannot be applied. */
    boolean setMode(RopeHookMode mode);
}
