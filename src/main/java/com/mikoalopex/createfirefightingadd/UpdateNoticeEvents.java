package com.mikoalopex.createfirefightingadd;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class UpdateNoticeEvents {
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // This entry notice deliberately stays English regardless of the client's language.
        player.sendSystemMessage(Component.empty()
            .append(Component.literal("[Create Firefighting Add 0.2.9-beta] This is a dedicated world-conversion build.\n"))
            .append(Component.literal("BACK UP YOUR WORLD BEFORE RUNNING ANY CONVERSION.\n")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            .append(Component.literal("Admin: run /cffa prepare_split, then begin saving and quitting within 30 seconds "
                + "(multiplayer: stop the server with /stop). Timeout cancels cleanup. Wait for completion, "
                + "then install both split mods and test the new CFFA-029-CLEANED copy.\n"))
            .append(Component.literal("Find the instructions and documentation on GitHub: "))
            .append(Component.literal("MIKOALOPEX/CreateFireFightingAdd")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                        "https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/world-conversion/0.2.9-beta")))));
    }

    private UpdateNoticeEvents() {}
}
