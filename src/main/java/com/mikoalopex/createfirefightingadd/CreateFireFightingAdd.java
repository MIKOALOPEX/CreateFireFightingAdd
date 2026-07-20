package com.mikoalopex.createfirefightingadd;

import java.util.EnumMap;
import java.util.List;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnectorBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnectorBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseItem;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseItemHandler;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseMountedFluidStorageType;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseMovementBehaviour;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseRenderer;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseDynamicRenderer;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_pole.FirePoleBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.flow_meter.FlowMeterRenderer;
import com.mikoalopex.createfirefightingadd.content.equipment.backtank.MultipurposeBacktankBlock;
import com.mikoalopex.createfirefightingadd.content.equipment.backtank.MultipurposeBacktankBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.backtank.MultipurposeBacktankItem;
import com.mikoalopex.createfirefightingadd.content.equipment.backtank.MultipurposeBacktankRenderer;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlock;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetMenu;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetMovementBehaviour;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetRenderer;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetScreen;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleClientExtensions;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleClientHandler;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerEntityRenderer;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerItem;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleHoseRenderer;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleSprayHandler;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.AbstractSprayDeviceBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.BucketControllerBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.ConeNozzleBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.ConeNozzleBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.ConeNozzleRenderer;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.FlatNozzleBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.FlatNozzleBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.FlatNozzleRenderer;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayClientSounds;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.SprayDebugRenderer;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.SprayDeviceMountedFluidStorageType;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.SprayDeviceMovementBehaviour;
import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.BoundBlockHighlightHandler;
import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;
import com.mikoalopex.createfirefightingadd.content.items.PneumaticHammerClientExtensions;
import com.mikoalopex.createfirefightingadd.content.items.PneumaticHammerItem;
import com.mikoalopex.createfirefightingadd.content.items.PneumaticHammerItemRenderer;
import com.mikoalopex.createfirefightingadd.content.items.WaterIntakeBindingItem;
import com.mikoalopex.createfirefightingadd.content.kinetics.pump.HighPressurePumpBlock;
import com.mikoalopex.createfirefightingadd.content.kinetics.pump.HighPressurePumpBlockEntity;
import com.mikoalopex.createfirefightingadd.content.kinetics.pump.HighPressurePumpRenderer;
import com.mikoalopex.createfirefightingadd.content.kinetics.turbine.PipelineTurbineBlock;
import com.mikoalopex.createfirefightingadd.content.kinetics.turbine.PipelineTurbineBlockEntity;
import com.mikoalopex.createfirefightingadd.content.ponder.CreateFireFightingPonderPlugin;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.mojang.logging.LogUtils;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

