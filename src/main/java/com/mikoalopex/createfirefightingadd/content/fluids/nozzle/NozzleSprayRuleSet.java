package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

public record NozzleSprayRuleSet(List<NozzleSprayRule> rules) {
	public static final String TAG = "NozzleSprayRules";
	public static final NozzleSprayRuleSet EMPTY = new NozzleSprayRuleSet(List.of());
	private static final int MAX_RULES = 64;

	public NozzleSprayRuleSet {
		rules = List.copyOf(rules);
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	public Optional<NozzleSprayRule> find(FluidStack stack) {
		if (stack == null || stack.isEmpty())
			return Optional.empty();
		for (NozzleSprayRule rule : rules) {
			if (!rule.locked() && rule.matches(stack))
				return Optional.of(rule);
		}
		return Optional.empty();
	}

	public NozzleSprayRuleSet withRule(NozzleSprayRule rule) {
		if (rule == null || rule.target().isEmpty())
			return this;
		List<NozzleSprayRule> copy = new ArrayList<>(rules);
		for (int i = 0; i < copy.size(); i++) {
			if (copy.get(i).matches(rule.target())) {
				copy.set(i, rule);
				return new NozzleSprayRuleSet(copy);
			}
		}
		if (copy.size() < MAX_RULES)
			copy.add(rule);
		return new NozzleSprayRuleSet(copy);
	}

	public NozzleSprayRuleSet without(int index) {
		if (index < 0 || index >= rules.size())
			return this;
		List<NozzleSprayRule> copy = new ArrayList<>(rules);
		copy.remove(index);
		return copy.isEmpty() ? EMPTY : new NozzleSprayRuleSet(copy);
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (NozzleSprayRule rule : rules) {
			if (!rule.target().isEmpty())
				list.add(rule.write(registries));
		}
		tag.put("Rules", list);
		return tag;
	}

	public static NozzleSprayRuleSet read(HolderLookup.Provider registries, CompoundTag tag) {
		if (tag == null || !tag.contains("Rules", Tag.TAG_LIST))
			return EMPTY;
		ListTag list = tag.getList("Rules", Tag.TAG_COMPOUND);
		List<NozzleSprayRule> rules = new ArrayList<>();
		for (int i = 0; i < list.size() && rules.size() < MAX_RULES; i++) {
			NozzleSprayRule rule = NozzleSprayRule.read(registries, list.getCompound(i));
			if (!rule.target().isEmpty())
				rules.add(rule);
		}
		return rules.isEmpty() ? EMPTY : new NozzleSprayRuleSet(rules);
	}

	public static NozzleSprayRuleSet fromStack(ItemStack stack, HolderLookup.Provider registries) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return EMPTY;
		CompoundTag root = data.copyTag();
		if (!root.contains(TAG, Tag.TAG_COMPOUND))
			return EMPTY;
		return read(registries, root.getCompound(TAG));
	}

	public static void setOnStack(ItemStack stack, HolderLookup.Provider registries, NozzleSprayRuleSet rules) {
		CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (rules == null || rules.isEmpty())
			root.remove(TAG);
		else
			root.put(TAG, rules.write(registries));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}
}
