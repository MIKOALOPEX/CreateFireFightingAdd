package com.mikoalopex.createfirefightingadd;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public class Config {
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	// === Cone Nozzle ===
	private static final ModConfigSpec.IntValue CONE_MAX_DISTANCE;
	private static final ModConfigSpec.DoubleValue CONE_SPEED;
	private static final ModConfigSpec.DoubleValue CONE_GRAVITY;
	private static final ModConfigSpec.DoubleValue CONE_FRICTION;

	// === Flat Nozzle ===
	private static final ModConfigSpec.IntValue FLAT_MAX_DISTANCE;
	private static final ModConfigSpec.DoubleValue FLAT_SPEED;
	private static final ModConfigSpec.DoubleValue FLAT_GRAVITY;
	private static final ModConfigSpec.DoubleValue FLAT_FRICTION;

	// === Global ===
	private static final ModConfigSpec.IntValue BUCKET_DEFAULT_HEIGHT;
	private static final ModConfigSpec.IntValue BUCKET_MAX_HEIGHT;
	private static final ModConfigSpec.IntValue BUCKET_RADIUS;
	private static final ModConfigSpec.IntValue BUCKET_WATER_CONSUMPTION;
	private static final ModConfigSpec.IntValue BUCKET_SCAN_INTERVAL;
	private static final ModConfigSpec.IntValue BUCKET_DIRECT_TRANSFER_SPEED;
	private static final ModConfigSpec.IntValue INTAKE_SCAN_RANGE;
	private static final ModConfigSpec.IntValue INTAKE_SCAN_INTERVAL;
	private static final ModConfigSpec.IntValue WIRELESS_MAX_BIND_DISTANCE;
	private static final ModConfigSpec.IntValue WIRELESS_TRANSFER_SPEED;
	private static final ModConfigSpec.IntValue HEAT_CLEAR_RADIUS;
	private static final ModConfigSpec.IntValue FULL_SWEEP_INTERVAL;
	private static final ModConfigSpec.IntValue HOSE_MAX_LENGTH;
	private static final ModConfigSpec.DoubleValue HOSE_SNAP_MULTIPLIER;
	private static final ModConfigSpec.IntValue HOSE_TANK_CAPACITY;
	private static final ModConfigSpec.IntValue HOSE_PUMP_SCAN_INTERVAL;
	private static final ModConfigSpec.IntValue HOSE_TOPOLOGY_REFRESH_INTERVAL;
	private static final ModConfigSpec.DoubleValue HOSE_INPUT_SMOOTHING;
	private static final ModConfigSpec.DoubleValue HOSE_EXTERNAL_INPUT_PRESSURE_SCALE;
	private static final ModConfigSpec.DoubleValue HOSE_FLOW_TO_PRESSURE_SCALE;
	private static final ModConfigSpec.DoubleValue HOSE_PRESSURE_RECOVERY_PER_TICK;
	private static final ModConfigSpec.DoubleValue HOSE_PRESSURE_DROP_PER_TICK;
	private static final ModConfigSpec.IntValue HOSE_LOW_BUFFER_TICKS_TO_LIMIT;
	private static final ModConfigSpec.IntValue HOSE_EXTERNAL_INPUT_MEMORY_TICKS;
	private static final ModConfigSpec.IntValue HOSE_EXTERNAL_INPUT_OUTPUT_RANGE;
	private static final ModConfigSpec.DoubleValue HIGH_PRESSURE_PUMP_MULTIPLIER;
	private static final ModConfigSpec.IntValue NOZZLE_SPRAY_BUILDUP_TICKS;
	private static final ModConfigSpec.BooleanValue NOZZLE_THRUST_ENABLED;
	private static final ModConfigSpec.DoubleValue NOZZLE_THRUST_MULTIPLIER;
	private static final ModConfigSpec.DoubleValue MIST_SPREAD_RADIUS;
	private static final ModConfigSpec.DoubleValue MIST_TRANSITION_START;
	private static final ModConfigSpec.IntValue SERVER_PROJECTILES_PER_TICK;
	private static final ModConfigSpec.IntValue NOZZLE_IGNITION_CHANCE;
	private static final ModConfigSpec.BooleanValue CDG_IGNITION_ENABLED;
	private static final ModConfigSpec.DoubleValue FLAME_PROPAGATION_RADIUS;

	static {
		BUILDER.push("ConeNozzle");
		CONE_MAX_DISTANCE = BUILDER
			.comment("Maximum effective spray distance of the cone firefighting nozzle in blocks (1-512). Clamped to the physical limit imposed by projectile speed and friction.")
			.defineInRange("MaxDistance(blocks)", 50, 1, 512);
		CONE_SPEED = BUILDER
			.comment("Muzzle velocity of cone nozzle spray droplets in blocks per tick (0.5-10.0). Higher = farther reach and flatter trajectory.")
			.defineInRange("ProjectileSpeed", 1.8, 0.5, 10.0);
		CONE_GRAVITY = BUILDER
			.comment("Downward acceleration per tick for cone nozzle droplets (0.0-0.1). Default 0.015 matches vanilla projectile gravity.")
			.defineInRange("ProjectileGravity", 0.015, 0.0, 0.1);
		CONE_FRICTION = BUILDER
			.comment("Air friction retention per tick for cone nozzle droplets (0.8-0.999). Velocity is multiplied by this value each tick. Higher = droplets carry farther.")
			.defineInRange("ProjectileFriction", 0.97, 0.8, 0.999);
		BUILDER.pop();

		BUILDER.push("FlatNozzle");
		FLAT_MAX_DISTANCE = BUILDER
			.comment("Maximum effective spray distance of the flat spray nozzle in blocks (1-512). Clamped to the physical limit imposed by projectile speed and friction.")
			.defineInRange("MaxDistance(blocks)", 35, 1, 512);
		FLAT_SPEED = BUILDER
			.comment("Muzzle velocity of flat nozzle spray droplets in blocks per tick (0.5-10.0).")
			.defineInRange("ProjectileSpeed", 1.5, 0.5, 10.0);
		FLAT_GRAVITY = BUILDER
			.comment("Downward acceleration per tick for flat nozzle droplets (0.0-0.1).")
			.defineInRange("ProjectileGravity", 0.0125, 0.0, 0.1);
		FLAT_FRICTION = BUILDER
			.comment("Air friction retention per tick for flat nozzle droplets (0.8-0.999).")
			.defineInRange("ProjectileFriction", 0.97, 0.8, 0.999);
		BUILDER.pop();

		BUILDER.push("BucketController");
		BUCKET_DEFAULT_HEIGHT = BUILDER
			.comment("Default spray height, initial scroll value when the block is placed (1-256). Players can adjust this on the block itself via the value board")
			.defineInRange("DefaultHeight(blocks)", 64, 1, 256);
		BUCKET_MAX_HEIGHT = BUILDER
			.comment("Maximum spray height the player can set on the value board (1-256). Caps the scroll value, not the board UI range")
			.defineInRange("MaxHeight(blocks)", 256, 1, 256);
		BUCKET_RADIUS = BUILDER
			.comment("Extinguishing cylinder radius of the bucket controller (1-128)")
			.defineInRange("Radius(blocks)", 16, 1, 128);
		BUCKET_WATER_CONSUMPTION = BUILDER
			.comment("Water consumption rate of the bucket controller (1-10000)")
			.defineInRange("WaterConsumption(mB/s)", 200, 1, 10000);
		BUCKET_SCAN_INTERVAL = BUILDER
			.comment("Ticks between each fire scan of the bucket controller (1-40). Lower = faster extinguishing but more CPU load")
			.defineInRange("ScanInterval(tick)", 5, 1, 40);
		BUCKET_DIRECT_TRANSFER_SPEED = BUILDER
			.comment("Fluid transfer rate (mB/tick) when the bucket controller pulls directly from an adjacent fluid tank or vessel (1-10000)")
			.defineInRange("DirectTransferSpeed(mB/tick)", 500, 1, 10000);
		BUILDER.pop();

		BUILDER.push("WaterIntake");
		INTAKE_SCAN_RANGE = BUILDER
			.comment("Radius the water intake scans for infinite water sources (1-64)")
			.defineInRange("ScanRange(blocks)", 16, 1, 64);
		INTAKE_SCAN_INTERVAL = BUILDER
			.comment("Ticks between each water source scan of the water intake (1-200). Higher = less CPU load")
			.defineInRange("ScanInterval(tick)", 40, 1, 200);
		BUILDER.pop();

		BUILDER.push("WirelessTransfer");
		WIRELESS_MAX_BIND_DISTANCE = BUILDER
			.comment("Maximum distance between water intake and bound bucket controller (1-256)")
			.defineInRange("MaxBindDistance(blocks)", 32, 1, 256);
		WIRELESS_TRANSFER_SPEED = BUILDER
			.comment("Wireless transfer rate between water intake and bound bucket controller (1-64)")
			.defineInRange("TransferSpeed(B/tick)", 5, 1, 64);
		BUILDER.pop();

		BUILDER.push("FireExtinguish");
		HEAT_CLEAR_RADIUS = BUILDER
			.comment("Radius (in blocks) around an extinguished fire to clear TFC Wildfire smolder heat (0-8). Larger values prevent re-ignition more aggressively")
			.defineInRange("HeatClearRadius(blocks)", 2, 0, 8);
		FULL_SWEEP_INTERVAL = BUILDER
			.comment("Ticks between full-area fire sweeps (1-6000). The sweep ignores obstacles to catch all fires in range. Lower = faster catch but more CPU load")
			.defineInRange("FullSweepInterval(ticks)", 100, 1, 6000);
		BUILDER.pop();

		BUILDER.push("FireHose");
		HOSE_MAX_LENGTH = BUILDER
			.comment("Maximum distance (blocks) between fire hose endpoints before snapping (1-32)")
			.defineInRange("hoseMaxLength", 16, 1, 32);
		HOSE_SNAP_MULTIPLIER = BUILDER
			.comment("Snap distance multiplier. Actual snap distance = maxLength * snapMultiplier (1.0-4.0)")
			.defineInRange("hoseSnapMultiplier", 1.5, 1.0, 4.0);
		HOSE_TANK_CAPACITY = BUILDER
			.comment("Shared internal fluid buffer of one fire hose pair (100-64000 mB). Larger buffers tolerate high-flow custom pumps and long hose chains better.")
			.defineInRange("hoseTankCapacity", 1000, 100, 64000);
		BUILDER.pop();

		BUILDER.push("FireHosePressure");
		HOSE_PUMP_SCAN_INTERVAL = BUILDER
			.comment("Ticks between fire hose source scans (1-100). Lower updates pump direction and adjacent hose chains faster, but costs more CPU.")
			.defineInRange("pumpScanInterval(ticks)", 5, 1, 100);
		HOSE_TOPOLOGY_REFRESH_INTERVAL = BUILDER
			.comment("Ticks between remote output topology refreshes (1-200). Lower reacts faster to pipe edits far from the hose output, but costs more CPU.")
			.defineInRange("topologyRefreshInterval(ticks)", 20, 1, 200);
		HOSE_INPUT_SMOOTHING = BUILDER
			.comment("Smoothing factor for observed hose input flow (0.01-1.0). Higher reacts faster; lower is more stable.")
			.defineInRange("inputSmoothing", 0.15, 0.01, 1.0);
		HOSE_EXTERNAL_INPUT_PRESSURE_SCALE = BUILDER
			.comment("Pressure inferred from generic external fluid insertion. pressure = mB/t * this value. Create pump baseline: 128 mB/t -> 256 pressure, so default is 2.0.")
			.defineInRange("externalInputPressureScale", 2.0, 0.1, 64.0);
		HOSE_FLOW_TO_PRESSURE_SCALE = BUILDER
			.comment("Compatibility limiter estimate for how much pressure the observed input flow can sustain. Higher is more permissive for custom pumps; lower limits output sooner when the buffer runs dry.")
			.defineInRange("observedFlowPressureScale", 4.0, 0.1, 64.0);
		HOSE_PRESSURE_RECOVERY_PER_TICK = BUILDER
			.comment("Maximum pressure recovered per tick after the hose has enough input again (1-4096).")
			.defineInRange("pressureRecoveryPerTick", 32.0, 1.0, 4096.0);
		HOSE_PRESSURE_DROP_PER_TICK = BUILDER
			.comment("Maximum pressure dropped per tick when the hose detects sustained under-supply (1-4096).")
			.defineInRange("pressureDropPerTick", 48.0, 1.0, 4096.0);
		HOSE_LOW_BUFFER_TICKS_TO_LIMIT = BUILDER
			.comment("Consecutive low-buffer ticks before dynamic output limiting starts (1-200). Higher tolerates brief bursts; lower protects small buffers sooner.")
			.defineInRange("lowBufferTicksToLimit", 10, 1, 200);
		HOSE_EXTERNAL_INPUT_MEMORY_TICKS = BUILDER
			.comment("Ticks to remember generic external fluid insertion as a hose source after the last fill call (1-100).")
			.defineInRange("externalInputMemoryTicks", 10, 1, 100);
		HOSE_EXTERNAL_INPUT_OUTPUT_RANGE = BUILDER
			.comment("Output pipe propagation range when a generic non-Create fluid source inserts into a hose and the opposite end drives a Create pipe network (4-64).")
			.defineInRange("externalInputOutputRange(blocks)", 16, 4, 64);
		BUILDER.pop();

		BUILDER.push("HighPressurePump");
		HIGH_PRESSURE_PUMP_MULTIPLIER = BUILDER
			.comment("Amplification multiplier relative to Create's mechanical pump. This scales both pressure and pipe propagation range. Default 2.0 means 2x pressure and 2x Create pump range.")
			.defineInRange("amplificationMultiplier", 2.0, 1.0, 16.0);
		BUILDER.pop();

		BUILDER.push("NozzleFireChance");
		NOZZLE_IGNITION_CHANCE = BUILDER
			.comment("Probability (0-100%) per air block per tick that lava/ignited spray places fire (wide cone only; projectile core path is 100%)")
			.translation("createfirefightingadd.config.nozzleFireChance")
			.defineInRange("NozzleFireChance", 100, 0, 100);
		BUILDER.pop();

		BUILDER.push("NozzleSpray");
		SERVER_PROJECTILES_PER_TICK = BUILDER
			.comment("Number of projectile entities spawned per tick on the server for game logic (1-10). Lower TPS cost. Visual density comes from client particles")
			.defineInRange("serverProjectilesPerTick", 1, 1, 10);
		MIST_TRANSITION_START = BUILDER
			.comment("Fraction of projectile lifetime (0.0-1.0) where the stream begins breaking into mist. 0.6 = mist starts at 60% of max range")
			.defineInRange("mistTransitionStart", 0.6, 0.0, 1.0);
		MIST_SPREAD_RADIUS = BUILDER
			.comment("Maximum random spread radius (blocks) of mist particles around the projectile (0.5-8.0)")
			.defineInRange("mistSpreadRadius", 2.0, 0.5, 8.0);
		NOZZLE_SPRAY_BUILDUP_TICKS = BUILDER
			.comment("Ticks for the spray range to build up from 0 to full distance when the nozzle starts spraying (1-60). Uses ease-in quadratic curve for natural pressure buildup feel")
			.translation("createfirefightingadd.config.nozzleBuildupTicks")
			.defineInRange("BuildupTicks", 20, 1, 60);
		NOZZLE_THRUST_ENABLED = BUILDER
			.comment("Whether spray nozzles produce recoil thrust on the contraption. True = nozzles push the contraption backward when spraying")
			.define("nozzleThrustEnabled", true);
		NOZZLE_THRUST_MULTIPLIER = BUILDER
			.comment("Global recoil thrust multiplier for nozzle spray (0.0-500.0). At 10.0, a flat nozzle at full range with water produces ~10N of thrust")
			.defineInRange("nozzleThrustMultiplier", 10.0, 0.0, 500.0);
		BUILDER.pop();

		BUILDER.push("CDGCompat");
		CDG_IGNITION_ENABLED = BUILDER
			.comment("When Create Diesel Generators is installed, allow flammable fuel spray to be ignited by fire sources (fire blocks, lava, campfires, CDG lighters, CDG chemical projectiles). Ignited spray places fire blocks (same as LAVA) and particle effect changes to match CDG's ignited spray")
			.define("cdgIgnitionEnabled", true);
		FLAME_PROPAGATION_RADIUS = BUILDER
			.comment("Maximum distance (blocks) between projectiles for flame propagation along the spray stream. Ignited projectiles ignite nearby unignited flammable projectiles within this radius")
			.defineInRange("flamePropagationRadius", 3.0, 0.5, 16.0);
		BUILDER.pop();
	}

	static final ModConfigSpec SPEC = BUILDER.build();

	// Cone nozzle
	public static int coneNozzleMaxDistance;
	public static double coneNozzleSpeed;
	public static double coneNozzleGravity;
	public static double coneNozzleFriction;

	// Flat nozzle
	public static int flatNozzleMaxDistance;
	public static double flatNozzleSpeed;
	public static double flatNozzleGravity;
	public static double flatNozzleFriction;

	// Global
	public static int bucketDefaultHeight;
	public static int bucketMaxHeight;
	public static int bucketRadius;
	public static int bucketWaterConsumption;
	public static int bucketScanInterval;
	public static int bucketDirectTransferSpeed;
	public static int intakeScanRange;
	public static int intakeScanInterval;
	public static int wirelessMaxBindDistance;
	public static int wirelessTransferSpeed;
	public static int heatClearRadius;
	public static int fullSweepInterval;
	public static int hoseMaxLength;
	public static double hoseSnapMultiplier;
	public static int hoseTankCapacity;
	public static int hosePumpScanInterval;
	public static int hoseTopologyRefreshInterval;
	public static float hoseInputSmoothing;
	public static float hoseExternalInputPressureScale;
	public static float hoseFlowToPressureScale;
	public static float hosePressureRecoveryPerTick;
	public static float hosePressureDropPerTick;
	public static int hoseLowBufferTicksToLimit;
	public static int hoseExternalInputMemoryTicks;
	public static int hoseExternalInputOutputRange;
	public static float highPressurePumpMultiplier;
	public static int nozzleSprayBuildupTicks;
	public static double mistSpreadRadius;
	public static double mistTransitionStart;
	public static int serverProjectilesPerTick;
	public static int nozzleIgnitionChance;
	public static boolean nozzleThrustEnabled;
	public static double nozzleThrustMultiplier;
	public static boolean cdgIgnitionEnabled;
	public static double flamePropagationRadius;

	@SubscribeEvent
	static void onLoad(final ModConfigEvent event) {
		if (event instanceof ModConfigEvent.Unloading)
			return;
		if (event.getConfig().getSpec() != SPEC)
			return;

		coneNozzleMaxDistance = CONE_MAX_DISTANCE.get();
		coneNozzleSpeed = CONE_SPEED.get();
		coneNozzleGravity = CONE_GRAVITY.get();
		coneNozzleFriction = CONE_FRICTION.get();

		flatNozzleMaxDistance = FLAT_MAX_DISTANCE.get();
		flatNozzleSpeed = FLAT_SPEED.get();
		flatNozzleGravity = FLAT_GRAVITY.get();
		flatNozzleFriction = FLAT_FRICTION.get();

		bucketDefaultHeight = BUCKET_DEFAULT_HEIGHT.get();
		bucketMaxHeight = BUCKET_MAX_HEIGHT.get();
		bucketRadius = BUCKET_RADIUS.get();
		bucketWaterConsumption = BUCKET_WATER_CONSUMPTION.get();
		bucketScanInterval = BUCKET_SCAN_INTERVAL.get();
		bucketDirectTransferSpeed = BUCKET_DIRECT_TRANSFER_SPEED.get();
		intakeScanRange = INTAKE_SCAN_RANGE.get();
		intakeScanInterval = INTAKE_SCAN_INTERVAL.get();
		wirelessMaxBindDistance = WIRELESS_MAX_BIND_DISTANCE.get();
		wirelessTransferSpeed = WIRELESS_TRANSFER_SPEED.get() * 1000;
		heatClearRadius = HEAT_CLEAR_RADIUS.get();
		fullSweepInterval = FULL_SWEEP_INTERVAL.get();
		hoseMaxLength = HOSE_MAX_LENGTH.get();
		hoseSnapMultiplier = HOSE_SNAP_MULTIPLIER.get();
		hoseTankCapacity = HOSE_TANK_CAPACITY.get();
		hosePumpScanInterval = HOSE_PUMP_SCAN_INTERVAL.get();
		hoseTopologyRefreshInterval = HOSE_TOPOLOGY_REFRESH_INTERVAL.get();
		hoseInputSmoothing = HOSE_INPUT_SMOOTHING.get().floatValue();
		hoseExternalInputPressureScale = HOSE_EXTERNAL_INPUT_PRESSURE_SCALE.get().floatValue();
		hoseFlowToPressureScale = HOSE_FLOW_TO_PRESSURE_SCALE.get().floatValue();
		hosePressureRecoveryPerTick = HOSE_PRESSURE_RECOVERY_PER_TICK.get().floatValue();
		hosePressureDropPerTick = HOSE_PRESSURE_DROP_PER_TICK.get().floatValue();
		hoseLowBufferTicksToLimit = HOSE_LOW_BUFFER_TICKS_TO_LIMIT.get();
		hoseExternalInputMemoryTicks = HOSE_EXTERNAL_INPUT_MEMORY_TICKS.get();
		hoseExternalInputOutputRange = HOSE_EXTERNAL_INPUT_OUTPUT_RANGE.get();
		highPressurePumpMultiplier = HIGH_PRESSURE_PUMP_MULTIPLIER.get().floatValue();
		nozzleSprayBuildupTicks = NOZZLE_SPRAY_BUILDUP_TICKS.get();
		mistSpreadRadius = MIST_SPREAD_RADIUS.get();
		mistTransitionStart = MIST_TRANSITION_START.get();
		serverProjectilesPerTick = SERVER_PROJECTILES_PER_TICK.get();
		nozzleIgnitionChance = NOZZLE_IGNITION_CHANCE.get();
		nozzleThrustEnabled = NOZZLE_THRUST_ENABLED.get();
		nozzleThrustMultiplier = NOZZLE_THRUST_MULTIPLIER.get();
		cdgIgnitionEnabled = CDG_IGNITION_ENABLED.get();
		flamePropagationRadius = FLAME_PROPAGATION_RADIUS.get();

		// Physics clamping: cap MaxDistance at floor(speed / (1 - friction))
		coneNozzleMaxDistance = clampToPhysics(coneNozzleMaxDistance, coneNozzleSpeed, coneNozzleFriction, "ConeNozzle");
		flatNozzleMaxDistance = clampToPhysics(flatNozzleMaxDistance, flatNozzleSpeed, flatNozzleFriction, "FlatNozzle");
	}

	private static int clampToPhysics(int configured, double speed, double friction, String nozzleName) {
		int physicsMax = (int) Math.floor(speed / (1.0 - friction));
		if (configured > physicsMax) {
			LOGGER.warn("{} MaxDistance {} exceeds physical limit {} (speed={}, friction={}). Clamping to {}.",
				nozzleName, configured, physicsMax, speed, friction, physicsMax);
			return physicsMax;
		}
		return configured;
	}
}
