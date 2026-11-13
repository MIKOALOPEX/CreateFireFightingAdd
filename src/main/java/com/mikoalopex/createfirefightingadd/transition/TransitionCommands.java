package com.mikoalopex.createfirefightingadd.transition;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Command and expiry are memory-only. A closed, timely stopped world is the sole I/O entry. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class TransitionCommands {
    private static final Map<MinecraftServer, CleanupRequest> REQUESTS = new IdentityHashMap<>();
    static final AtomicReference<TransitionCleanupJob.Result> CLIENT_RESULT = new AtomicReference<>();
    private TransitionCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cffa")
            .then(Commands.literal("prepare_split").requires(source -> source.hasPermission(2)).executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                synchronized (REQUESTS) { REQUESTS.put(server, new CleanupRequest(System.nanoTime())); }
                server.getPlayerList().broadcastSystemMessage(Component.translatable("createfirefightingadd.transition.queued"), false);
                server.sendSystemMessage(Component.translatable("createfirefightingadd.transition.queued"));
                return 1;
            }))
            .then(Commands.literal("cancel_split").requires(source -> source.hasPermission(2)).executes(context -> {
                synchronized (REQUESTS) { REQUESTS.remove(context.getSource().getServer()); }
                context.getSource().sendSuccess(() -> Component.translatable("createfirefightingadd.transition.cancelled"), true);
                return 1;
            })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        boolean expired;
        synchronized (REQUESTS) {
            CleanupRequest request = REQUESTS.get(server);
            expired = request != null && request.expire(System.nanoTime());
            if (expired) REQUESTS.remove(server);
        }
        if (expired) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("createfirefightingadd.transition.expired"), false);
            server.sendSystemMessage(Component.translatable("createfirefightingadd.transition.expired"));
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        synchronized (REQUESTS) {
            CleanupRequest request = REQUESTS.get(event.getServer());
            if (request != null) request.exitStarted(System.nanoTime());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        CleanupRequest request;
        synchronized (REQUESTS) { request = REQUESTS.remove(event.getServer()); }
        if (request == null) return;
        request.storageClosed();
        if (!request.consume()) return;
        CreateFireFightingAdd.LOGGER.info("CFFA transition: world stopped. Checking limits, then backing up and cleaning a separate copy.");
        TransitionCleanupJob.Result result = TransitionCleanupJob.run(event.getServer().getWorldPath(LevelResource.ROOT));
        if (result.success()) CreateFireFightingAdd.LOGGER.info("{}", result.reason());
        else CreateFireFightingAdd.LOGGER.error("{}", result.reason());
        if (!event.getServer().isDedicatedServer()) CLIENT_RESULT.set(result);
    }
}
