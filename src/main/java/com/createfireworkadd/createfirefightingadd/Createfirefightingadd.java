package com.createfireworkadd.createfirefightingadd;

import com.createfireworkadd.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.createfireworkadd.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.blocks.fire_hose.FireHoseItem;
import com.createfireworkadd.createfirefightingadd.content.blocks.fire_hose.FireHoseItemHandler;
import com.createfireworkadd.createfirefightingadd.content.blocks.fire_hose.FireHoseRenderer;
import com.createfireworkadd.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlock;
import com.createfireworkadd.createfirefightingadd.content.blocks.flow_meter.FlowMeterBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.AbstractSprayDeviceBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.BucketControllerBlock;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.ConeNozzleBlock;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.ConeNozzleBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.ConeNozzleRenderer;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.FlatNozzleBlock;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.FlatNozzleBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.fluids.nozzle.FlatNozzleRenderer;
import com.createfireworkadd.createfirefightingadd.content.fluids.water_intake.BoundBlockHighlightHandler;
import com.createfireworkadd.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlock;
import com.createfireworkadd.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.items.WaterIntakeBindingItem;
import com.createfireworkadd.createfirefightingadd.content.kinetics.pump.HighPressurePumpBlock;
import com.createfireworkadd.createfirefightingadd.content.kinetics.pump.HighPressurePumpBlockEntity;
import com.createfireworkadd.createfirefightingadd.content.kinetics.pump.HighPressurePumpRenderer;
import com.createfireworkadd.createfirefightingadd.content.ponder.CreateFireFightingPonderPlugin;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lwjgl.glfw.GLFW;

// Spray behaviour is inspired by Create Diesel Generators' chemical sprayer system (MIT License).
@Mod(Createfirefightingadd.MODID)
public class Createfirefightingadd {
	public static final String MODID = "createfirefightingadd";

