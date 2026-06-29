package com.mikoalopex.createfirefightingadd.integration.sableschematic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.slf4j.Logger;

/**
 * Optional Sable Schematic API integration.
 */
public final class SableSchematicCompat {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static boolean registered;
	private static boolean disabled;

	private SableSchematicCompat() {
	}

	public static void register() {
		if (registered || disabled)
			return;

		try {
			Api api = Api.inspect();
			Object mapper = FireHoseBlueprintMapper.create(api.mapperApi());
			api.registerMethod().invoke(null, CreateFireFightingAdd.FIRE_HOSE_BE.get(), mapper);
			registered = true;
			LOGGER.info("Registered Create Firefighting Add Sable Blueprint compatibility");
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			disabled = true;
			LOGGER.warn("Sable Blueprint compatibility is unavailable; fire hose blueprint links will be skipped.", e);
		}
	}

	private record Api(FireHoseBlueprintMapper.Api mapperApi, Method registerMethod) {
		static Api inspect() throws ReflectiveOperationException {
			ClassLoader loader = SableSchematicCompat.class.getClassLoader();
			Class<?> mapperRegistry = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry",
				false,
				loader);
			Class<?> blockMapper = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper",
				false,
				loader);
			Class<?> saveContext = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockSaveContext",
				false,
				loader);
			Class<?> placeContext = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext",
				false,
				loader);
			Class<?> placeSession = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession",
				false,
				loader);
			Class<?> blockRef = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockRef",
				false,
				loader);
			Class<?> subLevelRefClass = Class.forName(
				"dev.rew1nd.sableschematicapi.api.blueprint.BlueprintSubLevelRef",
				false,
				loader);

			Method register = mapperRegistry.getMethod("register", BlockEntityType.class, blockMapper);
			Method saveBlockEntity = saveContext.getMethod("blockEntity");
			Method registryAccess = saveContext.getMethod("registryAccess");
			Method saveBlockRef = saveContext.getMethod("blockRef", BlockPos.class);
			Method subLevelRef = saveContext.getMethod("subLevelRef", UUID.class);
			Method mapBlock = findMapBlockMethod(placeContext, blockRef);
			Method mapSubLevel = placeContext.getMethod("mapSubLevel", UUID.class);
			Method blueprintSubLevelId = placeContext.getMethod("blueprintSubLevelId");
			Method session = placeContext.getMethod("session");
			Method placedSubLevel = placeSession.getMethod("placedSubLevel", int.class);
			Constructor<?> blockRefConstructor = blockRef.getConstructor(int.class, BlockPos.class);
			Method blockRefSubLevelId = blockRef.getMethod("subLevelId");
			Method blockRefLocalPos = blockRef.getMethod("localPos");
			Method subLevelId = subLevelRefClass.getMethod("subLevelId");
			Method sourceUuid = findSourceUuidMethod(subLevelRefClass);

			blockMapper.getMethod("save", saveContext, net.minecraft.nbt.CompoundTag.class);
			blockMapper.getMethod("beforeLoadBlockEntity", placeContext, net.minecraft.nbt.CompoundTag.class);
			blockMapper.getMethod("afterLoadBlockEntity", placeContext, BlockEntity.class,
				net.minecraft.nbt.CompoundTag.class);

			return new Api(new FireHoseBlueprintMapper.Api(
				blockMapper,
				blockRefConstructor,
				saveBlockEntity,
				registryAccess,
				saveBlockRef,
				subLevelRef,
				mapBlock,
				mapSubLevel,
				blueprintSubLevelId,
				session,
				placedSubLevel,
				blockRefSubLevelId,
				blockRefLocalPos,
				subLevelId,
				sourceUuid), register);
		}

		private static Method findMapBlockMethod(Class<?> placeContext, Class<?> blockRef) throws NoSuchMethodException {
			try {
				return placeContext.getMethod("mapBlock", blockRef);
			} catch (NoSuchMethodException ignored) {
				return placeContext.getMethod("mapBlockPos", blockRef);
			}
		}

		private static Method findSourceUuidMethod(Class<?> subLevelRef) throws NoSuchMethodException {
			try {
				return subLevelRef.getMethod("sourceUuid");
			} catch (NoSuchMethodException ignored) {
				return subLevelRef.getMethod("sourceUUID");
			}
		}
	}
}
