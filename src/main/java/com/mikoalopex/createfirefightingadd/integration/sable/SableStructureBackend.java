package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

final class SableStructureBackend implements SableStructureCompat.StructureBackend {

    @Override
    public double distanceSquared(Level level, Vec3 first, Vec3 second) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, first, second);
    }

    @Override
    public Vec3 projectToWorld(Level level, Vec3 localPos) {
        return Sable.HELPER.projectOutOfSubLevel(level, localPos);
    }

    @Override
    public Level worldLevel(BlockEntity owner) {
        SubLevel subLevel = Sable.HELPER.getContaining(owner);
        return subLevel != null ? subLevel.getLevel() : owner.getLevel();
    }

    @Override
    public BlockPos worldBlockPos(BlockEntity owner) {
        SubLevel subLevel = Sable.HELPER.getContaining(owner);
        if (subLevel == null)
            return owner.getBlockPos();
        return BlockPos.containing(subLevel.logicalPose().transformPosition(Vec3.atCenterOf(owner.getBlockPos())));
    }

    @Override
    public Vec3 transformPositionToWorld(BlockEntity owner, Vec3 localPos) {
        SubLevel subLevel = Sable.HELPER.getContaining(owner);
        return subLevel != null ? subLevel.logicalPose().transformPosition(localPos) : localPos;
    }

    @Override
    public Vec3 transformNormalToWorld(BlockEntity owner, Vec3 localNormal) {
        SubLevel subLevel = Sable.HELPER.getContaining(owner);
        return subLevel != null ? subLevel.logicalPose().transformNormal(localNormal).normalize() : localNormal;
    }

    @Override
    public Vec3 transformNormalToLocal(BlockEntity owner, Vec3 worldNormal) {
        SubLevel subLevel = Sable.HELPER.getContaining(owner);
        return subLevel != null ? subLevel.logicalPose().transformNormalInverse(worldNormal) : worldNormal;
    }

    @Override
    public boolean isInSubLevel(BlockEntity owner) {
        return Sable.HELPER.getContaining(owner) != null;
    }

    @Override
    public List<SableStructureCompat.SubLevelProjection> projectWorldPositionsToSubLevels(Level level,
                                                                                          List<Vec3> worldPositions) {
        if (!(level instanceof ServerLevel serverLevel) || worldPositions.isEmpty())
            return List.of();
        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (!(container instanceof ServerSubLevelContainer serverContainer))
            return List.of();

        List<SableStructureCompat.SubLevelProjection> projections = new ArrayList<>();
        for (ServerSubLevel subLevel : serverContainer.getAllSubLevels()) {
            if (subLevel.isRemoved())
                continue;
            List<Vec3> localPositions = new ArrayList<>(worldPositions.size());
            for (Vec3 worldPosition : worldPositions)
                localPositions.add(subLevel.logicalPose().transformPositionInverse(worldPosition));
            projections.add(new SableStructureCompat.SubLevelProjection(
                subLevel.getUniqueId(), subLevel.getLevel(), localPositions));
        }
        return projections;
    }

    @Override
    public boolean hasFirePoleNearEntity(Level level, AABB entityBox, List<Vec3> worldSamples, double radiusSqr) {
        if (worldSamples.isEmpty())
            return false;
        Iterable<SubLevel> subLevels = Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(entityBox.inflate(Math.sqrt(radiusSqr))));
        for (SubLevel subLevel : subLevels) {
            for (Vec3 worldSample : worldSamples) {
                Vec3 localSample = subLevel.logicalPose().transformPositionInverse(worldSample);
                if (isNearFirePoleInLocalSpace(level, localSample, radiusSqr))
                    return true;
            }
        }
        return false;
    }

    private static boolean isNearFirePoleInLocalSpace(Level level, Vec3 localPosition, double radiusSqr) {
        BlockPos center = BlockPos.containing(localPosition);
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!level.getBlockState(pos).is(CreateFireFightingAdd.FIRE_POLE.get()))
                        continue;
                    if (isInsidePoleCylinder(localPosition, pos, radiusSqr))
                        return true;
                }
            }
        }
        return false;
    }

    private static boolean isInsidePoleCylinder(Vec3 position, BlockPos polePos, double radiusSqr) {
        Vec3 start = new Vec3(polePos.getX() + 0.5, polePos.getY(), polePos.getZ() + 0.5);
        Vec3 end = new Vec3(polePos.getX() + 0.5, polePos.getY() + 1.0, polePos.getZ() + 0.5);
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6)
            return false;

        double t = position.subtract(start).dot(segment) / lengthSqr;
        if (t < 0.0 || t > 1.0)
            return false;
        Vec3 closest = start.add(segment.scale(t));
        return position.distanceToSqr(closest) <= radiusSqr;
    }

    @Override
    public void notifyBlockChanged(BlockEntity owner) {
        if (owner.getLevel() == null)
            return;
        notifyBlockChanged(owner.getLevel(), owner.getBlockPos(), owner.getBlockState());
    }

    @Override
    public void notifyBlockChanged(Level level, BlockPos pos, BlockState state) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel instanceof ServerSubLevel serverSubLevel && serverSubLevel.getPlot() != null)
            serverSubLevel.getPlot().onBlockChange(pos, state);
    }

    @Override
    @Nullable
    public UUID containingSubLevelId(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel != null ? subLevel.getUniqueId() : null;
    }

    @Override
    public Vec3 partnerCenterInOwnerSpace(BlockEntity owner, BlockPos partnerPos) {
        Level level = owner.getLevel();
        Vec3 partnerCenter = Vec3.atCenterOf(partnerPos);
        if (level == null)
            return partnerCenter;

        SubLevel ownerSubLevel = Sable.HELPER.getContaining(owner);
        SubLevel partnerSubLevel = Sable.HELPER.getContaining(level, partnerPos);
        if (partnerSubLevel != null)
            partnerCenter = partnerSubLevel.logicalPose().transformPosition(partnerCenter);
        if (ownerSubLevel != null)
            partnerCenter = ownerSubLevel.logicalPose().transformPositionInverse(partnerCenter);
        return partnerCenter;
    }

    @Override
    public boolean writeLinkedBlock(CompoundTag tag, String posTag, String subLevelTag,
                                    BlockPos pos, @Nullable UUID subLevelId) {
        SubLevelSchematicSerializationContext ctx = SubLevelSchematicSerializationContext.getCurrentContext();

        if (ctx == null || ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
            if (subLevelId != null)
                tag.putUUID(subLevelTag, subLevelId);

            BlockPos mappedPos = pos;
            if (ctx != null && subLevelId == null)
                mappedPos = ctx.getSetupTransform().apply(mappedPos);
            tag.putLong(posTag, mappedPos.asLong());
            return true;
        }

        BlockPos mappedPos = pos;
        UUID mappedSubLevelId = subLevelId;

        if (mappedSubLevelId != null) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(mappedSubLevelId);
            if (mapping != null) {
                mappedSubLevelId = mapping.newUUID();
                mappedPos = mapping.transform().apply(mappedPos);
            } else {
                mappedSubLevelId = null;
                mappedPos = null;
            }
        } else if (ctx.getBoundingBox().contains(mappedPos.getX(), mappedPos.getY(), mappedPos.getZ())) {
            mappedPos = ctx.getPlaceTransform().apply(mappedPos);
        } else {
            mappedPos = null;
        }

        if (mappedPos != null)
            tag.putLong(posTag, mappedPos.asLong());
        if (mappedSubLevelId != null)
            tag.putUUID(subLevelTag, mappedSubLevelId);
        return true;
    }

    @Override
    public SableStructureCompat.LinkedBlockRef readLinkedBlock(CompoundTag tag, String posTag, String subLevelTag) {
        SubLevelSchematicSerializationContext ctx = SubLevelSchematicSerializationContext.getCurrentContext();
        boolean placing = ctx != null && ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE;
        SubLevelSchematicSerializationContext.SchematicMapping mapping = null;

        UUID subLevelId = null;
        if (tag.hasUUID(subLevelTag)) {
            subLevelId = tag.getUUID(subLevelTag);
            if (placing) {
                mapping = ctx.getMapping(subLevelId);
                if (mapping == null)
                    return SableStructureCompat.LinkedBlockRef.empty();
                subLevelId = mapping.newUUID();
            }
        }

        BlockPos pos = null;
        if (tag.contains(posTag)) {
            pos = BlockPos.of(tag.getLong(posTag));
            if (placing) {
                if (mapping != null)
                    pos = mapping.transform().apply(pos);
                else
                    pos = ctx.getPlaceTransform().apply(pos);
            }
        }

        return new SableStructureCompat.LinkedBlockRef(pos, subLevelId);
    }
}
