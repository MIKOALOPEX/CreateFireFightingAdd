package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseConnectionAccess;
import com.mikoalopex.createfirefightingadd.content.fluids.SafeFluidStacks;
import com.mikoalopex.createfirefightingadd.content.kinetics.pump.FireFightingPumpPressureProvider;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_ITEM;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class FireHoseBlockEntity extends SmartBlockEntity implements FireHoseConnectionAccess {

    private static final double TIME_TO_SNAP = 3.0;
    private static final String TAG_CONTROLLER = "Controller";
    private static final String TAG_BLACK_HOSE = "BlackHose";
    private static final String TAG_ENDPOINT_ID = "EndpointId";
    private static final String TAG_PARTNER_POS = "PartnerPos";
    private static final String TAG_PARTNER_SUB_LEVEL = "PartnerSubLevel";
    private static final String TAG_PARTNER_ENDPOINT_ID = "PartnerEndpointId";
    private static final String TAG_PARTNER_MOVING = "PartnerMoving";
    private static final int MOVING_PARTNER_LEASE_TICKS = 8;

    // Rendering
    protected LerpedFloat renderLength = LerpedFloat.linear();

    // Partner
    protected boolean isController;
    protected boolean assembling;
    private UUID endpointId = UUID.randomUUID();
    protected BlockPos partnerPos;
    @Nullable
    private UUID partnerSubLevel;
    @Nullable
    private UUID partnerEndpointId;
    private boolean partnerMoving;
    @Nullable
    private UUID movingPartnerRuntimeId;
    private long movingPartnerLeaseExpiresAt;
    private boolean blackHose;
    private float ticksWithoutPartner;
    private double snappingTime;

    private SmartFluidTankBehaviour tank;

    // Pump detection
    static final int PUMP_SIDE_NONE = 0;
    static final int PUMP_SIDE_BACK = 1;
    static final int PUMP_SIDE_PARTNER = 2;
    private static final int SOURCE_NONE = 0;
    private static final int SOURCE_REAL_PUMP = 1;
    private static final int SOURCE_VIRTUAL_HOSE = 2;
    private static final int SOURCE_EXTERNAL_FILL = 3;
    private static final float PRESSURE_DIRECTION_EPSILON = 0.001f;

    private HoseFluidTransferBehaviour hoseTransferBehaviour;
    private float pumpSpeed;
    private int pumpSourceKind = SOURCE_NONE;
    private boolean wasTankEmpty = true;
    private int pumpDistance = Integer.MAX_VALUE;
    private int pumpRange;
    int pumpSide = PUMP_SIDE_NONE;
    boolean pumpPushesTowardHose;

    // Pressure distribution cache
    // Cached graphs are rebuilt when topology or source pressure changes, without wiping pressure every tick.
    private Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> cachedPipeGraph;
    private Set<BlockFace> cachedTargets;
    private Map<Integer, Set<BlockFace>> cachedValidFaces;
    private boolean cacheValid;
    private int lastPumpSide = PUMP_SIDE_NONE;
    private int lastPumpDistance = Integer.MAX_VALUE;
    private int lastPumpRange;
    private float lastPumpSpeed;
    private int lastPumpSourceKind = SOURCE_NONE;
    private boolean lastPull;
    private Direction lastBack;
    private int lastPartnerDist = Integer.MAX_VALUE;
    private boolean lastDriveBackPressure;

    private int pumpScanTimer;
    private int topologyRefreshTimer;

    private float effectivePumpSpeed;
    private float observedInputRate;
    private int inputThisTick;
    private int lowBufferTicks;
    private int noInputTicks;
    private int externalInputThisTick;
    private int externalInputMemoryTicks;
    private float externalInputRate;
    private boolean externalInputPushesTowardHose = true;

    public FireHoseBlockEntity(BlockPos pos, BlockState state) {
        this(com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_BE.get(), pos, state);
    }

    public FireHoseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.renderLength.startWithValue(0);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = SmartFluidTankBehaviour.single(this, Config.hoseTankCapacity);
        tank.allowInsertion();
        tank.whenFluidUpdates(this::notifyUpdate);
        behaviours.add(tank);
        hoseTransferBehaviour = new HoseFluidTransferBehaviour(this);
        behaviours.add(hoseTransferBehaviour);
    }

    // Geometry

    public Vector3d getCenter() {
        BlockState state = getBlockState();
        Direction facing = state.getValue(FireHoseBlock.FACING);
        Vec3i facingVec = facing.getNormal();
        double scale = 0.5 - 4.0 / 16.0;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        return new Vector3d(
                center.x - facingVec.getX() * scale,
                center.y - facingVec.getY() * scale,
                center.z - facingVec.getZ() * scale);
    }

    public double getRenderLength(float pt) {
        return this.renderLength.getValue(pt);
    }

    // Tick

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        if (level.isClientSide) {
            renderLength.chase(getCurrentLength(), 0.5, LerpedFloat.Chaser.EXP);
            renderLength.tickChaser();
            return;
        }

        if (partnerPos == null) {
            updateDisconnectedEndpoint();
            return;
        }

        if (level.isLoaded(partnerPos)) {
            if (getPairedHose() == null && !FireHoseMovingEndpoints.isMovingPartner(level, partnerPos, this)) {
                if (isWaitingForMovingPartner()) {
                    ticksWithoutPartner = 0;
                } else {
                    ticksWithoutPartner++;
                    if (ticksWithoutPartner > 20) {
                        FireHoseConnections.disconnect(this);
                        return;
                    }
                }
            } else {
                ticksWithoutPartner = 0;
            }
        }

        if (isWaitingForMovingPartner()) {
            snappingTime = 0;
            return;
        }

        double distance = getHoseDistance();
        double snapDist = Config.hoseMaxLength * Config.hoseSnapMultiplier;
        if (distance > snapDist) {
            snappingTime += 1.0 / 20.0;
        } else {
            snappingTime = 0;
        }
        if (snappingTime > TIME_TO_SNAP) {
            FireHoseConnections.disconnect(this);
            return;
        }

        Direction back = getBlockState().getValue(FireHoseBlock.FACING).getOpposite();
        FireHoseBlockEntity partner = getPairedHose();
        updateExternalInputSource();
        pumpScanTimer--;
        if (pumpScanTimer <= 0) {
            scanForPumpAndLock(back, partner);
            pumpScanTimer = Config.hosePumpScanInterval;
        }
        int inputThisCycle = consumeRecordedInput();
        refillFromSourceEndpoint(back, partner);
        inputThisCycle += consumeRecordedInput();
        updateEffectivePumpSpeed(inputThisCycle);

        SmartFluidTankBehaviour shared = getSharedTank();
        IFluidHandler sharedHandler = shared.getPrimaryHandler();
        boolean tankEmpty = sharedHandler.getFluidInTank(0).isEmpty();

        if (!tankEmpty) {
            if (wasTankEmpty) {
                wasTankEmpty = false;
                BlockPos pipePos = worldPosition.relative(back);
                FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
            }
        } else {
            if (!wasTankEmpty) {
                BlockPos pipePos = worldPosition.relative(back);
                FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
            }
            wasTankEmpty = true;
        }

        boolean apply = shouldApplyPressure();
        boolean driveBackPressure = shouldDriveBackPressure();
        refreshDrivenTopology(back, driveBackPressure);
        logPressureState(back, partner, apply);
        if (driveBackPressure) {
            boolean pull = isPulling();
            int partnerDist = partner != null ? partner.pumpDistance : Integer.MAX_VALUE;

            if (!cacheValid
                || pumpSide != lastPumpSide
                || pumpDistance != lastPumpDistance
                || pumpRange != lastPumpRange
                || pumpSpeed != lastPumpSpeed
                || pumpSourceKind != lastPumpSourceKind
                || pull != lastPull
                || back != lastBack
                || partnerDist != lastPartnerDist) {

                cachedPipeGraph = new HashMap<>();
                cachedTargets = new HashSet<>();
                cachedValidFaces = new HashMap<>();
                buildPipeGraph(back, getActivePumpSpeed(), pull, getDrivenOutputRange(partnerDist),
                    cachedPipeGraph, cachedTargets, cachedValidFaces);

                lastPumpSide = pumpSide;
                lastPumpDistance = pumpDistance;
                lastPumpRange = pumpRange;
                lastPumpSpeed = pumpSpeed;
                lastPumpSourceKind = pumpSourceKind;
                lastPull = pull;
                lastBack = back;
                lastPartnerDist = partnerDist;
                cacheValid = true;
            }

            applyCachedPressure(getActivePumpSpeed());
        } else if (!apply) {
            hoseTransferBehaviour.wipePressure();
            cacheValid = false;
        } else {
            if (lastDriveBackPressure)
                hoseTransferBehaviour.wipePressure();
            cacheValid = false;
        }
        lastDriveBackPressure = driveBackPressure;

        tryContainerInteraction(back);
    }

    private void refreshDrivenTopology(Direction back, boolean driveBackPressure) {
        if (!driveBackPressure) {
            topologyRefreshTimer = 0;
            return;
        }

        // Remote pipe edits do not notify hose directly, so the virtual output graph is refreshed periodically.
        topologyRefreshTimer--;
        if (topologyRefreshTimer > 0)
            return;
        topologyRefreshTimer = Config.hoseTopologyRefreshInterval;
        cacheValid = false;
    }

    private void updateDisconnectedEndpoint() {
        ticksWithoutPartner = 0;
        snappingTime = 0;
        pumpSide = PUMP_SIDE_NONE;
        pumpDistance = Integer.MAX_VALUE;
        pumpPushesTowardHose = false;
        pumpSpeed = 0;
        pumpSourceKind = SOURCE_NONE;
        pumpRange = 0;
        lastDriveBackPressure = false;
        invalidateFluidTopology(false);
    }

    // Pump detection
    // The side connected to a real Create pump remains open; only the opposite hose end drives virtual pressure.

    private void scanForPumpAndLock(Direction back, @Nullable FireHoseBlockEntity partner) {
        int oldPumpSide = pumpSide;
        int oldPumpDistance = pumpDistance;
        boolean oldPumpPushesTowardHose = pumpPushesTowardHose;
        float oldPumpSpeed = pumpSpeed;
        int oldPumpSourceKind = pumpSourceKind;
        int oldPumpRange = pumpRange;

        PumpScanResult backInfo = findPumpInfo(back);
        int backDist = backInfo.distance();
        boolean backPushesToward = backInfo.pushesTowardRequester();
        float backSpeed = backInfo.speed();
        int backSourceKind = backInfo.sourceKind();
        int backRange = backInfo.pumpRange();

        int partnerDist = Integer.MAX_VALUE;
        boolean partnerPushesToward = false;
        float partnerSpeed = 0;
        int partnerSourceKind = SOURCE_NONE;
        int partnerRange = 0;
        if (partner != null) {
            Direction partnerBack = partner.getBlockState()
                .getValue(FireHoseBlock.FACING).getOpposite();
            PumpScanResult partnerInfo = partner.findPumpInfo(partnerBack);
            partnerDist = partnerInfo.distance();
            partnerPushesToward = partnerInfo.pushesTowardRequester();
            partnerSpeed = partnerInfo.speed();
            partnerSourceKind = partnerInfo.sourceKind();
            partnerRange = partnerInfo.pumpRange();
        }

        if (backDist == Integer.MAX_VALUE && hasRecentExternalInput()) {
            backDist = 1;
            backPushesToward = externalInputPushesTowardHose;
            backSpeed = getExternalInputPressure();
            backSourceKind = SOURCE_EXTERNAL_FILL;
            backRange = Config.hoseExternalInputOutputRange;
        }

        if (partnerDist == Integer.MAX_VALUE && partner != null && partner.hasRecentExternalInput()) {
            partnerDist = 1;
            partnerPushesToward = partner.externalInputPushesTowardHose;
            partnerSpeed = partner.getExternalInputPressure();
            partnerSourceKind = SOURCE_EXTERNAL_FILL;
            partnerRange = Config.hoseExternalInputOutputRange;
        }

        if (backDist < Integer.MAX_VALUE) {
            pumpSide = PUMP_SIDE_BACK;
            pumpDistance = backDist;
            pumpPushesTowardHose = backPushesToward;
            pumpSpeed = backSpeed;
            pumpSourceKind = backSourceKind;
            pumpRange = backRange;
        } else if (partnerDist < Integer.MAX_VALUE) {
            pumpSide = PUMP_SIDE_PARTNER;
            pumpDistance = partnerDist;
            pumpPushesTowardHose = partnerPushesToward;
            pumpSpeed = partnerSpeed;
            pumpSourceKind = partnerSourceKind;
            pumpRange = partnerRange;
        } else {
            pumpSide = PUMP_SIDE_NONE;
            pumpDistance = Integer.MAX_VALUE;
            pumpPushesTowardHose = false;
            pumpSpeed = 0;
            pumpSourceKind = SOURCE_NONE;
            pumpRange = 0;
        }

        if (oldPumpSide != pumpSide
            || oldPumpDistance != pumpDistance
            || oldPumpPushesTowardHose != pumpPushesTowardHose
            || oldPumpSpeed != pumpSpeed
            || oldPumpSourceKind != pumpSourceKind
            || oldPumpRange != pumpRange) {
            markPressureDirty();
            if (partner != null)
                partner.markPressureDirty();
        }

        FireHoseDebugLog.logRaw("scan pos={} ctrl={} back={} backDist={} backRange={} backPush={} partner={} partnerDist={} partnerRange={} partnerPush={} -> side={} dist={} range={} push={} pull={} rule={}",
            worldPosition.toShortString(),
            isController,
            back,
            distanceLabel(backDist),
            distanceLabel(backRange),
            backPushesToward,
            partner != null ? partner.getBlockPos().toShortString() : "null",
            distanceLabel(partnerDist),
            distanceLabel(partnerRange),
            partnerPushesToward,
            pumpSideLabel(pumpSide),
            distanceLabel(pumpDistance),
            distanceLabel(pumpRange),
            pumpPushesTowardHose,
            isPulling(),
            backDist < Integer.MAX_VALUE ? "LOCAL_PUMP" : partnerDist < Integer.MAX_VALUE ? "PARTNER_PUMP" : "NO_PUMP");
    }

    private void markPressureDirty() {
        cacheValid = false;
        topologyRefreshTimer = 0;
    }

    private void updateExternalInputSource() {
        if (externalInputThisTick > 0) {
            if (externalInputRate <= 0)
                externalInputRate = externalInputThisTick;
            else
                externalInputRate = externalInputRate * (1f - Config.hoseInputSmoothing)
                    + externalInputThisTick * Config.hoseInputSmoothing;
            externalInputMemoryTicks = Config.hoseExternalInputMemoryTicks;
            externalInputThisTick = 0;
            return;
        }

        if (externalInputMemoryTicks > 0)
            externalInputMemoryTicks--;
        else
            externalInputRate *= 0.85f;
    }

    private boolean hasRecentExternalInput() {
        return externalInputMemoryTicks > 0 && externalInputRate > 0;
    }

    private float getExternalInputPressure() {
        return clampPumpSpeed(externalInputRate * Config.hoseExternalInputPressureScale);
    }

    private int consumeRecordedInput() {
        int input = inputThisTick;
        inputThisTick = 0;
        return input;
    }

    private void recordSharedInput(int amount) {
        if (amount <= 0)
            return;
        inputThisTick += amount;
        FireHoseBlockEntity partner = getPairedHose();
        if (partner != null)
            partner.inputThisTick += amount;
    }

    private void recordExternalInput(int amount, boolean pushesTowardHose) {
        if (amount <= 0)
            return;
        if (amount >= externalInputThisTick)
            externalInputPushesTowardHose = pushesTowardHose;
        externalInputThisTick += amount;
    }

    private void updateEffectivePumpSpeed(int inputAmount) {
        float sourcePressure = pumpSpeed;
        if (sourcePressure <= 0) {
            effectivePumpSpeed = 0;
            observedInputRate = 0;
            lowBufferTicks = 0;
            noInputTicks = 0;
            return;
        }

        if (pumpSourceKind != SOURCE_EXTERNAL_FILL) {
            effectivePumpSpeed = sourcePressure;
            observedInputRate = 0;
            lowBufferTicks = 0;
            noInputTicks = 0;
            return;
        }

        observedInputRate = observedInputRate * (1f - Config.hoseInputSmoothing) + inputAmount * Config.hoseInputSmoothing;
        if (inputAmount <= 0)
            noInputTicks++;
        else
            noInputTicks = 0;

        int tankAmount = getSharedTankRawAmount();
        int lowThreshold = Math.max(16, (int) (sourcePressure / 8f));
        if (tankAmount <= lowThreshold)
            lowBufferTicks++;
        else
            lowBufferTicks = Math.max(0, lowBufferTicks - 2);

        if (effectivePumpSpeed <= 0)
            effectivePumpSpeed = sourcePressure;
        if (effectivePumpSpeed > sourcePressure)
            effectivePumpSpeed = sourcePressure;

        float supportedPressure = observedInputRate * Config.hoseFlowToPressureScale;
        boolean supplyLimited = lowBufferTicks >= Config.hoseLowBufferTicksToLimit
            && supportedPressure < sourcePressure * 0.75f;

        float target = sourcePressure;
        if (supplyLimited) {
            target = Math.max(1f, supportedPressure * 1.25f);
            if (noInputTicks >= 5)
                target = Math.min(target, sourcePressure * 0.5f);
            target = Math.min(target, sourcePressure);
        }

        if (effectivePumpSpeed < target)
            effectivePumpSpeed = Math.min(target, effectivePumpSpeed + Config.hosePressureRecoveryPerTick);
        else if (effectivePumpSpeed > target)
            effectivePumpSpeed = Math.max(target, effectivePumpSpeed - Config.hosePressureDropPerTick);

        FireHoseDebugLog.logRawEvery("flow-limit:" + worldPosition.asLong(), 20,
            "flow limit pos={} source={} effective={} input={} avgInput={} tank={} lowTicks={} noInputTicks={} limited={}",
            worldPosition.toShortString(),
            sourcePressure,
            effectivePumpSpeed,
            inputAmount,
            observedInputRate,
            tankAmount,
            lowBufferTicks,
            noInputTicks,
            supplyLimited);
    }

    private void refillFromSourceEndpoint(Direction back, @Nullable FireHoseBlockEntity partner) {
        if (pumpSide == PUMP_SIDE_BACK) {
            pullFromSourceEndpoint(this, back);
            return;
        }
        if (pumpSide == PUMP_SIDE_PARTNER && partner != null)
            pullFromSourceEndpoint(partner, partner.getBack());
    }

    private void pullFromSourceEndpoint(FireHoseBlockEntity endpoint, Direction sourceBack) {
        if (level == null || !endpoint.isPulling())
            return;

        IFluidHandler tankHandler = getSharedTank().getPrimaryHandler();
        FluidStack current = tankHandler.getFluidInTank(0);
        int space = Config.hoseTankCapacity - current.getAmount();
        int rate = (int) getActivePumpSpeed();
        int amount = Math.min(space, rate);
        if (amount <= 0)
            return;

        BlockPos sourcePos = endpoint.getBlockPos().relative(sourceBack);
        IFluidHandler sourceHandler = level.getCapability(
            Capabilities.FluidHandler.BLOCK, sourcePos, sourceBack.getOpposite());
        if (sourceHandler != null) {
            FluidStack simulated = sourceHandler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                FireHoseDebugLog.logRawEvery("refill-empty-handler:" + endpoint.getBlockPos().asLong(), 5,
                    "refill empty handler endpoint={} source={} tank={} space={} rate={} amount={}",
                    endpoint.getBlockPos().toShortString(),
                    sourcePos.toShortString(),
                    current.getAmount(),
                    space,
                    rate,
                    amount);
                return;
            }
            FluidStack toFill = simulated.copy();
            toFill.setAmount(Math.min(amount, simulated.getAmount()));
            int filled = tankHandler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                sourceHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                recordSharedInput(filled);
                FireHoseDebugLog.logRawEvery("refill-handler:" + endpoint.getBlockPos().asLong(), 5,
                    "refill handler endpoint={} source={} filled={} tankBefore={} tankAfter={} space={} rate={} type={}",
                    endpoint.getBlockPos().toShortString(),
                    sourcePos.toShortString(),
                    filled,
                    current.getAmount(),
                    getSharedTankRawAmount(),
                    space,
                    rate,
                    toFill.getHoverName().getString());
            }
            return;
        }

        FluidTransportBehaviour sourcePipe = FluidPropagator.getPipe(level, sourcePos);
        if (sourcePipe == null) {
            FireHoseDebugLog.logRawEvery("refill-no-source:" + endpoint.getBlockPos().asLong(), 10,
                "refill no source endpoint={} source={} tank={} space={} rate={}",
                endpoint.getBlockPos().toShortString(),
                sourcePos.toShortString(),
                current.getAmount(),
                space,
                rate);
            return;
        }
        FluidStack provided = sourcePipe.getProvidedOutwardFluid(sourceBack.getOpposite());
        if (provided.isEmpty()) {
            FireHoseDebugLog.logRawEvery("refill-empty-pipe:" + endpoint.getBlockPos().asLong(), 5,
                "refill empty pipe endpoint={} source={} tank={} space={} rate={} side={}",
                endpoint.getBlockPos().toShortString(),
                sourcePos.toShortString(),
                current.getAmount(),
                space,
                rate,
                sourceBack.getOpposite());
            return;
        }
        FluidStack toFill = provided.copy();
        toFill.setAmount(Math.min(amount, provided.getAmount()));
        int filled = tankHandler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
        recordSharedInput(filled);
        FireHoseDebugLog.logRawEvery("refill-pipe:" + endpoint.getBlockPos().asLong(), 5,
            "refill pipe endpoint={} source={} filled={} provided={} tankBefore={} tankAfter={} space={} rate={} side={} type={}",
            endpoint.getBlockPos().toShortString(),
            sourcePos.toShortString(),
            filled,
            provided.getAmount(),
            current.getAmount(),
            getSharedTankRawAmount(),
            space,
            rate,
            sourceBack.getOpposite(),
            toFill.getHoverName().getString());
    }

    private PumpScanResult findPumpInfo(Direction back) {
        if (level == null) return PumpScanResult.none();
        BlockPos startPos = worldPosition.relative(back);
        PumpScanResult adjacentHose = findVirtualHoseInfo(startPos, worldPosition, 1);
        if (adjacentHose.distance() < Integer.MAX_VALUE)
            return adjacentHose;

        FluidTransportBehaviour startPipe = FluidPropagator.getPipe(level, startPos);
        if (startPipe == null) return PumpScanResult.none();

        PipePressureInfo requesterPressure = getRequesterSidePressure(startPos, back.getOpposite());
        PumpScanResult bestPump = PumpScanResult.none();

        Queue<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        frontier.add(Pair.of(1, startPos));
        parents.put(startPos, worldPosition);
        int maxSearch = getPumpSearchRange();

        while (!frontier.isEmpty()) {
            Pair<Integer, BlockPos> entry = frontier.poll();
            int dist = entry.getFirst();
            BlockPos pos = entry.getSecond();
            if (!level.isLoaded(pos) || visited.contains(pos)) continue;
            visited.add(pos);

            BlockEntity currentBE = level.getBlockEntity(pos);
            BlockPos parent = parents.getOrDefault(pos, worldPosition);
            Direction towardParent = Direction.getNearest(
                parent.getX() - pos.getX(),
                parent.getY() - pos.getY(),
                parent.getZ() - pos.getZ());

            PumpScanResult virtualHose = findVirtualHoseInfo(pos, parent, dist);
            if (virtualHose.distance() < Integer.MAX_VALUE)
                return virtualHose;

            if (currentBE instanceof PumpBlockEntity pumpBe) {
                PumpScanResult candidate = createPumpScanResult(pumpBe, dist, parent, towardParent, requesterPressure);
                bestPump = choosePumpCandidate(bestPump, candidate, requesterPressure);
                continue;
            }

            if (dist >= maxSearch) continue;

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
            if (pipe == null) continue;
            for (Direction face : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
                BlockPos next = pos.relative(face);
                if (!level.isLoaded(next) || visited.contains(next)) continue;
                frontier.add(Pair.of(dist + 1, next));
                if (!parents.containsKey(next))
                    parents.put(next, pos);
            }
        }
        return bestPump;
    }

    private PumpScanResult findVirtualHoseInfo(BlockPos pos, BlockPos parent, int dist) {
        if (level == null)
            return PumpScanResult.none();
        BlockEntity currentBE = level.getBlockEntity(pos);
        Direction towardParent = Direction.getNearest(
            parent.getX() - pos.getX(),
            parent.getY() - pos.getY(),
            parent.getZ() - pos.getZ());

        if (!(currentBE instanceof FireHoseBlockEntity sourceHose)
            || sourceHose == this
            || sourceHose.getPairedHose() == this
            || !sourceHose.canRelayVirtualPressure()
            || sourceHose.getBack() != towardParent)
            return PumpScanResult.none();

        boolean sourcePulling = sourceHose.isPulling();
        FireHoseDebugLog.logRaw("scan virtual hose {} requester={} source={} dist={} sourceBack={} towardRequester={} sourceSide={} sourcePull={} sourceApply={}",
            sourcePulling ? "sink" : "source",
            worldPosition.toShortString(),
            sourceHose.getBlockPos().toShortString(),
            dist,
            sourceHose.getBack(),
            towardParent,
            pumpSideLabel(sourceHose.pumpSide),
            sourcePulling,
            sourceHose.shouldApplyPressure());
        float speed = sourceHose.getActivePumpSpeed();
        if (speed <= 0)
            return PumpScanResult.none();
        return PumpScanResult.found(dist, !sourcePulling, speed, sourceHose.getRelayPumpRange(), sourceHose.getRelaySourceKind());
    }

    private int getRelaySourceKind() {
        return pumpSourceKind == SOURCE_EXTERNAL_FILL ? SOURCE_EXTERNAL_FILL : SOURCE_VIRTUAL_HOSE;
    }

    private int getRelayPumpRange() {
        return pumpSourceKind == SOURCE_EXTERNAL_FILL ? Config.hoseExternalInputOutputRange : pumpRange;
    }

    private static float clampPumpSpeed(float speed) {
        if (speed <= 0)
            return 0;
        return speed;
    }

    private PumpScanResult createPumpScanResult(PumpBlockEntity pump, int distance, BlockPos parentPipePos,
                                                Direction towardRequester, PipePressureInfo requesterPressure) {
        PipePressureInfo sourcePressure = getSourceSidePressure(parentPipePos, towardRequester.getOpposite());
        boolean pushesTowardRequester = sourcePressure.directionKnown()
            ? sourcePressure.pushesTowardRequester()
            : requesterPressure.directionKnown()
                ? requesterPressure.pushesTowardRequester()
                : getPumpFacingPushesTowardRequester(pump, towardRequester);

        float speed = 0;
        if (requesterPressure.directionKnown()
            && requesterPressure.pushesTowardRequester() == pushesTowardRequester)
            speed = requesterPressure.speed();
        if (speed <= 0)
            speed = sourcePressure.speed();
        if (speed <= 0)
            speed = getPumpPressureFallback(pump);
        if (speed <= 0)
            return PumpScanResult.none();

        return PumpScanResult.found(distance, pushesTowardRequester, speed, getPumpRangeFallback(pump), SOURCE_REAL_PUMP);
    }

    private PumpScanResult choosePumpCandidate(PumpScanResult current, PumpScanResult candidate,
                                               PipePressureInfo requesterPressure) {
        if (candidate.distance() == Integer.MAX_VALUE)
            return current;
        if (current.distance() == Integer.MAX_VALUE)
            return candidate;

        if (requesterPressure.directionKnown()) {
            boolean candidateAligned = candidate.pushesTowardRequester() == requesterPressure.pushesTowardRequester();
            boolean currentAligned = current.pushesTowardRequester() == requesterPressure.pushesTowardRequester();
            if (candidateAligned != currentAligned)
                return candidateAligned ? candidate : current;
        }

        if (candidate.distance() != current.distance())
            return candidate.distance() < current.distance() ? candidate : current;
        if (candidate.speed() != current.speed())
            return candidate.speed() > current.speed() ? candidate : current;
        return current;
    }

    private PipePressureInfo getRequesterSidePressure(BlockPos pipePos, Direction pipeSide) {
        PipePressureInfo raw = getPipePressureInfo(pipePos, pipeSide);
        if (!raw.hasPressure())
            return raw;
        return raw.withDirection(raw.outwardDominant());
    }

    private PipePressureInfo getSourceSidePressure(BlockPos pipePos, Direction pipeSide) {
        PipePressureInfo raw = getPipePressureInfo(pipePos, pipeSide);
        if (!raw.hasPressure())
            return raw;
        return raw.withDirection(raw.inboundDominant());
    }

    private PipePressureInfo getPipePressureInfo(BlockPos pipePos, Direction pipeSide) {
        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pipePos);
        if (pipe == null || pipe.interfaces == null)
            return PipePressureInfo.none();
        PipeConnection connection = pipe.interfaces.get(pipeSide);
        if (connection == null || connection.getPressure() == null)
            return PipePressureInfo.none();
        Couple<Float> pressure = connection.getPressure();
        return PipePressureInfo.of(pressure.getFirst(), pressure.getSecond());
    }

    private static boolean getPumpFacingPushesTowardRequester(PumpBlockEntity pump, Direction towardRequester) {
        Direction effectiveFront = getEffectivePumpFront(pump);
        return effectiveFront == towardRequester;
    }

    private static Direction getEffectivePumpFront(PumpBlockEntity pump) {
        try {
            java.lang.reflect.Method method = pump.getClass().getMethod("getEffectiveFront");
            Object value = method.invoke(pump);
            if (value instanceof Direction direction)
                return direction;
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }

        BlockState pumpState = pump.getBlockState();
        if (pumpState.hasProperty(PumpBlock.FACING))
            return pumpState.getValue(PumpBlock.FACING);
        return Direction.NORTH;
    }

    private static float getPumpPressureFallback(PumpBlockEntity pump) {
        if (pump instanceof FireFightingPumpPressureProvider provider)
            return clampPumpSpeed(provider.CreateFireFightingAdd$getFluidPressure());
        float pressure = Math.abs(pump.getSpeed());
        return clampPumpSpeed(pressure);
    }

    private static int getPumpRangeFallback(PumpBlockEntity pump) {
        if (pump instanceof FireFightingPumpPressureProvider provider)
            return Math.max(1, provider.CreateFireFightingAdd$getPumpRange());
        return Math.max(1, FluidPropagator.getPumpRange());
    }

    private static int getPumpSearchRange() {
        int configuredRange = Math.round(FluidPropagator.getPumpRange() * Config.highPressurePumpMultiplier);
        return Math.max(Config.hoseMaxLength, configuredRange);
    }

    private record PipePressureInfo(float inbound, float outward, boolean directionKnown, boolean pushesTowardRequester) {
        private static PipePressureInfo none() {
            return new PipePressureInfo(0, 0, false, false);
        }

        private static PipePressureInfo of(float inbound, float outward) {
            return new PipePressureInfo(clampPumpSpeed(inbound), clampPumpSpeed(outward), false, false);
        }

        private boolean hasPressure() {
            return inbound > 0 || outward > 0;
        }

        private float speed() {
            return clampPumpSpeed(Math.max(inbound, outward));
        }

        private boolean inboundDominant() {
            return inbound > outward + PRESSURE_DIRECTION_EPSILON;
        }

        private boolean outwardDominant() {
            return outward > inbound + PRESSURE_DIRECTION_EPSILON;
        }

        private PipePressureInfo withDirection(boolean pushesTowardRequester) {
            if (!inboundDominant() && !outwardDominant())
                return new PipePressureInfo(inbound, outward, false, false);
            return new PipePressureInfo(inbound, outward, true, pushesTowardRequester);
        }
    }

    private record PumpScanResult(int distance, boolean pushesTowardRequester, float speed, int pumpRange, int sourceKind) {
        private static PumpScanResult none() {
            return new PumpScanResult(Integer.MAX_VALUE, false, 0, 0, SOURCE_NONE);
        }

        private static PumpScanResult found(int distance, boolean pushesTowardRequester, float speed, int pumpRange, int sourceKind) {
            return new PumpScanResult(distance, pushesTowardRequester, clampPumpSpeed(speed), Math.max(1, pumpRange), sourceKind);
        }
    }

    // Pressure distribution

    private int getDrivenOutputRange(int partnerDist) {
        int range = pumpSourceKind == SOURCE_EXTERNAL_FILL ? Config.hoseExternalInputOutputRange : pumpRange;
        if (range <= 0)
            range = Config.hoseMaxLength;
        if (partnerDist < Integer.MAX_VALUE)
            return Math.max(0, range - partnerDist);
        return range;
    }

    private void buildPipeGraph(Direction side, float pumpSpeed, boolean pull, int maxDistance,
            Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
            Set<BlockFace> targets,
            Map<Integer, Set<BlockFace>> validFaces) {
        if (level == null || pumpSpeed == 0)
            return;

        if (maxDistance <= 0)
            return;

        BlockFace start = new BlockFace(worldPosition, side);

        if (!hasEndpoint(level, start, pull)) {

            pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>()))
                .getSecond()
                .put(side, pull);
            pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
                .getSecond()
                .put(side.getOpposite(), !pull);

            Queue<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            frontier.add(Pair.of(1, start.getConnectedPos()));

            while (!frontier.isEmpty()) {
                Pair<Integer, BlockPos> entry = frontier.poll();
                int distance = entry.getFirst();
                BlockPos currentPos = entry.getSecond();

                if (!level.isLoaded(currentPos))
                    continue;
                if (visited.contains(currentPos))
                    continue;
                visited.add(currentPos);
                BlockState currentState = level.getBlockState(currentPos);
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
                if (pipe == null)
                    continue;

                for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
                    BlockFace blockFace = new BlockFace(currentPos, face);
                    BlockPos connectedPos = blockFace.getConnectedPos();

                    if (!level.isLoaded(connectedPos))
                        continue;
                    if (blockFace.isEquivalent(start))
                        continue;
                    if (hasEndpoint(level, blockFace, pull)) {
                        pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                            .getSecond()
                            .put(face, pull);
                        targets.add(blockFace);
                        continue;
                    }

                    FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
                    if (pipeBehaviour == null)
                        continue;
                    if (level.getBlockEntity(connectedPos) instanceof PumpBlockEntity)
                        continue;
                    if (visited.contains(connectedPos))
                        continue;
                    if (distance + 1 >= maxDistance) {
                        pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                            .getSecond()
                            .put(face, pull);
                        targets.add(blockFace);
                        continue;
                    }

                    pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
                        .getSecond()
                        .put(face, pull);
                    pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
                        .getSecond()
                        .put(face.getOpposite(), !pull);
                    frontier.add(Pair.of(distance + 1, connectedPos));
                }
            }
        }

        searchForEndpointRecursively(pipeGraph, targets, validFaces,
            new BlockFace(start.getPos(), start.getOppositeFace()), pull, new HashSet<>());
    }

    private void applyCachedPressure(float pumpSpeed) {
        if (level == null || cachedPipeGraph == null || cachedValidFaces == null)
            return;

        float pressure = pumpSpeed;
        for (Set<BlockFace> set : cachedValidFaces.values()) {
            int parallelBranches = Math.max(1, set.size() - 1);
            for (BlockFace face : set) {
                BlockPos pipePos = face.getPos();
                Direction pipeSide = face.getFace();

                if (pipePos.equals(worldPosition))
                    continue;

                Pair<Integer, Map<Direction, Boolean>> entry = cachedPipeGraph.get(pipePos);
                if (entry == null)
                    continue;
                boolean inbound = entry.getSecond().get(pipeSide);
                FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
                if (pipeBehaviour == null || pipeBehaviour.interfaces == null)
                    continue;

                PipeConnection connection = pipeBehaviour.interfaces.get(pipeSide);
                if (connection == null || connection.getPressure() == null)
                    continue;
                Couple<Float> cp = connection.getPressure();
                float val = pressure / parallelBranches;
                if (inbound) {
                    cp.setFirst(val);
                    cp.setSecond(0f);
                } else {
                    cp.setFirst(0f);
                    cp.setSecond(val);
                }
                FireHoseDebugLog.logRaw("graph pressure hose={} pipe={} side={} inbound={} value={} pressure=({}, {})",
                    worldPosition.toShortString(),
                    pipePos.toShortString(),
                    pipeSide,
                    inbound,
                    val,
                    cp.getFirst(),
                    cp.getSecond());
            }
        }
    }

    private boolean hasEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
        BlockPos connectedPos = blockFace.getConnectedPos();
        BlockState connectedState = world.getBlockState(connectedPos);
        BlockEntity blockEntity = world.getBlockEntity(connectedPos);
        Direction face = blockFace.getFace();
        Direction accessSide = face.getOpposite();

        if (blockEntity instanceof PumpBlockEntity pumpBE) {
            FluidTransportBehaviour pumpBehaviour = FluidPropagator.getPipe(world, connectedPos);
            if (pumpBehaviour != null
                && !pumpBehaviour.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
                return false;

            PipePressureInfo pressure = getSourceSidePressure(blockFace.getPos(), face);
            if (pressure.directionKnown()) {
                boolean pumpPullsFromPipe = !pressure.pushesTowardRequester();
                return pumpPullsFromPipe != pull;
            }

            boolean pumpFront = getEffectivePumpFront(pumpBE) == blockFace.getOppositeFace();
            return pumpBE.isPullingOnSide(pumpFront) != pull;
        }

        if (blockEntity instanceof FireHoseBlockEntity otherHose && otherHose != this) {
            FireHoseDebugLog.logRaw("graph endpoint firehose source={} target={} face={} access={} pull={} targetPull={} targetFluid={}",
                worldPosition.toShortString(),
                connectedPos.toShortString(),
                face,
                accessSide,
                pull,
                otherHose.isPulling(),
                otherHose.getSharedTankRawAmount());
            return true;
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
        if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
            return false;

        if (blockEntity != null) {
            Level beLevel = blockEntity.getLevel();
            if (beLevel != null) {
                IFluidHandler handler = beLevel.getCapability(Capabilities.FluidHandler.BLOCK, connectedPos, accessSide);
                if (handler == null)
                    handler = beLevel.getCapability(Capabilities.FluidHandler.BLOCK, connectedPos, null);
                if (handler != null)
                    return true;
            }
        }

        return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
    }


    private boolean searchForEndpointRecursively(Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
                                                  Set<BlockFace> targets, Map<Integer, Set<BlockFace>> validFaces,
                                                  BlockFace currentFace, boolean pull, Set<BlockPos> visited) {
        BlockPos currentPos = currentFace.getPos();
        if (!visited.add(currentPos) || !pipeGraph.containsKey(currentPos))
            return false;
        Pair<Integer, Map<Direction, Boolean>> pair = pipeGraph.get(currentPos);
        int distance = pair.getFirst();

        boolean atLeastOneBranchSuccessful = false;
        for (Direction nextFacing : Iterate.directions) {
            if (nextFacing == currentFace.getFace())
                continue;
            Map<Direction, Boolean> map = pair.getSecond();
            if (!map.containsKey(nextFacing))
                continue;

            BlockFace localTarget = new BlockFace(currentPos, nextFacing);
            if (targets.contains(localTarget)) {
                validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
                    .add(localTarget);
                atLeastOneBranchSuccessful = true;
                continue;
            }

            if (map.get(nextFacing) != pull)
                continue;
            if (!searchForEndpointRecursively(pipeGraph, targets, validFaces,
                new BlockFace(currentPos.relative(nextFacing), nextFacing.getOpposite()), pull, visited))
                continue;

            validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
                .add(localTarget);
            atLeastOneBranchSuccessful = true;
        }

        if (atLeastOneBranchSuccessful)
            validFaces.computeIfAbsent(distance, $ -> new HashSet<>())
                .add(currentFace);

        return atLeastOneBranchSuccessful;
    }

    // Lifecycle

    @Override
    public void remove() {
        BlockPos wasPartner = partnerPos;
        partnerPos = null;
        partnerSubLevel = null;
        if (!level.isClientSide && !assembling) {
            FireHoseMovingEndpoints.disconnectAttachedTo(level, worldPosition);
            if (wasPartner != null) {
                BlockEntity be = level.getBlockEntity(wasPartner);
                if (be instanceof FireHoseBlockEntity partnerBe && isConnectedTo(partnerBe))
                    partnerBe.clearFireHoseConnection();
                else
                    FireHoseMovingEndpoints.disconnect(level, wasPartner, worldPosition);
            }
            Block.popResource(level, worldPosition, new ItemStack(FIRE_HOSE_ITEM.get()));
        }
    }

    void clearFireHoseConnection() {
        partnerPos = null;
        partnerSubLevel = null;
        partnerEndpointId = null;
        partnerMoving = false;
        onFireHoseConnectionChanged();
    }

    void onFireHoseConnectionChanged() {
        movingPartnerRuntimeId = null;
        movingPartnerLeaseExpiresAt = 0;
        ticksWithoutPartner = 0;
        snappingTime = 0;
        markPressureDirty();
        if (partnerPos != null && partnerEndpointId != null)
            FireHoseConnectorBlockEntity.refreshAdjacentTo(this);
        notifyUpdate();
    }

    // Distance & rendering

    private double getCurrentLength() {
        if (partnerPos == null) return 0;
        Vec3 movingPartnerCenter = getMovingPartnerWorldCenter();
        if (movingPartnerCenter != null)
            return getWorldCenterVec().distanceTo(movingPartnerCenter);
        Vec3 center = Vec3.atCenterOf(worldPosition);
        Vec3 partnerCenter = SableStructureCompat.partnerCenterInOwnerSpace(this, partnerPos);
        return center.distanceTo(partnerCenter);
    }

    private double getHoseDistance() {
        if (partnerPos == null)
            return 0;
        Vec3 movingPartnerCenter = getMovingPartnerWorldCenter();
        if (movingPartnerCenter != null)
            return getWorldCenterVec().distanceTo(movingPartnerCenter);
        return SableStructureCompat.distance(level, worldPosition, partnerPos);
    }

    private boolean isWaitingForMovingPartner() {
        if (!partnerMoving || partnerPos == null || partnerEndpointId == null || level == null)
            return false;
        if (FireHoseMovingEndpoints.isMovingPartner(level, partnerPos, this))
            return false;
        if (!level.isLoaded(partnerPos))
            return true;
        return !(level.getBlockEntity(partnerPos) instanceof FireHoseBlockEntity);
    }

    // Capability
    //
    // Fire hose endpoints intentionally expose fluid access only through the two
    // directions defined by FireHoseBlock.FACING. This mapping has been verified
    // in the hose's supported runtime environments, including Create pipe
    // networks and the mod's own hose transfer logic. External integrations
    // should treat this block-state facing as the authoritative endpoint axis,
    // even when the rendered model or placement context appears to suggest a
    // different visual orientation.

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        Direction facing = getBlockState().getValue(FireHoseBlock.FACING);
        if (side == facing || side == facing.getOpposite())
            return new HoseFluidHandler(this, side);
        return null;
    }

    // Public accessors

    public void setPartnerPos(@Nullable BlockPos pos, @Nullable UUID subLevel) {
        this.partnerPos = pos;
        this.partnerSubLevel = pos == null ? null : subLevel;
        if (pos == null) {
            this.partnerEndpointId = null;
            this.partnerMoving = false;
        } else {
            this.partnerMoving = false;
        }
        onFireHoseConnectionChanged();
    }

    boolean acceptMovingPartner(BlockPos originPos, UUID endpointId, UUID runtimeId) {
        if (partnerPos == null || !partnerPos.equals(originPos))
            return false;
        if (partnerEndpointId == null || !partnerEndpointId.equals(endpointId))
            return false;
        long now = level == null ? 0 : level.getGameTime();
        if (movingPartnerRuntimeId == null) {
            partnerMoving = true;
            movingPartnerRuntimeId = runtimeId;
            movingPartnerLeaseExpiresAt = now + MOVING_PARTNER_LEASE_TICKS;
            return true;
        }
        if (movingPartnerRuntimeId.equals(runtimeId)) {
            partnerMoving = true;
            movingPartnerLeaseExpiresAt = now + MOVING_PARTNER_LEASE_TICKS;
            return true;
        }
        if (now > movingPartnerLeaseExpiresAt) {
            partnerMoving = true;
            movingPartnerRuntimeId = runtimeId;
            movingPartnerLeaseExpiresAt = now + MOVING_PARTNER_LEASE_TICKS;
            return true;
        }
        return false;
    }

    boolean isAcceptedMovingPartner(BlockPos originPos, UUID endpointId, UUID runtimeId) {
        return partnerPos != null
            && partnerPos.equals(originPos)
            && endpointId.equals(partnerEndpointId)
            && runtimeId.equals(movingPartnerRuntimeId);
    }

    void clearMovingPartnerRuntime(@Nullable UUID runtimeId) {
        if (runtimeId == null || runtimeId.equals(movingPartnerRuntimeId))
            movingPartnerRuntimeId = null;
    }

    @Nullable
    UUID getMovingPartnerRuntimeId() {
        return movingPartnerRuntimeId;
    }

    public void markStructureAssembling() {
        this.assembling = true;
    }

    public void updatePartnerEndpoint(BlockPos pos, @Nullable UUID subLevel) {
        FireHoseBlockEntity partner = getPairedHose();
        if (partner != null)
            partner.setPartnerPos(pos, subLevel);
    }

    public FluidStack getMountedFluidStack() {
        if (tank == null)
            return FluidStack.EMPTY;
        return SafeFluidStacks.copy(tank.getPrimaryHandler().getFluidInTank(0));
    }

    public void setMountedFluidStack(FluidStack stack) {
        if (tank == null)
            return;
        IFluidHandler handler = tank.getPrimaryHandler();
        handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        stack = SafeFluidStacks.copy(stack);
        if (!stack.isEmpty())
            handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        notifyUpdate();
    }

    IFluidHandler getSharedFluidHandlerForMovement() {
        return getSharedTank().getPrimaryHandler();
    }

    public Direction getFacingDirection() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FireHoseBlock))
            return Direction.NORTH;
        return state.getValue(FireHoseBlock.FACING);
    }

    public Vec3 getWorldCenterVec() {
        Vector3d center = getCenter();
        return SableStructureCompat.transformPositionToWorld(this, new Vec3(center.x(), center.y(), center.z()));
    }

    @Override
    @Nullable
    public BlockPos getFireHosePartnerPos() {
        return partnerPos;
    }

    @Override
    @Nullable
    public UUID getFireHosePartnerSubLevel() {
        return partnerSubLevel;
    }

    @Override
    public UUID getFireHoseEndpointId() {
        if (endpointId == null)
            endpointId = UUID.randomUUID();
        return endpointId;
    }

    public void setFireHoseEndpointId(UUID endpointId) {
        this.endpointId = endpointId == null ? UUID.randomUUID() : endpointId;
    }

    @Override
    @Nullable
    public UUID getFireHosePartnerEndpointId() {
        return partnerEndpointId;
    }

    @Override
    public boolean isFireHosePartnerMoving() {
        return partnerMoving;
    }

    @Override
    public boolean isFireHoseController() {
        return isController;
    }

    @Override
    public boolean isFireHoseBlack() {
        return blackHose;
    }

    @Override
    public void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
            @Nullable UUID partnerSubLevel, boolean blackHose) {
        setFireHoseConnection(controller, partnerPos, partnerSubLevel, null, blackHose);
    }

    @Override
    public void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
            @Nullable UUID partnerSubLevel, @Nullable UUID partnerEndpointId, boolean blackHose) {
        setFireHoseConnection(controller, partnerPos, partnerSubLevel, partnerEndpointId, false, blackHose);
    }

    @Override
    public void setFireHoseConnection(boolean controller, @Nullable BlockPos partnerPos,
            @Nullable UUID partnerSubLevel, @Nullable UUID partnerEndpointId,
            boolean partnerMoving, boolean blackHose) {
        this.isController = controller;
        this.partnerPos = partnerPos;
        this.partnerSubLevel = partnerPos == null ? null : partnerSubLevel;
        this.partnerEndpointId = partnerPos == null ? null : partnerEndpointId;
        this.partnerMoving = partnerPos != null && partnerMoving;
        this.blackHose = blackHose;
        onFireHoseConnectionChanged();
    }

    public boolean isController() {
        return isController;
    }

    public boolean isBlackHose() {
        return blackHose;
    }

    public boolean setBlackHose(boolean black) {
        boolean changed = blackHose != black;
        if (changed) {
            blackHose = black;
            notifyUpdate();
        }
        FireHoseBlockEntity partner = getPairedHose();
        if (partner != null && partner.blackHose != black) {
            partner.blackHose = black;
            partner.notifyUpdate();
            changed = true;
        }
        return changed;
    }

    public void setController(boolean b) {
        this.isController = b;
    }

    public double getSnappingDistance() {
        return Config.hoseMaxLength * Config.hoseSnapMultiplier;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        invalidateRenderBoundingBox();
    }

    @Nullable
    public FireHoseBlockEntity getPairedHose() {
        if (partnerPos == null || level == null) return null;
        BlockEntity be = level.getBlockEntity(partnerPos);
        if (!(be instanceof FireHoseBlockEntity hose))
            return null;
        return isConnectedTo(hose) ? hose : null;
    }

    private boolean isConnectedTo(FireHoseBlockEntity hose) {
        return partnerEndpointId != null
            && partnerEndpointId.equals(hose.getFireHoseEndpointId())
            && hose.partnerPos != null
            && hose.partnerPos.equals(worldPosition)
            && getFireHoseEndpointId().equals(hose.partnerEndpointId);
    }

    private SmartFluidTankBehaviour getSharedTank() {
        if (isController) return tank;
        FireHoseBlockEntity ctrl = getPairedHose();
        if (ctrl != null) return ctrl.tank;
        return tank;
    }

    int getSharedTankRawAmount() {
        SmartFluidTankBehaviour shared = getSharedTank();
        return shared.getPrimaryHandler().getFluidInTank(0).getAmount();
    }

    FluidStack getSharedTankFluidForPipeOutput() {
        if (isPulling())
            return FluidStack.EMPTY;
        IFluidHandler handler = getSharedTank().getPrimaryHandler();
        int rate = (int) getActivePumpSpeed();
        if (rate <= 0)
            return FluidStack.EMPTY;
        FluidStack drained = handler.drain(rate, IFluidHandler.FluidAction.SIMULATE);
        if (!drained.isEmpty()) {
            FireHoseDebugLog.logRawEvery("provide:" + worldPosition.asLong(), 10,
                "provide outward pos={} amount={} tank={} type={} pull={} side={} speed={}",
                worldPosition.toShortString(),
                drained.getAmount(),
                getSharedTankRawAmount(),
                drained.getHoverName().getString(),
                isPulling(),
                pumpSideLabel(pumpSide),
                getActivePumpSpeed());
        } else {
            FireHoseDebugLog.logRawEvery("provide-empty:" + worldPosition.asLong(), 5,
                "provide empty pos={} tank={} pull={} side={} speed={} pumpDist={} sourceKind={}",
                worldPosition.toShortString(),
                getSharedTankRawAmount(),
                isPulling(),
                pumpSideLabel(pumpSide),
                getActivePumpSpeed(),
                distanceLabel(pumpDistance),
                pumpSourceKind);
        }
        return drained;
    }

    // HoseFluidTransferBehaviour accessors

    Direction getBack() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FireHoseBlock))
            return Direction.NORTH;
        return state.getValue(FireHoseBlock.FACING).getOpposite();
    }

    boolean shouldApplyPressure() {
        if (level == null) return false;
        if (pumpSide == PUMP_SIDE_NONE) return false;
        if (getActivePumpSpeed() <= 0) return false;

        SmartFluidTankBehaviour shared = getSharedTank();
        IFluidHandler handler = shared.getPrimaryHandler();
        if (handler.getTanks() == 0) return false;
        boolean empty = handler.getFluidInTank(0).isEmpty();
        boolean full = handler.getFluidInTank(0).getAmount() >= Config.hoseTankCapacity;

        return isPulling() ? !full : !empty;
    }

    void invalidateFluidTopology() {
        invalidateFluidTopology(true);
    }

    private void invalidateFluidTopology(boolean resetRefreshTimer) {
        cacheValid = false;
        if (resetRefreshTimer)
            topologyRefreshTimer = 0;
        if (hoseTransferBehaviour != null)
            hoseTransferBehaviour.wipePressure();
    }

    boolean shouldDriveBackPressure() {
        if (level == null || pumpSide == PUMP_SIDE_NONE || getActivePumpSpeed() <= 0)
            return false;
        // The source side stays open, whether the source is a real pump or an upstream virtual hose.
        return pumpSide != PUMP_SIDE_BACK;
    }

    private boolean canRelayVirtualPressure() {
        return level != null
            && pumpSide != PUMP_SIDE_NONE
            && pumpSide != PUMP_SIDE_BACK
            && getActivePumpSpeed() > 0;
    }

    boolean isPulling() {
        // Describes the virtual hose end, not the external pump's action at the open end.
        if (pumpSide == PUMP_SIDE_NONE)
            return false;
        return (pumpSide == PUMP_SIDE_BACK) == pumpPushesTowardHose;
    }

    float getActivePumpSpeed() {
        if (pumpSpeed <= 0)
            return 0;
        if (effectivePumpSpeed <= 0)
            return pumpSpeed;
        return Math.min(pumpSpeed, effectivePumpSpeed);
    }

    private void logPressureState(Direction back, @Nullable FireHoseBlockEntity partner, boolean apply) {
        if (level == null || level.isClientSide || !FireHoseDebugLog.ENABLED)
            return;

        SmartFluidTankBehaviour shared = getSharedTank();
        IFluidHandler handler = shared.getPrimaryHandler();
        FluidStack fluid = handler.getFluidInTank(0);
        Direction facing = getBlockState().getValue(FireHoseBlock.FACING);
        boolean backPipe = FluidPropagator.getPipe(level, worldPosition.relative(back)) != null;
        boolean frontPipe = FluidPropagator.getPipe(level, worldPosition.relative(facing)) != null;
        boolean backHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(back), back.getOpposite()) != null;
        boolean frontHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.relative(facing), facing.getOpposite()) != null;

        FireHoseDebugLog.logRaw(
            "state pos={} ctrl={} facing={} back={} partner={} side={} dist={} push={} pull={} apply={} fluid={}/{} fluidType={} activeSpeed={} backPipe={} frontPipe={} backHandler={} frontHandler={} snap={}",
            worldPosition.toShortString(),
            isController,
            facing,
            back,
            partner != null ? partner.getBlockPos().toShortString() : "null",
            pumpSideLabel(pumpSide),
            distanceLabel(pumpDistance),
            pumpPushesTowardHose,
            isPulling(),
            apply,
            fluid.getAmount(),
            Config.hoseTankCapacity,
            fluid.isEmpty() ? "empty" : fluid.getHoverName().getString(),
            getActivePumpSpeed(),
            backPipe,
            frontPipe,
            backHandler,
            frontHandler,
            String.format("%.2f", snappingTime));
    }

    static String pumpSideLabel(int side) {
        if (side == PUMP_SIDE_BACK)
            return "BACK";
        if (side == PUMP_SIDE_PARTNER)
            return "PARTNER";
        return "NONE";
    }

    private static String distanceLabel(int distance) {
        return distance == Integer.MAX_VALUE ? "inf" : Integer.toString(distance);
    }

    // Container interaction

    private void tryDirectHoseInteraction(Direction back) {
        if (level == null || pumpSide == PUMP_SIDE_NONE || isPulling())
            return;

        BlockPos backPos = worldPosition.relative(back);
        BlockEntity be = level.getBlockEntity(backPos);
        if (!(be instanceof FireHoseBlockEntity targetHose))
            return;
        if (!targetHose.isPulling())
            return;

        IFluidHandler source = getSharedTank().getPrimaryHandler();
        IFluidHandler target = targetHose.getSharedTank().getPrimaryHandler();
        int rate = (int) getActivePumpSpeed();
        if (rate <= 0)
            return;

        FluidStack drained = source.drain(rate, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty())
            return;

        int filled = target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0)
            return;

        source.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        notifyDirectHoseTransferChanged(targetHose);
        FireHoseDebugLog.logRaw("direct hose transfer source={} target={} amount={} type={} sourcePull={} targetPull={} sourceTank={} targetTank={}",
            worldPosition.toShortString(),
            targetHose.getBlockPos().toShortString(),
            filled,
            drained.getHoverName().getString(),
            isPulling(),
            targetHose.isPulling(),
            getSharedTankRawAmount(),
            targetHose.getSharedTankRawAmount());
    }

    private void notifyDirectHoseTransferChanged(FireHoseBlockEntity targetHose) {
        notifyHoseAndPipeChanged(this);
        notifyHoseAndPipeChanged(targetHose);
        FireHoseBlockEntity sourcePartner = getPairedHose();
        if (sourcePartner != null)
            notifyHoseAndPipeChanged(sourcePartner);
        FireHoseBlockEntity targetPartner = targetHose.getPairedHose();
        if (targetPartner != null)
            notifyHoseAndPipeChanged(targetPartner);
    }

    private void notifyHoseAndPipeChanged(FireHoseBlockEntity hose) {
        if (level == null)
            return;
        hose.invalidateFluidTopology();
        hose.notifyUpdate();
        Direction hoseBack = hose.getBack();
        BlockPos pipePos = hose.getBlockPos().relative(hoseBack);
        FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
    }

    private void tryContainerInteraction(Direction back) {
        if (level == null || pumpSide == PUMP_SIDE_NONE) return;
        BlockPos backPos = worldPosition.relative(back);
        if (FluidPropagator.getPipe(level, backPos) != null) return;
        if (level.getBlockEntity(backPos) instanceof FireHoseBlockEntity) return;

        IFluidHandler container = level.getCapability(
            Capabilities.FluidHandler.BLOCK, backPos, back.getOpposite());
        if (container == null || container.getTanks() == 0) return;

        SmartFluidTankBehaviour shared = getSharedTank();
        IFluidHandler tankHandler = shared.getPrimaryHandler();
        int rate = (int) getActivePumpSpeed();
        if (rate <= 0)
            return;

        if (isPulling()) {
            pullFromContainer(tankHandler, container, rate);
        } else {
            pushToContainer(tankHandler, container, rate);
        }
    }

    private void pushToContainer(IFluidHandler tank, IFluidHandler container, int rate) {
        FluidStack drained = tank.drain(rate, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) return;
        int filled = container.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            FireHoseDebugLog.logRaw("container push source={} amount={} type={} tank={} container={}",
                worldPosition.toShortString(),
                filled,
                drained.getHoverName().getString(),
                getSharedTankRawAmount(),
                container.getClass().getName());
        }
    }

    private void pullFromContainer(IFluidHandler tank, IFluidHandler container, int rate) {
        FluidStack drained = container.drain(rate, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) return;
        int filled = tank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            container.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            FireHoseDebugLog.logRaw("container pull target={} amount={} type={} tank={} container={}",
                worldPosition.toShortString(),
                filled,
                drained.getHoverName().getString(),
                getSharedTankRawAmount(),
                container.getClass().getName());
        }
    }

    // Rendering

    @Override
    public AABB getRenderBoundingBox() {
        FireHoseBlockEntity partner = getPairedHose();
        Vec3 movingPartnerCenter = getMovingPartnerWorldCenter();
        if (partner == null && movingPartnerCenter == null)
            return new AABB(getBlockPos());
        if (movingPartnerCenter != null)
            return new AABB(getBlockPos()).inflate(Config.hoseMaxLength * Config.hoseSnapMultiplier + 3.0);

        Vec3 center = Vec3.atCenterOf(getBlockPos());
        Vec3 partnerCenter = SableStructureCompat.partnerCenterInOwnerSpace(this, partnerPos);

        return new AABB(center, partnerCenter).inflate(3.0);
    }

    @Nullable
    private Vec3 getMovingPartnerWorldCenter() {
        return FireHoseMovingEndpoints.worldCenter(level, partnerPos, this);
    }

    @Nullable
    public UUID getPartnerSubLevelID() {
        return partnerSubLevel;
    }

    // NBT serialization

    @Override
    public void writeFireHoseConnection(CompoundTag tag, HolderLookup.Provider registries) {
        writeFireHoseConnectionFields(tag);

        CompoundTag connection = new CompoundTag();
        writeFireHoseConnectionFields(connection);
        tag.put(CONNECTION_TAG, connection);
    }

    @Override
    public void readFireHoseConnection(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag source = tag.contains(CONNECTION_TAG, Tag.TAG_COMPOUND)
            ? tag.getCompound(CONNECTION_TAG)
            : tag;
        readFireHoseConnectionFields(source);
    }

    private void writeFireHoseConnectionFields(CompoundTag tag) {
        tag.putBoolean(TAG_CONTROLLER, isController);
        tag.putBoolean(TAG_BLACK_HOSE, blackHose);
        tag.putUUID(TAG_ENDPOINT_ID, getFireHoseEndpointId());

        if (partnerPos == null)
            return;

        SableStructureCompat.writeLinkedBlock(tag, TAG_PARTNER_POS, TAG_PARTNER_SUB_LEVEL,
                partnerPos, partnerSubLevel);
        if (partnerEndpointId != null)
            tag.putUUID(TAG_PARTNER_ENDPOINT_ID, partnerEndpointId);
        if (partnerMoving)
            tag.putBoolean(TAG_PARTNER_MOVING, true);
    }

    private void readFireHoseConnectionFields(CompoundTag tag) {
        isController = tag.getBoolean(TAG_CONTROLLER);
        blackHose = tag.getBoolean(TAG_BLACK_HOSE);
        endpointId = tag.hasUUID(TAG_ENDPOINT_ID) ? tag.getUUID(TAG_ENDPOINT_ID) : UUID.randomUUID();
        partnerPos = null;
        partnerSubLevel = null;
        partnerEndpointId = null;
        partnerMoving = false;
        movingPartnerRuntimeId = null;
        movingPartnerLeaseExpiresAt = 0;

        SableStructureCompat.LinkedBlockRef ref = SableStructureCompat.readLinkedBlock(
                tag, TAG_PARTNER_POS, TAG_PARTNER_SUB_LEVEL);
        partnerPos = ref.pos();
        partnerSubLevel = ref.subLevelId();
        if (partnerPos != null && tag.hasUUID(TAG_PARTNER_ENDPOINT_ID))
            partnerEndpointId = tag.getUUID(TAG_PARTNER_ENDPOINT_ID);
        partnerMoving = partnerPos != null && tag.getBoolean(TAG_PARTNER_MOVING);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFireHoseConnection(tag, registries);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeFireHoseConnection(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readFireHoseConnection(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (renderLength.getValue() == 0)
            renderLength.setValue(1);
        readFireHoseConnection(tag, registries);
    }

    private static class HoseFluidHandler implements IFluidHandler {
        private final FireHoseBlockEntity owner;
        @Nullable
        private final Direction side;

        HoseFluidHandler(FireHoseBlockEntity owner, @Nullable Direction side) {
            this.owner = owner;
            this.side = side;
        }

        private IFluidHandler shared() {
            return owner.getSharedTank().getPrimaryHandler();
        }

        @Override
        public int getTanks() {
            return shared().getTanks();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return SafeFluidStacks.normalize(shared().getFluidInTank(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return shared().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return shared().isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            resource = SafeFluidStacks.normalize(resource);
            boolean externalBackInput = side == owner.getBack();
            if (!owner.isPulling() && !externalBackInput)
                return 0;
            boolean tankFull = shared().getFluidInTank(0).getAmount() >= shared().getTankCapacity(0);
            if (externalBackInput && !resource.isEmpty() && (action.execute() || tankFull))
                owner.recordExternalInput(resource.getAmount(), true);
            int filled = shared().fill(resource, action);
            if (filled > 0 && action.execute()) {
                owner.recordSharedInput(filled);
            }
            if (filled > 0)
                FireHoseDebugLog.logRaw("capability fill pos={} amount={} type={} action={} pull={} pumpSide={} accessSide={} tank={}",
                    owner.worldPosition.toShortString(),
                    filled,
                    resource.getHoverName().getString(),
                    action,
                    owner.isPulling(),
                    pumpSideLabel(owner.pumpSide),
                    side,
                    owner.getSharedTankRawAmount());
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            resource = SafeFluidStacks.normalize(resource);
            boolean externalBackOutput = side == owner.getBack();
            if (!externalBackOutput && (owner.pumpSide == PUMP_SIDE_NONE || owner.isPulling()))
                return FluidStack.EMPTY;
            boolean tankEmpty = shared().getFluidInTank(0).isEmpty();
            if (externalBackOutput && !resource.isEmpty() && (action.execute() || tankEmpty))
                owner.recordExternalInput(resource.getAmount(), false);
            FluidStack drained = shared().drain(resource, action);
            logDrain(drained, action);
            return SafeFluidStacks.normalize(drained);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            boolean externalBackOutput = side == owner.getBack();
            if (!externalBackOutput && (owner.pumpSide == PUMP_SIDE_NONE || owner.isPulling()))
                return FluidStack.EMPTY;
            boolean tankEmpty = shared().getFluidInTank(0).isEmpty();
            if (externalBackOutput && maxDrain > 0 && (action.execute() || tankEmpty))
                owner.recordExternalInput(maxDrain, false);
            FluidStack drained = shared().drain(maxDrain, action);
            logDrain(drained, action);
            return SafeFluidStacks.normalize(drained);
        }

        private void logDrain(FluidStack drained, FluidAction action) {
            if (drained.isEmpty())
                return;
            FireHoseDebugLog.logRaw("capability drain pos={} amount={} type={} action={} pull={} side={} tank={}",
                owner.worldPosition.toShortString(),
                drained.getAmount(),
                drained.getHoverName().getString(),
                action,
                owner.isPulling(),
                pumpSideLabel(owner.pumpSide),
                owner.getSharedTankRawAmount());
        }
    }
}
