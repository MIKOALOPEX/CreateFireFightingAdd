package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

public record NozzleSprayRule(
	FluidStack target,
	NozzleParticlePalette particles,
	boolean flammable,
	boolean extinguishing,
	boolean igniting,
	List<ResourceLocation> effects,
	@Nullable ResourceLocation fanProcessingType,
	boolean locked
) {
	private static final int MAX_EFFECTS = 32;

	public NozzleSprayRule {
		target = target == null || target.isEmpty() ? FluidStack.EMPTY : target.copyWithAmount(1);
		particles = particles == null ? NozzleParticleColors.defaultPalette(target) : particles;
		if (extinguishing && igniting)
			igniting = false;
		effects = effects == null ? List.of() : List.copyOf(effects.stream().limit(MAX_EFFECTS).toList());
	}

	public static NozzleSprayRule of(FluidStack target) {
		return new NozzleSprayRule(target, NozzleParticleColors.defaultPalette(target), false, false, false,
			List.of(), null, false);
	}

	public int color() {
		return particles.primaryColor();
	}

	public NozzleSprayRule withParticleColor(int index, int color) {
		return new NozzleSprayRule(target, particles.withColor(index, color), flammable, extinguishing, igniting,
			effects, fanProcessingType, locked);
	}

	public NozzleSprayRule withParticleWeight(int index, int weight) {
		return new NozzleSprayRule(target, particles.withWeight(index, weight), flammable, extinguishing, igniting,
			effects, fanProcessingType, locked);
	}

	public NozzleSprayRule withParticles(NozzleParticlePalette particles) {
		return new NozzleSprayRule(target, particles, flammable, extinguishing, igniting,
			effects, fanProcessingType, locked);
	}

	public NozzleSprayRule withFlags(boolean flammable, boolean extinguishing, boolean igniting) {
		if (extinguishing && igniting)
			igniting = false;
		return new NozzleSprayRule(target, particles, flammable, extinguishing, igniting,
			effects, fanProcessingType, locked);
	}

	public NozzleSprayRule withEffects(List<ResourceLocation> effects) {
		return new NozzleSprayRule(target, particles, flammable, extinguishing, igniting,
			effects, fanProcessingType, locked);
	}

	public NozzleSprayRule withFanProcessing(@Nullable ResourceLocation fanProcessingType) {
		return new NozzleSprayRule(target, particles, flammable, extinguishing, igniting,
			effects, fanProcessingType, locked);
	}

	public NozzleSprayRule asLocked() {
		return new NozzleSprayRule(target, particles, flammable, extinguishing, igniting,
			effects, fanProcessingType, true);
	}

	public boolean matches(FluidStack stack) {
		if (target.isEmpty() || stack == null || stack.isEmpty())
			return false;
		return FluidStack.isSameFluidSameComponents(target, stack);
	}

	CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.put("Target", target.saveOptional(registries));
		tag.put("Particles", particles.write());
		tag.putBoolean("Flammable", flammable);
		tag.putBoolean("Extinguishing", extinguishing);
		tag.putBoolean("Igniting", igniting);
		tag.putBoolean("Locked", locked);
		ListTag effectList = new ListTag();
		for (ResourceLocation effect : effects)
			effectList.add(StringTag.valueOf(effect.toString()));
		tag.put("Effects", effectList);
		if (fanProcessingType != null)
			tag.putString("FanProcessing", fanProcessingType.toString());
		return tag;
	}

	static NozzleSprayRule read(HolderLookup.Provider registries, CompoundTag tag) {
		FluidStack target = tag.contains("Target", Tag.TAG_COMPOUND)
			? FluidStack.parseOptional(registries, tag.getCompound("Target"))
			: FluidStack.EMPTY;
		int fallbackColor = tag.contains("Color") ? tag.getInt("Color") : NozzleParticleColors.baseColor(target);
		NozzleParticlePalette particles = tag.contains("Particles", Tag.TAG_COMPOUND)
			? NozzleParticlePalette.read(tag.getCompound("Particles"), fallbackColor)
			: NozzleParticlePalette.single(fallbackColor);
		boolean flammable = tag.getBoolean("Flammable");
		boolean extinguishing = tag.getBoolean("Extinguishing");
		boolean igniting = tag.getBoolean("Igniting") && !extinguishing;
		boolean locked = tag.getBoolean("Locked") || tag.getBoolean("PotionLocked");
		List<ResourceLocation> effects = new ArrayList<>();
		if (tag.contains("Effects", Tag.TAG_LIST)) {
			ListTag list = tag.getList("Effects", Tag.TAG_STRING);
			for (int i = 0; i < list.size() && effects.size() < MAX_EFFECTS; i++) {
				ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
				if (id != null)
					effects.add(id);
			}
		}
		ResourceLocation fanProcessing = null;
		if (tag.contains("FanProcessing", Tag.TAG_STRING))
			fanProcessing = ResourceLocation.tryParse(tag.getString("FanProcessing"));
		return new NozzleSprayRule(target, particles, flammable, extinguishing, igniting,
			effects, fanProcessing, locked);
	}
}
