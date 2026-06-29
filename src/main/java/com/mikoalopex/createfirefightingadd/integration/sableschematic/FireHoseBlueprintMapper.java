package com.mikoalopex.createfirefightingadd.integration.sableschematic;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseConnectionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;

final class FireHoseBlueprintMapper implements InvocationHandler {
	private static final String BLUEPRINT_TAG = "SableBlueprintFireHose";
	private static final String PARTNER_REF_TAG = "PartnerRef";
	private static final String PARTNER_SUB_LEVEL_REF_TAG = "PartnerSubLevelRef";

	private static final String TAG_PARTNER_POS = "PartnerPos";
	private static final String TAG_PARTNER_SUB_LEVEL = "PartnerSubLevel";
	private static final String TAG_PARTNER_ENDPOINT_ID = "PartnerEndpointId";
	private static final String TAG_PARTNER_MOVING = "PartnerMoving";

	private final Api api;

	private FireHoseBlueprintMapper(Api api) {
		this.api = api;
	}

	static Object create(Api api) {
		return Proxy.newProxyInstance(
			FireHoseBlueprintMapper.class.getClassLoader(),
			new Class<?>[] { api.blockMapperClass() },
			new FireHoseBlueprintMapper(api));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return switch (method.getName()) {
			case "save" -> save(args[0], (CompoundTag) args[1]);
			case "beforeLoadBlockEntity" -> {
				beforeLoadBlockEntity(args[0], (CompoundTag) args[1]);
				yield null;
			}
			case "afterLoadBlockEntity" -> {
				afterLoadBlockEntity(args[0], (BlockEntity) args[1], (CompoundTag) args[2]);
				yield null;
			}
			case "toString" -> "CreateFireFightingAdd FireHoseBlueprintMapper";
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == args[0];
			default -> method.invoke(this, args);
		};
	}

	private CompoundTag save(Object context, CompoundTag tag) throws ReflectiveOperationException {
		Object blockEntity = api.saveBlockEntity().invoke(context);
		if (tag == null || !(blockEntity instanceof FireHoseConnectionAccess hose))
			return tag;

		CompoundTag copy = tag.copy();
		Object registryAccess = api.registryAccess().invoke(context);
		if (!(registryAccess instanceof HolderLookup.Provider registries))
			return copy;
		hose.writeFireHoseConnection(copy, registries);

		BlockPos partnerPos = hose.getFireHosePartnerPos();
		if (partnerPos == null) {
			clearNativePartner(copy);
			return copy;
		}

		Optional<BlockRef> partnerRef = blockRef(api.blockRef().invoke(context, partnerPos));
		if (partnerRef.isEmpty()) {
			clearNativePartner(copy);
			return copy;
		}

		CompoundTag blueprint = new CompoundTag();
		blueprint.put(PARTNER_REF_TAG, writeBlockRef(partnerRef.get()));
		UUID partnerSubLevel = hose.getFireHosePartnerSubLevel();
		if (partnerSubLevel != null)
			subLevelRef(api.subLevelRef().invoke(context, partnerSubLevel))
				.ifPresent(ref -> blueprint.put(PARTNER_SUB_LEVEL_REF_TAG,
					writeSubLevelRef(ref.subLevelId(), ref.sourceUuid())));

		copy.put(BLUEPRINT_TAG, blueprint);
		return copy;
	}

