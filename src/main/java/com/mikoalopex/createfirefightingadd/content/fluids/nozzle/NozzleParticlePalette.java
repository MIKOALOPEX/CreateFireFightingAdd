package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import org.joml.Vector3f;

public record NozzleParticlePalette(Entry first, Entry second, Entry third) {
	public static final int MAX_WEIGHT = 100;

	public NozzleParticlePalette {
		first = first == null ? Entry.EMPTY : first;
		second = second == null ? Entry.EMPTY : second;
		third = third == null ? Entry.EMPTY : third;
		int total = first.weight() + second.weight() + third.weight();
		if (total > MAX_WEIGHT) {
			first = first.withWeight(scaleWeight(first.weight(), total));
			second = second.withWeight(scaleWeight(second.weight(), total));
			third = third.withWeight(Math.max(0, MAX_WEIGHT - first.weight() - second.weight()));
		}
	}

	public static NozzleParticlePalette single(int color) {
		return new NozzleParticlePalette(new Entry(color, 100), Entry.EMPTY, Entry.EMPTY);
	}

	public int primaryColor() {
		return first.weight() > 0 ? first.color() : pick(0).color();
	}

	public Entry get(int index) {
		return switch (index) {
			case 1 -> second;
			case 2 -> third;
			default -> first;
		};
	}

	public NozzleParticlePalette withColor(int index, int color) {
		return switch (index) {
			case 1 -> new NozzleParticlePalette(first, second.withColor(color), third);
			case 2 -> new NozzleParticlePalette(first, second, third.withColor(color));
			default -> new NozzleParticlePalette(first.withColor(color), second, third);
		};
	}

	public NozzleParticlePalette withWeight(int index, int weight) {
		weight = Math.clamp(weight, 0, MAX_WEIGHT);
		int remaining = MAX_WEIGHT - weight;
		return switch (index) {
			case 1 -> {
				Entry[] other = fitPair(first, third, remaining);
				yield new NozzleParticlePalette(other[0], second.withWeight(weight), other[1]);
			}
			case 2 -> {
				Entry[] other = fitPair(first, second, remaining);
				yield new NozzleParticlePalette(other[0], other[1], third.withWeight(weight));
			}
			default -> {
				Entry[] other = fitPair(second, third, remaining);
				yield new NozzleParticlePalette(first.withWeight(weight), other[0], other[1]);
			}
		};
	}

	public int remainingFor(int index) {
		return MAX_WEIGHT - switch (index) {
			case 1 -> first.weight() + third.weight();
			case 2 -> first.weight() + second.weight();
			default -> second.weight() + third.weight();
		};
	}

	public Vector3f pickVector(RandomSource random) {
		return toVector(pick(random.nextInt(MAX_WEIGHT)).color());
	}

	public Vector3f primaryVector() {
		return toVector(primaryColor());
	}

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		list.add(first.write());
		list.add(second.write());
		list.add(third.write());
		tag.put("Entries", list);
		return tag;
	}

	public static NozzleParticlePalette read(CompoundTag tag, int fallbackColor) {
		if (tag == null || !tag.contains("Entries", Tag.TAG_LIST))
			return single(fallbackColor);
		ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
		return new NozzleParticlePalette(
			list.size() > 0 ? Entry.read(list.getCompound(0), fallbackColor, 100) : new Entry(fallbackColor, 100),
			list.size() > 1 ? Entry.read(list.getCompound(1), brighten(fallbackColor), 0) : Entry.EMPTY,
			list.size() > 2 ? Entry.read(list.getCompound(2), darken(fallbackColor), 0) : Entry.EMPTY);
	}

	private Entry pick(int roll) {
		if (roll < first.weight())
			return first;
		roll -= first.weight();
		if (roll < second.weight())
			return second;
		roll -= second.weight();
		if (roll < third.weight())
			return third;
		if (first.weight() > 0)
			return first;
		if (second.weight() > 0)
			return second;
		return third.weight() > 0 ? third : first;
	}

	static Vector3f toVector(int rgb) {
		return new Vector3f(((rgb >> 16) & 0xFF) / 255f,
			((rgb >> 8) & 0xFF) / 255f,
			(rgb & 0xFF) / 255f);
	}

	private static int scaleWeight(int weight, int total) {
		return Math.clamp((int) Math.floor(weight * (MAX_WEIGHT / (double) total)), 0, MAX_WEIGHT);
	}

	private static Entry[] fitPair(Entry first, Entry second, int remaining) {
		int total = first.weight() + second.weight();
		if (total <= remaining)
			return new Entry[] { first, second };
		if (remaining <= 0 || total <= 0)
			return new Entry[] { first.withWeight(0), second.withWeight(0) };
		int firstWeight = Math.clamp((int) Math.floor(first.weight() * (remaining / (double) total)), 0, remaining);
		return new Entry[] { first.withWeight(firstWeight), second.withWeight(remaining - firstWeight) };
	}

	private static int brighten(int rgb) {
		return mix(rgb, 0xFFFFFF, 0.35);
	}

	private static int darken(int rgb) {
		return mix(rgb, 0x000000, 0.25);
	}

	static int mix(int from, int to, double amount) {
		amount = Math.clamp(amount, 0.0, 1.0);
		int r = (int) (((from >> 16) & 0xFF) * (1.0 - amount) + ((to >> 16) & 0xFF) * amount);
		int g = (int) (((from >> 8) & 0xFF) * (1.0 - amount) + ((to >> 8) & 0xFF) * amount);
		int b = (int) ((from & 0xFF) * (1.0 - amount) + (to & 0xFF) * amount);
		return (r << 16) | (g << 8) | b;
	}

	public record Entry(int color, int weight) {
		static final Entry EMPTY = new Entry(0xFFFFFF, 0);

		public Entry {
			color &= 0xFFFFFF;
			weight = Math.clamp(weight, 0, MAX_WEIGHT);
		}

		Entry withColor(int color) {
			return new Entry(color, weight);
		}

		Entry withWeight(int weight) {
			return new Entry(color, weight);
		}

		CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("Color", color);
			tag.putInt("Weight", weight);
			return tag;
		}

		static Entry read(CompoundTag tag, int fallbackColor, int fallbackWeight) {
			int color = tag.contains("Color") ? tag.getInt("Color") : fallbackColor;
			int weight = tag.contains("Weight") ? tag.getInt("Weight") : fallbackWeight;
			return new Entry(color, weight);
		}
	}
}
