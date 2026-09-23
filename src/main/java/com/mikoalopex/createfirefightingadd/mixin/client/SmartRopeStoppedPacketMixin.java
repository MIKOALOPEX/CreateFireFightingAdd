package com.mikoalopex.createfirefightingadd.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.network.packets.rope.ClientboundRopeStoppedPacket", remap = false)
public abstract class SmartRopeStoppedPacketMixin {
	@Shadow public abstract BlockPos ownerPos();

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 0)
	private void smartRopeConnector$route(CallbackInfo ci) {
		if (Minecraft.getInstance().level != null
				&& Minecraft.getInstance().level.getBlockEntity(ownerPos()) instanceof SmartRopeConnectorBlockEntity smart) {
			// Smart connectors receive a UUID-scoped stop packet instead.
			ci.cancel();
		}
	}
}
