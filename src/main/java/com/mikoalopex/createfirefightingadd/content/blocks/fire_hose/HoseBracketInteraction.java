package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Handles the two-node selection stored on Create bracket items and inserts a
 * decorative bracket between the selected adjacent route nodes.
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class HoseBracketInteraction {
    public static final String SELECTION = "HoseBracketSelection";

    private HoseBracketInteraction() {}

    public static boolean isBracket(ItemStack stack) {
        return AllBlocks.METAL_BRACKET.isIn(stack) || AllBlocks.WOODEN_BRACKET.isIn(stack);
    }

    public static CompoundTag selection(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(SELECTION);
    }

    private static void selection(ItemStack stack, CompoundTag selection) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (selection.isEmpty()) tag.remove(SELECTION);
            else tag.put(SELECTION, selection);
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!isBracket(stack))
            return;
        Level level = event.getLevel();
        BlockEntity target = level.getBlockEntity(event.getPos());
        CompoundTag selection = selection(stack);
        boolean node = target instanceof FireHoseBlockEntity || target instanceof HoseBracketBlockEntity;
        if (!node && selection.isEmpty())
            return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide || !event.getEntity().mayBuild()
            || !level.mayInteract(event.getEntity(), event.getPos()))
            return;
        if (event.getEntity().isShiftKeyDown()) {
            selection(stack, new CompoundTag());
            message(event, "cancelled");
            return;
        }
        HoseRoutes data = HoseRoutes.get(level);
        if (node) {
            HoseRoute route = target instanceof FireHoseBlockEntity hose ? data.ensure(hose)
                : data.find(((HoseBracketBlockEntity) target).route == null ? null
                    : ((HoseBracketBlockEntity) target).route.id);
            UUID id = target instanceof FireHoseBlockEntity hose ? hose.getFireHoseEndpointId()
                : ((HoseBracketBlockEntity) target).nodeId;
            if (route == null || !com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances
                .get(route.appearance).rendersHose()) {
                message(event, "unavailable");
                return;
            }
            if (selection.hasUUID("First") && !selection.hasUUID("Second")) {
                if (!selection.hasUUID("Route") || !route.id.equals(selection.getUUID("Route"))
                    || route.index(selection.getUUID("First")) < 0 || route.index(id) < 0
                    || Math.abs(route.index(selection.getUUID("First")) - route.index(id)) != 1) {
                    message(event, "adjacent");
                    return;
                }
                selection.putUUID("Second", id);
                message(event, "place");
            } else {
                selection = new CompoundTag();
                selection.putUUID("Route", route.id);
                selection.putUUID("First", id);
                selection.putString("Dimension", level.dimension().location().toString());
                message(event, "second");
            }
            selection(stack, selection);
            return;
        }
        if (!selection.hasUUID("Second") || !selection.hasUUID("First") || !selection.hasUUID("Route")) {
            message(event, "second");
            return;
        }
        HoseRoute route = data.find(selection.getUUID("Route"));
        if (route == null || !level.dimension().location().toString().equals(selection.getString("Dimension"))) {
            selection(stack, new CompoundTag());
            message(event, "unavailable");
            return;
        }
        int first = route.index(selection.getUUID("First"));
        int second = route.index(selection.getUUID("Second"));
        if (first < 0 || second < 0 || Math.abs(first - second) != 1) {
            message(event, "adjacent");
            return;
        }
        BlockPos pos = event.getPos().relative(event.getFace());
        Direction face = event.getFace();
        BlockPlaceContext context = new BlockPlaceContext(event.getEntity(), event.getHand(), stack, event.getHitVec());
        var state = CreateFireFightingAdd.HOSE_BRACKET.get().defaultBlockState()
            .setValue(HoseBracketBlock.FACING, face)
            .setValue(HoseBracketBlock.WOODEN, AllBlocks.WOODEN_BRACKET.isIn(stack));
        if (!level.getBlockState(pos).canBeReplaced(context) || !level.isInWorldBounds(pos)
            || !level.mayInteract(event.getEntity(), pos)
            || !event.getEntity().mayUseItemAt(pos, face, stack)
            || !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.of(event.getEntity()))) {
            message(event, "blocked");
            return;
        }
        BlockEntity a = route.nodes.getFirst().resolve(level);
        BlockEntity b = route.nodes.getLast().resolve(level);
        if (!(a instanceof FireHoseBlockEntity hoseA) || !(b instanceof FireHoseBlockEntity hoseB)
            || hoseA.getPairedHose() != hoseB) {
            message(event, "unavailable");
            return;
        }
        Vec3 world = SableStructureCompat.projectToWorld(level, pos.getCenter());
        double limit = Config.hoseMaxLength * Config.hoseSnapMultiplier;
        if (world.distanceTo(hoseA.getWorldCenterVec()) > limit || world.distanceTo(hoseB.getWorldCenterVec()) > limit
            || HoseRoutes.maximumDistance(hoseA, hoseA.getWorldCenterVec().distanceTo(hoseB.getWorldCenterVec())) > limit
            || !hoseA.getHoseAppearance().rendersHose()) {
            message(event, "range");
            return;
        }
        BlockSnapshot original = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3))
            return;
        if (EventHooks.onBlockPlace(event.getEntity(), original, face) || data.find(route.id) != route) {
            original.restore();
            return;
        }
        if (level.getBlockEntity(pos) instanceof HoseBracketBlockEntity bracket) {
            int insertion = Math.min(first, second) + 1;
            bracket.route = route;
            Vec3 towardNext = SableStructureCompat.projectToWorld(level, route.nodes.get(insertion).center()).subtract(world);
            Vec3 normal = SableStructureCompat.transformNormalToWorld(bracket, HoseRoute.Node.of(bracket).normal());
            bracket.reversed = normal.dot(towardNext) < 0;
            route.nodes.add(insertion, HoseRoute.Node.of(bracket));
            data.publish(level, route);
            selection(stack, new CompoundTag());
            if (!event.getEntity().isCreative())
                stack.shrink(1);
        }
    }

    private static void message(PlayerInteractEvent.RightClickBlock event, String key) {
        event.getEntity().displayClientMessage(Component.translatable("createfirefightingadd.hose_bracket." + key), true);
    }
}
