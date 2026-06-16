package com.createfireworkadd.createfirefightingadd.content.fluids.nozzle;

import java.util.function.BiConsumer;

import com.createfireworkadd.createfirefightingadd.Config;
import com.createfireworkadd.createfirefightingadd.ClientConfig;
import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

public class ConeNozzleBlockEntity extends AbstractSprayDeviceBlockEntity {

	private static final int FLUID_CONSUMPTION_PER_TICK = 10;
	private static final double EXTINGUISH_HALF_ANGLE_DEG = 20.0;

	private static final float PARTICLE_HALF_ANGLE_DEG = 15f;

	private static final double ENTITY_HALF_ANGLE_DEG = 8.0;

	private static final double PROJECTILE_HALF_ANGLE_DEG = 10.0;

	public ConeNozzleBlockEntity(BlockPos pos, BlockState state) {
		this(Createfirefightingadd.CONE_NOZZLE_BE.get(), pos, state);
	}

	public ConeNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected SprayShape getSprayShape() {
		return new ConeSprayShape(getEffectiveRange(), EXTINGUISH_HALF_ANGLE_DEG);
	}

	@Override
	protected SprayShape getProjectileSprayShape() {
		return new ConeSprayShape(getEffectiveRange(), PROJECTILE_HALF_ANGLE_DEG);
	}

	@Override
	protected int getMaxRange() {
		return Config.coneNozzleMaxDistance;
	}

	@Override
	protected double getProjectileSpeed() {
		return Config.coneNozzleSpeed;
	}

	@Override
	protected double getProjectileGravity() {
		return Config.coneNozzleGravity;
	}

	@Override
	protected double getProjectileFriction() {
		return Config.coneNozzleFriction;
	}

	@Override
	protected int getTrailParticlesPerTick() {
		return 3;
	}

	@Override
	protected int getTankCapacity() {
		return getFluidConsumptionPerTick();
	}

	@Override
	protected int getFluidConsumptionPerTick() {
		return FLUID_CONSUMPTION_PER_TICK;
	}

	@Override
	protected Direction getFacing() {
		return getBlockState().getValue(ConeNozzleBlock.FACING);
	}

	@Override
	protected boolean useProjectileSpray() {
		return true;
	}

	@Override
	protected boolean isPointInStream(Vec3 point, Vec3 streamPos, Vec3 streamDir, double axialDist) {
		double dx = point.x - streamPos.x;
		double dy = point.y - streamPos.y;
		double dz = point.z - streamPos.z;
		// Project onto stream direction to get axial component
		double along = dx * streamDir.x + dy * streamDir.y + dz * streamDir.z;
		// Perpendicular distance squared (total^2 - axial^2)
		double perpSq = (dx * dx + dy * dy + dz * dz) - along * along;
		if (perpSq < 0) perpSq = 0;
		double maxRadius = axialDist * Math.tan(Math.toRadians(ENTITY_HALF_ANGLE_DEG));
		if (along < -1.0) return false;
		return perpSq <= maxRadius * maxRadius;
	}

	@Override
	protected void spawnClientParticles() {
		Vec3 origin = getWorldSprayOrigin();
		Vec3 direction = getWorldSprayDirection();
		RandomSource random = RandomSource.create();
		double halfAngleRad = Math.toRadians(PARTICLE_HALF_ANGLE_DEG);

		for (int i = 0; i < 1; i++) {
			if (random.nextDouble() >= ClientConfig.particleDensity) continue;
			double angle = random.nextDouble() * 2.0 * Math.PI;
			double r = random.nextDouble() * Math.tan(halfAngleRad) * 0.15;
			Vec3[] perps = ConeSprayShape.perpendiculars(direction);
			Vec3 offset = perps[0].scale(Math.cos(angle) * r).add(perps[1].scale(Math.sin(angle) * r));
			Vec3 pos = origin.add(direction.scale(0.3 + random.nextDouble() * 0.4)).add(offset);
			Vec3 vel = direction.scale(getProjectileSpeed());
			Vector3f color = pickStreamColor(random);
			level.addParticle(new DustParticleOptions(color, 2.5f + random.nextFloat()),
				pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
		}
	}

}
