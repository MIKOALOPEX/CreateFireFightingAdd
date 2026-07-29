package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Disabled-by-default diagnostics for fire hose pressure and transfer behaviour.
 *
 * <p>The call sites remain available for field testing without scattering
 * temporary logger calls throughout the transfer path.</p>
 */
public final class FireHoseDebugLog {

    static final boolean ENABLED = false;
    static final String TAG = "[FH_DBG]";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Long> LAST_LOG_TICK = new HashMap<>();
    private static final Map<String, TickFlow> FLOWS = new HashMap<>();
    private static long serverTick;
    private static long lastTickNanos;
    private static double avgTickMs = 50.0;
    private static int tickCount;

    private FireHoseDebugLog() {
    }

    public static void init() {
        if (!ENABLED)
            return;
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

                if (!FLOWS.isEmpty()) {
                    FLOWS.forEach((key, flow) -> LOGGER.info(
                        "{} FLOW_SUMMARY tick={} conn={} in={} out={} net={} externalIn={} externalOut={} virtualIn={} virtualOut={} notes={}",
                        TAG, serverTick, key, flow.in, flow.out, flow.in - flow.out,
                        flow.externalIn, flow.externalOut, flow.virtualIn, flow.virtualOut, flow.notes));
                    FLOWS.clear();
                }
            }
        });
    }

    static void logTickStart(FireHoseBlockEntity hose, FireHoseBlockEntity partner, int sharedAmount) {
        if (!ENABLED)
            return;
        LOGGER.info("{} TICK_START tick={} dim={} pos={} conn={} ctrl={} partner={} tank={} side={} pull={} apply={} speed={} sourceKind={} dist={} range={} pushToward={}",
            TAG,
            tick(hose),
            dimension(hose),
            pos(hose),
            connectionKey(hose, partner),
            hose.isController(),
            pos(partner),
            sharedAmount,
            FireHoseBlockEntity.pumpSideLabel(hose.pumpSide),
            hose.isPulling(),
            hose.shouldApplyPressure(),
            hose.getActivePumpSpeed(),
            hose.pumpSourceKindDebug(),
            hose.pumpDistanceDebug(),
            hose.pumpRangeDebug(),
            hose.pumpPushesTowardHose);
    }

    static void logTickEnd(FireHoseBlockEntity hose, FireHoseBlockEntity partner, int before, int after,
            int recordedInput) {
        if (!ENABLED)
            return;
        int delta = after - before;
        LOGGER.info("{} TICK_END tick={} dim={} pos={} conn={} tankBefore={} tankAfter={} delta={} recordedInput={} side={} pull={} apply={} speed={}",
            TAG,
            tick(hose),
            dimension(hose),
            pos(hose),
            connectionKey(hose, partner),
            before,
            after,
            delta,
            recordedInput,
            FireHoseBlockEntity.pumpSideLabel(hose.pumpSide),
            hose.isPulling(),
            hose.shouldApplyPressure(),
            hose.getActivePumpSpeed());
        if (delta > recordedInput && recordedInput >= 0)
            LOGGER.warn("{} DUPLICATION_SUSPECT tick={} pos={} conn={} tankDelta={} recordedInput={}",
                TAG, tick(hose), pos(hose), connectionKey(hose, partner), delta, recordedInput);
    }

    static void logSharedInput(FireHoseBlockEntity hose, int amount, String reason) {
        if (!ENABLED)
            return;
        flow(hose).in += amount;
        flow(hose).externalIn += amount;
        flow(hose).notes++;
        LOGGER.info("{} SHARED_INPUT tick={} dim={} pos={} conn={} amount={} reason={} tank={}",
            TAG, tick(hose), dimension(hose), pos(hose), connectionKey(hose, hose.getPairedHose()),
            amount, reason, hose.getSharedTankRawAmount());
    }

    static void logExternalSignal(FireHoseBlockEntity hose, int amount, boolean pushesTowardHose, String reason) {
        if (!ENABLED)
            return;
        LOGGER.info("{} EXTERNAL_SIGNAL tick={} dim={} pos={} conn={} amount={} pushToward={} reason={} pendingExternal={} memory={}",
            TAG, tick(hose), dimension(hose), pos(hose), connectionKey(hose, hose.getPairedHose()),
            amount, pushesTowardHose, reason, hose.externalInputThisTickDebug(), hose.externalInputMemoryTicksDebug());
    }

    static void logTransfer(FireHoseBlockEntity actor, String phase, @Nullable FireHoseBlockEntity sourceHose,
            @Nullable FireHoseBlockEntity targetHose, BlockPos sourcePos, BlockPos targetPos,
            FluidStack fluid, int amount, int sourceBefore, int sourceAfter, int targetBefore, int targetAfter) {
        if (!ENABLED)
            return;

        TickFlow flow = flow(actor);
        if (phase.contains("push") || phase.contains("provide") || phase.contains("drain"))
            flow.out += amount;
        if (phase.contains("pull") || phase.contains("refill") || phase.contains("fill"))
            flow.in += amount;
        if (phase.contains("container") || phase.contains("capability"))
            flow.externalOut += phase.contains("push") || phase.contains("drain") ? amount : 0;
        if (phase.contains("container") || phase.contains("capability") || phase.contains("refill"))
            flow.externalIn += phase.contains("pull") || phase.contains("fill") || phase.contains("refill") ? amount : 0;
        if (phase.contains("direct hose")) {
            flow.virtualOut += amount;
            flow.virtualIn += amount;
        }

        LOGGER.info("{} TRANSFER tick={} dim={} phase={} actor={} conn={} sourceHose={} targetHose={} sourcePos={} targetPos={} amount={} fluid={} sourceBefore={} sourceAfter={} targetBefore={} targetAfter={} actorTank={} side={} pull={} speed={}",
            TAG,
            tick(actor),
            dimension(actor),
            phase,
            pos(actor),
            connectionKey(actor, actor.getPairedHose()),
            pos(sourceHose),
            pos(targetHose),
            shortPos(sourcePos),
            shortPos(targetPos),
            amount,
            fluidName(fluid),
            sourceBefore,
            sourceAfter,
            targetBefore,
            targetAfter,
            actor.getSharedTankRawAmount(),
            FireHoseBlockEntity.pumpSideLabel(actor.pumpSide),
            actor.isPulling(),
            actor.getActivePumpSpeed());
    }

    static void logTransferSkip(FireHoseBlockEntity actor, String phase, BlockPos sourcePos, String reason,
            int tank, int space, int rate, @Nullable FluidStack fluid) {
        if (!ENABLED)
            return;
        LOGGER.info("{} TRANSFER_SKIP tick={} dim={} phase={} actor={} conn={} sourcePos={} reason={} tank={} space={} rate={} fluid={} side={} pull={} speed={}",
            TAG,
            tick(actor),
            dimension(actor),
            phase,
            pos(actor),
            connectionKey(actor, actor.getPairedHose()),
            shortPos(sourcePos),
            reason,
            tank,
            space,
            rate,
            fluidName(fluid),
            FireHoseBlockEntity.pumpSideLabel(actor.pumpSide),
            actor.isPulling(),
            actor.getActivePumpSpeed());
    }

    static void logHoseState(String label, FireHoseBlockEntity hose,
                             int backDist, boolean backPushesToward,
                             int partnerDist, boolean partnerPushesToward) {
        if (!ENABLED)
            return;

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
        if (s == FireHoseBlockEntity.PUMP_SIDE_BACK)
            return "BACK";
        if (s == FireHoseBlockEntity.PUMP_SIDE_PARTNER)
            return "PARTNER";
        return "NONE";
    }

    public static void logRaw(String msg, Object... args) {
        if (!ENABLED)
            return;
        LOGGER.info(TAG + " tick=" + serverTick + " " + msg, args);
    }

    public static void logRawEvery(String key, int intervalTicks, String msg, Object... args) {
        if (!ENABLED)
            return;
        long lastTick = LAST_LOG_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (serverTick - lastTick < intervalTicks)
            return;
        LAST_LOG_TICK.put(key, serverTick);
        LOGGER.info(TAG + " tick=" + serverTick + " " + msg, args);
    }

    private static TickFlow flow(FireHoseBlockEntity hose) {
        return FLOWS.computeIfAbsent(connectionKey(hose, hose.getPairedHose()), $ -> new TickFlow());
    }

    private static long tick(FireHoseBlockEntity hose) {
        Level level = hose.getLevel();
        if (level != null)
            return level.getGameTime();
        return serverTick;
    }

    private static String dimension(FireHoseBlockEntity hose) {
        Level level = hose.getLevel();
        return level == null ? "null" : level.dimension().location().toString();
    }

    private static String pos(@Nullable FireHoseBlockEntity hose) {
        return hose == null ? "null" : hose.getBlockPos().toShortString();
    }

    private static String shortPos(@Nullable BlockPos pos) {
        return pos == null ? "null" : pos.toShortString();
    }

    private static String connectionKey(FireHoseBlockEntity hose, @Nullable FireHoseBlockEntity partner) {
        UUID first = hose.getFireHoseEndpointId();
        UUID second = partner == null ? hose.getFireHosePartnerEndpointId() : partner.getFireHoseEndpointId();
        if (second == null)
            return first.toString();
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + "<->" + b : b + "<->" + a;
    }

    private static String fluidName(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty())
            return "empty";
        return stack.getHoverName().getString() + "@" + stack.getAmount();
    }

    private static final class TickFlow {
        int in;
        int out;
        int externalIn;
        int externalOut;
        int virtualIn;
        int virtualOut;
        int notes;
    }
}
