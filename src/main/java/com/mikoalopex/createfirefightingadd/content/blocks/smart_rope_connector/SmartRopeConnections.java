package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Creates mixed native/smart links without changing native connector capacity. */
public final class SmartRopeConnections {
	private SmartRopeConnections() {}

	public static boolean isSmart(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof SmartRopeConnectorBlockEntity;
	}

	public static boolean canAttach(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof SmartRopeConnectorBlockEntity smart)
			return smart.hasCapacity();
		if (be instanceof SmartBlockEntity smartBe) {
			RopeStrandHolderBehavior holder = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
			return holder != null && !holder.isAttached();
		}
		return false;
	}

	public static boolean connect(Level level, BlockPos firstPos, BlockPos secondPos) {
		if (level.isClientSide || firstPos.equals(secondPos) || !canAttach(level, firstPos) || !canAttach(level, secondPos))
			return false;

		BlockEntity first = level.getBlockEntity(firstPos);
		BlockEntity second = level.getBlockEntity(secondPos);
		if (first instanceof RopeWinchBlockEntity && second instanceof RopeWinchBlockEntity)
			return false;

		boolean connected;
		if (first instanceof SmartRopeConnectorBlockEntity smartA
				&& second instanceof SmartRopeConnectorBlockEntity smartB) {
			connected = connectSmartPair(smartA, smartB);
		} else if (first instanceof SmartRopeConnectorBlockEntity smart) {
			connected = connectNativeToSmart(nativeHolder(second), smart);
		} else if (second instanceof SmartRopeConnectorBlockEntity smart) {
			connected = connectNativeToSmart(nativeHolder(first), smart);
		} else {
			return false;
		}

		if (connected) {
			level.playSound(null, firstPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
			level.playSound(null, secondPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
		}
		return connected;
	}

	private static boolean connectSmartPair(SmartRopeConnectorBlockEntity ownerBe,
			SmartRopeConnectorBlockEntity targetBe) {
		RopeStrandHolderBehavior owner = new RopeStrandHolderBehavior(ownerBe);
		RopeStrandHolderBehavior target = new RopeStrandHolderBehavior(targetBe);
		if (!createRope(owner, target))
			return false;
		ServerRopeStrand strand = owner.getOwnedStrand();
		if (strand == null)
			return false;
		ownerBe.addOwned(owner);
		targetBe.addExternal(strand.getUUID(), ownerBe.getBlockPos());
		return true;
	}

	private static boolean connectNativeToSmart(@Nullable RopeStrandHolderBehavior nativeHolder,
			SmartRopeConnectorBlockEntity smart) {
		if (nativeHolder == null || nativeHolder.isAttached())
			return false;
		RopeStrandHolderBehavior smartTarget = new RopeStrandHolderBehavior(smart);
		if (!createRope(nativeHolder, smartTarget))
			return false;
		ServerRopeStrand strand = nativeHolder.getOwnedStrand();
		if (strand == null)
			return false;
		smart.addExternal(strand.getUUID(), nativeHolder.blockEntity.getBlockPos());
		return true;
	}

	@Nullable
	private static RopeStrandHolderBehavior nativeHolder(BlockEntity be) {
		if (!(be instanceof SmartBlockEntity smartBe) || be instanceof SmartRopeConnectorBlockEntity)
			return null;
		return smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
	}

    // Simulated 1.3 added the drop-item argument; neither call replaces an existing link here.
    private static boolean createRope(RopeStrandHolderBehavior owner, RopeStrandHolderBehavior target) {
        try {
            try {
                return (boolean) RopeStrandHolderBehavior.class.getMethod("createRope",
                    RopeStrandHolderBehavior.class, boolean.class).invoke(owner, target, false);
            } catch (NoSuchMethodException oldVersion) {
                return (boolean) RopeStrandHolderBehavior.class.getMethod("createRope",
                    RopeStrandHolderBehavior.class).invoke(owner, target);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot create a Simulated rope", error);
        }
    }

    static void destroyRope(RopeStrandHolderBehavior owner, net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.phys.Vec3 position, boolean returnItem) {
        try {
            try {
                RopeStrandHolderBehavior.class.getMethod("destroyRope", net.minecraft.server.level.ServerPlayer.class,
                    net.minecraft.world.phys.Vec3.class, boolean.class).invoke(owner, player, position, returnItem);
            } catch (NoSuchMethodException oldVersion) {
                RopeStrandHolderBehavior.class.getMethod("destroyRope", net.minecraft.server.level.ServerPlayer.class,
                    net.minecraft.world.phys.Vec3.class).invoke(owner, player, position);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot remove a Simulated rope", error);
        }
    }
}
