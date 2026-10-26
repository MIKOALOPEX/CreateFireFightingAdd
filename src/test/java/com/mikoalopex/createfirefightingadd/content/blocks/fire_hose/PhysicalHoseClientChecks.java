package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoses;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Exercises baked models and the real vertex emitter in the transformed client. */
public final class PhysicalHoseClientChecks {
    private static BlockPos origin;
    private static long started;
    private static long received;
    private static volatile Throwable serverFailure;

    public static boolean nativeReady() {
        if (!PhysicalHoses.enabled()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (origin == null) {
            origin = mc.player.blockPosition().offset(1, 3, 1);
            started = System.nanoTime();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var level = mc.getSingleplayerServer().overworld();
                    var blocks = List.of(PhysicalHoses.SMALL.get(), PhysicalHoses.MEDIUM.get(), PhysicalHoses.LARGE.get());
                    for (int i = 0; i < blocks.size(); i++) {
                        BlockPos a = origin.offset(0, 0, i * 2), b = a.offset(5, 0, 0);
                        level.setBlockAndUpdate(a, blocks.get(i).defaultBlockState().setValue(FireHoseBlock.FACING, net.minecraft.core.Direction.EAST));
                        level.setBlockAndUpdate(b, blocks.get(i).defaultBlockState().setValue(FireHoseBlock.FACING, net.minecraft.core.Direction.WEST));
                        if (!((PhysicalHoseBlockEntity) level.getBlockEntity(a)).connectPhysical((PhysicalHoseBlockEntity) level.getBlockEntity(b)))
                            throw new AssertionError("Native client fixture could not connect");
                    }
                } catch (Throwable error) { serverFailure = error; }
            });
            return false;
        }
        if (serverFailure != null) throw new AssertionError("Native client fixture failed", serverFailure);
        for (int i = 0; i < 3; i++) {
            if (!(mc.level.getBlockEntity(origin.offset(0, 0, i * 2)) instanceof PhysicalHoseBlockEntity hose)
                    || hose.renderPoints(1).size() < 2) {
                if (System.nanoTime() - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(30))
                    throw new AssertionError("Native hose points never reached the client");
                return false;
            }
        }
        if (received == 0) received = System.nanoTime();
        if (System.nanoTime() - received < java.util.concurrent.TimeUnit.SECONDS.toNanos(3)) return false;
        CreateFireFightingAdd.LOGGER.info("Physical hose native client synchronization: PASSED");
        return true;
    }

    public static void check() {
        Minecraft mc = Minecraft.getInstance();
        for (var block : List.of(PhysicalHoses.SMALL.get(), PhysicalHoses.MEDIUM.get(), PhysicalHoses.LARGE.get())) {
            for (String color : new String[] {"", "_black"}) {
                for (String part : new String[] {"a", "mid", "b"}) {
                    var id = ModelResourceLocation.standalone(CreateFireFightingAdd.path("block/physical_fire_hose/" + block.defaultBlockState().getValue(com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlock.SIZE).width + "_" + part + color));
                    var model = mc.getModelManager().getModel(id);
                    if (model == mc.getModelManager().getMissingModel()) throw new AssertionError("Missing model " + id);
                    var quads = model.getQuads(null, null, RandomSource.create(0));
                    if (quads.size() != 4) throw new AssertionError("Expected four open tube faces: " + id);
                    for (var quad : quads)
                        if (!quad.getSprite().contents().name().equals(CreateFireFightingAdd.path("block/fire_hose" + color)))
                            throw new AssertionError("Missing hose texture: " + id);
                }
            }
            var be = new Preview(block.defaultBlockState()); be.setLevel(mc.level);
            var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer == null) throw new AssertionError("Missing physical hose renderer");
            List<float[]> positions = new ArrayList<>();
            VertexConsumer sink = sink(positions, new ArrayList<>());
            renderer.render(be, 0, new PoseStack(), type -> sink, 0xf000f0, 0);
            if (positions.size() < 64) throw new AssertionError("No physical tube geometry");
            for (float[] point : positions) for (float component : point)
                if (!Float.isFinite(component)) throw new AssertionError("Non-finite hose geometry");
        }
        List<float[]> positions = new ArrayList<>(), uv = new ArrayList<>();
        FireHoseDynamicRenderer.renderSegment(new PoseStack(), new Vector3d(0, 0, 1), new Vector3d(0, 0, 1),
            new Vector3d(0, 1, 0), new Vector3d(0, 1, 0), new Vector3d(), new Vector3d(0, 0, 2.25),
            0, 2.25f, 0xf000f0, -1, sink(positions, uv), 6, 6);
        if (positions.size() != 48 || uv.size() != 48) throw new AssertionError("Expected three tiles on each of four faces");
        for (int i = 0; i < uv.size(); i += 4) {
            float dz = positions.get(i + 1)[2] - positions.get(i)[2];
            float dv = uv.get(i + 1)[1] - uv.get(i)[1];
            if (Math.abs(dz - dv) > 1e-6 || uv.get(i)[0] != 1 / 16f || uv.get(i + 2)[0] != 7 / 16f)
                throw new AssertionError("Texture stretched or cropped off-centre");
        }
        CreateFireFightingAdd.LOGGER.info("Physical hose client geometry and texture checks: PASSED");
    }

    private static VertexConsumer sink(List<float[]> positions, List<float[]> uv) {
        return (VertexConsumer) Proxy.newProxyInstance(PhysicalHoseClientChecks.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
            (proxy, method, args) -> {
                if (method.getName().equals("addVertex") && args.length == 3)
                    positions.add(new float[]{((Number)args[0]).floatValue(), ((Number)args[1]).floatValue(), ((Number)args[2]).floatValue()});
                else if (method.getName().equals("addVertex") && args.length == 4) {
                    org.joml.Vector3f point = ((org.joml.Matrix4f)args[0]).transformPosition(((Number)args[1]).floatValue(),
                        ((Number)args[2]).floatValue(), ((Number)args[3]).floatValue(), new org.joml.Vector3f());
                    positions.add(new float[]{point.x, point.y, point.z});
                }
                if (method.getName().equals("setUv")) uv.add(new float[]{(float)args[0], (float)args[1]});
                return method.getReturnType() == void.class ? null : proxy;
            });
    }

    private static final class Preview extends PhysicalHoseBlockEntity {
        Preview(BlockState state) { super(BlockPos.ZERO, state); }
        @Override public List<Vec3> renderPoints(float partial) {
            return List.of(new Vec3(0, 0, 0), new Vec3(0, 0, 0.2), new Vec3(0, 0, 1.2),
                new Vec3(0, 0, 2.2), new Vec3(0, 1, 2.2), new Vec3(1, 1, 2.2));
        }
    }
}