	public static ResourceLocation path(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
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
		() -> new FireHoseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()));

	public static final DeferredItem<FireHoseItem> FIRE_HOSE_ITEM = ITEMS.register("fire_hose",
		() -> new FireHoseItem(new Item.Properties()));
	// Experimental flow monitor. Kept separate from the stable firefighting blocks.
	public static final DeferredBlock<FlowMeterBlock> FLOW_METER = BLOCKS.register("flow_meter",
		() -> new FlowMeterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion()));

	public static final DeferredItem<BlockItem> FLOW_METER_ITEM = ITEMS.registerSimpleBlockItem("flow_meter", FLOW_METER);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowMeterBlockEntity>> FLOW_METER_BE =
		BLOCK_ENTITY_TYPES.register("flow_meter",
			() -> BlockEntityType.Builder.of(FlowMeterBlockEntity::new, FLOW_METER.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HighPressurePumpBlockEntity>> HIGH_PRESSURE_PUMP_BE =
		BLOCK_ENTITY_TYPES.register("high_pressure_pump",
			() -> BlockEntityType.Builder.of(HighPressurePumpBlockEntity::new, HIGH_PRESSURE_PUMP.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConeNozzleBlockEntity>> CONE_NOZZLE_BE =
		BLOCK_ENTITY_TYPES.register("cone_nozzle",
			() -> {
				var legacies = RemapManager.getLegacyBlocksFor("cone_nozzle");
				Block[] all = new Block[1 + legacies.size()];
				all[0] = CONE_NOZZLE.get();
				for (int i = 0; i < legacies.size(); i++)
					all[i + 1] = (Block) legacies.get(i).get();
				return BlockEntityType.Builder.of(ConeNozzleBlockEntity::new, all).build(null);
			});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlatNozzleBlockEntity>> FLAT_NOZZLE_BE =
		BLOCK_ENTITY_TYPES.register("flat_nozzle",
			() -> {
				var legacies = RemapManager.getLegacyBlocksFor("flat_nozzle");
				Block[] all = new Block[1 + legacies.size()];
				all[0] = FLAT_NOZZLE.get();
				for (int i = 0; i < legacies.size(); i++)
					all[i + 1] = (Block) legacies.get(i).get();
				return BlockEntityType.Builder.of(FlatNozzleBlockEntity::new, all).build(null);
			});

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BucketControllerBlockEntity>> BUCKET_CONTROLLER_BE =
		BLOCK_ENTITY_TYPES.register("bucket_controller",
			() -> BlockEntityType.Builder.of(BucketControllerBlockEntity::new, BUCKET_CONTROLLER.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterIntakeBlockEntity>> WATER_INTAKE_BE =
		BLOCK_ENTITY_TYPES.register("water_intake",
			() -> BlockEntityType.Builder.of(WaterIntakeBlockEntity::new, WATER_INTAKE.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireHoseBlockEntity>> FIRE_HOSE_BE =
		BLOCK_ENTITY_TYPES.register("fire_hose",
			() -> BlockEntityType.Builder.of(FireHoseBlockEntity::new, FIRE_HOSE.get()).build(null));


	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("tab",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createfirefightingadd"))
			.icon(() -> HIGH_PRESSURE_PUMP_ITEM.get().getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(HIGH_PRESSURE_PUMP_ITEM.get());
				output.accept(CONE_NOZZLE_ITEM.get());
				output.accept(FLAT_NOZZLE_ITEM.get());
				output.accept(BUCKET_CONTROLLER_ITEM.get());
				output.accept(WATER_INTAKE_ITEM.get());
				output.accept(FIRE_HOSE_ITEM.get());
			}).build());

	public Createfirefightingadd(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::commonSetup);
		modEventBus.addListener(this::registerCapabilities);

		RemapManager.registerAll(blockName -> switch (blockName) {
			case "cone_nozzle" -> new ConeNozzleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion());
			case "flat_nozzle" -> new FlatNozzleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).noOcclusion());
			default -> throw new IllegalArgumentException("[CreateFireFightingAdd] Unknown block in remap: " + blockName);
		});

		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		BLOCK_ENTITY_TYPES.register(modEventBus);

		NeoForge.EVENT_BUS.register(this);
		modEventBus.addListener(this::addCreative);

		modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
		modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		BlockStressValues.IMPACTS.register(HIGH_PRESSURE_PUMP.get(), () -> 8.0);
		BlockStressValues.IMPACTS.register(WATER_INTAKE.get(), () -> 4.0);
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
	}

	private void addCreative(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
			event.accept(HIGH_PRESSURE_PUMP_ITEM);
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

	@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			BoundBlockHighlightHandler.init();
			PartialModels.init();
			PonderIndex.addPlugin(new CreateFireFightingPonderPlugin());

			SimpleBlockEntityVisualizer.builder(WATER_INTAKE_BE.get())
				.factory(SingleAxisRotatingVisual::shaft)
				.apply();

			SimpleBlockEntityVisualizer.builder(HIGH_PRESSURE_PUMP_BE.get())
				.factory(SingleAxisRotatingVisual.of(PartialModels.HIGH_PRESSURE_PUMP_COG))
				.apply();

			ItemBlockRenderTypes.setRenderLayer(BUCKET_CONTROLLER.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(CONE_NOZZLE.get(), RenderType.cutout());
			ItemBlockRenderTypes.setRenderLayer(FLOW_METER.get(), RenderType.cutout());
		}

		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			event.registerBlockEntityRenderer(CONE_NOZZLE_BE.get(), ctx -> new ConeNozzleRenderer());
			event.registerBlockEntityRenderer(FLAT_NOZZLE_BE.get(), ctx -> new FlatNozzleRenderer());
			event.registerBlockEntityRenderer(WATER_INTAKE_BE.get(), ctx -> new ShaftRenderer<>(ctx));
			event.registerBlockEntityRenderer(HIGH_PRESSURE_PUMP_BE.get(), ctx -> new HighPressurePumpRenderer(ctx));
			event.registerBlockEntityRenderer(FIRE_HOSE_BE.get(), FireHoseRenderer::new);
		}

		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.player != null) {
				FireHoseItemHandler.INSTANCE.clientTick(mc.level, mc.player);
			}
		}

		@SubscribeEvent
		public static void onMouseInput(InputEvent.MouseButton.Post event) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
					&& event.getAction() == GLFW.GLFW_PRESS
					&& mc.player != null
					&& mc.screen == null) {
				FireHoseItemHandler.INSTANCE.onUse(0, GLFW.GLFW_PRESS, mc.options.keyUse);
			}
		}

	}
}
