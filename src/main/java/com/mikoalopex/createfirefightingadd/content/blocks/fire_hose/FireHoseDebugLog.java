package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * FireHose debug monitor. Set {@code ENABLED = false} to disable all logging.
 */
public class FireHoseDebugLog {

    static final boolean ENABLED = false;
    static final String TAG = "[FH_DBG]";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Long> LAST_LOG_TICK = new HashMap<>();
    private static long serverTick;
    private static long lastTickNanos;
    private static double avgTickMs = 50.0;
    private static int tickCount;

    public static void init() {
        if (!ENABLED) return;
        NeoForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            void onServerTickPre(ServerTickEvent.Pre event) {
                serverTick++;
                lastTickNanos = System.nanoTime();
            }

            @SubscribeEvent
            void onServerTickPost(ServerTickEvent.Post event) {
                long now = System.nanoTime();
                long elapsedNs = now - lastTickNanos;
                double elapsedMs = elapsedNs / 1_000_000.0;
                avgTickMs = avgTickMs * 0.9 + elapsedMs * 0.1;
                tickCount++;

                if (tickCount % 20 == 0 || elapsedMs > 100.0) {
                    LOGGER.info("{} TPS | tick={}ms avg={}ms",
                        TAG,
                        String.format("%.1f", elapsedMs),
                        String.format("%.1f", avgTickMs));
                }
            }
        });
    }

    static void logHoseState(String label, FireHoseBlockEntity hose,
                             int backDist, boolean backPushesToward,
                             int partnerDist, boolean partnerPushesToward) {
        if (!ENABLED) return;

        BlockPos pos = hose.getBlockPos();
        boolean pulling = hose.isPulling();
        boolean apply = hose.shouldApplyPressure();
        int fluid = hose.getSharedTankRawAmount();

        LOGGER.info("{} {} | pos={} side={} toward={} pull={} apply={} fluid={}mB | backDist={}/push={} partnerDist={}/push={}",
            TAG, label,
            pos.toShortString(),
            sideLabel(hose),
            hose.pumpPushesTowardHose,
            pulling,
            apply,
            fluid,
            backDist, backPushesToward,
            partnerDist, partnerPushesToward);
    }

    private static String sideLabel(FireHoseBlockEntity hose) {
        int s = hose.pumpSide;
        if (s == FireHoseBlockEntity.PUMP_SIDE_BACK) return "BACK";
        if (s == FireHoseBlockEntity.PUMP_SIDE_PARTNER) return "PARTNER";
        return "NONE";
    }

    public static void logRaw(String msg, Object... args) {
        if (!ENABLED) return;
        LOGGER.info(TAG + " " + msg, args);
    }

    public static void logRawEvery(String key, int intervalTicks, String msg, Object... args) {
        if (!ENABLED) return;
        long lastTick = LAST_LOG_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (serverTick - lastTick < intervalTicks)
            return;
        LAST_LOG_TICK.put(key, serverTick);
        LOGGER.info(TAG + " " + msg, args);
    }
}
