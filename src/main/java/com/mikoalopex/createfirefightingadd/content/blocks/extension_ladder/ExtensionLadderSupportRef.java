package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public record ExtensionLadderSupportRef(@Nullable BlockPos blockPos, @Nullable UUID subLevelId,
										Vec3 localContact, Vec3 worldContact) {
	public boolean isStillPresent(ExtensionLadderBlockEntity ladder) {
		return ladder.hasCollisionNearLocal(localContact);
	}

	public void write(CompoundTag tag, String prefix) {
		if (blockPos != null)
			tag.putLong(prefix + "Pos", blockPos.asLong());
		if (subLevelId != null)
			tag.putUUID(prefix + "SubLevel", subLevelId);
		tag.putDouble(prefix + "LocalX", localContact.x);
		tag.putDouble(prefix + "LocalY", localContact.y);
		tag.putDouble(prefix + "LocalZ", localContact.z);
		tag.putDouble(prefix + "WorldX", worldContact.x);
		tag.putDouble(prefix + "WorldY", worldContact.y);
		tag.putDouble(prefix + "WorldZ", worldContact.z);
	}

	@Nullable
	public static ExtensionLadderSupportRef read(CompoundTag tag, String prefix) {
		if (!tag.contains(prefix + "LocalX") || !tag.contains(prefix + "WorldX"))
			return null;
		BlockPos pos = tag.contains(prefix + "Pos") ? BlockPos.of(tag.getLong(prefix + "Pos")) : null;
		UUID subLevel = tag.hasUUID(prefix + "SubLevel") ? tag.getUUID(prefix + "SubLevel") : null;
		Vec3 local = new Vec3(tag.getDouble(prefix + "LocalX"), tag.getDouble(prefix + "LocalY"),
			tag.getDouble(prefix + "LocalZ"));
		Vec3 world = new Vec3(tag.getDouble(prefix + "WorldX"), tag.getDouble(prefix + "WorldY"),
			tag.getDouble(prefix + "WorldZ"));
		return new ExtensionLadderSupportRef(pos, subLevel, local, world);
	}

	public static ExtensionLadderSupportRef at(ExtensionLadderBlockEntity ladder, Vec3 localContact,
											   Vec3 worldContact) {
		BlockPos pos = BlockPos.containing(worldContact);
		UUID subLevel = ladder.getLevel() == null ? null
			: SableStructureCompat.containingSubLevelId(ladder.getLevel(), pos);
		return new ExtensionLadderSupportRef(pos, subLevel, localContact, worldContact);
	}
}
