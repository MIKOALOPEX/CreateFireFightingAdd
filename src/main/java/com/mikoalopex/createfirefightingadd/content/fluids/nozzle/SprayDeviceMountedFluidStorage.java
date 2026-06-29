package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.SafeFluidStacks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SprayDeviceMountedFluidStorage extends WrapperMountedFluidStorage<SprayDeviceMountedFluidStorage.Handler>
	implements SyncedMountedStorage {

	public static final MapCodec<SprayDeviceMountedFluidStorage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(SprayDeviceMountedFluidStorage::getCapacity),
		FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid")
			.forGetter(SprayDeviceMountedFluidStorage::getFluidForEncoding),
		Codec.BOOL.fieldOf("water_only").forGetter(SprayDeviceMountedFluidStorage::isWaterOnly)
	).apply(instance, SprayDeviceMountedFluidStorage::new));

	private final boolean waterOnly;
	private boolean dirty;
	private FluidStack lastCleanFluidState;

	public SprayDeviceMountedFluidStorage(int capacity, FluidStack stack, boolean waterOnly) {
		super(CreateFireFightingAdd.SPRAY_DEVICE_MOUNTED_FLUID_STORAGE.get(),
			new Handler(capacity, stack, waterOnly));
		this.waterOnly = waterOnly;
		this.lastCleanFluidState = syncFluidState(stack);
		this.wrapped.onChange = this::onFluidChanged;
	}

	private SprayDeviceMountedFluidStorage(int capacity, Optional<FluidStack> stack, boolean waterOnly) {
		this(capacity, stack.map(SafeFluidStacks::copy).orElse(FluidStack.EMPTY), waterOnly);
	}

	public static SprayDeviceMountedFluidStorage fromDevice(AbstractSprayDeviceBlockEntity device) {
		boolean bucket = device instanceof BucketControllerBlockEntity;
		return new SprayDeviceMountedFluidStorage(
			device.getMountedFluidCapacity(),
			device.getMountedFluidStack(),
			bucket);
	}

	@Override
	public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
		if (be instanceof AbstractSprayDeviceBlockEntity device)
			device.setMountedFluidStack(getFluid());
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
		if (be instanceof AbstractSprayDeviceBlockEntity device)
			device.setMountedFluidStack(getFluid());
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

	public boolean isWaterOnly() {
		return waterOnly;
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

		Handler(int capacity, FluidStack stack, boolean waterOnly) {
			super(capacity);
			setValidator(resource -> waterOnly
				? resource.getFluid().is(FluidTags.WATER)
				: AbstractSprayDeviceBlockEntity.isFluidSupportedForSpray(null, resource));
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
