package com.mikoalopex.createfirefightingadd.mixin;

import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Swaps only the solver handle, leaving native ownership and rope serialization intact. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject", remap = false)
public interface RopeLatchPhysicsAccess {
    @Accessor("handle") RopeHandle firefighting$getHandle();
    @Accessor("handle") void firefighting$setHandle(RopeHandle handle);
}
