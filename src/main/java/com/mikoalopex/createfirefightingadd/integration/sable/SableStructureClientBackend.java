package com.mikoalopex.createfirefightingadd.integration.sable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class SableStructureClientBackend implements SableStructureClientCompat.ClientStructureBackend {

	@Override
	public boolean renderTargetAvailable(UUID subLevel) {
		if (Minecraft.getInstance().level == null) return false;
		ClientSubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
		ClientSubLevel target = container == null ? null : (ClientSubLevel) container.getSubLevel(subLevel);
		return target != null && !target.isRemoved();
	}

	@Override
	public SableStructureClientCompat.FireHoseRenderTransform transformFireHoseTarget(BlockEntity owner,
			@Nullable UUID partnerSubLevel, Vector3d partnerCenter, Vector3d partnerNormal) {
		Vector3d transformedCenter = new Vector3d(partnerCenter);
		Vector3d transformedNormal = new Vector3d(partnerNormal);
		Pose3dc ownerPose = null;
		Pose3dc partnerPose = null;

		if (Minecraft.getInstance().level != null) {
			ClientSubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
			ClientSubLevel otherSubLevel = partnerSubLevel != null && container != null
				? (ClientSubLevel) container.getSubLevel(partnerSubLevel)
				: null;
			ClientSubLevel ownerSubLevel = Sable.HELPER.getContainingClient(owner);

			ownerPose = ownerSubLevel != null ? ownerSubLevel.renderPose() : null;
			partnerPose = otherSubLevel != null ? otherSubLevel.renderPose() : null;
		}

		if (partnerPose != null) {
			partnerPose.transformNormal(transformedNormal);
			partnerPose.transformPosition(transformedCenter);
		}
		if (ownerPose != null) {
			ownerPose.transformNormalInverse(transformedNormal);
			ownerPose.transformPositionInverse(transformedCenter);
		}

		return new SableStructureClientCompat.FireHoseRenderTransform(
			transformedCenter,
			transformedNormal,
			ownerPose != null ? new Quaterniond(ownerPose.orientation()) : new Quaterniond(),
			partnerPose != null ? new Quaterniond(partnerPose.orientation()) : new Quaterniond());
	}

	@Override
	public Vec3 renderPositionToWorld(BlockEntity owner, Vec3 localPos) {
		dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(owner);
		return clientAccess != null ? clientAccess.renderPose().transformPosition(localPos) : localPos;
	}

	@Override
	public Vec3 renderNormalToWorld(BlockEntity owner, Vec3 localNormal) {
		dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(owner);
		return clientAccess != null ? clientAccess.renderPose().transformNormal(localNormal) : localNormal;
	}

	@Override
	public Vec3 projectToWorld(Level level, Vec3 localPos) {
		ClientSubLevelContainer container = Minecraft.getInstance().level == null ? null
			: SubLevelContainer.getContainer(Minecraft.getInstance().level);
		if (container == null)
			return localPos;
		for (ClientSubLevel subLevel : container.getAllSubLevels()) {
			if (subLevel.isRemoved())
				continue;
			if (subLevel.getLevel() == level)
				return subLevel.logicalPose().transformPosition(localPos);
		}
		return localPos;
	}

	@Override
	public Vec3 logicalPositionToWorld(BlockEntity owner, Vec3 localPos) {
		dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(owner);
		return clientAccess != null ? clientAccess.logicalPose().transformPosition(localPos) : localPos;
	}

	@Override
	public Vec3 logicalNormalToWorld(BlockEntity owner, Vec3 localNormal) {
		dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(owner);
		return clientAccess != null ? clientAccess.logicalPose().transformNormal(localNormal) : localNormal;
	}

	@Override
	public Vec3 logicalNormalToLocal(BlockEntity owner, Vec3 worldNormal) {
		dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(owner);
		return clientAccess != null ? clientAccess.logicalPose().transformNormalInverse(worldNormal) : worldNormal;
	}

	@Override
	public boolean isInSubLevel(BlockEntity owner) {
		return Sable.HELPER.getContainingClient(owner) != null;
	}

	@Override
	public Level worldLevel(Level level, BlockPos pos) {
		ClientSubLevelContainer container = Minecraft.getInstance().level == null ? null
			: SubLevelContainer.getContainer(Minecraft.getInstance().level);
		if (container == null)
			return level;
		for (ClientSubLevel subLevel : container.getAllSubLevels()) {
			if (subLevel.isRemoved())
				continue;
			if (subLevel.getLevel() == level)
				return Minecraft.getInstance().level;
		}
		return level;
	}

	@Override
	public List<SableStructureCompat.SubLevelProjection> projectWorldPositionsToSubLevels(Level level,
			List<Vec3> worldPositions) {
		if (worldPositions.isEmpty() || Minecraft.getInstance().level == null)
			return List.of();

		ClientSubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
		if (container == null)
			return List.of();

		List<SableStructureCompat.SubLevelProjection> projections = new ArrayList<>();
		for (ClientSubLevel subLevel : container.getAllSubLevels()) {
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
	public List<SableStructureCompat.SubLevelProjection> projectWorldPositionsToIntersectingSubLevels(Level level,
			List<Vec3> worldPositions) {
		if (worldPositions.isEmpty() || Minecraft.getInstance().level == null)
			return List.of();

		List<SableStructureCompat.SubLevelProjection> projections = new ArrayList<>();
		for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(Minecraft.getInstance().level,
			new BoundingBox3d(around(worldPositions)))) {
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

	private static AABB around(List<Vec3> positions) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Vec3 position : positions) {
			minX = Math.min(minX, position.x);
			minY = Math.min(minY, position.y);
			minZ = Math.min(minZ, position.z);
			maxX = Math.max(maxX, position.x);
			maxY = Math.max(maxY, position.y);
			maxZ = Math.max(maxZ, position.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.01);
	}
}
