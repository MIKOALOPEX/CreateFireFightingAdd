package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.fluids.FluidPropagator;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE;
import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_ITEM;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public record PlaceFireHosePacket(BlockPos parentPos, BlockPos childPos,
                                   Direction parentFacing, Direction childFacing,
                                   InteractionHand hand) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<PlaceFireHosePacket> TYPE =
            new Type<>(CreateFireFightingAdd.path("place_fire_hose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceFireHosePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PlaceFireHosePacket::parentPos,
                    BlockPos.STREAM_CODEC, PlaceFireHosePacket::childPos,
                    Direction.STREAM_CODEC, PlaceFireHosePacket::parentFacing,
                    Direction.STREAM_CODEC, PlaceFireHosePacket::childFacing,
                    ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], InteractionHand::ordinal),
                        PlaceFireHosePacket::hand,
                    PlaceFireHosePacket::new
            );

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

        BlockPos parentRelative = packet.parentPos().relative(packet.parentFacing());
        BlockPos childRelative = packet.childPos().relative(packet.childFacing());

        FireHoseDebugLog.logRaw("place received parentClick={} parentFacing={} parentPlace={} childClick={} childFacing={} childPlace={} hand={}",
                packet.parentPos(), packet.parentFacing(), parentRelative,
                packet.childPos(), packet.childFacing(), childRelative,
                packet.hand());

        ItemStack hose = player.getItemInHand(packet.hand());
        long maxLen = Config.hoseMaxLength;
        double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(
                level, parentRelative.getCenter(), childRelative.getCenter());

        FireHoseDebugLog.logRaw("place range distanceSquared={} maxLenSquared={} validItem={}",
                String.format("%.1f", distanceSquared),
                (maxLen + 1) * (maxLen + 1),
                hose.getItem() instanceof FireHoseItem);

        if (!(hose.getItem() instanceof FireHoseItem)
                || distanceSquared > (maxLen + 1) * (maxLen + 1)) {
            LOGGER.warn("[FireHosePacket] REJECTED: bad item or out of range");
            return;
        }

        BlockEntity existingParent = level.getBlockEntity(parentRelative);
        BlockEntity existingChild = level.getBlockEntity(childRelative);
        if (existingParent instanceof FireHoseBlockEntity) {
            LOGGER.warn("[FireHosePacket] OLD BE at parent {} will be replaced", parentRelative);
        }
        if (existingChild instanceof FireHoseBlockEntity) {
            LOGGER.warn("[FireHosePacket] OLD BE at child {} will be replaced", childRelative);
        }

        FireHoseBlockEntity controller = addHose(level, parentRelative, childRelative,
                packet.parentFacing(), true);
        FireHoseBlockEntity partner = addHose(level, childRelative, parentRelative,
                packet.childFacing(), false);

        if (controller == null || partner == null) {
            LOGGER.error("[FireHosePacket] PLACEMENT FAILED: controller={} partner={} rolling back",
                    controller != null, partner != null);
            level.setBlockAndUpdate(parentRelative, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(childRelative, Blocks.AIR.defaultBlockState());
            return;
        }

        FireHoseDebugLog.logRaw("place complete controller={}@{} partner={}@{}",
                Integer.toHexString(System.identityHashCode(controller)), parentRelative,
                Integer.toHexString(System.identityHashCode(partner)), childRelative);

        FluidPropagator.propagateChangedPipe(level, parentRelative, level.getBlockState(parentRelative));
        FluidPropagator.propagateChangedPipe(level, childRelative, level.getBlockState(childRelative));
        FireHoseDebugLog.logRaw("place propagated changed pipes parent={} child={}", parentRelative, childRelative);

        FireHoseDebugLog.logRaw("place done parent={} child={} distance={}",
                parentRelative, childRelative, String.format("%.1f", Math.sqrt(distanceSquared)));

        player.awardStat(Stats.ITEM_USED.get(hose.getItem()));
        if (!player.hasInfiniteMaterials()) {
            hose.shrink(1);
        }
    }

    private static FireHoseBlockEntity addHose(Level level, BlockPos placedPos, BlockPos partnerPos,
                                                Direction facing, boolean isController) {
        BlockState newState = FIRE_HOSE.get().defaultBlockState().setValue(FireHoseBlock.FACING, facing);

        BlockState oldState = level.getBlockState(placedPos);
        FireHoseDebugLog.logRaw("add hose pos={} facing={} controller={} oldState={}",
                placedPos, facing, isController, oldState.getBlock());

        if (level.setBlockAndUpdate(placedPos, newState)) {
            FireHoseBlockEntity be = (FireHoseBlockEntity) level.getBlockEntity(placedPos);
            if (be == null) {
                LOGGER.error("[FireHosePacket] addHose at {} returned null BE after setBlockAndUpdate", placedPos);
                return null;
            }
            be.setController(isController);
            SubLevel subLevel = Sable.HELPER.getContaining(level, partnerPos);
            be.setPartnerPos(partnerPos, subLevel != null ? subLevel.getUniqueId() : null);
            be.notifyUpdate();

            Direction back = facing.getOpposite();
            FireHoseDebugLog.logRaw("add hose direction pos={} facing={} back={}", placedPos, facing, back);
            for (Direction d : Direction.values()) {
                BlockPos neighbor = placedPos.relative(d);
                boolean isPipe = FluidPropagator.getPipe(level, neighbor) != null;
                String label = "";
                if (d == facing) label = " [FACING front]";
                else if (d == back) label = " [BACK]";
                FireHoseDebugLog.logRaw("add hose neighbor {} -> {} pipe={}{}",
                        d, neighbor, isPipe, label);
            }

            FireHoseDebugLog.logRaw("add hose ok pos={} beHash={} controller={} partnerPos={}",
                    placedPos,
                    Integer.toHexString(System.identityHashCode(be)),
                    isController, partnerPos);
            return be;
        }
        LOGGER.error("[FireHosePacket] addHose FAILED at {} setBlockAndUpdate returned false", placedPos);
        return null;
    }
}
