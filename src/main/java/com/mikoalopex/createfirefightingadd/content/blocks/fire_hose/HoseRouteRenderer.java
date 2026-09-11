package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Static blocks and Create actors contribute poses to the same visual path. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class HoseRouteRenderer {
    private static ClientLevel currentLevel;
    private static final Map<UUID, Source> SOURCES = new HashMap<>();

    private record Source(HoseRoute route, HoseRoute.Node node, BlockEntity be, MovementContext moving, long expires) {}
    private record Pose(Vec3 center, Vec3 incoming, Vec3 outgoing, Vec3 normal, Vec3 up) {}

    private static void checkLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (currentLevel != level) {
            currentLevel = level;
            SOURCES.clear();
        }
    }

    public static void track(BlockEntity be, HoseRoute route) {
        checkLevel();
        if (route == null || currentLevel == null || be.getLevel() != currentLevel)
            return;
        HoseRoute.Node node = be instanceof FireHoseBlockEntity hose ? HoseRoute.Node.of(hose)
            : HoseRoute.Node.of((HoseBracketBlockEntity) be);
        SOURCES.put(node.id(), new Source(route, node, be, null, currentLevel.getGameTime() + 2));
    }

    public static void track(MovementContext context, HoseRoute route, UUID id) {
        checkLevel();
        if (route == null || currentLevel == null || context.position == null)
            return;
        int index = route.index(id);
        if (index >= 0)
            SOURCES.put(id, new Source(route, route.nodes.get(index), null, context, currentLevel.getGameTime() + 2));
    }

    public static boolean hasRoute(UUID endpoint) {
        Source source = SOURCES.get(endpoint);
        return source != null && source.route.nodes.size() >= 2;
    }

    private static Pose pose(HoseRoute.Node node, float partialTick) {
        Source source = SOURCES.get(node.id());
        if (source != null && source.moving != null) {
            MovementContext context = source.moving;
            Vec3 normal = context.rotation.apply(node.normal());
            Vec3 up = context.rotation.apply(node.up());
            Vec3 center = context.position;
            if (context.contraption.entity != null) {
                Vec3 localCenter = context.localPos.getCenter();
                center = context.contraption.entity.toGlobalVector(localCenter, partialTick);
                normal = context.contraption.entity.toGlobalVector(localCenter.add(node.normal()), partialTick).subtract(center);
                up = context.contraption.entity.toGlobalVector(localCenter.add(node.up()), partialTick).subtract(center);
            }
            return new Pose(center, center.add(normal.scale(-0.25)),
                center.add(normal.scale(node.endpoint() ? -0.25 : 0.25)), normal, up);
        }
        BlockEntity be = node.resolve(currentLevel);
        if (be == null)
            return null;
        HoseRoute.Node actual = be instanceof FireHoseBlockEntity hose ? HoseRoute.Node.of(hose)
            : HoseRoute.Node.of((HoseBracketBlockEntity) be);
        return new Pose(SableStructureClientCompat.renderPositionToWorld(be, actual.center()),
            SableStructureClientCompat.renderPositionToWorld(be, actual.port(false)),
            SableStructureClientCompat.renderPositionToWorld(be, actual.port(true)),
            SableStructureClientCompat.renderNormalToWorld(be, actual.normal()),
            SableStructureClientCompat.renderNormalToWorld(be, actual.up()));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        checkLevel();
        if (currentLevel == null)
            return;
        SOURCES.values().removeIf(source -> source.expires < currentLevel.getGameTime());
        Map<UUID, HoseRoute> routes = new HashMap<>();
        for (Source source : SOURCES.values())
            routes.merge(source.route.id, source.route, (a, b) -> a.revision >= b.revision ? a : b);
        var minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers().bufferSource();
        var matrix = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        matrix.pushPose();
        matrix.translate(-camera.x, -camera.y, -camera.z);
        CompoundTag selected = new CompoundTag();
        if (minecraft.player != null)
            for (InteractionHand hand : InteractionHand.values()) {
                var item = minecraft.player.getItemInHand(hand);
                if (HoseBracketInteraction.isBracket(item)) {
                    selected = HoseBracketInteraction.selection(item);
                    if (!selected.isEmpty()) break;
                }
            }
        for (HoseRoute route : routes.values()) {
            if (route.nodes.size() < 2 || !FireHoseAppearances.get(route.appearance).rendersHose())
                continue;
            RenderType type = RenderType.entityCutoutNoCull(FireHoseAppearances.get(route.appearance).hoseTexture());
            for (int i = 0; i + 1 < route.nodes.size(); i++) {
                HoseRoute.Node a = route.nodes.get(i);
                HoseRoute.Node b = route.nodes.get(i + 1);
                float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                Pose start = pose(a, partialTick);
                Pose end = pose(b, partialTick);
                if (start == null || end == null)
                    continue;
                boolean chosen = selected.hasUUID("Route") && selected.getUUID("Route").equals(route.id);
                if (chosen && selected.hasUUID("First")) {
                    if (a.id().equals(selected.getUUID("First")))
                        highlightNode("hose_route_first", start.center);
                    if (b.id().equals(selected.getUUID("First")))
                        highlightNode("hose_route_first", end.center);
                    if (selected.hasUUID("Second")) {
                        if (a.id().equals(selected.getUUID("Second")))
                            highlightNode("hose_route_second", start.center);
                        if (b.id().equals(selected.getUUID("Second")))
                            highlightNode("hose_route_second", end.center);
                    }
                }
                boolean selectedSegment = chosen && selected.hasUUID("Second")
                    && (a.id().equals(selected.getUUID("First")) && b.id().equals(selected.getUUID("Second"))
                    || b.id().equals(selected.getUUID("First")) && a.id().equals(selected.getUUID("Second")));
                int light = LevelRenderer.getLightColor(currentLevel, net.minecraft.core.BlockPos.containing(start.outgoing));
                Vec3 endNormal = b.endpoint() ? end.normal : end.normal.scale(-1);
                var points = FireHoseDynamicRenderer.attachedSpline(start.outgoing, end.incoming,
                    start.normal, endNormal, start.up, end.up);
                FireHoseDynamicRenderer.renderAttachedSpline(matrix, buffers.getBuffer(type), points, light);
                if (selectedSegment)
                    highlightSegment(points);
            }
            buffers.endBatch(type);
        }
        matrix.popPose();
    }

    private static void highlightNode(String key, Vec3 center) {
        Outliner.getInstance().showAABB(key, new AABB(center, center).inflate(0.2))
            .colored(FireHoseItemHandler.SUCCESS_LIME).lineWidth(1 / 16f);
    }

    private static void highlightSegment(java.util.List<FireHoseDynamicRenderer.AttachedPoint> points) {
        Vec3[] previous = null;
        for (int i = 0; i < points.size(); i++) {
            var sample = points.get(i);
            Vec3 normal = new Vec3(sample.normal().x(), sample.normal().y(), sample.normal().z()).normalize();
            Vec3 up = new Vec3(sample.up().x(), sample.up().y(), sample.up().z());
            Vec3 left = up.cross(normal).normalize().scale(0.255);
            up = up.scale(0.255);
            Vec3 point = new Vec3(sample.point().x(), sample.point().y(), sample.point().z());
            Vec3[] corners = {point.add(up).add(left), point.add(up).subtract(left),
                point.subtract(up).subtract(left), point.subtract(up).add(left)};
            for (int edge = 0; edge < 4; edge++) {
                if (previous != null)
                    Outliner.getInstance().showLine("hose_segment_" + i + "_" + edge, previous[edge], corners[edge])
                        .colored(FireHoseItemHandler.SUCCESS_LIME).lineWidth(1 / 16f);
            }
            previous = corners;
        }
    }
}
