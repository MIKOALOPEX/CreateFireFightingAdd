package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.world.phys.Vec3;

/**
 * Data-only spray projectile used for collision and effects without Entity overhead.
 */
public class LightweightProjectile {

	public Vec3 position;
	public Vec3 prevPosition;
	public Vec3 velocity;
	public int age;
	public final int maxLifetime;
	public final AbstractSprayDeviceBlockEntity.FluidBehavior fluidBehavior;
	public final float pushSpeed;
	public Vec3 gravity;
	public final double friction;
	/** Tick when forward momentum was first lost (-1 = still has momentum). */
	public int momentumLostTick = -1;
	/** Whether this projectile has been ignited by a fire source. */
	public boolean ignited = false;
	/** Age (in ticks) when this projectile was first ignited (-1 = never ignited). */
	public int ignitedAtAge = -1;

	public LightweightProjectile(Vec3 position, Vec3 velocity, int maxLifetime,
			AbstractSprayDeviceBlockEntity.FluidBehavior fluidBehavior,
			float pushSpeed, Vec3 gravity, double friction) {
		this.position = position;
		this.prevPosition = position;
		this.velocity = velocity;
		this.maxLifetime = maxLifetime;
		this.fluidBehavior = fluidBehavior;
		this.pushSpeed = pushSpeed;
		this.gravity = gravity;
		this.friction = friction;
		this.age = 0;
	}

	public void tick() {
		prevPosition = position;
		velocity = velocity.add(gravity);
		velocity = velocity.scale(friction);
		position = position.add(velocity);
		age++;
	}

	/** True once past peak and horizontal speed is negligible; the stream has broken into mist. */
	public boolean hasLostForwardMomentum() {
		if (age <= 30 || velocity.y >= 0)
			return false;
		double hSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
		return hSpeedSq < 0.09;
	}

	public boolean isExpired() {
		if (age >= maxLifetime)
			return true;
		return velocity.lengthSqr() < 0.0001;
	}
}
