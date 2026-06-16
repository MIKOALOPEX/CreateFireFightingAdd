package com.mikoalopex.createfirefightingadd.integration;

import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class WaterIntakeJadePlugin implements IWailaPlugin {

	public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
		"CreateFireFightingAdd", "water_intake_fluid");

	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(
			new WaterIntakeDataProvider(), WaterIntakeBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(
			new WaterIntakeComponentProvider(), WaterIntakeBlock.class);
	}

	private static class WaterIntakeDataProvider implements IServerDataProvider<BlockAccessor> {

		@Override
		public void appendServerData(CompoundTag data, BlockAccessor accessor) {
			BlockEntity be = accessor.getBlockEntity();
			if (!(be instanceof WaterIntakeBlockEntity intake))
				return;

			IFluidHandler tank = intake.getTankCapability();
			FluidStack fluid = tank.getFluidInTank(0);
			if (!fluid.isEmpty()) {
				data.putInt("fluidAmount", fluid.getAmount());
				data.putInt("fluidCapacity", 1000);
			}
		}

		@Override
		public ResourceLocation getUid() {
			return UID;
		}
	}

	private static class WaterIntakeComponentProvider implements IBlockComponentProvider {

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			CompoundTag data = accessor.getServerData();
			if (!data.contains("fluidAmount"))
				return;

			int amount = data.getInt("fluidAmount");
			int capacity = data.getInt("fluidCapacity");
			tooltip.add(Component.translatable(
				"jade.createfirefightingadd.water_intake_fluid",
				Component.literal(amount + " / " + capacity + " mB")));
		}

		@Override
		public ResourceLocation getUid() {
			return UID;
		}
	}
}
