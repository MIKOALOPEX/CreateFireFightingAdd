package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class FirefighterHandbookCommands {
	private FirefighterHandbookCommands() {
	}

	@SubscribeEvent
	static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("createfirefightingadd")
			.then(Commands.literal("firefighter_invite")
				.then(Commands.literal("accept")
					.then(Commands.argument("inviter", UuidArgument.uuid())
						.executes(context -> respond(context.getSource().getPlayerOrException(),
							UuidArgument.getUuid(context, "inviter"), true))))
				.then(Commands.literal("decline")
					.then(Commands.argument("inviter", UuidArgument.uuid())
						.executes(context -> respond(context.getSource().getPlayerOrException(),
							UuidArgument.getUuid(context, "inviter"), false))))));
	}

	private static int respond(ServerPlayer player, UUID inviter, boolean accepted) {
		FirefighterRecordStore.respondInvite(player, inviter, accepted);
		return 1;
	}
}
