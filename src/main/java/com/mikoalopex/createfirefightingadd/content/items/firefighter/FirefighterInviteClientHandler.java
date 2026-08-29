package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

public final class FirefighterInviteClientHandler {
	private FirefighterInviteClientHandler() {
	}

	public static void receive(UUID inviter, String inviterName) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null)
			mc.player.displayClientMessage(Component.translatable(
				"createfirefightingadd.firefighter_handbook.invite_prompt", inviterName)
				.append(" ")
				.append(responseButton(inviter, true))
				.append(" ")
				.append(responseButton(inviter, false)), false);
	}

	private static Component responseButton(UUID inviter, boolean accepted) {
		String command = "/createfirefightingadd firefighter_invite "
			+ (accepted ? "accept " : "decline ") + inviter;
		return Component.literal(accepted ? "[Y]" : "[N]")
			.withStyle(style -> style
				.withColor(accepted ? ChatFormatting.GREEN : ChatFormatting.RED)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
	}
}