// Spray behaviour is inspired by Create Diesel Generators' chemical sprayer system (MIT License).
@Mod(CreateFireFightingAdd.MODID)
public class CreateFireFightingAdd {
	public static final String MODID = "createfirefightingadd";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static ResourceLocation path(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
	public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
	public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
	public static final DeferredRegister<MountedFluidStorageType<?>> MOUNTED_FLUID_STORAGE_TYPES =
		DeferredRegister.create(CreateRegistries.MOUNTED_FLUID_STORAGE_TYPE, MODID);

	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MULTIPURPOSE_BACKTANK_ARMOR_MATERIAL =
		ARMOR_MATERIALS.register("multipurpose_backtank", CreateFireFightingAdd::createMultipurposeBacktankArmorMaterial);

	private static ArmorMaterial createMultipurposeBacktankArmorMaterial() {
		EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		for (ArmorItem.Type type : ArmorItem.Type.values())
			defense.put(type, 0);
		defense.put(ArmorItem.Type.CHESTPLATE, 3);

		return new ArmorMaterial(defense, 15, SoundEvents.ARMOR_EQUIP_NETHERITE,
			() -> Ingredient.of(Items.NETHERITE_INGOT),
			List.of(new ArmorMaterial.Layer(path("multipurpose_backtank"))),
			3.0F, 0.1F);
	}

	private static BlockBehaviour.Properties fluidPipeProperties() {
		return BlockBehaviour.Properties.ofFullCopy(AllBlocks.FLUID_PIPE.get()).noOcclusion();
	}

	public static final DeferredBlock<HighPressurePumpBlock> HIGH_PRESSURE_PUMP = BLOCKS.register("high_pressure_pump",
		() -> new HighPressurePumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).noOcclusion()));

	public static final DeferredItem<BlockItem> HIGH_PRESSURE_PUMP_ITEM = ITEMS.registerSimpleBlockItem("high_pressure_pump", HIGH_PRESSURE_PUMP);

	public static final DeferredBlock<ConeNozzleBlock> CONE_NOZZLE = BLOCKS.register("cone_nozzle",
		() -> new ConeNozzleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()));

	public static final DeferredItem<BlockItem> CONE_NOZZLE_ITEM = ITEMS.registerSimpleBlockItem("cone_nozzle", CONE_NOZZLE);

	public static final DeferredBlock<FlatNozzleBlock> FLAT_NOZZLE = BLOCKS.register("flat_nozzle",
		() -> new FlatNozzleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()));

	public static final DeferredItem<BlockItem> FLAT_NOZZLE_ITEM = ITEMS.registerSimpleBlockItem("flat_nozzle", FLAT_NOZZLE);

	public static final DeferredBlock<BucketControllerBlock> BUCKET_CONTROLLER = BLOCKS.register("bucket_controller",
		() -> new BucketControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).noOcclusion()));

	public static final DeferredItem<WaterIntakeBindingItem> BUCKET_CONTROLLER_ITEM = ITEMS.register("bucket_controller",
		() -> new WaterIntakeBindingItem(BUCKET_CONTROLLER.get(), new Item.Properties()));

	public static final DeferredBlock<WaterIntakeBlock> WATER_INTAKE = BLOCKS.register("water_intake",
		() -> new WaterIntakeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f).noOcclusion()));

	public static final DeferredItem<BlockItem> WATER_INTAKE_ITEM = ITEMS.registerSimpleBlockItem("water_intake", WATER_INTAKE);

	public static final DeferredBlock<FireHoseBlock> FIRE_HOSE = BLOCKS.register("fire_hose",
		() -> SableStructureCompat.createFireHoseBlock(
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()));

	public static final DeferredItem<FireHoseItem> FIRE_HOSE_ITEM = ITEMS.register("fire_hose",
		() -> new FireHoseItem(new Item.Properties()));

	public static final DeferredBlock<FireHoseConnectorBlock> FIRE_HOSE_CONNECTOR = BLOCKS.register("fire_hose_connector",
		() -> new FireHoseConnectorBlock(fluidPipeProperties()));

	public static final DeferredItem<BlockItem> FIRE_HOSE_CONNECTOR_ITEM =
		ITEMS.registerSimpleBlockItem("fire_hose_connector", FIRE_HOSE_CONNECTOR);

	public static final DeferredBlock<PipelineTurbineBlock> PIPELINE_TURBINE = BLOCKS.register("pipeline_turbine",
		() -> new PipelineTurbineBlock(fluidPipeProperties()));

	public static final DeferredItem<BlockItem> PIPELINE_TURBINE_ITEM =
		ITEMS.registerSimpleBlockItem("pipeline_turbine", PIPELINE_TURBINE);

	public static final DeferredBlock<FirePoleBlock> FIRE_POLE = BLOCKS.register("fire_pole",
		() -> new FirePoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

	public static final DeferredItem<BlockItem> FIRE_POLE_ITEM =
		ITEMS.registerSimpleBlockItem("fire_pole", FIRE_POLE);

	public static final DeferredBlock<FlowMeterBlock> FLUID_FLOW_METER = BLOCKS.register("fluid_flow_meter",
		() -> new FlowMeterBlock(fluidPipeProperties()));

	public static final DeferredItem<BlockItem> FLUID_FLOW_METER_ITEM =
		ITEMS.registerSimpleBlockItem("fluid_flow_meter", FLUID_FLOW_METER);

	public static final DeferredItem<PneumaticHammerItem> PNEUMATIC_HAMMER_ITEM = ITEMS.register("pneumatic_hammer",
		() -> new PneumaticHammerItem(new Item.Properties()
			.attributes(AxeItem.createAttributes(Tiers.DIAMOND, 5.0f, -3.0f))));

	public static final DeferredBlock<MultipurposeBacktankBlock> MULTIPURPOSE_BACKTANK = BLOCKS.register("multipurpose_backtank",
		() -> new MultipurposeBacktankBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(5.0f)
			.sound(SoundType.NETHERITE_BLOCK)
			.noOcclusion()));

	public static final DeferredItem<BacktankItem.BacktankBlockItem> MULTIPURPOSE_BACKTANK_PLACEABLE_ITEM =
		ITEMS.register("multipurpose_backtank_placeable",
			() -> new BacktankItem.BacktankBlockItem(MULTIPURPOSE_BACKTANK.get(),
				CreateFireFightingAdd::getMultipurposeBacktankActualItem, new Item.Properties()));

	public static final DeferredItem<MultipurposeBacktankItem> MULTIPURPOSE_BACKTANK_ITEM =
		ITEMS.register("multipurpose_backtank",
			() -> new MultipurposeBacktankItem(MULTIPURPOSE_BACKTANK_ARMOR_MATERIAL, new Item.Properties(),
				ResourceLocation.fromNamespaceAndPath("create", "copper_diving"),
				() -> MULTIPURPOSE_BACKTANK_PLACEABLE_ITEM.get()));

	public static final DeferredBlock<FireHydrantCabinetBlock> FIRE_HYDRANT_CABINET = BLOCKS.register("fire_hydrant_cabinet",
		() -> new FireHydrantCabinetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(3.0f)
			.noOcclusion()));

	public static final DeferredItem<BlockItem> FIRE_HYDRANT_CABINET_ITEM =
		ITEMS.registerSimpleBlockItem("fire_hydrant_cabinet", FIRE_HYDRANT_CABINET);

	public static final DeferredItem<HandheldNozzleControllerItem> HANDHELD_NOZZLE_CONTROLLER_ITEM =
		ITEMS.register("handheld_nozzle_controller", () -> new HandheldNozzleControllerItem(new Item.Properties().stacksTo(1)));

	public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_HANDHELD_NOZZLE_CONTROLLER_ITEM =
		ITEMS.register("incomplete_handheld_nozzle_controller", () -> new SequencedAssemblyItem(new Item.Properties()));

	public static final DeferredHolder<EntityType<?>, EntityType<HandheldNozzleControllerEntity>> HANDHELD_NOZZLE_CONTROLLER_ENTITY =
		ENTITY_TYPES.register("handheld_nozzle_controller",
			() -> EntityType.Builder.<HandheldNozzleControllerEntity>of(HandheldNozzleControllerEntity::new, MobCategory.MISC)
				.sized(0.65f, 0.35f)
				.clientTrackingRange(8)
				.updateInterval(3)
				.build("handheld_nozzle_controller"));

	// Internal flow monitor kept for registry compatibility. The player-facing
	// meter is registered as fluid_flow_meter and shares this block entity type.
	public static final DeferredBlock<FlowMeterBlock> FLOW_METER = BLOCKS.register("flow_meter",
		() -> new FlowMeterBlock(fluidPipeProperties()));

	public static final DeferredItem<BlockItem> FLOW_METER_ITEM = ITEMS.registerSimpleBlockItem("flow_meter", FLOW_METER);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowMeterBlockEntity>> FLOW_METER_BE =
		BLOCK_ENTITY_TYPES.register("flow_meter",
			() -> BlockEntityType.Builder.of(FlowMeterBlockEntity::new, FLOW_METER.get(), FLUID_FLOW_METER.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultipurposeBacktankBlockEntity>> MULTIPURPOSE_BACKTANK_BE =
		BLOCK_ENTITY_TYPES.register("multipurpose_backtank",
			() -> BlockEntityType.Builder.of(MultipurposeBacktankBlockEntity::new, MULTIPURPOSE_BACKTANK.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireHydrantCabinetBlockEntity>> FIRE_HYDRANT_CABINET_BE =
		BLOCK_ENTITY_TYPES.register("fire_hydrant_cabinet",
			() -> BlockEntityType.Builder.of(FireHydrantCabinetBlockEntity::new, FIRE_HYDRANT_CABINET.get()).build(null));

	public static final DeferredHolder<MenuType<?>, MenuType<FireHydrantCabinetMenu>> FIRE_HYDRANT_CABINET_MENU =
		MENU_TYPES.register("fire_hydrant_cabinet",
			() -> IMenuTypeExtension.create(FireHydrantCabinetMenu::new));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HighPressurePumpBlockEntity>> HIGH_PRESSURE_PUMP_BE =
		BLOCK_ENTITY_TYPES.register("high_pressure_pump",
			() -> BlockEntityType.Builder.of(HighPressurePumpBlockEntity::new, HIGH_PRESSURE_PUMP.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipelineTurbineBlockEntity>> PIPELINE_TURBINE_BE =
		BLOCK_ENTITY_TYPES.register("pipeline_turbine",
			() -> BlockEntityType.Builder.of(PipelineTurbineBlockEntity::new, PIPELINE_TURBINE.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConeNozzleBlockEntity>> CONE_NOZZLE_BE =
		BLOCK_ENTITY_TYPES.register("cone_nozzle",
			() -> {
				var legacies = RemapManager.getLegacyBlocksFor("cone_nozzle");
				Block[] all = new Block[1 + legacies.size()];
				all[0] = CONE_NOZZLE.get();
				for (int i = 0; i < legacies.size(); i++)
					all[i + 1] = (Block) legacies.get(i).get();
				return BlockEntityType.Builder.of(SableStructureCompat::createConeNozzleBlockEntity, all).build(null);
			});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlatNozzleBlockEntity>> FLAT_NOZZLE_BE =
		BLOCK_ENTITY_TYPES.register("flat_nozzle",
			() -> {
				var legacies = RemapManager.getLegacyBlocksFor("flat_nozzle");
				Block[] all = new Block[1 + legacies.size()];
				all[0] = FLAT_NOZZLE.get();
				for (int i = 0; i < legacies.size(); i++)
					all[i + 1] = (Block) legacies.get(i).get();
				return BlockEntityType.Builder.of(SableStructureCompat::createFlatNozzleBlockEntity, all).build(null);
			});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BucketControllerBlockEntity>> BUCKET_CONTROLLER_BE =
		BLOCK_ENTITY_TYPES.register("bucket_controller",
			() -> BlockEntityType.Builder.of(SableStructureCompat::createBucketControllerBlockEntity, BUCKET_CONTROLLER.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterIntakeBlockEntity>> WATER_INTAKE_BE =
		BLOCK_ENTITY_TYPES.register("water_intake",
			() -> BlockEntityType.Builder.of(SableStructureCompat::createWaterIntakeBlockEntity, WATER_INTAKE.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireHoseBlockEntity>> FIRE_HOSE_BE =
		BLOCK_ENTITY_TYPES.register("fire_hose",
			() -> BlockEntityType.Builder.of(SableStructureCompat::createFireHoseBlockEntity, FIRE_HOSE.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireHoseConnectorBlockEntity>> FIRE_HOSE_CONNECTOR_BE =
		BLOCK_ENTITY_TYPES.register("fire_hose_connector",
			() -> BlockEntityType.Builder.of(FireHoseConnectorBlockEntity::new, FIRE_HOSE_CONNECTOR.get()).build(null));

	public static final DeferredHolder<MountedFluidStorageType<?>, SprayDeviceMountedFluidStorageType> SPRAY_DEVICE_MOUNTED_FLUID_STORAGE =
		MOUNTED_FLUID_STORAGE_TYPES.register("spray_device", SprayDeviceMountedFluidStorageType::new);

	public static final DeferredHolder<MountedFluidStorageType<?>, FireHoseMountedFluidStorageType> FIRE_HOSE_MOUNTED_FLUID_STORAGE =
		MOUNTED_FLUID_STORAGE_TYPES.register("fire_hose", FireHoseMountedFluidStorageType::new);

	public static final DeferredHolder<SoundEvent, SoundEvent> PNEUMATIC_HAMMER_CHARGE_SOUND =
		SOUND_EVENTS.register("pneumatic_hammer_charge",
			() -> SoundEvent.createVariableRangeEvent(path("pneumatic_hammer_charge")));

	public static final DeferredHolder<SoundEvent, SoundEvent> NOZZLE_SPRAY_SOUND =
		SOUND_EVENTS.register("nozzle_spray",
			() -> SoundEvent.createVariableRangeEvent(path("nozzle_spray")));

	public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_HYDRANT_CABINET_OPEN_SOUND =
		SOUND_EVENTS.register("fire_hydrant_cabinet_open",
			() -> SoundEvent.createVariableRangeEvent(path("fire_hydrant_cabinet_open")));

	public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_HYDRANT_CABINET_CLOSE_SOUND =
		SOUND_EVENTS.register("fire_hydrant_cabinet_close",
			() -> SoundEvent.createVariableRangeEvent(path("fire_hydrant_cabinet_close")));

	private static Item getMultipurposeBacktankActualItem() {
		return MULTIPURPOSE_BACKTANK_ITEM.get();
	}

	private static ItemStack createFullMultipurposeBacktankStack() {
		return MultipurposeBacktankItem.withFullAir(new ItemStack(MULTIPURPOSE_BACKTANK_ITEM.get()));
	}

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("tab",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createfirefightingadd"))
			.icon(() -> FIRE_HOSE_ITEM.get().getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(HIGH_PRESSURE_PUMP_ITEM.get());
				output.accept(CONE_NOZZLE_ITEM.get());
				output.accept(FLAT_NOZZLE_ITEM.get());
				output.accept(BUCKET_CONTROLLER_ITEM.get());
				output.accept(WATER_INTAKE_ITEM.get());
				output.accept(FIRE_HOSE_ITEM.get());
				output.accept(FIRE_HOSE_CONNECTOR_ITEM.get());
				output.accept(PIPELINE_TURBINE_ITEM.get());
				output.accept(FIRE_POLE_ITEM.get());
				output.accept(FLUID_FLOW_METER_ITEM.get());
				output.accept(FIRE_HYDRANT_CABINET_ITEM.get());
				output.accept(PNEUMATIC_HAMMER_ITEM.get());
				output.accept(createFullMultipurposeBacktankStack());
				output.accept(HANDHELD_NOZZLE_CONTROLLER_ITEM.get());
			}).build());

	public CreateFireFightingAdd(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::commonSetup);
		modEventBus.addListener(this::registerCapabilities);
		modEventBus.addListener(this::registerEntityAttributes);

		RemapManager.registerAll(blockName -> switch (blockName) {
			case "cone_nozzle" -> new ConeNozzleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion());
			case "flat_nozzle" -> new FlatNozzleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion());
			default -> throw new IllegalArgumentException("[CreateFireFightingAdd] Unknown block in remap: " + blockName);
		});

		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		BLOCK_ENTITY_TYPES.register(modEventBus);
		MENU_TYPES.register(modEventBus);
		SOUND_EVENTS.register(modEventBus);
		ARMOR_MATERIALS.register(modEventBus);
		ENTITY_TYPES.register(modEventBus);
		MOUNTED_FLUID_STORAGE_TYPES.register(modEventBus);

		NeoForge.EVENT_BUS.register(this);

		modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
		modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		BlockStressValues.IMPACTS.register(HIGH_PRESSURE_PUMP.get(), () -> 8.0);
		BlockStressValues.IMPACTS.register(WATER_INTAKE.get(), () -> 4.0);
		BlockStressValues.IMPACTS.register(MULTIPURPOSE_BACKTANK.get(), () -> 4.0);
		BlockStressValues.CAPACITIES.register(PIPELINE_TURBINE.get(), () -> 4.0);
		event.enqueueWork(CreateFireFightingAdd::registerCreateContraptionCompat);
		if (ModList.get().isLoaded("sable_schematic_api"))
			event.enqueueWork(CreateFireFightingAdd::registerSableSchematicCompat);
	}

	private static void registerCreateContraptionCompat() {
		MovementBehaviour.REGISTRY.register(CONE_NOZZLE.get(), SprayDeviceMovementBehaviour.INSTANCE);
		MovementBehaviour.REGISTRY.register(FLAT_NOZZLE.get(), SprayDeviceMovementBehaviour.INSTANCE);
		MovementBehaviour.REGISTRY.register(BUCKET_CONTROLLER.get(), SprayDeviceMovementBehaviour.INSTANCE);
		MovementBehaviour.REGISTRY.register(FIRE_HOSE.get(), FireHoseMovementBehaviour.INSTANCE);
		MovementBehaviour.REGISTRY.register(FIRE_HYDRANT_CABINET.get(), FireHydrantCabinetMovementBehaviour.INSTANCE);

		MountedFluidStorageType.REGISTRY.register(CONE_NOZZLE.get(), SPRAY_DEVICE_MOUNTED_FLUID_STORAGE.get());
		MountedFluidStorageType.REGISTRY.register(FLAT_NOZZLE.get(), SPRAY_DEVICE_MOUNTED_FLUID_STORAGE.get());
		MountedFluidStorageType.REGISTRY.register(BUCKET_CONTROLLER.get(), SPRAY_DEVICE_MOUNTED_FLUID_STORAGE.get());
		MountedFluidStorageType.REGISTRY.register(FIRE_HOSE.get(), FIRE_HOSE_MOUNTED_FLUID_STORAGE.get());
	}

	private static void registerSableSchematicCompat() {
		try {
			Class<?> compat = Class.forName(
				"com.mikoalopex.createfirefightingadd.integration.sableschematic.SableSchematicCompat",
				true,
				CreateFireFightingAdd.class.getClassLoader());
			compat.getMethod("register").invoke(null);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			CreateFireFightingAdd.LOGGER.warn("Sable Blueprint compatibility could not be registered.", e);
		}
	}

	private void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			CONE_NOZZLE_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			FLAT_NOZZLE_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			BUCKET_CONTROLLER_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			WATER_INTAKE_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			FIRE_HOSE_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			FLOW_METER_BE.get(),
			(be, context) -> null
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			MULTIPURPOSE_BACKTANK_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			FIRE_HYDRANT_CABINET_BE.get(),
			(be, context) -> be.getFluidHandler(context)
		);
		event.registerBlockEntity(
			Capabilities.ItemHandler.BLOCK,
			FIRE_HYDRANT_CABINET_BE.get(),
			(be, context) -> be.getItemHandler(context)
		);
	}

	private void registerEntityAttributes(EntityAttributeCreationEvent event) {
		event.put(HANDHELD_NOZZLE_CONTROLLER_ENTITY.get(), HandheldNozzleControllerEntity.createAttributes().build());
	}

	@SubscribeEvent
	public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().isClientSide()) return;
		var be = event.getLevel().getBlockEntity(event.getPos());
		if (be instanceof AbstractSprayDeviceBlockEntity nozzle
			&& nozzle.tryIgniteWithItem(event.getEntity(), event.getHand())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		PneumaticHammerItem.handleBreakSpeed(event);
	}

	@SubscribeEvent
	public void onHarvestCheck(PlayerEvent.HarvestCheck event) {
		PneumaticHammerItem.handleHarvestCheck(event);
	}

	@SubscribeEvent
	public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		HandheldNozzleSprayHandler.cancelHandheldBreak(event);
		PneumaticHammerItem.handleChargedBlockBreak(event);
	}

	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent.Post event) {
		if (!event.getEntity().level().isClientSide)
			HandheldNozzleSprayHandler.serverTick(event.getEntity());
	}

	@SubscribeEvent
	public void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
			HandheldNozzleSprayHandler.clearPlayer(player);
	}

	@SubscribeEvent
	public void onLivingDamagePre(LivingDamageEvent.Pre event) {
		PneumaticHammerItem.boostChargedAttackDamage(event);
	}

	@SubscribeEvent
	public void onLivingDamagePost(LivingDamageEvent.Post event) {
		PneumaticHammerItem.applyChargedAttackArea(event);
	}

	@SubscribeEvent
	public void onEntityJoinLevel(EntityJoinLevelEvent event) {
		HandheldNozzleControllerEntity.tryConvertDroppedItem(event);
	}

	@SubscribeEvent
	public void onItemToss(ItemTossEvent event) {
		HandheldNozzleControllerEntity.tryConvertTossedItem(event);
	}

	@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
			PartialModels.registerAdditional(event);
		}

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			BoundBlockHighlightHandler.init();
			PartialModels.init();
			PonderIndex.addPlugin(new CreateFireFightingPonderPlugin());
			registerCreateTooltips();

			SimpleBlockEntityVisualizer.builder(WATER_INTAKE_BE.get())
				.factory(SingleAxisRotatingVisual::shaft)
				.apply();

			SimpleBlockEntityVisualizer.builder(HIGH_PRESSURE_PUMP_BE.get())
				.factory(SingleAxisRotatingVisual.of(PartialModels.HIGH_PRESSURE_PUMP_COG))
				.apply();

			SimpleBlockEntityVisualizer.builder(PIPELINE_TURBINE_BE.get())
				.factory(SingleAxisRotatingVisual::shaft)
				.apply();

			ItemBlockRenderTypes.setRenderLayer(BUCKET_CONTROLLER.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CONE_NOZZLE.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(FLOW_METER.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(FLUID_FLOW_METER.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(MULTIPURPOSE_BACKTANK.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(FIRE_HYDRANT_CABINET.get(), RenderType.cutout());
		}

		private static void registerCreateTooltips() {
			registerCreateTooltip(PNEUMATIC_HAMMER_ITEM.get());
			registerCreateTooltip(MULTIPURPOSE_BACKTANK_ITEM.get());
			registerCreateTooltip(HANDHELD_NOZZLE_CONTROLLER_ITEM.get());
			registerCreateTooltip(FIRE_POLE_ITEM.get());
		}

		private static void registerCreateTooltip(Item item) {
			TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, Palette.STANDARD_CREATE));
		}

		@SubscribeEvent
		public static void registerMenuScreens(RegisterMenuScreensEvent event) {
			event.register(FIRE_HYDRANT_CABINET_MENU.get(), FireHydrantCabinetScreen::new);
		}

		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			event.registerEntityRenderer(HANDHELD_NOZZLE_CONTROLLER_ENTITY.get(), HandheldNozzleControllerEntityRenderer::new);
			event.registerBlockEntityRenderer(CONE_NOZZLE_BE.get(), ctx -> new ConeNozzleRenderer());
			event.registerBlockEntityRenderer(FLAT_NOZZLE_BE.get(), ctx -> new FlatNozzleRenderer());
			event.registerBlockEntityRenderer(WATER_INTAKE_BE.get(), ctx -> new ShaftRenderer<>(ctx));
			event.registerBlockEntityRenderer(HIGH_PRESSURE_PUMP_BE.get(), ctx -> new HighPressurePumpRenderer(ctx));
			event.registerBlockEntityRenderer(PIPELINE_TURBINE_BE.get(), ctx -> new ShaftRenderer<>(ctx));
			event.registerBlockEntityRenderer(FIRE_HOSE_BE.get(), FireHoseRenderer::new);
			event.registerBlockEntityRenderer(FLOW_METER_BE.get(), FlowMeterRenderer::new);
			event.registerBlockEntityRenderer(MULTIPURPOSE_BACKTANK_BE.get(), MultipurposeBacktankRenderer::new);
			event.registerBlockEntityRenderer(FIRE_HYDRANT_CABINET_BE.get(), FireHydrantCabinetRenderer::new);
		}

		@SubscribeEvent
		public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
			event.registerItem(new PneumaticHammerClientExtensions(), PNEUMATIC_HAMMER_ITEM.get());
			event.registerItem(new HandheldNozzleClientExtensions(), HANDHELD_NOZZLE_CONTROLLER_ITEM.get());
		}

		@SubscribeEvent
		public static void wrapCustomItemModels(ModelEvent.ModifyBakingResult event) {
			ModelResourceLocation location = ModelResourceLocation.inventory(path("pneumatic_hammer"));
			BakedModel model = event.getModels().get(location);
			if (model != null && !(model instanceof CustomRenderedItemModel))
				event.getModels().put(location, new CustomRenderedItemModel(model));
			ModelResourceLocation handheld = ModelResourceLocation.inventory(path("handheld_nozzle_controller"));
			BakedModel handheldModel = event.getModels().get(handheld);
			if (handheldModel != null && !(handheldModel instanceof CustomRenderedItemModel))
				event.getModels().put(handheld, new CustomRenderedItemModel(handheldModel));
		}

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			NozzleSprayClientSounds.clientTick();
			if (mc.player != null) {
				FireHoseItemHandler.INSTANCE.clientTick(mc.level, mc.player);
				HandheldNozzleClientHandler.clientTick();
			}
		}

		@SubscribeEvent
		public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
			if (event.isUseItem() && HandheldNozzleClientHandler.shouldSuppressControllerUseAnimation()) {
				event.setSwingHand(false);
				HandheldNozzleClientHandler.suppressUseSwing();
			}
			if (!event.isAttack() || !HandheldNozzleClientHandler.shouldCancelAttackInput())
				return;
			event.setSwingHand(false);
			event.setCanceled(true);
		}

		@SubscribeEvent
		public static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
			SprayDebugRenderer.renderDynamicSprays(event);
			FireHoseDynamicRenderer.render(event);
			HandheldNozzleHoseRenderer.render(event);
		}

		@SubscribeEvent
		public static void onMouseInput(InputEvent.MouseButton.Post event) {
			HandheldNozzleClientHandler.onMouseButton(event.getButton(), event.getAction());
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
					&& event.getAction() == GLFW.GLFW_PRESS
					&& mc.player != null
					&& mc.screen == null
					&& PneumaticHammerItem.isCharged(mc.player.getMainHandItem())) {
				PneumaticHammerItemRenderer.triggerReleaseSpin();
			}
			if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
					&& event.getAction() == GLFW.GLFW_PRESS
					&& mc.player != null
					&& mc.screen == null) {
				FireHoseItemHandler.INSTANCE.onUse(0, GLFW.GLFW_PRESS, mc.options.keyUse);
			}
		}

	}
}
