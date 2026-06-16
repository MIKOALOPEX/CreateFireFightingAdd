package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import com.mikoalopex.createfirefightingadd.Config;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import dev.ryanhcode.sable.sublevel.SubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import org.joml.Vector3f;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

// Projectile spray handling is inspired by CDG's ChemicalTurretBlockEntity (MIT License).
public abstract class AbstractSprayDeviceBlockEntity extends SmartBlockEntity
		implements BlockEntitySubLevelActor, BlockSubLevelAssemblyListener {

	protected static final Vector3f BLUE = new Vector3f(0.3f, 0.55f, 1.0f);
	protected static final Vector3f LIGHT_BLUE = new Vector3f(0.6f, 0.8f, 1.0f);
	protected static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
	protected static final double RAY_STEP = 0.5;
	private static final int MAX_PATH_SWEEP_STEPS_PER_PROJECTILE = 6;
	private static final int MAX_PATH_SWEEP_CENTERS_PER_TICK = 14;
	private static final int MAX_PATH_BLOCK_CHECKS_PER_TICK = 4096;
	private static final int MAX_PATH_EFFECTS_PER_TICK = 96;
	private static final double MAX_PATH_SWEEP_DISTANCE_PER_TICK = 4.0;
	private static final int PATH_SWEEP_RADIUS = 4;

	private static final Vector3f LAVA_RED = new Vector3f(1.0f, 0.2f, 0.05f);
	private static final Vector3f LAVA_ORANGE = new Vector3f(1.0f, 0.5f, 0.1f);
	private static final Vector3f LAVA_YELLOW = new Vector3f(0.95f, 0.7f, 0.15f);
	private static final Vector3f DRAGON_PURPLE = new Vector3f(0.62f, 0.25f, 1.0f);
	private static final Vector3f DRAGON_PINK = new Vector3f(1.0f, 0.42f, 0.88f);
	private static final Vector3f DRAGON_PALE = new Vector3f(0.86f, 0.72f, 1.0f);
	private static final Vector3f DRAGON_WHITE = new Vector3f(1.0f, 0.92f, 1.0f);

	protected static final double PUSH_STREAM_SPEED = 0.20;

	private static final TagKey<Fluid> MILK_TAG =
		TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "milk"));
	private static final TagKey<Fluid> DRAGON_BREATH_TAG =
		TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "dragon_breath"));

	enum FluidBehavior {
		WATER, LAVA, MILK, POTION, DRAGON_BREATH, FLAMMABLE, UNSUPPORTED
	}

	private static boolean tfcAvailable;
	private static boolean tfcChecked;
	private static Method tfcDouseMethod;

	private static boolean wildfireAvailable;
	private static boolean wildfireChecked;
	private static Class<?> smolderTrackerClass;
	private static Method smolderGetMethod;
	private static Field smolderStrengthField;
	private static Field smolderExpireField;

	private static Method fireGetStateMethod;
	private static boolean fireGetStateReady;

	// CDG integration
	private static boolean cdgAvailable;
	private static boolean cdgChecked;
	private static java.lang.reflect.Field cdgFuelTypeKeyField;
	private static Method cdgGetTypeForMethod;
	private static Method cdgNormalMethod;
	private static Method cdgSpeedMethod;

	private static final Vector3f FUEL_AMBER = new Vector3f(0.95f, 0.65f, 0.15f);
	private static final Vector3f FUEL_DARK = new Vector3f(0.4f, 0.25f, 0.1f);
	private static final Vector3f FUEL_LIGHT = new Vector3f(0.9f, 0.75f, 0.3f);

	private static final Vector3f[] BIODIESEL_COLORS = {
		new Vector3f(161f/255f, 101f/255f, 87f/255f),
		new Vector3f(193f/255f, 132f/255f, 86f/255f),
		new Vector3f(214f/255f, 171f/255f, 93f/255f)
	};
	private static final Vector3f[] DIESEL_COLORS = {
		new Vector3f(155f/255f, 115f/255f, 84f/255f),
		new Vector3f(180f/255f, 142f/255f, 84f/255f),
		new Vector3f(214f/255f, 201f/255f, 94f/255f)
	};
	private static final Vector3f[] GASOLINE_COLORS = {
		new Vector3f(164f/255f, 136f/255f, 119f/255f),
		new Vector3f(180f/255f, 154f/255f, 124f/255f),
		new Vector3f(217f/255f, 210f/255f, 140f/255f)
	};
	private static final Vector3f[] PLANT_OIL_COLORS = {
		new Vector3f(129f/255f, 121f/255f, 81f/255f),
		new Vector3f(105f/255f, 101f/255f, 77f/255f),
		new Vector3f(173f/255f, 163f/255f, 103f/255f)
	};
	private static final Vector3f[] ETHANOL_COLORS = {
		new Vector3f(179f/255f, 183f/255f, 164f/255f),
		new Vector3f(163f/255f, 160f/255f, 145f/255f),
		new Vector3f(181f/255f, 189f/255f, 172f/255f)
	};
	private static final Vector3f[] CRUDE_OIL_COLORS = {
		new Vector3f(0.08f, 0.08f, 0.08f),
		new Vector3f(0.15f, 0.15f, 0.15f),
		new Vector3f(0.25f, 0.25f, 0.25f),
		new Vector3f(0.35f, 0.35f, 0.35f),
		new Vector3f(0.45f, 0.45f, 0.45f)
	};

	protected static boolean tryTfcDouse(Level level, BlockPos pos) {
		if (!tfcChecked) {
			tfcChecked = true;
			try {
				Class<?> clazz = Class.forName("net.dries007.tfc.util.events.DouseFireEvent");
				tfcDouseMethod = clazz.getMethod("douse", Level.class, BlockPos.class, Player.class);
				tfcAvailable = true;
			} catch (Exception e) {
				tfcAvailable = false;
			}
		}
		if (!tfcAvailable)
			return false;
		try {
			return (boolean) tfcDouseMethod.invoke(null, level, pos, null);
		} catch (Exception e) {
			return false;
		}
	}

	private static void initWildfireReflection() {
		if (wildfireChecked) return;
		wildfireChecked = true;
		try {
			smolderTrackerClass = Class.forName("com.tfcwildfire.wildfire.SmolderTracker");
			smolderGetMethod = smolderTrackerClass.getMethod("get", net.minecraft.server.level.ServerLevel.class);
			smolderStrengthField = smolderTrackerClass.getDeclaredField("strengthByPos");
			smolderStrengthField.setAccessible(true);
			smolderExpireField = smolderTrackerClass.getDeclaredField("expireByPos");
			smolderExpireField.setAccessible(true);
			wildfireAvailable = true;
		} catch (Exception e) {
			wildfireAvailable = false;
		}
	}

	protected static void clearWildfireHeat(Level level, BlockPos pos) {
		initWildfireReflection();
		if (!wildfireAvailable || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel))
			return;
		try {
			Object tracker = smolderGetMethod.invoke(null, serverLevel);
			if (tracker == null) return;
			long key = pos.asLong();
			((it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap) smolderStrengthField.get(tracker)).remove(key);
			((it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap) smolderExpireField.get(tracker)).remove(key);
		} catch (Exception ignored) {
		}
	}

	private static final FireBlock FIRE_BLOCK = (FireBlock) Blocks.FIRE;
	private static Method canBurnMethod;
	private static boolean canBurnMethodReady;

	protected static boolean canBurn(BlockState state) {
		if (!canBurnMethodReady) {
			canBurnMethodReady = true;
			Class<?> clazz = FireBlock.class;
			while (clazz != null) {
				try {
					canBurnMethod = clazz.getDeclaredMethod("canBurn", BlockState.class);
					canBurnMethod.setAccessible(true);
					break;
				} catch (NoSuchMethodException e) {
					clazz = clazz.getSuperclass();
				}
			}
		}
		if (canBurnMethod == null)
			return false;
		try {
			return (boolean) canBurnMethod.invoke(FIRE_BLOCK, state);
		} catch (Exception e) {
			return false;
		}
	}

	private static BlockState getFireState(Level level, BlockPos pos) {
		if (!fireGetStateReady) {
			fireGetStateReady = true;
			try {
				fireGetStateMethod = BaseFireBlock.class.getDeclaredMethod("getState", LevelReader.class, BlockPos.class);
				fireGetStateMethod.setAccessible(true);
			} catch (NoSuchMethodException ignored) {
			}
		}
		if (fireGetStateMethod == null)
			return Blocks.FIRE.defaultBlockState();
		try {
			return (BlockState) fireGetStateMethod.invoke(null, level, pos);
		} catch (Exception e) {
			return Blocks.FIRE.defaultBlockState();
		}
	}

	private static void initCdgReflection() {
		if (cdgChecked) return;
		cdgChecked = true;
		try {
			Class<?> regClass = Class.forName("com.jesz.createdieselgenerators.CDGRegistries");
			cdgFuelTypeKeyField = regClass.getField("FUEL_TYPE");

			Class<?> ftClass = Class.forName("com.jesz.createdieselgenerators.fuel_type.FuelType");
			for (Method m : ftClass.getDeclaredMethods()) {
				if (m.getName().equals("getTypeFor") && m.getParameterCount() == 2
					&& m.getReturnType() == ftClass) {
					cdgGetTypeForMethod = m;
					break;
				}
			}
			cdgNormalMethod = ftClass.getMethod("normal");
			Class<?> generatedClass = cdgNormalMethod.getReturnType();
			cdgSpeedMethod = generatedClass.getMethod("speed");
			cdgAvailable = true;
		} catch (Exception e) {
			cdgAvailable = false;
		}
	}

	private boolean isCdgFlammable(Fluid fluid) {
		if (!Config.cdgIgnitionEnabled) return false;
		initCdgReflection();
		if (!cdgAvailable || level == null) return false;
		try {
			Object key = cdgFuelTypeKeyField.get(null);
			Object registry = level.registryAccess().lookupOrThrow((net.minecraft.resources.ResourceKey) key);
			Object fuelType = cdgGetTypeForMethod.invoke(null, registry, fluid);
			if (fuelType == null) return false;
			Object normal = cdgNormalMethod.invoke(fuelType);
			double speed = ((Number) cdgSpeedMethod.invoke(normal)).doubleValue();
			return speed != 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Fallback check that does not require the level  - allows CDG fluids to pass the tank validator
	 *  even when the block entity has not yet been added to a level. */
	private static boolean isCdgFluidByNamespace(Fluid fluid) {
		initCdgReflection();
		if (!cdgAvailable) return false;
		net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
		return id != null && id.getNamespace().equals("createdieselgenerators");
	}

	/** Returns the per-fuel color palette from a cached fluid path, falling back to amber. */
	private static Vector3f[] getFuelColorsByPath(String path) {
		return switch (path) {
			case "biodiesel" -> BIODIESEL_COLORS;
			case "diesel" -> DIESEL_COLORS;
			case "gasoline" -> GASOLINE_COLORS;
			case "plant_oil" -> PLANT_OIL_COLORS;
			case "ethanol" -> ETHANOL_COLORS;
			case "crude_oil" -> CRUDE_OIL_COLORS;
			default -> new Vector3f[]{FUEL_AMBER, FUEL_DARK, FUEL_LIGHT};
		};
	}

	/** Returns the per-fuel color palette for a CDG fluid, falling back to amber. */
	private static Vector3f[] getCdgFuelColors(Fluid fluid) {
		net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
		if (id == null || !id.getNamespace().equals("createdieselgenerators"))
			return new Vector3f[]{FUEL_AMBER};
		return getFuelColorsByPath(id.getPath());
	}

	private static final int EXTINGUISH_SOUND_COOLDOWN = 5;

	protected SmartFluidTankBehaviour tank;
	private int tickCounter;
	private int fullSweepCounter;
	private int ticksSinceLastExtinguishSound = 100;
	protected FluidBehavior currentFluid = FluidBehavior.UNSUPPORTED;
	protected Vector3f potionColor = WHITE;
	protected PotionContents currentPotionContents = PotionContents.EMPTY;
	protected double currentSprayRange;
	private long sprayStartedAt = -1;
	private Random projectileRandom;
	private FluidBehavior lastSprayFluid = FluidBehavior.UNSUPPORTED;
	private boolean sprayIgnited;
	private String sprayedFuelPath = "";
	private FluidStack cachedFluidStack = FluidStack.EMPTY;
	private int stuckTicks;
	private Fluid stuckFluidType;
	private int stuckFluidAmount;
	private final Map<Long, ProcessingProgress> depotProcessing = new HashMap<>();

	// Cached thrust (computed once per game tick, applied every physics substep)
	private Vector3d cachedThrustDir;
	private double cachedThrustMagnitude;

	// Lightweight projectile lists (server: collision + effects, client: visual only)
	private final List<LightweightProjectile> serverProjectiles = new ArrayList<>();
	private final List<LightweightProjectile> clientProjectiles = new ArrayList<>();
	private final Map<Long, PathSweepSample> pendingPathSweepSamples = new HashMap<>();

	private record ProcessingProgress(ResourceLocation type, ItemStack stack, int time) {}
	private record PathSweepSample(Vec3 position, FluidBehavior fluidBehavior, boolean ignited) {}

	protected AbstractSprayDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// Required hooks

	protected abstract SprayShape getSprayShape();
	protected abstract int getTankCapacity();
	protected abstract int getFluidConsumptionPerTick();
	protected abstract Direction getFacing();
	protected abstract int getMaxRange();

	/** Relative thrust strength. Override per nozzle type. 1.0 = baseline. */
	protected double getThrustCoefficient() {
		return 1.0;
	}

	protected int getScanInterval() {
		return 1;
	}

	/** Override to true in nozzle subclasses to use projectile-based spray instead of sync block scanning. */
	protected boolean useProjectileSpray() {
		return false;
	}

	protected int getEffectiveRange() {
		return (int) Math.ceil(currentSprayRange);
	}

	protected double getParticleRange() {
		return Math.max(1.0, currentSprayRange);
	}

	/** Iterate entities within the spray cone/fan. Calls action with (entity, axialDistance). */
	protected void forEachEntityInSpray(BiConsumer<Entity, Double> action) {
	}

	// Optional hooks

	protected void spawnClientParticles() {}

	// Projectile spray

	protected int getProjectileLifetime() {
		double f = getProjectileFriction();
		double v = getProjectileSpeed();
		double d = Math.min(getMaxRange(), v / (1.0 - f));
		double target = 1.0 - d * (1.0 - f) / v;
		if (target <= 0) target = 0.001;
		int ticks = (int) (Math.log(target) / Math.log(f));
		return Math.max(20, ticks);
	}

	/** Muzzle velocity in blocks per tick. Lower = shorter, steeper trajectory. Override per nozzle type. */
	protected double getProjectileSpeed() {
		return 1.8;
	}

	/** Downward acceleration per tick. Lower = flatter arc. Override per nozzle type. */
	protected double getProjectileGravity() {
		return 0.015;
	}

	/** Trail particles spawned per projectile per tick. Override per nozzle type to balance visual density. */
	protected int getTrailParticlesPerTick() {
		return 2;
	}

	/** Projectile air friction per tick (0.0-1.0). Higher = less drag, longer arcs. */
	protected double getProjectileFriction() {
		return 0.97;
	}

	/** Computes gravity vector, transformed into sub-level local coordinates when on a contraption. */
	Vec3 getGravityVector() {
		double g = getProjectileGravity();
		Vec3 worldGravity = new Vec3(0, -g, 0);
		SubLevel subLevel = Sable.HELPER.getContaining(this);
		if (subLevel != null) {
			return subLevel.logicalPose().transformNormalInverse(worldGravity);
		}
		return worldGravity;
	}

	/** Spray shape used for projectile direction randomization. Override to tighten visual spread. */
	protected SprayShape getProjectileSprayShape() {
		return getSprayShape();
	}

	/** Creates projectiles using a seeded Random for server-client deterministic sync without network packets. */
	protected void spawnSprayProjectiles(FluidStack fluidForProjectile) {
		Vec3 spawnPos = Vec3.atCenterOf(worldPosition).relative(getFacing(), 0.6);
		Vec3 baseDir = Vec3.atLowerCornerOf(getFacing().getNormal());
		SprayShape shape = getProjectileSprayShape();
		int lifetime = getProjectileLifetime();
		for (int i = 0; i < Config.serverProjectilesPerTick; i++) {
			Vec3 dir = shape.randomSprayDirection(baseDir, projectileRandom);
			Vec3 velocity = dir.scale(getProjectileSpeed());
			LightweightProjectile proj = new LightweightProjectile(spawnPos, velocity, lifetime,
				currentFluid, (float) PUSH_STREAM_SPEED, getGravityVector(), getProjectileFriction());
			serverProjectiles.add(proj);
		}
	}

	// Recoil thrust

	@Override
	public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
		if (!Config.nozzleThrustEnabled || cachedThrustMagnitude < 1e-6)
			return;

		Vector3d point = new Vector3d(
			worldPosition.getX() + 0.5,
			worldPosition.getY() + 0.5,
			worldPosition.getZ() + 0.5
		);
		Vector3d impulse = new Vector3d(cachedThrustDir).mul(cachedThrustMagnitude * timeStep);

		subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get())
			.applyAndRecordPointForce(point, impulse);
	}

	@Override
	public void afterMove(ServerLevel newLevel, ServerLevel oldLevel, BlockState state,
						 BlockPos newPos, BlockPos oldPos) {
		SubLevel sl = Sable.HELPER.getContaining(newLevel, newPos);
		if (sl instanceof ServerSubLevel ssl && ssl.getPlot() != null) {
			ssl.getPlot().onBlockChange(newPos, state);
		}
	}


	// Block entity behaviour

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		tank = SmartFluidTankBehaviour.single(this, getTankCapacity());
		tank.getPrimaryHandler().setValidator(stack -> {
			Fluid fluid = stack.getFluid();
			return fluid.is(FluidTags.WATER)
				|| fluid.is(FluidTags.LAVA)
				|| fluid.is(MILK_TAG)
				|| isDragonBreath(fluid)
				|| (AllFluids.POTION != null && fluid.isSame(AllFluids.POTION.get()))
				|| isCdgFlammable(fluid)
				|| isCdgFluidByNamespace(fluid);
		});
		tank.allowInsertion();
		tank.forbidExtraction();
		tank.whenFluidUpdates(() -> {
			notifyUpdate();
		});
		behaviours.add(tank);
	}


	@Override
	public void tick() {
		super.tick();
		if (level == null)
			return;

		if (isSpraying()) {
			Direction facing = getFacing();
			cachedThrustDir = new Vector3d(
				facing.getOpposite().getNormal().getX(),
				facing.getOpposite().getNormal().getY(),
				facing.getOpposite().getNormal().getZ()
			);
			double rangeRatio = currentSprayRange / Math.max(1, getMaxRange());
			double massFactor = switch (currentFluid) {
				case LAVA -> 3.0;
				case MILK, POTION, WATER, DRAGON_BREATH, FLAMMABLE -> 1.0;
				default -> 0.0;
			};
			cachedThrustMagnitude = Config.nozzleThrustMultiplier
				* getThrustCoefficient()
				* massFactor
				* rangeRatio;

			if (tickCounter == 0 && level instanceof ServerLevel) {
				SubLevel sl = Sable.HELPER.getContaining(this);
				if (sl instanceof ServerSubLevel ssl && ssl.getPlot() != null) {
					ssl.getPlot().onBlockChange(worldPosition, getBlockState());
				}
			}
		} else {
			cachedThrustMagnitude = 0;
		}

		if (useProjectileSpray())
			nozzleSprayTick();
		else
			baseSprayTick();
	}

	private void nozzleSprayTick() {
		IFluidHandler handler = tank.getPrimaryHandler();
		FluidStack fluid = handler.getFluidInTank(0);
		currentFluid = classifyFluid(fluid);
		boolean sprayingNow = isSpraying();

		if (!level.isClientSide()) {
			boolean fluidChanged = sprayingNow && currentFluid != lastSprayFluid;
			boolean sprayStarting = sprayStartedAt < 0 && sprayingNow;
			boolean sprayStopping = sprayStartedAt >= 0 && !sprayingNow;

			if (sprayStarting || fluidChanged) {
				sprayStartedAt = level.getGameTime();
				projectileRandom = new Random(sprayStartedAt * 31);
				serverProjectiles.clear();
				pendingPathSweepSamples.clear();
				if (currentFluid == FluidBehavior.FLAMMABLE && !fluid.isEmpty())
					sprayedFuelPath = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath();
				else
					sprayedFuelPath = "";
				sendData();
			} else if (sprayStopping) {
				sprayStartedAt = -1;
				sprayIgnited = false;
				serverProjectiles.clear();
				pendingPathSweepSamples.clear();
				sendData();
			}

			if (sprayingNow && sprayStartedAt >= 0) {
				long elapsed = level.getGameTime() - sprayStartedAt;
				if (elapsed >= Config.nozzleSprayBuildupTicks)
					currentSprayRange = getMaxRange();
				else {
					double t = (double) elapsed / Config.nozzleSprayBuildupTicks;
					currentSprayRange = getMaxRange() * t * t;
				}
			} else {
				currentSprayRange = 0;
			}
			if (sprayingNow)
				lastSprayFluid = currentFluid;

			tickServerProjectiles();
		} else {
			if (sprayStartedAt >= 0) {
				long elapsed = level.getGameTime() - sprayStartedAt;
				if (elapsed >= Config.nozzleSprayBuildupTicks)
					currentSprayRange = getMaxRange();
				else {
					double t = (double) elapsed / Config.nozzleSprayBuildupTicks;
					currentSprayRange = getMaxRange() * t * t;
				}
				currentFluid = lastSprayFluid;
				cachedFluidStack = tank.getPrimaryHandler().getFluidInTank(0);
			} else {
				currentSprayRange = 0;
			}

			tickClientProjectiles();
			return;
		}

		if (fluid.isEmpty())
			return;
		if (currentFluid == FluidBehavior.UNSUPPORTED)
			return;

		if (fluid.getAmount() < getFluidConsumptionPerTick()) {
			Fluid fluidType = fluid.getFluid();
			if (stuckFluidType == fluidType && stuckFluidAmount == fluid.getAmount()) {
				stuckTicks++;
				if (stuckTicks >= 20) {
					handler.drain(fluid.getAmount(), IFluidHandler.FluidAction.EXECUTE);
					stuckTicks = 0;
					stuckFluidType = null;
					stuckFluidAmount = 0;
				}
			} else {
				stuckTicks = 0;
				stuckFluidType = fluidType;
				stuckFluidAmount = fluid.getAmount();
			}
			return;
		} else {
			stuckTicks = 0;
			stuckFluidType = null;
			stuckFluidAmount = 0;
		}

		FluidStack fluidForProjectile = fluid.copy();
		if (currentFluid == FluidBehavior.POTION) {
			currentPotionContents = fluidForProjectile.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		}
		handler.drain(getFluidConsumptionPerTick(), IFluidHandler.FluidAction.EXECUTE);

		tickCounter++;
		boolean igniteNow = currentFluid == FluidBehavior.FLAMMABLE && !sprayIgnited && scanForIgnitionSource();
		spawnSprayProjectiles(fluidForProjectile);
		if (igniteNow) {
			sprayIgnited = true;
			for (LightweightProjectile proj : serverProjectiles) {
				if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && !proj.ignited) {
					proj.ignited = true;
					proj.ignitedAtAge = proj.age;
				}
			}
		}
		if (sprayingNow)
			applyEntityScan();
		fullSweepCounter++;
		if (sprayingNow && currentFluid != FluidBehavior.LAVA && currentFluid != FluidBehavior.FLAMMABLE
			&& currentFluid != FluidBehavior.DRAGON_BREATH
			&& fullSweepCounter % Config.fullSweepInterval == 0)
			doFullSweep();
	}

	protected void baseSprayTick() {
		IFluidHandler handler = tank.getPrimaryHandler();
		FluidStack fluid = handler.getFluidInTank(0);
		currentFluid = classifyFluid(fluid);
		boolean sprayingNow = isSpraying();

		if (!level.isClientSide()) {
			boolean fluidChanged = sprayingNow && currentFluid != lastSprayFluid;
			if ((sprayStartedAt < 0 && sprayingNow) || fluidChanged) {
				sprayStartedAt = level.getGameTime();
				projectileRandom = new Random(sprayStartedAt * 31);
				if (currentFluid == FluidBehavior.FLAMMABLE && !fluid.isEmpty())
					sprayedFuelPath = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath();
				else
					sprayedFuelPath = "";
				sendData();
			} else if (sprayStartedAt >= 0 && !sprayingNow) {
				sprayStartedAt = -1;
				sprayIgnited = false;
				sendData();
			}

			if (sprayingNow && sprayStartedAt >= 0) {
				long elapsed = level.getGameTime() - sprayStartedAt;
				if (elapsed >= Config.nozzleSprayBuildupTicks)
					currentSprayRange = getMaxRange();
				else {
					double t = (double) elapsed / Config.nozzleSprayBuildupTicks;
					currentSprayRange = getMaxRange() * t * t;
				}
			} else {
				currentSprayRange = 0;
			}
			if (sprayingNow)
				lastSprayFluid = currentFluid;
		} else {
			if (sprayStartedAt >= 0) {
				long elapsed = level.getGameTime() - sprayStartedAt;
				if (elapsed >= Config.nozzleSprayBuildupTicks)
					currentSprayRange = getMaxRange();
				else {
					double t = (double) elapsed / Config.nozzleSprayBuildupTicks;
					currentSprayRange = getMaxRange() * t * t;
				}
				currentFluid = lastSprayFluid;
				cachedFluidStack = tank.getPrimaryHandler().getFluidInTank(0);
			} else {
				currentSprayRange = 0;
			}
			if (sprayStartedAt >= 0)
				spawnClientParticles();
			return;
		}

		if (!sprayingNow)
			return;

		if (fluid.isEmpty() || fluid.getAmount() < getFluidConsumptionPerTick())
			return;
		if (currentFluid == FluidBehavior.UNSUPPORTED)
			return;

		handler.drain(getFluidConsumptionPerTick(), IFluidHandler.FluidAction.EXECUTE);

		tickCounter++;
		switch (currentFluid) {
			case WATER -> {
				if (tickCounter % getScanInterval() == 0)
					waterBehavior();
				fullSweepCounter++;
				if (isSpraying() && fullSweepCounter % Config.fullSweepInterval == 0)
					doFullSweep();
			}
			case LAVA -> {
				if (tickCounter % getScanInterval() == 0)
					lavaBehavior();
			}
			case FLAMMABLE -> {
				if (sprayIgnited && tickCounter % getScanInterval() == 0)
					lavaBehavior();
			}
			case MILK -> {
				if (tickCounter % getScanInterval() == 0)
					milkBehavior();
				fullSweepCounter++;
				if (isSpraying() && fullSweepCounter % Config.fullSweepInterval == 0)
					doFullSweep();
			}
			case POTION -> {
				if (tickCounter % getScanInterval() == 0)
					potionBehavior();
				fullSweepCounter++;
				if (isSpraying() && fullSweepCounter % Config.fullSweepInterval == 0)
					doFullSweep();
			}
			case DRAGON_BREATH -> {
				if (tickCounter % getScanInterval() == 0)
					dragonBreathBehavior();
			}
		}
	}

	// Server projectile processing

	private void tickServerProjectiles() {
		int effectiveLifetime = getProjectileLifetime();
		Iterator<LightweightProjectile> it = serverProjectiles.iterator();
		while (it.hasNext()) {
			LightweightProjectile proj = it.next();
			proj.gravity = getGravityVector();
			proj.tick();

			if (proj.age >= effectiveLifetime) {
				it.remove();
				continue;
			}

			if (proj.hasLostForwardMomentum()) {
				it.remove();
				continue;
			}

			if (proj.isExpired()) {
				it.remove();
				continue;
			}

			BlockHitResult blockHit = rayTraceBlock(proj.prevPosition, proj.position);
			sampleBlocksAlongPath(proj, blockHit);
			if (blockHit.getType() != HitResult.Type.MISS) {
				applyBlockEffect(proj, blockHit);
				it.remove();
			}
		}

		processPathSweepSamples();

		if (Config.cdgIgnitionEnabled && !serverProjectiles.isEmpty()) {
			List<LightweightProjectile> ignitedList = new ArrayList<>();
			for (LightweightProjectile proj : serverProjectiles) {
				if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && proj.ignited)
					ignitedList.add(proj);
			}
			if (!ignitedList.isEmpty()) {
				double radius = Config.flamePropagationRadius;
				for (LightweightProjectile proj : serverProjectiles) {
					if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && !proj.ignited) {
						for (LightweightProjectile ignited : ignitedList) {
							if (proj.position.distanceToSqr(ignited.position) < radius * radius) {
								proj.ignited = true;
								proj.ignitedAtAge = proj.age;
								break;
							}
						}
					}
				}
			}
		}

		boolean anyIgnited = false;
		for (LightweightProjectile proj : serverProjectiles) {
			if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && proj.ignited) {
				anyIgnited = true;
				break;
			}
		}
		if (sprayIgnited != anyIgnited) {
			sprayIgnited = anyIgnited;
			sendData();
		}
	}

	/** Scans the full spray cone for any fire, lava, or lit campfire. */
	private boolean scanForIgnitionSource() {
		if (!Config.cdgIgnitionEnabled) return false;
		boolean[] found = {false};
		forEachBlockInSpray((pos, state) -> {
			if (found[0]) return;
			if (state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) found[0] = true;
			else if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) found[0] = true;
			else if (state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
				&& state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) found[0] = true;
		});
		return found[0];
	}

	private boolean detectIgnitionSource(Vec3 samplePos) {
		if (!Config.cdgIgnitionEnabled) return false;
		BlockPos pos = BlockPos.containing(samplePos);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos checkPos = pos.offset(dx, dy, dz);
					BlockState state = level.getBlockState(checkPos);
					if (state.getBlock() instanceof BaseFireBlock) return true;
					if (state.getFluidState().is(FluidTags.LAVA)) return true;
					if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return true;
				}
			}
		}
		AABB aabb = new AABB(pos).inflate(2.0);
		for (Entity entity : level.getEntities(null, aabb)) {
			if (entity instanceof ItemEntity itemEntity) {
				if (itemEntity.getItem().getItem().getClass().getName()
						.equals("com.jesz.createdieselgenerators.content.tools.lighter.LighterItem")) {
					try {
						var stack = itemEntity.getItem();
						var components = stack.getClass().getMethod("getComponents").invoke(stack);
						if (components.toString().toLowerCase().contains("ignited"))
							return true;
					} catch (Exception ignored2) {}
				}
			}
			if (entity.getClass().getName()
					.equals("com.jesz.createdieselgenerators.content.tools.ChemicalSprayerProjectileEntity")) {
				try {
					var fireField = entity.getClass().getDeclaredField("fire");
					fireField.setAccessible(true);
					if (fireField.getBoolean(entity)) return true;
				} catch (Exception ignored) {}
			}
		}
		return false;
	}

	public boolean tryIgniteWithItem(Player player, InteractionHand hand) {
		if (level == null || level.isClientSide) return false;
		if (currentFluid != FluidBehavior.FLAMMABLE) return false;
		if (!isSpraying()) return false;
		if (sprayIgnited) return false;

		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.FLINT_AND_STEEL) && !held.is(Items.FIRE_CHARGE))
			return false;

		sprayIgnited = true;
		for (LightweightProjectile proj : serverProjectiles) {
			if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && !proj.ignited) {
				proj.ignited = true;
				proj.ignitedAtAge = proj.age;
			}
		}
		sendData();

		if (held.is(Items.FLINT_AND_STEEL)) {
			held.setDamageValue(held.getDamageValue() + 1);
			if (held.getDamageValue() >= held.getMaxDamage())
				held.shrink(1);
		} else {
			held.shrink(1);
		}

		return true;
	}

	private void sampleBlocksAlongPath(LightweightProjectile proj, BlockHitResult blockHit) {
		Vec3 start = proj.prevPosition;
		Vec3 end = blockHit.getType() == HitResult.Type.MISS ? proj.position : blockHit.getLocation();
		double totalDist = start.distanceTo(end);
		if (totalDist < 0.01) return;
		if (totalDist > MAX_PATH_SWEEP_DISTANCE_PER_TICK)
			end = start.add(end.subtract(start).normalize().scale(MAX_PATH_SWEEP_DISTANCE_PER_TICK));

		Vec3 dir = end.subtract(start).normalize();
		double cappedDist = start.distanceTo(end);
		int steps = Math.max(1, Math.min(MAX_PATH_SWEEP_STEPS_PER_PROJECTILE, (int) Math.ceil(cappedDist / RAY_STEP)));

		for (int i = 0; i <= steps; i++) {
			Vec3 sample = start.add(dir.scale(cappedDist * i / steps));
			BlockPos center = BlockPos.containing(sample);
			if (!level.isLoaded(center))
				continue;
			long key = pathSweepKey(center, proj.fluidBehavior, proj.ignited);
			pendingPathSweepSamples.putIfAbsent(key, new PathSweepSample(sample, proj.fluidBehavior, proj.ignited));

			if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && !proj.ignited && detectIgnitionSource(sample)) {
				proj.ignited = true;
				proj.ignitedAtAge = proj.age;
			}
		}
	}

	private void processPathSweepSamples() {
		if (pendingPathSweepSamples.isEmpty())
			return;

		int centers = 0;
		int checked = 0;
		int effects = 0;
		Set<Long> visitedBlocks = new HashSet<>();
		Iterator<PathSweepSample> samples = pendingPathSweepSamples.values().iterator();

		while (samples.hasNext()) {
			if (centers >= MAX_PATH_SWEEP_CENTERS_PER_TICK)
				break;
			PathSweepSample sample = samples.next();
			samples.remove();
			centers++;

			BlockPos center = BlockPos.containing(sample.position());
			for (int dx = -PATH_SWEEP_RADIUS; dx <= PATH_SWEEP_RADIUS; dx++) {
				for (int dy = -PATH_SWEEP_RADIUS; dy <= PATH_SWEEP_RADIUS; dy++) {
					for (int dz = -PATH_SWEEP_RADIUS; dz <= PATH_SWEEP_RADIUS; dz++) {
						if (dx * dx + dy * dy + dz * dz > PATH_SWEEP_RADIUS * PATH_SWEEP_RADIUS)
							continue;
						BlockPos pos = center.offset(dx, dy, dz);
						if (!visitedBlocks.add(pos.asLong()))
							continue;
						if (++checked > MAX_PATH_BLOCK_CHECKS_PER_TICK)
							return;
						if (!level.isLoaded(pos))
							continue;

						BlockState state = level.getBlockState(pos);
						if (!canPathSweepAffect(sample, pos, state))
							continue;
						if (!hasBlockEffectLineOfSight(sample.position(), pos))
							continue;

						if (applyPathSweepEffect(sample, pos, state) && ++effects >= MAX_PATH_EFFECTS_PER_TICK)
							return;
					}
				}
			}
		}
	}

	private boolean canPathSweepAffect(PathSweepSample sample, BlockPos pos, BlockState state) {
		return switch (sample.fluidBehavior()) {
			case WATER, MILK, POTION -> state.getBlock() instanceof BaseFireBlock
				|| (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
				|| state.is(Blocks.SNOW);
			case LAVA -> state.isAir()
				|| state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.PACKED_ICE)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
			case FLAMMABLE -> sample.ignited() && (state.isAir()
				|| state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.PACKED_ICE)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK));
			case DRAGON_BREATH, UNSUPPORTED -> false;
		};
	}

	private boolean applyPathSweepEffect(PathSweepSample sample, BlockPos pos, BlockState state) {
		switch (sample.fluidBehavior()) {
			case WATER, MILK, POTION -> {
				if (state.getBlock() instanceof BaseFireBlock) {
					if (tryTfcDouse(level, pos))
						return false;
					level.removeBlock(pos, false);
					clearWildfireHeat(level, pos);
					return true;
				}
				if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
					level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
					return true;
				}
				if (state.is(Blocks.SNOW)) {
					int layers = state.getValue(SnowLayerBlock.LAYERS);
					if (layers > 1)
						level.setBlock(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1), 3);
					else
						level.removeBlock(pos, false);
					return true;
				}
			}
			case LAVA, FLAMMABLE -> {
				boolean ignited = sample.fluidBehavior() == FluidBehavior.LAVA || sample.ignited();
				if (!ignited)
					return false;
				if (state.isAir() && level.getFluidState(pos).isEmpty()
					&& level.getRandom().nextDouble() < Config.nozzleIgnitionChance / 100.0
					&& BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
					level.setBlock(pos, getFireState(level, pos), 3);
					return true;
				}
				if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.PACKED_ICE)) {
					level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
					return true;
				}
				if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
					level.removeBlock(pos, false);
					return true;
				}
			}
			case DRAGON_BREATH, UNSUPPORTED -> {
			}
		}
		return false;
	}

	private static long pathSweepKey(BlockPos pos, FluidBehavior behavior, boolean ignited) {
		return pos.asLong() ^ ((long) behavior.ordinal() << 56) ^ (ignited ? 1L << 55 : 0);
	}

	private BlockHitResult rayTraceBlock(Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, CollisionContext.empty()));
	}


	private void applyBlockEffect(LightweightProjectile proj, BlockHitResult hit) {
		if (level.isClientSide) return;

		BlockPos facePos = hit.getBlockPos().relative(hit.getDirection());

		switch (proj.fluidBehavior) {
			case WATER, MILK, POTION -> {
				int radius = 5;
				BlockPos center = facePos;
				boolean soundPlayed = false;
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dy = -radius; dy <= radius; dy++) {
						for (int dz = -radius; dz <= radius; dz++) {
							if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
							BlockPos pos = center.offset(dx, dy, dz);
							if (!level.isLoaded(pos))
								continue;
							if (!isOnImpactSide(hit, pos) || !hasBlockEffectLineOfSight(hit.getLocation(), pos))
								continue;
							BlockState st = level.getBlockState(pos);
							if (st.getBlock() instanceof BaseFireBlock) {
								level.removeBlock(pos, false);
								clearWildfireHeat(level, pos);
								if (!soundPlayed) {
									level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.2f);
									soundPlayed = true;
								}
							} else if (st.getBlock() instanceof CampfireBlock && st.getValue(CampfireBlock.LIT)) {
								level.setBlock(pos, st.setValue(CampfireBlock.LIT, false), 3);
								if (!soundPlayed) {
									level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.2f);
									soundPlayed = true;
								}
							} else if (st.is(Blocks.SNOW)) {
								int layers = st.getValue(SnowLayerBlock.LAYERS);
								if (layers > 1)
									level.setBlock(pos, st.setValue(SnowLayerBlock.LAYERS, layers - 1), 3);
								else
									level.removeBlock(pos, false);
							}
						}
					}
				}
			}
			case LAVA, FLAMMABLE -> {
				if (proj.fluidBehavior == FluidBehavior.FLAMMABLE && !proj.ignited) break;
				if (level.getRandom().nextDouble() < Config.nozzleIgnitionChance / 100.0
						&& level.getBlockState(facePos).canBeReplaced()
						&& level.getFluidState(facePos).isEmpty()
						&& BaseFireBlock.canBePlacedAt(level, facePos, hit.getDirection())) {
					level.setBlockAndUpdate(facePos, BaseFireBlock.getState(level, facePos));
				}
					int meltRadius = 2;
				BlockPos center = facePos;
				for (int dx = -meltRadius; dx <= meltRadius; dx++) {
					for (int dy = -meltRadius; dy <= meltRadius; dy++) {
						for (int dz = -meltRadius; dz <= meltRadius; dz++) {
							if (dx * dx + dy * dy + dz * dz > meltRadius * meltRadius) continue;
							BlockPos pos2 = center.offset(dx, dy, dz);
							BlockState st2 = level.getBlockState(pos2);
							if (st2.is(Blocks.ICE) || st2.is(Blocks.FROSTED_ICE) || st2.is(Blocks.PACKED_ICE))
								level.setBlock(pos2, Blocks.WATER.defaultBlockState(), 3);
							else if (st2.is(Blocks.SNOW) || st2.is(Blocks.SNOW_BLOCK))
								level.removeBlock(pos2, false);
						}
					}
				}
		}
			case DRAGON_BREATH -> {
			}
	}
	}

	// Client projectile processing

	private void tickClientProjectiles() {
		if (sprayStartedAt >= 0 && currentFluid != FluidBehavior.UNSUPPORTED) {
			long elapsed = level.getGameTime() - sprayStartedAt;
			int lifetime = getProjectileLifetime();
			Vec3 spawnPos = Vec3.atCenterOf(worldPosition).relative(getFacing(), 0.6);
			Vec3 baseDir = Vec3.atLowerCornerOf(getFacing().getNormal());
			SprayShape shape = getProjectileSprayShape();

			for (int i = 0; i < Config.serverProjectilesPerTick; i++) {
				Vec3 dir = shape.randomSprayDirection(baseDir, projectileRandom);
				Vec3 velocity = dir.scale(getProjectileSpeed());
				LightweightProjectile proj = new LightweightProjectile(spawnPos, velocity, lifetime,
					currentFluid, (float) PUSH_STREAM_SPEED, getGravityVector(), getProjectileFriction());
				if (sprayIgnited && currentFluid == FluidBehavior.FLAMMABLE) {
					proj.ignited = true;
					proj.ignitedAtAge = 0;
				}
				clientProjectiles.add(proj);
			}
		}

		Iterator<LightweightProjectile> it = clientProjectiles.iterator();
		while (it.hasNext()) {
			LightweightProjectile proj = it.next();
			proj.gravity = getGravityVector();
			proj.tick();

			if (proj.hasLostForwardMomentum() && proj.momentumLostTick < 0)
				proj.momentumLostTick = proj.age;

			boolean inMistGrace = proj.momentumLostTick >= 0 && proj.age <= proj.momentumLostTick + 10;

			if (!inMistGrace) {
				BlockHitResult blockHit = level.clip(new ClipContext(
					proj.prevPosition, proj.position,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
				if (blockHit.getType() != HitResult.Type.MISS) {
					it.remove();
					continue;
				}
			} else if (proj.age > proj.momentumLostTick + 10) {
				it.remove();
				continue;
			}

			spawnTrailParticles(proj);
			if (proj.isExpired())
				it.remove();
		}
	}

	private static Vector3f getFluidDustColor(FluidStack stack) {
		try {
			net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions ext =
				net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid().getFluidType());
			int tint = ext.getTintColor(stack);
			if (tint != -1) {
				float r = ((tint >> 16) & 0xFF) / 255f;
				float g = ((tint >> 8) & 0xFF) / 255f;
				float b = (tint & 0xFF) / 255f;
				return new Vector3f(r, g, b);
			}
		} catch (Exception ignored) {}
		return FUEL_AMBER;
	}

	private void spawnTrailParticles(LightweightProjectile proj) {
		if (currentFluid == FluidBehavior.UNSUPPORTED || currentFluid == null)
			return;

		dev.ryanhcode.sable.companion.ClientSubLevelAccess clientAccess = Sable.HELPER.getContainingClient(AbstractSprayDeviceBlockEntity.this);

		double progress = (double) proj.age / proj.maxLifetime;
		double timeMist = Math.clamp((progress - Config.mistTransitionStart) / Math.max(0.01, 1.0 - Config.mistTransitionStart), 0.0, 1.0);

		boolean momentumSpent = proj.hasLostForwardMomentum();
		double mistFactor = momentumSpent ? 1.0 : timeMist;

		var player = net.minecraft.client.Minecraft.getInstance().player;
		double dist = player != null ? player.position().distanceTo(proj.position) : 0;
		double distFactor = 0;

		int baseCount = getTrailParticlesPerTick();
		double countScale = 1.0 + mistFactor * 2.0;
		int count = Math.max(1, (int) (baseCount * countScale * (1.0 - 0.75 * distFactor)));

		float streamSize = 2.6f + (float) distFactor * 1.2f;
		float mistSize = 5.0f + (float) distFactor * 1.5f;
		float baseSize = streamSize + (float) mistFactor * (mistSize - streamSize);

		double streamSpread = 0.2;
		double mistSpread = Config.mistSpreadRadius;
		double spreadRadius = streamSpread + mistFactor * (mistSpread - streamSpread);

		for (int i = 0; i < count; i++) {
			double t = level.random.nextDouble();
			double baseX = proj.prevPosition.x + (proj.position.x - proj.prevPosition.x) * t;
			double baseY = proj.prevPosition.y + (proj.position.y - proj.prevPosition.y) * t;
			double baseZ = proj.prevPosition.z + (proj.position.z - proj.prevPosition.z) * t;

			double px, py, pz;
			if (mistFactor < 0.01) {
				px = baseX + (level.random.nextDouble() - 0.5) * spreadRadius * 2.0;
				py = baseY + (level.random.nextDouble() - 0.5) * spreadRadius * 0.6;
				pz = baseZ + (level.random.nextDouble() - 0.5) * spreadRadius * 2.0;
			} else if (mistFactor < 0.5) {
				double blend = mistFactor / 0.5;
				double bx = baseX + (level.random.nextDouble() - 0.5) * spreadRadius * 2.0;
				double by = baseY + (level.random.nextDouble() - 0.5) * spreadRadius * 0.6;
				double bz = baseZ + (level.random.nextDouble() - 0.5) * spreadRadius * 2.0;
				double theta = level.random.nextDouble() * 2.0 * Math.PI;
				double phi = Math.acos(1.0 - 2.0 * level.random.nextDouble());
				double r = Math.cbrt(level.random.nextDouble()) * spreadRadius;
				double sx = baseX + r * Math.sin(phi) * Math.cos(theta);
				double sy = baseY + r * Math.sin(phi) * Math.sin(theta);
				double sz = baseZ + r * Math.cos(phi);
				px = bx + (sx - bx) * blend;
				py = by + (sy - by) * blend;
				pz = bz + (sz - bz) * blend;
			} else {
				double theta = level.random.nextDouble() * 2.0 * Math.PI;
				double phi = Math.acos(1.0 - 2.0 * level.random.nextDouble());
				double r = Math.cbrt(level.random.nextDouble()) * spreadRadius;
				px = baseX + r * Math.sin(phi) * Math.cos(theta);
				py = baseY + r * Math.sin(phi) * Math.sin(theta);
				pz = baseZ + r * Math.cos(phi);
			}

			if (clientAccess != null) {
				net.minecraft.world.phys.Vec3 worldPos = clientAccess.renderPose().transformPosition(new net.minecraft.world.phys.Vec3(px, py, pz));
				px = worldPos.x; py = worldPos.y; pz = worldPos.z;
			}

			float size = baseSize + level.random.nextFloat() * 0.8f;

			double velX, velY, velZ;
			double streamWeight = 1.0 - mistFactor;
			velX = proj.velocity.x * 0.1 * streamWeight + (level.random.nextDouble() - 0.5) * 0.04 * mistFactor;
			velY = proj.velocity.y * 0.1 * streamWeight - 0.03 * mistFactor + (level.random.nextDouble() - 0.5) * 0.02 * mistFactor;
			velZ = proj.velocity.z * 0.1 * streamWeight + (level.random.nextDouble() - 0.5) * 0.04 * mistFactor;

			if (currentFluid == FluidBehavior.LAVA) {
				Vector3f lavaColor = level.random.nextFloat() < 0.5f
					? new Vector3f(1.0f, 0.4f, 0.0f)
					: new Vector3f(1.0f, 0.7f, 0.1f);
				level.addParticle(new DustParticleOptions(lavaColor, size),
					px, py, pz, velX, velY, velZ);
			} else if (currentFluid == FluidBehavior.DRAGON_BREATH) {
				Vector3f color = pickDragonBreathColor(level.random);
				level.addParticle(new DustParticleOptions(color, size * 1.1f),
					px, py, pz, velX * 0.6, velY * 0.6 + 0.01, velZ * 0.6);
				if (level.random.nextFloat() < 0.08f)
					level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
						px, py, pz, velX * 0.2, velY * 0.2 + 0.01, velZ * 0.2);
			} else {
				if (currentFluid == FluidBehavior.FLAMMABLE) {
					double particleAge = proj.age - 1 + t;
					if (proj.ignited && proj.ignitedAtAge >= 0 && particleAge >= proj.ignitedAtAge) {
						if (level.random.nextBoolean())
							level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
								px, py, pz, velX, velY, velZ);
					} else {
						Vector3f[] fuelColors = getFuelColorsByPath(sprayedFuelPath);
						Vector3f fuelColor = fuelColors[level.random.nextInt(fuelColors.length)];
						level.addParticle(new DustParticleOptions(fuelColor, size),
							px, py, pz, velX, velY, velZ);
					}
				} else {
					Vector3f color;
					if (currentFluid == FluidBehavior.MILK) {
						color = new Vector3f(1.0f, 1.0f, 1.0f);
					} else if (currentFluid == FluidBehavior.POTION) {
						color = potionColor;
					} else {
						if (level.random.nextFloat() < 0.6f * progress) {
							color = new Vector3f(1.0f, 1.0f, 1.0f);
						} else {
							color = level.random.nextFloat() < 0.5f
								? new Vector3f(0.6f, 0.8f, 1.0f)
								: new Vector3f(0.3f, 0.55f, 1.0f);
						}
					}
					level.addParticle(new DustParticleOptions(color, size),
						px, py, pz, velX, velY, velZ);
				}
			}
		}
	}

	// Network sync

		@Override
		protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
			super.write(tag, registries, clientPacket);
			if (clientPacket) {
				tag.putLong("SprayStartedAt", sprayStartedAt);
				tag.putString("LastSprayFluid", lastSprayFluid.name());
				tag.putBoolean("SprayIgnited", sprayIgnited);
				tag.putFloat("PotionColorR", potionColor.x);
				tag.putFloat("PotionColorG", potionColor.y);
				tag.putFloat("PotionColorB", potionColor.z);
				tag.putString("SprayedFuelPath", sprayedFuelPath);
			}
		}

		@Override
		protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
			super.read(tag, registries, clientPacket);
			if (clientPacket) {
				if (tag.contains("SprayStartedAt")) {
					long newSprayStartedAt = tag.getLong("SprayStartedAt");
					if (sprayStartedAt != newSprayStartedAt) {
						sprayStartedAt = newSprayStartedAt;
						if (sprayStartedAt >= 0)
							projectileRandom = new Random(sprayStartedAt * 31);
					}
				}
				if (tag.contains("LastSprayFluid"))
					lastSprayFluid = FluidBehavior.valueOf(tag.getString("LastSprayFluid"));
				if (tag.contains("SprayIgnited"))
					sprayIgnited = tag.getBoolean("SprayIgnited");
				if (tag.contains("PotionColorR"))
					potionColor = new Vector3f(tag.getFloat("PotionColorR"), tag.getFloat("PotionColorG"), tag.getFloat("PotionColorB"));
				if (tag.contains("SprayedFuelPath"))
					sprayedFuelPath = tag.getString("SprayedFuelPath");
			}
		}

	// Fluid classification

	protected FluidBehavior classifyFluid(FluidStack stack) {
		if (stack.isEmpty())
			return FluidBehavior.UNSUPPORTED;
		Fluid fluid = stack.getFluid();
		if (fluid.is(FluidTags.WATER))
			return FluidBehavior.WATER;
		if (fluid.is(FluidTags.LAVA))
			return FluidBehavior.LAVA;
		if (isDragonBreath(fluid))
			return FluidBehavior.DRAGON_BREATH;
		if (AllFluids.POTION != null && fluid.isSame(AllFluids.POTION.get())) {
			currentPotionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			int rgb = currentPotionContents.getColor();
			potionColor = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
			return FluidBehavior.POTION;
		}
		if (fluid.is(MILK_TAG))
			return FluidBehavior.MILK;
		if (isCdgFlammable(fluid) || isCdgFluidByNamespace(fluid))
			return FluidBehavior.FLAMMABLE;
		return FluidBehavior.UNSUPPORTED;
	}

	private static boolean isDragonBreath(Fluid fluid) {
		if (fluid.is(DRAGON_BREATH_TAG))
			return true;
		ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
		return id != null
			&& id.getNamespace().equals("create_dragons_plus")
			&& (id.getPath().equals("dragon_breath") || id.getPath().equals("flowing_dragon_breath"));
	}


	// Centerline sampling
	public record CenterlineSample(Vec3 position, Vec3 direction, double axialDist) {}

	/** Whether a world-space point lies within the spray stream cross-section at the given centerline sample.
	 *  ConeNozzle returns circular; FlatNozzle returns elliptical. Default returns false (sync scanners). */
	protected boolean isPointInStream(Vec3 point, Vec3 streamPos, Vec3 streamDir, double axialDist) {
		return false;
	}

	/** Traces a single reference projectile to map the curved stream path for entity scanning. */
	public List<CenterlineSample> computeStreamCenterline() {
		List<CenterlineSample> samples = new ArrayList<>();
		Vec3 position = getWorldSprayOrigin();
		Vec3 baseDir = getWorldSprayDirection();
		Vec3 velocity = baseDir.scale(getProjectileSpeed());
		Vec3 gravity = new Vec3(0, -getProjectileGravity(), 0);
		double friction = getProjectileFriction();
		double traveled = 0;
		int maxTicks = getProjectileLifetime();

		samples.add(new CenterlineSample(position, baseDir, 0));

		for (int tick = 0; tick < maxTicks; tick++) {
			Vec3 prevPos = position;
			velocity = velocity.add(gravity);
			velocity = velocity.scale(friction);
			position = position.add(velocity);
			traveled += position.distanceTo(prevPos);

			if (traveled > getEffectiveRange()) break;
			if (velocity.lengthSqr() < 0.0001) break;

			Vec3 dir = velocity.normalize();
			samples.add(new CenterlineSample(position, dir, traveled));
		}
		return samples;
	}

	/** Relative-to-BE variant of computeStreamCenterline for debug rendering. */
	public List<CenterlineSample> computeLocalCenterline() {
		List<CenterlineSample> samples = new ArrayList<>();
		Vec3 position = Vec3.atCenterOf(worldPosition).relative(getFacing(), 0.6);
		Vec3 baseDir = Vec3.atLowerCornerOf(getFacing().getNormal());
		Vec3 velocity = baseDir.scale(getProjectileSpeed());
		Vec3 gravity = getGravityVector();
		double friction = getProjectileFriction();
		double traveled = 0;
		int maxTicks = getProjectileLifetime();

		samples.add(new CenterlineSample(position, baseDir, 0));

		for (int tick = 0; tick < maxTicks; tick++) {
			Vec3 prevPos = position;
			velocity = velocity.add(gravity);
			velocity = velocity.scale(friction);
			position = position.add(velocity);
			traveled += position.distanceTo(prevPos);

			if (traveled > getEffectiveRange()) break;
			if (velocity.lengthSqr() < 0.0001) break;

			Vec3 dir = velocity.normalize();
			samples.add(new CenterlineSample(position, dir, traveled));
		}
		return samples;
	}

	private AABB computeCenterlineAABB(List<CenterlineSample> centerline) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (CenterlineSample cs : centerline) {
			if (cs.position.x < minX) minX = cs.position.x;
			if (cs.position.y < minY) minY = cs.position.y;
			if (cs.position.z < minZ) minZ = cs.position.z;
			if (cs.position.x > maxX) maxX = cs.position.x;
			if (cs.position.y > maxY) maxY = cs.position.y;
			if (cs.position.z > maxZ) maxZ = cs.position.z;
		}
		double margin = 4;
		return new AABB(minX - margin, minY - margin, minZ - margin,
			maxX + margin, maxY + margin, maxZ + margin);
	}

	/** Finds the centerline sample closest to the entity's AABB surface that lies within the stream tube. */
	private CenterlineSample findClosestSample(Entity entity, List<CenterlineSample> centerline) {
		AABB aabb = entity.getBoundingBox();
		double cx = (aabb.minX + aabb.maxX) / 2.0;
		double cy = (aabb.minY + aabb.maxY) / 2.0;
		double cz = (aabb.minZ + aabb.maxZ) / 2.0;

		double[] sx = { cx, aabb.minX, aabb.maxX, cx, cx, cx, cx };
		double[] sy = { cy, cy, cy, aabb.minY, aabb.maxY, cy, cy };
		double[] sz = { cz, cz, cz, cz, cz, aabb.minZ, aabb.maxZ };

		CenterlineSample best = null;
		double bestScore = Double.MAX_VALUE;

		for (int i = 0; i < 7; i++) {
			Vec3 pt = new Vec3(sx[i], sy[i], sz[i]);
			for (CenterlineSample cs : centerline) {
				if (cs.axialDist <= 0 || cs.axialDist > getEffectiveRange())
					continue;
				if (isPointInStream(pt, cs.position, cs.direction, cs.axialDist)) {
					double score = pt.distanceToSqr(cs.position);
					if (score < bestScore) {
						bestScore = score;
						best = cs;
					}
				}
			}
		}
		return best;
	}

	private void forEachEntityOnCenterline(Level entityLevel, AABB scanArea, List<CenterlineSample> centerline,
			BiConsumer<Entity, CenterlineSample> action) {
		Set<Entity> processed = new HashSet<>();
		for (Entity entity : entityLevel.getEntities(null, scanArea)) {
			if (!processed.add(entity)) continue;
			CenterlineSample sample = findClosestSample(entity, centerline);
			if (sample == null)
				continue;
			action.accept(entity, sample);
		}
	}

	private void applyEntityScan() {
		List<CenterlineSample> centerline = computeStreamCenterline();
		if (centerline.isEmpty()) return;
		AABB scanArea = computeCenterlineAABB(centerline);
		Vec3 origin = centerline.get(0).position;

		SubLevel subLevel = Sable.HELPER.getContaining(this);
		Level entityLevel = subLevel != null ? subLevel.getLevel() : level;
		FanProcessingType fanProcessingType = getNozzleFanProcessingType();
		if (fanProcessingType != null)
			processDepotsOnCenterline(centerline, scanArea, origin, fanProcessingType);

		switch (currentFluid) {
			case WATER -> {
				forEachEntityOnCenterline(entityLevel, scanArea, centerline, (entity, sample) -> {
					if (isEntityBlockedByWall(entityLevel, origin, entity)) return;
					if (entity instanceof ItemEntity itemEntity)
						processItemEntity(itemEntity, fanProcessingType);
					if (entity.getType() == EntityType.SNOW_GOLEM)
						entity.hurt(entityLevel.damageSources().drown(), 1.0f);
					if (entity.isCrouching() || entity.getPose() == Pose.SWIMMING)
						return;

					Vec3 worldDir = sample.direction;
					double pushSpeed = PUSH_STREAM_SPEED * (1.0 - sample.axialDist / getEffectiveRange());
					entity.push(worldDir.x * pushSpeed, worldDir.y * pushSpeed, worldDir.z * pushSpeed);
					if (entity instanceof ServerPlayer player)
						player.connection.send(new ClientboundSetEntityMotionPacket(player));
				});
			}
			case LAVA -> {
				forEachEntityOnCenterline(entityLevel, scanArea, centerline, (entity, sample) -> {
					if (isEntityBlockedByWall(entityLevel, origin, entity)) return;
					if (entity instanceof ItemEntity itemEntity) {
						processItemEntity(itemEntity, fanProcessingType);
						return;
					}
					if (entity.getRemainingFireTicks() < 100)
						entity.setRemainingFireTicks(100);
				});
			}
			case FLAMMABLE -> {
				if (sprayIgnited) {
					forEachEntityOnCenterline(entityLevel, scanArea, centerline, (entity, sample) -> {
						if (isEntityBlockedByWall(entityLevel, origin, entity)) return;
						if (entity instanceof ItemEntity itemEntity) {
							processItemEntity(itemEntity, fanProcessingType);
							return;
						}
						if (entity.getRemainingFireTicks() < 100)
							entity.setRemainingFireTicks(100);
					});
				}
			}
			case MILK -> {
				forEachEntityOnCenterline(entityLevel, scanArea, centerline, (entity, sample) -> {
					if (isEntityBlockedByWall(entityLevel, origin, entity)) return;
					if (entity instanceof LivingEntity living)
						living.removeAllEffects();
				});
			}
			case POTION -> {
				boolean potionEmpty = currentPotionContents.equals(PotionContents.EMPTY);
				if (!potionEmpty) {
					forEachEntityOnCenterline(entityLevel, scanArea, centerline, (entity, sample) -> {
						if (isEntityBlockedByWall(entityLevel, origin, entity)) return;
						if (entity instanceof LivingEntity living) {
							for (var effect : currentPotionContents.getAllEffects())
								living.addEffect(new MobEffectInstance(effect));
						}
					});
				}
			}
			case DRAGON_BREATH -> {
				if (level.getGameTime() % 5 != 0)
					return;
				forEachEntityOnCenterline(entityLevel, scanArea, centerline, (entity, sample) -> {
					if (isEntityBlockedByWall(entityLevel, origin, entity)) return;
					if (entity instanceof ItemEntity itemEntity) {
						processItemEntity(itemEntity, fanProcessingType);
						return;
					}
					if (entity instanceof LivingEntity living)
						living.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1, false, false, false));
				});
			}
		}
	}

	private FanProcessingType getNozzleFanProcessingType() {
		return switch (currentFluid) {
			case WATER -> AllFanProcessingTypes.SPLASHING;
			case LAVA -> AllFanProcessingTypes.BLASTING;
			case FLAMMABLE -> sprayIgnited ? AllFanProcessingTypes.BLASTING : null;
			case DRAGON_BREATH -> FanProcessingType.parse("create_dragons_plus:ending");
			default -> null;
		};
	}

	private void processItemEntity(ItemEntity entity, FanProcessingType type) {
		if (type == null || entity.isRemoved())
			return;
		Level itemLevel = entity.level();
		if (!type.canProcess(entity.getItem(), itemLevel))
			return;
		if (decrementEntityProcessingTime(entity, type) > 0)
			return;
		ItemStack original = entity.getItem();
		List<ItemStack> results = type.process(original, itemLevel);
		if (results == null || results.isEmpty()) {
			markEntityProcessingBlocked(entity, type);
			return;
		}
		entity.setItem(results.get(0).copy());
		for (int i = 1; i < results.size(); i++) {
			ItemStack extra = results.get(i);
			if (extra.isEmpty())
				continue;
			ItemEntity extraEntity = new ItemEntity(itemLevel, entity.getX(), entity.getY(), entity.getZ(), extra.copy());
			extraEntity.setDeltaMovement(entity.getDeltaMovement());
			itemLevel.addFreshEntity(extraEntity);
		}
	}

	private int decrementEntityProcessingTime(ItemEntity entity, FanProcessingType type) {
		CompoundTag processing = getNozzleProcessingTag(entity);
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		String typeName = typeId == null ? "" : typeId.toString();
		if (!typeName.equals(processing.getString("Type")) || !ItemStack.matches(entity.getItem(), readProcessingStack(processing))) {
			processing.putString("Type", typeName);
			processing.put("Stack", entity.getItem().saveOptional(entity.registryAccess()));
			processing.putInt("Time", processingTimeFor(entity.getItem()));
		}
		int time = processing.getInt("Time");
		if (time < 0)
			return time;
		time--;
		processing.putInt("Time", time);
		return time;
	}

	private void markEntityProcessingBlocked(ItemEntity entity, FanProcessingType type) {
		CompoundTag processing = getNozzleProcessingTag(entity);
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		processing.putString("Type", typeId == null ? "" : typeId.toString());
		processing.put("Stack", entity.getItem().saveOptional(entity.registryAccess()));
		processing.putInt("Time", -1);
	}

	private CompoundTag getNozzleProcessingTag(ItemEntity entity) {
		CompoundTag data = entity.getPersistentData();
		if (!data.contains("CreateFireFightingAdd"))
			data.put("CreateFireFightingAdd", new CompoundTag());
		CompoundTag modData = data.getCompound("CreateFireFightingAdd");
		if (!modData.contains("NozzleProcessing"))
			modData.put("NozzleProcessing", new CompoundTag());
		return modData.getCompound("NozzleProcessing");
	}

	private ItemStack readProcessingStack(CompoundTag processing) {
		if (!processing.contains("Stack"))
			return ItemStack.EMPTY;
		return ItemStack.parseOptional(level.registryAccess(), processing.getCompound("Stack"));
	}

	private void processDepotsOnCenterline(List<CenterlineSample> centerline, AABB scanArea, Vec3 origin,
			FanProcessingType type) {
		Set<Long> seen = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(
			(int) Math.floor(scanArea.minX), (int) Math.floor(scanArea.minY), (int) Math.floor(scanArea.minZ),
			(int) Math.floor(scanArea.maxX), (int) Math.floor(scanArea.maxY), (int) Math.floor(scanArea.maxZ))) {
			BlockPos depotPos = pos.immutable();
			if (!seen.add(depotPos.asLong()))
				continue;
			if (!level.isLoaded(depotPos))
				continue;
			if (!(level.getBlockEntity(depotPos) instanceof DepotBlockEntity depot))
				continue;
			ItemStack stack = depot.getHeldItem();
			if (stack.isEmpty() || !type.canProcess(stack, level))
				continue;
			Vec3 itemPos = Vec3.atCenterOf(depotPos).add(0, 0.55, 0);
			CenterlineSample sample = findClosestSample(itemPos, centerline);
			if (sample == null || isPointBlockedByWall(origin, itemPos))
				continue;
			if (decrementDepotProcessingTime(depotPos, stack, type) > 0)
				continue;
			List<ItemStack> results = type.process(stack, level);
			if (results == null || results.isEmpty()) {
				markDepotProcessingBlocked(depotPos, stack, type);
				continue;
			}
			depot.setHeldItem(results.get(0).copy());
			for (int i = 1; i < results.size(); i++) {
				ItemStack extra = results.get(i);
				if (!extra.isEmpty())
					Block.popResource(level, depotPos.above(), extra.copy());
			}
			depot.notifyUpdate();
		}
	}

	private CenterlineSample findClosestSample(Vec3 point, List<CenterlineSample> centerline) {
		CenterlineSample best = null;
		double bestScore = Double.MAX_VALUE;
		for (CenterlineSample sample : centerline) {
			if (sample.axialDist <= 0 || sample.axialDist > getEffectiveRange())
				continue;
			if (!isPointInStream(point, sample.position, sample.direction, sample.axialDist))
				continue;
			double score = point.distanceToSqr(sample.position);
			if (score < bestScore) {
				bestScore = score;
				best = sample;
			}
		}
		return best;
	}

	private int decrementDepotProcessingTime(BlockPos pos, ItemStack stack, FanProcessingType type) {
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		ProcessingProgress progress = depotProcessing.get(pos.asLong());
		if (progress == null || !typeId.equals(progress.type()) || !ItemStack.matches(stack, progress.stack())) {
			progress = new ProcessingProgress(typeId, stack.copy(), processingTimeFor(stack));
		}
		if (progress.time() < 0)
			return progress.time();
		int time = progress.time() - 1;
		depotProcessing.put(pos.asLong(), new ProcessingProgress(typeId, stack.copy(), time));
		return time;
	}

	private void markDepotProcessingBlocked(BlockPos pos, ItemStack stack, FanProcessingType type) {
		ResourceLocation typeId = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
		depotProcessing.put(pos.asLong(), new ProcessingProgress(typeId, stack.copy(), -1));
	}

	private int processingTimeFor(ItemStack stack) {
		int timeModifierForStackSize = ((stack.getCount() - 1) / 16) + 1;
		return (int) (AllConfigs.server().kinetics.fanProcessingTime.get() * timeModifierForStackSize) + 1;
	}

	private boolean isPointBlockedByWall(Vec3 origin, Vec3 target) {
		double dist = origin.distanceTo(target);
		if (dist < 0.5)
			return false;
		BlockHitResult hit = level.clip(new ClipContext(
			origin, target, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS)
			return false;
		return hit.getLocation().distanceToSqr(origin) < (dist - 0.3) * (dist - 0.3);
	}

	// Shared block iteration

	protected void forEachBlockInSpray(BiConsumer<BlockPos, BlockState> action) {
		SprayShape shape = getSprayShape();
		Vec3 origin = getWorldSprayOrigin();
		Vec3 direction = getWorldSprayDirection();
		Set<Long> blockedDirections = new HashSet<>();

		shape.forEachPosition(origin, direction, (pos, dist, gridU, gridV) -> {
			long key = directionKey(gridU, gridV, dist);
			if (blockedDirections.contains(key))
				return;

			BlockState state = level.getBlockState(pos);

			if (state.isCollisionShapeFullBlock(level, pos) && !canBurn(state)) {
				blockedDirections.add(key);
				return;
			}

			if (isRayToPositionBlocked(origin, pos)) {
				blockedDirections.add(key);
				return;
			}

			action.accept(pos, state);
		});
	}
	// Fluid behaviours

	protected void waterBehavior() {
		forEachBlockInSpray((pos, state) -> {
			if (isExtinguishable(state))
				extinguishBlock(pos, state);
		});
		forEachEntityInSpray((entity, axialDist) -> {
			Vec3 direction = getWorldSprayDirection();
			double pushSpeed = PUSH_STREAM_SPEED * (1.0 - axialDist / getEffectiveRange());
			entity.push(
				direction.x * pushSpeed,
				direction.y * pushSpeed,
				direction.z * pushSpeed);
			if (entity instanceof ServerPlayer player) {
				player.connection.send(new ClientboundSetEntityMotionPacket(player));
			}
		});
	}


	protected void lavaBehavior() {
		RandomSource random = level.getRandom();
		forEachBlockInSpray((pos, state) -> {
			if (random.nextDouble() < Config.nozzleIgnitionChance / 100.0 && level.getBlockState(pos).isAir()) {
				BlockState fireState = getFireState(level, pos);
				level.setBlock(pos, fireState, 3);
			}
		});
		forEachEntityInSpray((entity, axialDist) -> {
			if (entity.getRemainingFireTicks() < 100) {
				entity.setRemainingFireTicks(100);
			}
		});
	}

	protected void milkBehavior() {
		forEachBlockInSpray((pos, state) -> {
			if (isExtinguishable(state))
				extinguishBlock(pos, state);
		});
		forEachEntityInSpray((entity, axialDist) -> {
			if (entity instanceof LivingEntity living) {
				living.removeAllEffects();
			}
		});
	}

	protected void potionBehavior() {
		forEachBlockInSpray((pos, state) -> {
			if (isExtinguishable(state))
				extinguishBlock(pos, state);
		});

		FluidStack fluid = tank.getPrimaryHandler().getFluidInTank(0);
		PotionContents contents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		if (!contents.equals(PotionContents.EMPTY)) {
			forEachEntityInSpray((entity, axialDist) -> {
				if (entity instanceof LivingEntity living) {
					for (var effect : contents.getAllEffects()) {
						living.addEffect(new MobEffectInstance(effect));
					}
				}
			});
		}
	}

	protected void dragonBreathBehavior() {
		forEachEntityInSpray((entity, axialDist) -> {
			if (entity instanceof LivingEntity living)
				living.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1, false, false, false));
		});
	}

	// Area sweep

	private void doFullSweep() {
		forEachBlockInSpray((pos, state) -> {
			if (isExtinguishable(state))
				extinguishBlock(pos, state);
		});
	}

	private static final int ANGLE_PRECISION = 1000;

	private static long directionKey(int gridU, int gridV, int dist) {
		int u = (int) Math.round((double) gridU * ANGLE_PRECISION / dist);
		int v = (int) Math.round((double) gridV * ANGLE_PRECISION / dist);
		return ((long) u << 32) | (v & 0xFFFFFFFFL);
	}

	private static boolean isExtinguishable(BlockState state) {
		return state.getBlock() instanceof BaseFireBlock
			|| (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT));
	}

	private boolean isRayToPositionBlocked(Vec3 origin, BlockPos targetPos) {
		Vec3 targetCenter = Vec3.atCenterOf(targetPos);
		BlockHitResult hit = level.clip(new ClipContext(
			origin, targetCenter,
			ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return !targetPos.equals(hit.getBlockPos());
	}

	private boolean hasBlockEffectLineOfSight(Vec3 source, BlockPos targetPos) {
		if (!level.isLoaded(BlockPos.containing(source)) || !level.isLoaded(targetPos))
			return false;
		BlockHitResult hit = level.clip(new ClipContext(
			source, Vec3.atCenterOf(targetPos),
			ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || targetPos.equals(hit.getBlockPos());
	}

	private boolean isOnImpactSide(BlockHitResult hit, BlockPos targetPos) {
		Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
		Vec3 toTarget = Vec3.atCenterOf(targetPos).subtract(hit.getLocation());
		return toTarget.dot(normal) >= -0.01;
	}

	private void extinguishBlock(BlockPos pos, BlockState state) {
		if (tryTfcDouse(level, pos))
			return;
		if (state.getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
			if (ticksSinceLastExtinguishSound >= EXTINGUISH_SOUND_COOLDOWN) {
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
				ticksSinceLastExtinguishSound = 0;
			}
			clearWildfireHeat(level, pos);
			clearNearbyWildfireHeat(pos);
			return;
		}
		if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
			level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
			if (ticksSinceLastExtinguishSound >= EXTINGUISH_SOUND_COOLDOWN) {
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
				ticksSinceLastExtinguishSound = 0;
			}
		}
	}

	private void clearNearbyWildfireHeat(BlockPos pos) {
		int r = Config.heatClearRadius;
		for (int dx = -r; dx <= r; dx++)
			for (int dy = -r; dy <= r; dy++)
				for (int dz = -r; dz <= r; dz++)
					clearWildfireHeat(level, pos.offset(dx, dy, dz));
	}

	/** Checks whether a full block obstructs the straight line from spray origin to entity. */
	private boolean isEntityBlockedByWall(Level entityLevel, Vec3 origin, Entity entity) {
		Vec3 target = entity.getEyePosition();
		double dist = origin.distanceTo(target);
		if (dist < 0.5) return false;
		BlockHitResult hit = entityLevel.clip(new ClipContext(
			origin, target, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS) return false;
		return hit.getLocation().distanceToSqr(origin) < (dist - 0.3) * (dist - 0.3);
	}

	protected Vec3 getWorldSprayOrigin() {
		Direction facing = getFacing();
		Vec3 origin = Vec3.atCenterOf(worldPosition).relative(facing, 0.6);
		SubLevel subLevel = Sable.HELPER.getContaining(this);
		if (subLevel != null) {
			return subLevel.logicalPose().transformPosition(origin);
		}
		return origin;
	}

	protected Vec3 getWorldSprayDirection() {
		Direction facing = getFacing();
		Vec3 direction = Vec3.atLowerCornerOf(facing.getNormal());
		SubLevel subLevel = Sable.HELPER.getContaining(this);
		if (subLevel != null) {
			return subLevel.logicalPose().transformNormal(direction).normalize();
		}
		return direction;
	}

	public IFluidHandler getFluidHandler(Direction side) {
		if (side == getFacing().getOpposite())
			return tank.getCapability();
		return null;
	}


	// Particle colors

	protected Vector3f pickStreamColor(RandomSource random) {
		return switch (currentFluid) {
			case LAVA -> {
				float r = random.nextFloat();
				yield r < 0.4f ? LAVA_RED : (r < 0.75f ? LAVA_ORANGE : LAVA_YELLOW);
			}
			case MILK -> WHITE;
			case POTION -> potionColor;
			case DRAGON_BREATH -> pickDragonBreathColor(random);
			case FLAMMABLE -> {
				Vector3f[] colors = getFuelColorsByPath(sprayedFuelPath);
				yield colors[random.nextInt(colors.length)];
			}
			default -> {
				float r = random.nextFloat();
				yield r < 0.5f ? WHITE : (r < 0.8f ? LIGHT_BLUE : BLUE);
			}
		};
	}

	protected Vector3f pickMistColor(RandomSource random) {
		return switch (currentFluid) {
			case LAVA -> {
				float r = random.nextFloat();
				yield r < 0.5f ? LAVA_ORANGE : (r < 0.8f ? LAVA_RED : LAVA_YELLOW);
			}
			case MILK -> WHITE;
			case POTION -> potionColor;
			case DRAGON_BREATH -> pickDragonBreathColor(random);
			case FLAMMABLE -> {
				Vector3f[] colors = getFuelColorsByPath(sprayedFuelPath);
				yield colors[random.nextInt(colors.length)];
			}
			default -> {
				float r = random.nextFloat();
				yield r < 0.6f ? LIGHT_BLUE : (r < 0.9f ? BLUE : WHITE);
			}
		};
	}

	private static Vector3f pickDragonBreathColor(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.38f)
			return DRAGON_PURPLE;
		if (r < 0.68f)
			return DRAGON_PINK;
		if (r < 0.90f)
			return DRAGON_PALE;
		return DRAGON_WHITE;
	}

	public boolean isSpraying() {
		IFluidHandler handler = tank.getPrimaryHandler();
		FluidStack fluid = handler.getFluidInTank(0);
		return !fluid.isEmpty()
			&& fluid.getAmount() >= getFluidConsumptionPerTick()
			&& currentFluid != FluidBehavior.UNSUPPORTED;
	}
}
