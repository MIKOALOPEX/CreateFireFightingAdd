package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.SafeFluidStacks;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FireHoseMountedFluidStorage extends WrapperMountedFluidStorage<FireHoseMountedFluidStorage.Handler>
	implements SyncedMountedStorage {

	private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

	public static final MapCodec<FireHoseMountedFluidStorage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(FireHoseMountedFluidStorage::getCapacity),
		FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid").forGetter(FireHoseMountedFluidStorage::getFluidForEncoding),
		BlockPos.CODEC.fieldOf("origin").forGetter(FireHoseMountedFluidStorage::getOriginPos),
		UUID_CODEC.optionalFieldOf("endpoint_id").forGetter(FireHoseMountedFluidStorage::getEndpointIdForEncoding),
		Direction.CODEC.fieldOf("facing").forGetter(FireHoseMountedFluidStorage::getFacing),
		Codec.BOOL.fieldOf("controller").forGetter(FireHoseMountedFluidStorage::isController),
		Codec.BOOL.fieldOf("black").forGetter(FireHoseMountedFluidStorage::isBlackHose),
		BlockPos.CODEC.optionalFieldOf("partner").forGetter(FireHoseMountedFluidStorage::getPartnerForEncoding),
		UUID_CODEC.optionalFieldOf("partner_sublevel").forGetter(FireHoseMountedFluidStorage::getPartnerSubLevelForEncoding),
		Codec.BOOL.optionalFieldOf("partner_moving", false).forGetter(FireHoseMountedFluidStorage::isPartnerMoving),
		UUID_CODEC.optionalFieldOf("partner_endpoint_id").forGetter(FireHoseMountedFluidStorage::getPartnerEndpointIdForEncoding)
	).apply(instance, FireHoseMountedFluidStorage::new));

	private final BlockPos originPos;
	private final UUID endpointId;
	private final Direction facing;
	private boolean controller;
	private boolean blackHose;
	@Nullable
	private BlockPos partnerPos;
	@Nullable
	private UUID partnerSubLevel;
	private boolean partnerMoving;
	@Nullable
	private UUID partnerEndpointId;
	@Nullable
	private UUID runtimeId;
	private boolean dirty;
	private FluidStack lastCleanFluidState;
	private double snappingTime;
	private int partnerConflictTicks;

	public FireHoseMountedFluidStorage(int capacity, FluidStack stack, BlockPos originPos, Direction facing,
			UUID endpointId, boolean controller, boolean blackHose, @Nullable BlockPos partnerPos,
			@Nullable UUID partnerSubLevel, boolean partnerMoving, @Nullable UUID partnerEndpointId) {
		super(CreateFireFightingAdd.FIRE_HOSE_MOUNTED_FLUID_STORAGE.get(), new Handler(capacity, stack));
		this.originPos = originPos;
		this.endpointId = endpointId == null ? UUID.randomUUID() : endpointId;
		this.facing = facing;
		this.controller = controller;
		this.blackHose = blackHose;
		this.partnerPos = partnerPos;
		this.partnerSubLevel = partnerPos == null ? null : partnerSubLevel;
		this.partnerMoving = partnerPos != null && partnerMoving;
		this.partnerEndpointId = partnerPos == null ? null : partnerEndpointId;
		this.lastCleanFluidState = syncFluidState(stack);
		this.wrapped.onChange = this::onFluidChanged;
	}

	private FireHoseMountedFluidStorage(int capacity, Optional<FluidStack> stack, BlockPos originPos,
			Optional<UUID> endpointId, Direction facing, boolean controller, boolean blackHose,
			Optional<BlockPos> partnerPos, Optional<UUID> partnerSubLevel,
			boolean partnerMoving, Optional<UUID> partnerEndpointId) {
		this(capacity, stack.map(SafeFluidStacks::copy).orElse(FluidStack.EMPTY), originPos,
			facing, endpointId.orElseGet(UUID::randomUUID), controller, blackHose,
			partnerPos.orElse(null), partnerSubLevel.orElse(null), partnerMoving, partnerEndpointId.orElse(null));
	}

	public static FireHoseMountedFluidStorage fromEndpoint(FireHoseBlockEntity hose) {
		hose.markStructureAssembling();
		return new FireHoseMountedFluidStorage(
			Config.hoseTankCapacity,
			hose.getMountedFluidStack(),
			hose.getBlockPos(),
			hose.getFacingDirection(),
			hose.getFireHoseEndpointId(),
			hose.isController(),
			hose.isBlackHose(),
			hose.getFireHosePartnerPos(),
			hose.getFireHosePartnerSubLevel(),
			hose.isFireHosePartnerMoving(),
			hose.getFireHosePartnerEndpointId());
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (!(be instanceof FireHoseBlockEntity hose))
			return;

		BlockPos restoredPartnerPos = partnerPos;
		UUID restoredPartnerSubLevel = partnerSubLevel;
		FireHoseMovingEndpoints.Replacement replacement = FireHoseMovingEndpoints.replacement(level, partnerPos);
		if (replacement != null) {
			restoredPartnerPos = replacement.pos();
			restoredPartnerSubLevel = replacement.subLevel();
		}
		if (restoredPartnerPos != null)
			replacePartner(restoredPartnerPos, restoredPartnerSubLevel);
		if (restoredPartnerPos != null
			&& !FireHoseMovingEndpoints.canRestoreConnection(level, this, restoredPartnerPos)) {
			restoredPartnerPos = null;
			restoredPartnerSubLevel = null;
			disconnect();
		}

		hose.setFireHoseEndpointId(endpointId);
		hose.setMountedFluidStack(getFluid());
		hose.setFireHoseConnection(controller, restoredPartnerPos, restoredPartnerSubLevel,
			partnerEndpointId, partnerMoving, blackHose);
		if (restoredPartnerPos != null)
			FireHoseMovingEndpoints.replaceMovingEndpoint(level, this, pos,
				SableStructureCompat.containingSubLevelId(level, pos));
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public void markClean() {
		dirty = false;
		lastCleanFluidState = syncFluidState(wrapped.getFluid());
	}

	@Override
	public void afterSync(Contraption contraption, BlockPos localPos) {
		BlockEntity be = contraption.getBlockEntityClientSide(localPos);
		if (be instanceof FireHoseBlockEntity hose) {
			hose.setFireHoseEndpointId(endpointId);
			hose.setMountedFluidStack(getFluid());
			hose.setFireHoseConnection(controller, partnerPos, partnerSubLevel, partnerEndpointId,
				partnerMoving, blackHose);
		}
	}

	public int getCapacity() {
		return wrapped.getCapacity();
	}

	@Override
	@NotNull
	public FluidStack getFluidInTank(int tank) {
		return SafeFluidStacks.normalize(wrapped.getFluidInTank(tank));
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return wrapped.fill(SafeFluidStacks.normalize(resource), action);
	}

	@Override
	@NotNull
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return SafeFluidStacks.normalize(wrapped.drain(SafeFluidStacks.normalize(resource), action));
	}

	@Override
	@NotNull
	public FluidStack drain(int maxDrain, FluidAction action) {
		return SafeFluidStacks.normalize(wrapped.drain(maxDrain, action));
	}

	public FluidStack getFluid() {
		return SafeFluidStacks.normalize(wrapped.getFluid());
	}

	public BlockPos getOriginPos() {
		return originPos;
	}

	public UUID getEndpointId() {
		return endpointId;
	}

	public Direction getFacing() {
		return facing;
	}

	public boolean isController() {
		return controller;
	}

	public boolean isBlackHose() {
		return blackHose;
	}

	@Nullable
	public BlockPos getPartnerPos() {
		return partnerPos;
	}

	@Nullable
	public UUID getPartnerSubLevel() {
		return partnerSubLevel;
	}

	public boolean isPartnerMoving() {
		return partnerMoving;
	}

	@Nullable
	public UUID getPartnerEndpointId() {
		return partnerEndpointId;
	}

	@Nullable
	public UUID getRuntimeId() {
		return runtimeId;
	}

	public void setRuntimeId(UUID runtimeId) {
		this.runtimeId = runtimeId;
	}

	public Optional<BlockPos> getPartnerForEncoding() {
		return Optional.ofNullable(partnerPos);
	}

	public Optional<UUID> getEndpointIdForEncoding() {
		return Optional.of(endpointId);
	}

	public Optional<UUID> getPartnerSubLevelForEncoding() {
		return Optional.ofNullable(partnerSubLevel);
	}

	public Optional<UUID> getPartnerEndpointIdForEncoding() {
		return Optional.ofNullable(partnerEndpointId);
	}

	public double getSnappingTime() {
		return snappingTime;
	}

	public void setSnappingTime(double snappingTime) {
		this.snappingTime = snappingTime;
	}

	public void disconnect() {
		partnerPos = null;
		partnerSubLevel = null;
		partnerMoving = false;
		partnerEndpointId = null;
		partnerConflictTicks = 0;
		dirty = true;
	}

	public void replacePartner(BlockPos pos, @Nullable UUID subLevel) {
		partnerPos = pos;
		partnerSubLevel = subLevel;
		partnerMoving = false;
		partnerConflictTicks = 0;
		dirty = true;
	}

	boolean notePartnerConflict(int graceTicks) {
		partnerConflictTicks++;
		return partnerConflictTicks > graceTicks;
	}

	void clearPartnerConflict() {
		partnerConflictTicks = 0;
	}

	private Optional<FluidStack> getFluidForEncoding() {
		FluidStack stack = SafeFluidStacks.normalize(wrapped.getFluid());
		return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
	}

	private void onFluidChanged() {
		if (!sameSyncFluid(lastCleanFluidState, wrapped.getFluid()))
			dirty = true;
	}

	private static FluidStack syncFluidState(FluidStack stack) {
		stack = SafeFluidStacks.normalize(stack);
		return stack.isEmpty() ? FluidStack.EMPTY : stack.copyWithAmount(1);
	}

	private static boolean sameSyncFluid(FluidStack first, FluidStack second) {
		first = syncFluidState(first);
		second = syncFluidState(second);
		if (first.isEmpty() || second.isEmpty())
			return first.isEmpty() && second.isEmpty();
		return FluidStack.isSameFluidSameComponents(first, second);
	}

	static class Handler extends FluidTank {
		private Runnable onChange = () -> {
		};

		Handler(int capacity, FluidStack stack) {
			super(capacity);
			setFluid(stack);
		}

		@Override
		public FluidStack getFluid() {
			return SafeFluidStacks.normalize(super.getFluid());
		}

		@Override
		@NotNull
		public FluidStack getFluidInTank(int tank) {
			return getFluid();
		}

		@Override
		public void setFluid(FluidStack stack) {
			super.setFluid(SafeFluidStacks.copy(stack));
		}

		@Override
		@NotNull
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return SafeFluidStacks.normalize(super.drain(SafeFluidStacks.normalize(resource), action));
		}

		@Override
		@NotNull
		public FluidStack drain(int maxDrain, FluidAction action) {
			return SafeFluidStacks.normalize(super.drain(maxDrain, action));
		}

		@Override
		protected void onContentsChanged() {
			fluid = SafeFluidStacks.normalize(fluid);
			onChange.run();
		}
	}
}
