package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.handheld.HandheldNozzleBindingApi;
import com.mojang.math.Axis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import org.joml.Vector3f;

/**
 * World entity form for a bound handheld nozzle controller.
 * It keeps the hydrant binding alive while the controller rests in the world.
 */
public class HandheldNozzleControllerEntity extends LivingEntity {
	private static final EntityDataAccessor<ItemStack> CONTROLLER_STACK =
		SynchedEntityData.defineId(HandheldNozzleControllerEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Float> FIXED_YAW =
		SynchedEntityData.defineId(HandheldNozzleControllerEntity.class, EntityDataSerializers.FLOAT);

	private static final String STACK_TAG = "Controller";
	private static final String YAW_TAG = "FixedYaw";
	private static final int MAX_LIFETIME = 30 * 20;
	private static final double DROP_HORIZONTAL_SPEED_SCALE = 0.45;
	private static final double DROP_VERTICAL_SPEED_SCALE = 0.65;
	public static final float GROUND_RENDER_Y_OFFSET = 0.14f;
	private static final Vec3 HOSE_FACE_CENTER = new Vec3(0.0, 1.4142135 / 16.0, 0.5);
	private static final Vec3 HOSE_FACE_NORMAL = new Vec3(0.0, 0.0, 1.0);
	private static final Vec3 HOSE_FACE_UP = new Vec3(-0.70710677, 0.70710677, 0.0);
	private static final Map<BindingKey, Integer> ACTIVE_BINDINGS = new ConcurrentHashMap<>();

	public HandheldNozzleControllerEntity(EntityType<? extends HandheldNozzleControllerEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
			.add(Attributes.MAX_HEALTH, 5.0)
			.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	public static void tryConvertDroppedItem(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide())
			return;
		if (!(event.getEntity() instanceof ItemEntity itemEntity))
			return;
		try {
			HandheldNozzleControllerEntity entity = createFromItemEntity(itemEntity, readDropYaw(itemEntity));
			if (entity == null)
				return;
			entity.registerActiveBinding();
			if (event.getLevel().addFreshEntity(entity)) {
				event.setCanceled(true);
			} else {
				entity.unregisterActiveBinding();
			}
		} catch (RuntimeException e) {
			CreateFireFightingAdd.LOGGER.warn("Failed to convert bound handheld nozzle controller item into an entity.", e);
		}
	}

	public static void tryConvertTossedItem(ItemTossEvent event) {
		if (event.getPlayer().level().isClientSide())
			return;
		if (HandheldNozzleBindingApi.isBoundController(event.getEntity().getItem())) {
			event.getEntity().setYRot(event.getPlayer().getYRot());
			event.getEntity().yRotO = event.getEntity().getYRot();
		}
	}

	public static boolean hasActiveBinding(ResourceKey<Level> dimension, BlockPos pos, UUID hydrantId) {
		return ACTIVE_BINDINGS.containsKey(new BindingKey(dimension, pos.immutable(), hydrantId));
	}

	public static void forceClearBinding(Level level, BlockPos pos, UUID hydrantId) {
		ACTIVE_BINDINGS.remove(new BindingKey(level.dimension(), pos.immutable(), hydrantId));
	}

	public ItemStack getControllerStack() {
		return entityData.get(CONTROLLER_STACK);
	}

	public float getFixedYaw() {
		return entityData.get(FIXED_YAW);
	}

	public Vec3 getHoseAnchor(float partialTick) {
		double x = Mth.lerp(partialTick, xo, getX());
		double y = Mth.lerp(partialTick, yo, getY());
		double z = Mth.lerp(partialTick, zo, getZ());
		return new Vec3(x, y, z).add(transformModelPoint(HOSE_FACE_CENTER, getFixedYaw()));
	}

	public Vec3 getHoseNormal() {
		return transformModelDirection(HOSE_FACE_NORMAL, getFixedYaw());
	}

	public Vec3 getHoseUp() {
		return transformModelDirection(HOSE_FACE_UP, getFixedYaw());
	}

	@Override
	public void tick() {
		super.tick();
		setYRot(getFixedYaw());
		yRotO = getFixedYaw();
		setYHeadRot(getFixedYaw());
		yHeadRotO = getFixedYaw();

		if (!level().isClientSide && (!bindingStillValid() || tickCount >= MAX_LIFETIME)) {
			dropAndDiscard(true);
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (level().isClientSide)
			return InteractionResult.SUCCESS;
		ItemStack stack = getControllerStack().copy();
		if (stack.isEmpty())
			return InteractionResult.PASS;

		rebindCabinetTo(player, stack);
		if (!player.getInventory().add(stack))
			player.drop(stack, false);
		setControllerStack(ItemStack.EMPTY);
		discard();
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean isPickable() {
		return !getControllerStack().isEmpty();
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL))
			return false;
		if (!level().isClientSide)
			dropAndDiscard(true);
		return true;
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return java.util.Collections.emptyList();
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public void remove(RemovalReason reason) {
		unregisterActiveBinding();
		super.remove(reason);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CONTROLLER_STACK, ItemStack.EMPTY);
		builder.define(FIXED_YAW, 0.0F);
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		HolderLookup.Provider registries = registryAccess();
		setControllerStack(tag.contains(STACK_TAG)
			? ItemStack.parseOptional(registries, tag.getCompound(STACK_TAG))
			: ItemStack.EMPTY);
		setFixedYaw(tag.getFloat(YAW_TAG));
		registerActiveBinding();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		ItemStack stack = getControllerStack();
		if (!stack.isEmpty())
			tag.put(STACK_TAG, stack.save(registryAccess(), new CompoundTag()));
		tag.putFloat(YAW_TAG, getFixedYaw());
	}

	private static HandheldNozzleControllerEntity createFromItemEntity(ItemEntity itemEntity, float yaw) {
		ItemStack stack = itemEntity.getItem();
		if (!HandheldNozzleBindingApi.isBoundController(stack))
			return null;

		HandheldNozzleControllerEntity entity = CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ENTITY.get()
			.create(itemEntity.level());
		if (entity == null)
			return null;

		entity.setControllerStack(stack.copy());
		entity.setFixedYaw(yaw);
		entity.setYRot(entity.getFixedYaw());
		entity.yRotO = entity.getFixedYaw();
		entity.setPos(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
		Vec3 movement = itemEntity.getDeltaMovement();
		entity.setDeltaMovement(movement.x * DROP_HORIZONTAL_SPEED_SCALE,
			movement.y * DROP_VERTICAL_SPEED_SCALE,
			movement.z * DROP_HORIZONTAL_SPEED_SCALE);
		return entity;
	}

	private static float readDropYaw(ItemEntity itemEntity) {
		Vec3 movement = itemEntity.getDeltaMovement();
		if (movement.horizontalDistanceSqr() > 1.0E-5)
			return (float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG);
		return itemEntity.getYRot();
	}

	private static Vec3 transformModelPoint(Vec3 local, float yaw) {
		Vector3f vector = new Vector3f((float) local.x, (float) local.y, (float) local.z);
		Axis.YP.rotationDegrees(180.0f - yaw).transform(vector);
		return new Vec3(vector.x(), vector.y() + GROUND_RENDER_Y_OFFSET, vector.z());
	}

	private static Vec3 transformModelDirection(Vec3 local, float yaw) {
		Vector3f vector = new Vector3f((float) local.x, (float) local.y, (float) local.z);
		Axis.YP.rotationDegrees(180.0f - yaw).transform(vector);
		Vec3 result = new Vec3(vector.x(), vector.y(), vector.z());
		return result.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : result.normalize();
	}

	private void setControllerStack(ItemStack stack) {
		entityData.set(CONTROLLER_STACK, stack);
	}

	private void setFixedYaw(float yaw) {
		entityData.set(FIXED_YAW, yaw);
	}

	private boolean bindingStillValid() {
		Optional<HandheldNozzleControllerItem.Binding> binding = HandheldNozzleControllerItem.readBinding(getControllerStack());
		if (binding.isEmpty())
			return false;
		HandheldNozzleControllerItem.Binding data = binding.get();
		if (!level().dimension().equals(data.dimension()))
			return false;
		if (distanceToSqr(data.pos().getX() + 0.5, data.pos().getY() + 0.5, data.pos().getZ() + 0.5)
			> HandheldNozzleBindingApi.MAX_BINDING_DISTANCE_SQR)
			return false;
		if (!(level().getBlockEntity(data.pos()) instanceof FireHydrantCabinetBlockEntity cabinet))
			return false;
		return cabinet.getHydrantId().equals(data.hydrantId())
			&& cabinet.hasActiveBinding()
			&& cabinet.canServeHandheldNozzle();
	}

	private void rebindCabinetTo(Player player, ItemStack stack) {
		HandheldNozzleControllerItem.readBinding(stack).ifPresent(binding -> {
			if (!level().dimension().equals(binding.dimension()))
				return;
			if (level().getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet
				&& cabinet.getHydrantId().equals(binding.hydrantId()))
				cabinet.bindTo(player);
		});
	}

	private void dropAndDiscard(boolean clearBinding) {
		ItemStack stack = getControllerStack().copy();
		if (clearBinding)
			HandheldNozzleBindingApi.clearBinding(level(), stack, null);
		if (!stack.isEmpty())
			level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), stack));
		setControllerStack(ItemStack.EMPTY);
		discard();
	}

	private void registerActiveBinding() {
		HandheldNozzleControllerItem.readBinding(getControllerStack()).ifPresent(binding ->
			ACTIVE_BINDINGS.put(new BindingKey(binding.dimension(), binding.pos().immutable(), binding.hydrantId()), getId()));
	}

	private void unregisterActiveBinding() {
		HandheldNozzleControllerItem.readBinding(getControllerStack()).ifPresent(binding -> {
			BindingKey key = new BindingKey(binding.dimension(), binding.pos().immutable(), binding.hydrantId());
			ACTIVE_BINDINGS.computeIfPresent(key, (ignored, entityId) -> entityId == getId() ? null : entityId);
		});
	}

	private record BindingKey(ResourceKey<Level> dimension, BlockPos pos, UUID hydrantId) {
	}
}
