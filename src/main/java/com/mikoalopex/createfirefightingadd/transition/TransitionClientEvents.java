package com.mikoalopex.createfirefightingadd.transition;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Local integrated server results are displayed after the saving screen has finished. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class TransitionClientEvents {
    private TransitionClientEvents() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft game = Minecraft.getInstance();
        if (game.level != null || game.getSingleplayerServer() != null) return;
        var result = TransitionCommands.CLIENT_RESULT.getAndSet(null);
        if (result == null) return;
        var previous = game.screen;
        var folder = result.success() ? result.output() : result.report() == null ? null : result.report().getParent();
        String detail = result.success() ? result.output().getFileName().toString()
            : result.reason().lines().skip(1).findFirst().orElse(result.reason());
        game.setScreen(new ConfirmScreen(open -> {
            if (open) {
                if (result.success() && folder != null) Util.getPlatform().openPath(folder);
                else Util.getPlatform().openUri("https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/world-conversion/0.2.9-beta");
            }
            game.setScreen(previous);
        }, Component.translatable("createfirefightingadd.transition." + (result.success() ? "success" : "failed")),
            Component.translatable("createfirefightingadd.transition." + (result.success() ? "success_detail" : "failed_detail"), detail),
            Component.translatable("createfirefightingadd.transition." + (result.success() ? "open_folder" : "download_tool")),
            Component.translatable("gui.done")));
    }
}
