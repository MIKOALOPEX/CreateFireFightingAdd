package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.ConeNozzleBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.FlatNozzleBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SableStructureCompat {

    private static final StructureBackend BACKEND = loadBackend();

    private SableStructureCompat() {
    }

    public static double distance(Level level, BlockPos first, BlockPos second) {
        return Math.sqrt(distanceSquared(level, Vec3.atCenterOf(first), Vec3.atCenterOf(second)));
    }

    public static FireHoseBlock createFireHoseBlock(BlockBehaviour.Properties properties) {
        if (BACKEND != null) {
            FireHoseBlock block = instantiateSableFireHoseBlock(properties);
            if (block != null)
                return block;
        }
        return new FireHoseBlock(properties);
    }

    public static FireHoseBlockEntity createFireHoseBlockEntity(BlockPos pos, BlockState state) {
        if (BACKEND != null) {
            FireHoseBlockEntity blockEntity = instantiateSableFireHoseBlockEntity(pos, state);
            if (blockEntity != null)
                return blockEntity;
        }
        return new FireHoseBlockEntity(pos, state);
    }

    public static ConeNozzleBlockEntity createConeNozzleBlockEntity(BlockPos pos, BlockState state) {
        ConeNozzleBlockEntity blockEntity = instantiateSableBlockEntity(
            "com.mikoalopex.createfirefightingadd.integration.sable.SableConeNozzleBlockEntity",
            ConeNozzleBlockEntity.class,
            pos,
            state);
        return blockEntity != null ? blockEntity : new ConeNozzleBlockEntity(pos, state);
    }

    public static FlatNozzleBlockEntity createFlatNozzleBlockEntity(BlockPos pos, BlockState state) {
        FlatNozzleBlockEntity blockEntity = instantiateSableBlockEntity(
            "com.mikoalopex.createfirefightingadd.integration.sable.SableFlatNozzleBlockEntity",
            FlatNozzleBlockEntity.class,
            pos,
            state);
        return blockEntity != null ? blockEntity : new FlatNozzleBlockEntity(pos, state);
    }

    public static BucketControllerBlockEntity createBucketControllerBlockEntity(BlockPos pos, BlockState state) {
        BucketControllerBlockEntity blockEntity = instantiateSableBlockEntity(
            "com.mikoalopex.createfirefightingadd.integration.sable.SableBucketControllerBlockEntity",
            BucketControllerBlockEntity.class,
            pos,
            state);
        return blockEntity != null ? blockEntity : new BucketControllerBlockEntity(pos, state);
    }

    public static WaterIntakeBlockEntity createWaterIntakeBlockEntity(BlockPos pos, BlockState state) {
        WaterIntakeBlockEntity blockEntity = instantiateSableBlockEntity(
            "com.mikoalopex.createfirefightingadd.integration.sable.SableWaterIntakeBlockEntity",
            WaterIntakeBlockEntity.class,
            pos,
            state);
        return blockEntity != null ? blockEntity : new WaterIntakeBlockEntity(pos, state);
    }

    public static double distanceSquared(Level level, BlockPos first, BlockPos second) {
        return distanceSquared(level, Vec3.atCenterOf(first), Vec3.atCenterOf(second));
    }

    public static double distanceSquared(Level level, Vec3 first, Vec3 second) {
        if (level != null && BACKEND != null)
            return BACKEND.distanceSquared(level, first, second);
        return first.distanceToSqr(second);
    }

    public static Vec3 projectToWorld(Level level, Vec3 localPos) {
        if (level != null && BACKEND != null)
            return BACKEND.projectToWorld(level, localPos);
        return localPos;
    }

    public static Level worldLevel(BlockEntity owner) {
        if (owner.getLevel() != null && BACKEND != null)
            return BACKEND.worldLevel(owner);
        return owner.getLevel();
    }

    public static BlockPos worldBlockPos(BlockEntity owner) {
        if (owner.getLevel() != null && BACKEND != null)
            return BACKEND.worldBlockPos(owner);
        return owner.getBlockPos();
    }

    public static Vec3 transformPositionToWorld(BlockEntity owner, Vec3 localPos) {
        if (owner.getLevel() != null && BACKEND != null)
            return BACKEND.transformPositionToWorld(owner, localPos);
        return localPos;
    }

    public static Vec3 transformNormalToWorld(BlockEntity owner, Vec3 localNormal) {
        if (owner.getLevel() != null && BACKEND != null)
            return BACKEND.transformNormalToWorld(owner, localNormal);
        return localNormal;
    }

    public static Vec3 transformNormalToLocal(BlockEntity owner, Vec3 worldNormal) {
        if (owner.getLevel() != null && BACKEND != null)
            return BACKEND.transformNormalToLocal(owner, worldNormal);
        return worldNormal;
    }

    public static boolean isInSubLevel(BlockEntity owner) {
        return owner.getLevel() != null && BACKEND != null && BACKEND.isInSubLevel(owner);
    }

    public static List<SubLevelProjection> projectWorldPositionsToSubLevels(Level level, List<Vec3> worldPositions) {
        if (level != null && BACKEND != null)
            return BACKEND.projectWorldPositionsToSubLevels(level, worldPositions);
        return Collections.emptyList();
    }

    public static boolean hasFirePoleNearEntity(Level level, AABB entityBox, List<Vec3> worldSamples, double radiusSqr) {
        return level != null && BACKEND != null && BACKEND.hasFirePoleNearEntity(level, entityBox, worldSamples, radiusSqr);
    }

    public static void notifyBlockChanged(BlockEntity owner) {
        if (owner.getLevel() != null && BACKEND != null)
            BACKEND.notifyBlockChanged(owner);
    }

    public static void notifyBlockChanged(Level level, BlockPos pos, BlockState state) {
        if (level != null && BACKEND != null)
            BACKEND.notifyBlockChanged(level, pos, state);
    }

    @Nullable
    public static UUID containingSubLevelId(Level level, BlockPos pos) {
        if (level != null && BACKEND != null)
            return BACKEND.containingSubLevelId(level, pos);
        return null;
    }

    public static Vec3 partnerCenterInOwnerSpace(BlockEntity owner, BlockPos partnerPos) {
        if (owner.getLevel() != null && BACKEND != null)
            return BACKEND.partnerCenterInOwnerSpace(owner, partnerPos);
        return Vec3.atCenterOf(partnerPos);
    }

    public static void writeLinkedBlock(CompoundTag tag, String posTag, String subLevelTag,
                                        BlockPos pos, @Nullable UUID subLevelId) {
        if (BACKEND != null && BACKEND.writeLinkedBlock(tag, posTag, subLevelTag, pos, subLevelId))
            return;
        if (subLevelId != null)
            tag.putUUID(subLevelTag, subLevelId);
        tag.putLong(posTag, pos.asLong());
    }

    public static LinkedBlockRef readLinkedBlock(CompoundTag tag, String posTag, String subLevelTag) {
        if (BACKEND != null) {
            LinkedBlockRef mapped = BACKEND.readLinkedBlock(tag, posTag, subLevelTag);
            if (mapped != null)
                return mapped;
        }

        UUID subLevelId = tag.hasUUID(subLevelTag) ? tag.getUUID(subLevelTag) : null;
        BlockPos pos = tag.contains(posTag) ? BlockPos.of(tag.getLong(posTag)) : null;
        return new LinkedBlockRef(pos, subLevelId);
    }

    private static StructureBackend loadBackend() {
        try {
            ClassLoader loader = SableStructureCompat.class.getClassLoader();
            if (!hasSableBackendApi(loader))
                return null;
            Class<?> backendClass = Class.forName(
                "com.mikoalopex.createfirefightingadd.integration.sable.SableStructureBackend",
                true,
                loader);
            StructureBackend backend = (StructureBackend) backendClass.getDeclaredConstructor().newInstance();
            CreateFireFightingAdd.LOGGER.info("Sable structure compatibility enabled.");
            return backend;
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable e) {
            CreateFireFightingAdd.LOGGER.warn("Sable structure compatibility is disabled because the available Sable API is not compatible.", e);
            return null;
        }
    }

    private static boolean hasSableBackendApi(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", false, loader);
        Class<?> helper = sable.getField("HELPER").getType();
        helper.getMethod("distanceSquaredWithSubLevels", Level.class, Position.class, Position.class);
        helper.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
        helper.getMethod("getContaining", BlockEntity.class);
        helper.getMethod("getContaining", Level.class, Vec3i.class);

        Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", false, loader);
        subLevel.getMethod("getLevel");
        subLevel.getMethod("logicalPose");
        subLevel.getMethod("getUniqueId");

        Class<?> serverSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel", false, loader);
        serverSubLevel.getMethod("getPlot");

        Class<?> context = Class.forName(
            "dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext",
            false,
            loader);
        context.getMethod("getCurrentContext");
        context.getMethod("getType");
        context.getMethod("getSetupTransform");
        context.getMethod("getPlaceTransform");
        context.getMethod("getBoundingBox");
        context.getMethod("getMapping", UUID.class);
        return true;
    }

    @Nullable
    private static FireHoseBlock instantiateSableFireHoseBlock(BlockBehaviour.Properties properties) {
        try {
            Class<?> blockClass = Class.forName(
                "com.mikoalopex.createfirefightingadd.integration.sable.SableFireHoseBlock",
                true,
                SableStructureCompat.class.getClassLoader());
            return (FireHoseBlock) blockClass
                .getConstructor(BlockBehaviour.Properties.class)
                .newInstance(properties);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static FireHoseBlockEntity instantiateSableFireHoseBlockEntity(BlockPos pos, BlockState state) {
        return instantiateSableBlockEntity(
            "com.mikoalopex.createfirefightingadd.integration.sable.SableFireHoseBlockEntity",
            FireHoseBlockEntity.class,
            pos,
            state);
    }

    @Nullable
    private static <T extends BlockEntity> T instantiateSableBlockEntity(String className, Class<T> type,
                                                                         BlockPos pos, BlockState state) {
        if (BACKEND == null)
            return null;
        try {
            Class<?> blockEntityClass = Class.forName(className, true, SableStructureCompat.class.getClassLoader());
            return type.cast(blockEntityClass
                .getConstructor(BlockPos.class, BlockState.class)
                .newInstance(pos, state));
        } catch (Throwable ignored) {
            return null;
        }
    }

    interface StructureBackend {
        double distanceSquared(Level level, Vec3 first, Vec3 second);

        Vec3 projectToWorld(Level level, Vec3 localPos);

        Level worldLevel(BlockEntity owner);

        BlockPos worldBlockPos(BlockEntity owner);

        Vec3 transformPositionToWorld(BlockEntity owner, Vec3 localPos);

        Vec3 transformNormalToWorld(BlockEntity owner, Vec3 localNormal);

        Vec3 transformNormalToLocal(BlockEntity owner, Vec3 worldNormal);

        boolean isInSubLevel(BlockEntity owner);

        List<SubLevelProjection> projectWorldPositionsToSubLevels(Level level, List<Vec3> worldPositions);

        boolean hasFirePoleNearEntity(Level level, AABB entityBox, List<Vec3> worldSamples, double radiusSqr);

        void notifyBlockChanged(BlockEntity owner);

        void notifyBlockChanged(Level level, BlockPos pos, BlockState state);

        @Nullable
        UUID containingSubLevelId(Level level, BlockPos pos);

        Vec3 partnerCenterInOwnerSpace(BlockEntity owner, BlockPos partnerPos);

        boolean writeLinkedBlock(CompoundTag tag, String posTag, String subLevelTag,
                                 BlockPos pos, @Nullable UUID subLevelId);

        @Nullable
        LinkedBlockRef readLinkedBlock(CompoundTag tag, String posTag, String subLevelTag);
    }

    public record LinkedBlockRef(@Nullable BlockPos pos, @Nullable UUID subLevelId) {
        public static LinkedBlockRef empty() {
            return new LinkedBlockRef(null, null);
        }
    }

    public record SubLevelProjection(UUID id, Level level, List<Vec3> positions) {
    }
}
