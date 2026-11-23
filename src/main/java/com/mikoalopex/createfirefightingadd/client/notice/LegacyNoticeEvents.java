package com.mikoalopex.createfirefightingadd.client.notice;

import java.util.Optional;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Opens the legacy coexistence notice over a plain title screen.
 *
 * <p>The listener runs late so a page already opened by another mod is left
 * untouched. It only replaces an exact vanilla {@link TitleScreen}; any other
 * screen, including a modded title screen, is ignored. A single replacement per
 * session also prevents the notice from reopening when the player returns to
 * the menu.</p>
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class LegacyNoticeEvents {
    private static boolean shownThisSession;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.isCanceled()) {
            return;
        }
        Screen screen = event.getNewScreen();
        boolean plainTitle = screen != null && screen.getClass() == TitleScreen.class;
        if (!plainTitle) {
            return;
        }

        Optional<LegacyNoticePolicy.Detected> detected = LegacyNoticeClientState.detect();
        if (!LegacyNoticePolicy.shouldOpen(
                plainTitle, LegacyNoticeClientState.acknowledged(), shownThisSession, detected)) {
            return;
        }

        shownThisSession = true;
        event.setNewScreen(new LegacyNoticeScreen(screen, detected.get()));
    }

    private LegacyNoticeEvents() {
    }
}