	private void beforeLoadBlockEntity(Object context, CompoundTag tag) throws ReflectiveOperationException {
		if (tag == null || !tag.contains(BLUEPRINT_TAG, Tag.TAG_COMPOUND)) {
			clearNativePartner(tag);
			return;
		}

		CompoundTag blueprint = tag.getCompound(BLUEPRINT_TAG);
		Optional<BlockRef> partnerRef = readBlockRef(blueprint.getCompound(PARTNER_REF_TAG));
		if (partnerRef.isEmpty()) {
			clearNativePartner(tag);
			return;
		}

		Object mapped = api.mapBlock().invoke(context, partnerRef.get().nativeRef());
		if (!(mapped instanceof BlockPos partnerPos)) {
			clearNativePartner(tag);
			return;
		}

		CompoundTag connection = connectionTag(tag);
		connection.putLong(TAG_PARTNER_POS, partnerPos.asLong());
		connection.putBoolean(TAG_PARTNER_MOVING, false);
		UUID partnerSubLevel = mappedPartnerSubLevel(context, blueprint, partnerRef.get());
		if (partnerSubLevel != null)
			connection.putUUID(TAG_PARTNER_SUB_LEVEL, partnerSubLevel);
		else
			connection.remove(TAG_PARTNER_SUB_LEVEL);
	}

	private void afterLoadBlockEntity(Object context, BlockEntity blockEntity, CompoundTag tag) {
		if (!(blockEntity instanceof FireHoseConnectionAccess hose))
			return;
		if (tag == null || !tag.contains(BLUEPRINT_TAG, Tag.TAG_COMPOUND))
			return;

		CompoundTag connection = connectionTag(tag);
		BlockPos partnerPos = connection.contains(TAG_PARTNER_POS)
			? BlockPos.of(connection.getLong(TAG_PARTNER_POS))
			: null;
		UUID partnerSubLevel = connection.hasUUID(TAG_PARTNER_SUB_LEVEL)
			? connection.getUUID(TAG_PARTNER_SUB_LEVEL)
			: null;
		UUID partnerEndpointId = connection.hasUUID(TAG_PARTNER_ENDPOINT_ID)
			? connection.getUUID(TAG_PARTNER_ENDPOINT_ID)
			: null;
		boolean partnerMoving = connection.getBoolean(TAG_PARTNER_MOVING);

		hose.setFireHoseConnection(
			hose.isFireHoseController(),
			partnerPos,
			partnerSubLevel,
			partnerEndpointId,
			partnerMoving,
			hose.isFireHoseBlack());
	}

	private UUID mappedPartnerSubLevel(Object context, CompoundTag blueprint, BlockRef partnerRef)
			throws ReflectiveOperationException {
		if (blueprint.contains(PARTNER_SUB_LEVEL_REF_TAG, Tag.TAG_COMPOUND)) {
			CompoundTag ref = blueprint.getCompound(PARTNER_SUB_LEVEL_REF_TAG);
			if (ref.hasUUID("SourceUuid")) {
				Object mapped = api.mapSubLevel().invoke(context, ref.getUUID("SourceUuid"));
				if (mapped instanceof UUID uuid)
					return uuid;
			}
		}

		Object blueprintSubLevelId = api.blueprintSubLevelId().invoke(context);
		if (blueprintSubLevelId instanceof Integer id && partnerRef.subLevelId() == id)
			return null;

		return placedSubLevelUuid(api.session().invoke(context), partnerRef.subLevelId());
	}

