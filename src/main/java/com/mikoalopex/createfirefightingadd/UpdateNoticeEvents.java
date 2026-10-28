package com.mikoalopex.createfirefightingadd;

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
        player.sendSystemMessage(Component.translatable("createfirefightingadd.update_notice.split_plan"));
    }

    private UpdateNoticeEvents() {}
}
