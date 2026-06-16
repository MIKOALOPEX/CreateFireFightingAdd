package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.function.BiConsumer;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.ClientConfig;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

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

public class FlatNozzleBlockEntity extends AbstractSprayDeviceBlockEntity {

	private static final int FLUID_CONSUMPTION_PER_TICK = 10;
	private static final double EXTINGUISH_HALF_ANGLE_H = 60.0;
	private static final double EXTINGUISH_HALF_ANGLE_V = 4.0;

	private static final double PROJECTILE_HALF_ANGLE_H = 60.0;
	private static final double PROJECTILE_HALF_ANGLE_V = 4.0;

	private static final float PARTICLE_HALF_ANGLE_H = 60f;
	private static final float PARTICLE_HALF_ANGLE_V = 12f;

	private static final double ENTITY_HALF_ANGLE_H = 60.0;
	private static final double ENTITY_HALF_ANGLE_V = 4.0;



	public FlatNozzleBlockEntity(BlockPos pos, BlockState state) {
		this(CreateFireFightingAdd.FLAT_NOZZLE_BE.get(), pos, state);
	}

	public FlatNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected SprayShape getSprayShape() {
		return new FanSprayShape(getEffectiveRange(), EXTINGUISH_HALF_ANGLE_H, EXTINGUISH_HALF_ANGLE_V);
	}

	@Override
	protected SprayShape getProjectileSprayShape() {
		return new FanSprayShape(getEffectiveRange(), PROJECTILE_HALF_ANGLE_H, PROJECTILE_HALF_ANGLE_V);
	}

	@Override
	protected int getMaxRange() {
		return Config.flatNozzleMaxDistance;
	}

	@Override
	protected double getProjectileSpeed() {
		return Config.flatNozzleSpeed;
	}

	@Override
	protected double getProjectileGravity() {
		return Config.flatNozzleGravity;
	}
	@Override
	protected double getProjectileFriction() {
		return Config.flatNozzleFriction;
	}
	@Override
	protected int getTrailParticlesPerTick() {
		return 4;
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
		return getBlockState().getValue(FlatNozzleBlock.FACING);
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
		Vec3[] axes = FanSprayShape.fanAxes(streamDir);
		double hDist = dx * axes[0].x + dy * axes[0].y + dz * axes[0].z;
		double vDist = dx * axes[1].x + dy * axes[1].y + dz * axes[1].z;
		double maxH = axialDist * Math.tan(Math.toRadians(ENTITY_HALF_ANGLE_H));
		double maxV = axialDist * Math.tan(Math.toRadians(ENTITY_HALF_ANGLE_V));
		double along = dx * streamDir.x + dy * streamDir.y + dz * streamDir.z;
		if (along < -1.0) return false;
		double hNorm = hDist / maxH;
		double vNorm = vDist / maxV;
		return hNorm * hNorm + vNorm * vNorm <= 1.0;
	}

	@Override
	protected void spawnClientParticles() {
		Vec3 origin = getWorldSprayOrigin();
		Vec3 direction = getWorldSprayDirection();
		Vec3[] axes = FanSprayShape.fanAxes(direction);
		RandomSource random = RandomSource.create();
		double tanH = Math.tan(Math.toRadians(PARTICLE_HALF_ANGLE_H));
		double tanV = Math.tan(Math.toRadians(PARTICLE_HALF_ANGLE_V));
		Vec3 axisH = axes[0];
		Vec3 axisV = axes[1];

		for (int i = 0; i < 1; i++) {
			if (random.nextDouble() >= ClientConfig.particleDensity) continue;
			double r = Math.sqrt(random.nextDouble()) * 0.15;
			double theta = random.nextDouble() * 2.0 * Math.PI;
			double h = Math.cos(theta) * r * Math.tan(tanH);
			double v = Math.sin(theta) * r * Math.tan(tanV);
			Vec3 pos = origin.add(direction.scale(0.3 + random.nextDouble() * 0.4))
				.add(axisH.scale(h)).add(axisV.scale(v));
			Vec3 vel = direction.scale(getProjectileSpeed());
			Vector3f color = pickStreamColor(random);
			level.addParticle(new DustParticleOptions(color, 2.5f + random.nextFloat()),
				pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
		}
	}

}