	private UUID placedSubLevelUuid(Object session, int subLevelId) {
		if (session == null)
			return null;

		try {
			Object subLevel = api.placedSubLevel().invoke(session, subLevelId);
			if (subLevel == null)
				return null;

			Object id = subLevel.getClass()
				.getMethod("getUniqueId")
				.invoke(subLevel);
			return id instanceof UUID uuid ? uuid : null;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	private Optional<BlockRef> blockRef(Object value) throws ReflectiveOperationException {
		if (value instanceof Optional<?> optional)
			value = optional.orElse(null);
		if (value == null)
			return Optional.empty();
		return Optional.of(new BlockRef(value, readSubLevelId(value), readLocalPos(value)));
	}

	private Optional<SubLevelRef> subLevelRef(Object value) throws ReflectiveOperationException {
		if (value instanceof Optional<?> optional)
			value = optional.orElse(null);
		if (value == null)
			return Optional.empty();

		Object subLevelId = api.subLevelId().invoke(value);
		Object sourceUuid = api.sourceUuid().invoke(value);
		if (subLevelId instanceof Integer id && sourceUuid instanceof UUID uuid)
			return Optional.of(new SubLevelRef(id, uuid));
		return Optional.empty();
	}

	private int readSubLevelId(Object ref) throws ReflectiveOperationException {
		Object value = api.blockRefSubLevelId().invoke(ref);
		if (!(value instanceof Integer id))
			throw new IllegalStateException("BlueprintBlockRef.subLevelId() did not return an int");
		return id;
	}

	private BlockPos readLocalPos(Object ref) throws ReflectiveOperationException {
		Object value = api.blockRefLocalPos().invoke(ref);
		if (!(value instanceof BlockPos pos))
			throw new IllegalStateException("BlueprintBlockRef.localPos() did not return a BlockPos");
		return pos;
	}

	private CompoundTag connectionTag(CompoundTag tag) {
		if (tag.contains(FireHoseConnectionAccess.CONNECTION_TAG, Tag.TAG_COMPOUND))
			return tag.getCompound(FireHoseConnectionAccess.CONNECTION_TAG);

		CompoundTag connection = new CompoundTag();
		tag.put(FireHoseConnectionAccess.CONNECTION_TAG, connection);
		return connection;
	}

	private static void clearNativePartner(CompoundTag tag) {
		if (tag == null)
			return;

		if (tag.contains(FireHoseConnectionAccess.CONNECTION_TAG, Tag.TAG_COMPOUND)) {
			CompoundTag connection = tag.getCompound(FireHoseConnectionAccess.CONNECTION_TAG);
			connection.remove(TAG_PARTNER_POS);
			connection.remove(TAG_PARTNER_SUB_LEVEL);
			connection.remove(TAG_PARTNER_ENDPOINT_ID);
			connection.remove(TAG_PARTNER_MOVING);
		}

		tag.remove(TAG_PARTNER_POS);
		tag.remove(TAG_PARTNER_SUB_LEVEL);
		tag.remove(TAG_PARTNER_ENDPOINT_ID);
		tag.remove(TAG_PARTNER_MOVING);
		tag.remove(BLUEPRINT_TAG);
	}

	private static CompoundTag writeBlockRef(BlockRef ref) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("SubLevelId", ref.subLevelId());
		tag.putLong("LocalPos", ref.localPos().asLong());
		return tag;
	}

	private Optional<BlockRef> readBlockRef(CompoundTag tag) {
		if (!tag.contains("SubLevelId") || !tag.contains("LocalPos"))
			return Optional.empty();
		return Optional.of(new BlockRef(
			api.newBlockRef(tag.getInt("SubLevelId"), BlockPos.of(tag.getLong("LocalPos"))),
			tag.getInt("SubLevelId"),
			BlockPos.of(tag.getLong("LocalPos"))));
	}

	private static CompoundTag writeSubLevelRef(int subLevelId, UUID sourceUuid) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("SubLevelId", subLevelId);
		tag.putUUID("SourceUuid", sourceUuid);
		return tag;
	}

	record Api(
		Class<?> blockMapperClass,
		Constructor<?> blockRefConstructor,
		Method saveBlockEntity,
		Method registryAccess,
		Method blockRef,
		Method subLevelRef,
		Method mapBlock,
		Method mapSubLevel,
		Method blueprintSubLevelId,
		Method session,
		Method placedSubLevel,
		Method blockRefSubLevelId,
		Method blockRefLocalPos,
		Method subLevelId,
		Method sourceUuid
	) {
		Object newBlockRef(int subLevelId, BlockPos localPos) {
			try {
				return blockRefConstructor.newInstance(subLevelId, localPos);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Unable to create BlueprintBlockRef", e);
			}
		}
	}

	private record BlockRef(Object nativeRef, int subLevelId, BlockPos localPos) {
	}

	private record SubLevelRef(int subLevelId, UUID sourceUuid) {
	}
}
