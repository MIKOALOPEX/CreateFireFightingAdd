package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record PlaceFireHosePacket(BlockPos firstPos, BlockPos targetPos, Direction targetFacing,
                                  InteractionHand hand, int action) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int PLACE_ENDPOINT = 0;
    private static final int PLACE_AND_CONNECT_FREE = 1;
    private static final int PLACE_AND_CONNECT_CONSUME = 2;
    private static final int CONNECT_EXISTING = 3;

    public static final Type<PlaceFireHosePacket> TYPE =
            new Type<>(CreateFireFightingAdd.path("place_fire_hose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceFireHosePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PlaceFireHosePacket::firstPos,
                    BlockPos.STREAM_CODEC, PlaceFireHosePacket::targetPos,
                    Direction.STREAM_CODEC, PlaceFireHosePacket::targetFacing,
                    ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal),
                    PlaceFireHosePacket::hand,
                    ByteBufCodecs.VAR_INT, PlaceFireHosePacket::action,
                    PlaceFireHosePacket::new
            );

    public static PlaceFireHosePacket placeEndpoint(BlockPos clickedPos, Direction facing, InteractionHand hand) {
        return new PlaceFireHosePacket(BlockPos.ZERO, clickedPos, facing, hand, PLACE_ENDPOINT);
    }

    public static PlaceFireHosePacket placeAndConnect(BlockPos firstEndpoint, BlockPos clickedPos,
                                                      Direction facing, InteractionHand hand,
                                                      boolean consumeItem) {
        return new PlaceFireHosePacket(firstEndpoint, clickedPos, facing, hand,
            consumeItem ? PLACE_AND_CONNECT_CONSUME : PLACE_AND_CONNECT_FREE);
    }

    public static PlaceFireHosePacket connectExisting(BlockPos firstEndpoint, BlockPos secondEndpoint,
                                                      InteractionHand hand) {
        return new PlaceFireHosePacket(firstEndpoint, secondEndpoint, Direction.UP, hand, CONNECT_EXISTING);
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(CreateFireFightingAdd.MODID)
                .playToServer(TYPE, STREAM_CODEC, PlaceFireHosePacket::handle);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaceFireHosePacket packet, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        Level level = player.level();
        ItemStack hose = player.getItemInHand(packet.hand());
        if (!(hose.getItem() instanceof FireHoseItem)) {
            LOGGER.warn("[FireHosePacket] rejected action {}: player is not holding a fire hose", packet.action());
            return;
        }

        switch (packet.action()) {
            case PLACE_ENDPOINT -> placeEndpoint(level, player, hose, packet.targetPos(), packet.targetFacing(), true);
            case PLACE_AND_CONNECT_FREE ->
                placeAndConnect(level, player, hose, packet.firstPos(), packet.targetPos(), packet.targetFacing(), false);
            case PLACE_AND_CONNECT_CONSUME ->
                placeAndConnect(level, player, hose, packet.firstPos(), packet.targetPos(), packet.targetFacing(), true);
            case CONNECT_EXISTING -> connectExisting(level, player, packet.firstPos(), packet.targetPos());
            default -> LOGGER.warn("[FireHosePacket] rejected unknown action {}", packet.action());
        }
    }

    private static void placeAndConnect(Level level, ServerPlayer player, ItemStack hose,
                                        BlockPos firstEndpoint, BlockPos clickedPos,
                                        Direction facing, boolean consumeItem) {
        if (!(level.getBlockEntity(firstEndpoint) instanceof FireHoseBlockEntity)) {
            sendMessage(player, "missing_endpoint");
            return;
        }

        BlockPos placedPos = clickedPos.relative(facing);
        FireHoseBlockEntity placed = placeEndpoint(level, player, hose, clickedPos, facing, consumeItem);
        if (placed == null)
            return;

        FireHoseConnections.Result result = FireHoseConnections.tryConnect(level, firstEndpoint, placedPos);
        if (result != FireHoseConnections.Result.SUCCESS)
            sendConnectionError(player, result);
    }

    private static void connectExisting(Level level, ServerPlayer player, BlockPos firstEndpoint, BlockPos secondEndpoint) {
        FireHoseConnections.Result result = FireHoseConnections.tryConnect(level, firstEndpoint, secondEndpoint);
        if (result == FireHoseConnections.Result.SUCCESS)
            sendMessage(player, "connected");
        else
            sendConnectionError(player, result);
    }

    private static FireHoseBlockEntity placeEndpoint(Level level, ServerPlayer player, ItemStack hose,
                                                     BlockPos clickedPos, Direction facing, boolean consumeItem) {
        BlockPos placedPos = clickedPos.relative(facing);
        if (!level.getBlockState(placedPos).canBeReplaced()) {
            sendMessage(player, "block_exists");
            return null;
        }

        FireHoseBlockEntity endpoint = addHose(level, placedPos, facing);
        if (endpoint == null) {
            level.setBlockAndUpdate(placedPos, Blocks.AIR.defaultBlockState());
            sendMessage(player, "block_exists");
            return null;
        }

        player.awardStat(Stats.ITEM_USED.get(hose.getItem()));
        if (consumeItem && !player.hasInfiniteMaterials())
            hose.shrink(1);
        return endpoint;
    }

    private static FireHoseBlockEntity addHose(Level level, BlockPos placedPos, Direction facing) {
        BlockState newState = FIRE_HOSE.get().defaultBlockState().setValue(FireHoseBlock.FACING, facing);

        FireHoseDebugLog.logRaw("add hose endpoint pos={} facing={} oldState={}",
                placedPos, facing, level.getBlockState(placedPos).getBlock());

        if (level.setBlockAndUpdate(placedPos, newState)) {
            if (level.getBlockEntity(placedPos) instanceof FireHoseBlockEntity be) {
                be.setFireHoseConnection(true, null,
                    SableStructureCompat.containingSubLevelId(level, placedPos), false);
                FireHoseConnections.disconnect(be);
                FireHoseDebugLog.logRaw("add hose endpoint ok pos={} beHash={}",
                        placedPos, Integer.toHexString(System.identityHashCode(be)));
                return be;
            }
            LOGGER.error("[FireHosePacket] addHose at {} returned no fire hose BE", placedPos);
            return null;
        }

        LOGGER.error("[FireHosePacket] addHose FAILED at {} setBlockAndUpdate returned false", placedPos);
        return null;
    }

    private static void sendConnectionError(ServerPlayer player, FireHoseConnections.Result result) {
        switch (result) {
            case SAME_ENDPOINT -> sendMessage(player, "same_block");
            case OUT_OF_RANGE -> sendMessage(player, "out_of_range");
            case MISSING_ENDPOINT -> sendMessage(player, "missing_endpoint");
            default -> {
            }
        }
    }

    private static void sendMessage(ServerPlayer player, String message) {
        player.displayClientMessage(
            Component.translatable("createfirefightingadd.fire_hose." + message),
            true);
    }
}
