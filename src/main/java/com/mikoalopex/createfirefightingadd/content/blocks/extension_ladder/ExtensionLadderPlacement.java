package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ExtensionLadderPlacement {
	private static final double HALF_CLEARANCE_WIDTH = 0.25;
	private static final double HALF_PREVIEW_WIDTH = 0.5;
	private static final double HALF_PREVIEW_DEPTH = 0.2;
	private static final double EPSILON = 1.0E-5;
	// Structure transforms can create tiny contact overlaps; only meaningful volume overlap should block placement.
	private static final double SHAPE_INTERSECTION_TOLERANCE = 1.0E-4;

	private ExtensionLadderPlacement() {
	}

	public static boolean canPlaceAt(LevelReader level, BlockPos support) {
		AABB clearance = anchorClearanceColumn(support);
		return isShapeClear(level, clearance) && isClearInPhysicalSpace(level, clearance);
	}

	public static AABB anchorClearanceColumn(BlockPos support) {
		double x = support.getX() + 0.5;
		double y = support.getY() + 1;
		double z = support.getZ() + 0.5;
		return new AABB(x - HALF_CLEARANCE_WIDTH, y, z - HALF_CLEARANCE_WIDTH,
			x + HALF_CLEARANCE_WIDTH, y + ExtensionLadderGeometry.BASE_LENGTH, z + HALF_CLEARANCE_WIDTH);
	}

	public static float initialMoveOffsetPixels(LevelReader level, BlockPos support) {
		// Only the fixed section is required for placement; the moving section extends as far as the same column stays clear.
		int clear = 0;
		int blockedOrMax = ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS;
		while (clear < blockedOrMax) {
			int test = (clear + blockedOrMax + 1) / 2;
			if (isExtensionClear(level, support, test))
				clear = test;
			else
				blockedOrMax = test - 1;
		}
		return clear;
	}

	public static AABB anchorPreviewColumn(BlockPos support, Vec3 fallDirection) {
		Vec3 horizontal = ExtensionLadderGeometry.normalizeHorizontal(fallDirection);
		Direction facing = horizontal.lengthSqr() < 1.0E-6 ? Direction.SOUTH
			: Direction.getNearest(horizontal.x, 0, horizontal.z);
		double x = support.getX() + 0.5;
		double y = support.getY() + 1;
		double z = support.getZ() + 0.5;
		double halfX = facing.getAxis() == Direction.Axis.X ? HALF_PREVIEW_DEPTH : HALF_PREVIEW_WIDTH;
		double halfZ = facing.getAxis() == Direction.Axis.Z ? HALF_PREVIEW_DEPTH : HALF_PREVIEW_WIDTH;
		return new AABB(x - halfX, y, z - halfZ,
			x + halfX, y + ExtensionLadderGeometry.BASE_LENGTH, z + halfZ);
	}

	private static boolean isExtensionClear(LevelReader level, BlockPos support, int moveOffsetPixels) {
		AABB clearance = extensionClearanceColumn(support, moveOffsetPixels);
		return isShapeClear(level, clearance)
			&& isClearInPhysicalSpace(level, clearance);
	}

	private static AABB extensionClearanceColumn(BlockPos support, int moveOffsetPixels) {
		double x = support.getX() + 0.5;
		double y = support.getY() + 1 + ExtensionLadderGeometry.BASE_LENGTH;
		double z = support.getZ() + 0.5;
		double maxY = y + moveOffsetPixels * ExtensionLadderGeometry.PIXEL;
		return new AABB(x - HALF_CLEARANCE_WIDTH, y + EPSILON, z - HALF_CLEARANCE_WIDTH,
			x + HALF_CLEARANCE_WIDTH, maxY, z + HALF_CLEARANCE_WIDTH);
	}

	private static boolean isShapeClear(LevelReader level, AABB column) {
		BlockPos min = BlockPos.containing(column.minX, column.minY, column.minZ);
		BlockPos max = BlockPos.containing(column.maxX - EPSILON, column.maxY - EPSILON, column.maxZ - EPSILON);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty())
				continue;
			for (AABB box : shape.toAabbs()) {
				AABB movedBox = box.move(pos);
				if (intersectsBeyondTolerance(movedBox, column))
					return false;
			}
		}
		return true;
	}

	private static boolean intersectsBeyondTolerance(AABB first, AABB second) {
		return first.maxX > second.minX + SHAPE_INTERSECTION_TOLERANCE
			&& first.minX < second.maxX - SHAPE_INTERSECTION_TOLERANCE
			&& first.maxY > second.minY + SHAPE_INTERSECTION_TOLERANCE
			&& first.minY < second.maxY - SHAPE_INTERSECTION_TOLERANCE
			&& first.maxZ > second.minZ + SHAPE_INTERSECTION_TOLERANCE
			&& first.minZ < second.maxZ - SHAPE_INTERSECTION_TOLERANCE;
	}

	private static boolean isClearInPhysicalSpace(LevelReader level, AABB localColumn) {
		if (!(level instanceof Level concreteLevel))
			return true;
		// A Sable sublevel can be tilted or offset; validate the same physical column in world space and nearby sublevels.
		AABB worldColumn = around(projectToWorld(concreteLevel, corners(localColumn)));
		BlockPos localCenter = BlockPos.containing(localColumn.getCenter());
		UUID sourceSubLevelId = SableStructureCompat.containingSubLevelId(concreteLevel, localCenter);
		Level worldLevel = SableStructureCompat.worldLevel(concreteLevel, localCenter);
		if (worldLevel != null && !isShapeClear(worldLevel, worldColumn))
			return false;
		if (worldLevel == null)
			return true;
		return isClearInProjectedSubLevels(worldLevel, worldColumn, sourceSubLevelId);
	}

	private static boolean isClearInProjectedSubLevels(Level level, AABB worldColumn,
		@Nullable UUID ignoredSubLevelId) {
		List<Vec3> worldCorners = corners(worldColumn);
		List<SableStructureCompat.SubLevelProjection> projections = level.isClientSide
			? SableStructureCompat.projectWorldPositionsToSubLevels(level, worldCorners)
			: SableStructureCompat.projectWorldPositionsToIntersectingSubLevels(level, worldCorners);
		for (SableStructureCompat.SubLevelProjection projection : projections) {
			// The source sublevel was already checked locally; its tilted world AABB can be wider when projected back.
			if (projection.id().equals(ignoredSubLevelId))
				continue;
			AABB localColumn = around(projection.positions());
			if (!isShapeClear(projection.level(), localColumn))
				return false;
		}
		return true;
	}

	private static List<Vec3> projectToWorld(Level level, List<Vec3> localPositions) {
		List<Vec3> worldPositions = new ArrayList<>(localPositions.size());
		for (Vec3 localPosition : localPositions)
			worldPositions.add(SableStructureCompat.projectToWorld(level, localPosition));
		return worldPositions;
	}

	private static List<Vec3> corners(AABB box) {
		List<Vec3> corners = new ArrayList<>(8);
		double[] xs = { box.minX, box.maxX };
		double[] ys = { box.minY, box.maxY };
		double[] zs = { box.minZ, box.maxZ };
		for (double x : xs)
			for (double y : ys)
				for (double z : zs)
					corners.add(new Vec3(x, y, z));
		return corners;
	}

	private static AABB around(List<Vec3> positions) {
		if (positions.isEmpty())
			return new AABB(0, 0, 0, 0, 0, 0);
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
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

}
