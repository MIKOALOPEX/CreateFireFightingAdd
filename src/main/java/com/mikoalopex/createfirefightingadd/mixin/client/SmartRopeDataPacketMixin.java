package com.mikoalopex.createfirefightingadd.mixin.client;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Splits Simulated's owner-position packet stream by rope UUID on smart connectors. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.network.packets.rope.ClientboundRopeDataPacket", remap = false)
public abstract class SmartRopeDataPacketMixin {
	@Shadow public abstract int interpolationTick();
	@Shadow public abstract BlockPos ownerPos();
	@Shadow public abstract UUID uuid();
	@Shadow public abstract List<Vector3d> points();
	@Shadow @Nullable public abstract BlockPos startAttachmentPos();
	@Shadow @Nullable public abstract BlockPos endAttachmentPos();

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 0)
	private void smartRopeConnector$route(CallbackInfo ci) {
		if (Minecraft.getInstance().level != null
				&& Minecraft.getInstance().level.getBlockEntity(ownerPos()) instanceof SmartRopeConnectorBlockEntity smart) {
			smart.receiveClientRope(interpolationTick(), points(), uuid(), startAttachmentPos(), endAttachmentPos());
			ci.cancel();
		}
	}
}
