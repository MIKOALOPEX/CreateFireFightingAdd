package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copycat endpoint state. The route-owned body surface is shared by the
 * connection; each endpoint owns its port surface and payment.
 *
 * <p>Filling order is body first, then port. The wrench recovers the same way:
 * one surface per click, body first and port second. Only an endpoint with both
 * surfaces empty falls through to Create's native rotation.</p>
 */
public class CopycatFireHoseBlockEntity extends FireHoseBlockEntity {
	public final HoseMaterial portMaterial = new HoseMaterial();
	private final HoseMaterial pendingBody = new HoseMaterial();

	public CopycatFireHoseBlockEntity(BlockPos pos, BlockState state) {
		this(CreateFireFightingAdd.COPYCAT_FIRE_HOSE_BE.get(), pos, state);
	}

	public CopycatFireHoseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean isCopycat() {
		return true;
	}

	public HoseMaterial portMaterial() {
		return portMaterial;
	}

	public boolean bodyFilled() {
		HoseRoute route = authoritativeRoute();
		return (route != null && CopycatFireHose.bodyFilled(route)) || pendingBody.filled();
	}

	public BlockState bodyMaterialState() {
		HoseRoute route = authoritativeRoute();
		if (route != null) {
			BlockState state = CopycatFireHose.material(level, route);
			if (state != null)
				return state;
		}
		return pendingBody.filled() ? pendingBody.state() : null;
	}

	/** Fills the shared body first, then the port; cycles only after both exist. */
	public boolean applyCopycatMaterial(BlockState material, ItemStack held, Player player) {
		if (level == null || level.isClientSide || held.isEmpty())
			return false;
		HoseRoute route = authoritativeRoute();
		BlockState body = route != null ? CopycatFireHose.material(level, route) : null;
		if (body == null)
			body = pendingBody.filled() ? pendingBody.state() : null;

		if (body == null)
			return fillBody(material, held, player);

		if (!portMaterial.filled()) {
			ItemStack payment = player.isCreative() ? ItemStack.EMPTY : held.copyWithCount(1);
			if (!player.isCreative())
				held.shrink(1);
			portMaterial.fill(material, payment);
			level.playSound(null, worldPosition, material.getSoundType().getPlaceSound(),
				net.minecraft.sounds.SoundSource.BLOCKS, 1, .8f);
			notifyUpdate();
			return true;
		}

		// Both surfaces exist. Cycle the body first so an unconnected pending
		// body and a connected route body behave the same way.
		boolean bodyMatches = body.is(material.getBlock());
		boolean portMatches = portMaterial.state().is(material.getBlock());
		boolean changed;
		if (bodyMatches) {
			if (route != null)
				changed = CopycatFireHose.cycleBody(level, route);
			else
				changed = pendingBody.cycle();
		} else if (portMatches) {
			changed = portMaterial.cycle();
		} else {
			return true;
		}
		if (!changed)
			return true;
		if (bodyMatches && route != null)
			HoseRoutes.get(level).publish(level, route);
		notifyUpdate();
		return true;
	}

	private boolean fillBody(BlockState material, ItemStack held, Player player) {
		ItemStack payment = player.isCreative() ? ItemStack.EMPTY : held.copyWithCount(1);
		HoseRoute target = authoritativeRoute();
		if (target == null && getPairedHose() != null && level instanceof net.minecraft.server.level.ServerLevel)
			target = HoseRoutes.get(level).ensure(this);
		if (!player.isCreative())
			held.shrink(1);
		if (target != null) {
			CopycatFireHose.fillBody(level, target, material, payment);
			HoseRoutes.get(level).publish(level, target);
		} else {
			pendingBody.fill(material, payment);
			notifyUpdate();
		}
		level.playSound(null, worldPosition, material.getSoundType().getPlaceSound(),
			net.minecraft.sounds.SoundSource.BLOCKS, 1, .8f);
		return true;
	}

	public boolean hasCopycatMaterials() {
		return portMaterial.filled() || pendingBody.filled()
			|| (route != null && CopycatFireHose.bodyFilled(route));
	}

	/**
	 * Wrench sequence: one surface per call, body before port, so the click that
	 * recovers a material never also rotates the endpoint.
	 */
	public boolean releaseCopycatMaterials(BlockPos dropPos, Player player) {
		if (!(level instanceof net.minecraft.server.level.ServerLevel))
			return hasCopycatMaterials();
		HoseRoute route = authoritativeRoute();
		if (route != null && CopycatFireHose.bodyFilled(route)) {
			ItemStack body = CopycatFireHose.clearBody(level, route);
			HoseRoutes.get(level).publish(level, route);
			returnPayment(player, dropPos, body);
			notifyUpdate();
			return true;
		}
		if (pendingBody.filled()) {
			returnPayment(player, dropPos, pendingBody.clear());
			notifyUpdate();
			return true;
		}
		if (portMaterial.filled()) {
			returnPayment(player, dropPos, portMaterial.clear());
			notifyUpdate();
			return true;
		}
		return false;
	}

	private void returnPayment(Player player, BlockPos dropPos, ItemStack stack) {
		if (stack.isEmpty())
			return;
		if (player != null && !player.isSpectator())
			player.getInventory().placeItemBackInInventory(stack);
		else
			Block.popResource(level, dropPos, stack);
	}

	/** Break order follows route disposal (body) then this endpoint's port. */
	public void dropPortMaterial(BlockPos dropPos) {
		if (!(level instanceof net.minecraft.server.level.ServerLevel))
			return;
		ItemStack port = portMaterial.clear();
		if (!port.isEmpty())
			Block.popResource(level, dropPos, port);
	}

	private HoseRoute authoritativeRoute() {
		if (route == null || !(level instanceof net.minecraft.server.level.ServerLevel))
			return null;
		return HoseRoutes.get(level).find(route.id);
	}

	private void applyPendingBody() {
		if (!pendingBody.filled() || !(level instanceof net.minecraft.server.level.ServerLevel))
			return;
		HoseRoute target = authoritativeRoute();
		if (target == null && getPairedHose() != null)
			target = HoseRoutes.get(level).ensure(this);
		if (target == null)
			return;
		if (CopycatFireHose.bodyFilled(target)) {
			// The connection already owns a body payment; return the duplicate
			// instead of keeping a second authority for the same surface.
			ItemStack duplicate = pendingBody.clear();
			if (!duplicate.isEmpty())
				Block.popResource(level, worldPosition, duplicate);
			notifyUpdate();
			return;
		}
		CopycatFireHose.fillBody(level, target, pendingBody.state(), pendingBody.payment());
		pendingBody.clear();
		HoseRoutes.get(level).publish(level, target);
		notifyUpdate();
	}

	@Override
	void onFireHoseConnectionChanged() {
		super.onFireHoseConnectionChanged();
		if (level instanceof net.minecraft.server.level.ServerLevel && getFireHosePartnerPos() != null)
			// A connected copycat always owns a route so its body survives Create
			// and Sable movement even before a material is filled.
			HoseRoutes.get(level).ensure(this);
		applyPendingBody();
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		applyPendingBody();
	}

	@Override
	public void remove() {
		ItemStack pending = assembling ? ItemStack.EMPTY : pendingBody.clear();
		super.remove();
		if (!pending.isEmpty() && level != null && !level.isClientSide)
			Block.popResource(level, worldPosition, pending);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.put("CopycatPort", portMaterial.write(registries));
		// The client needs the pending body as well: otherwise an unconnected
		// endpoint would predict a rotation while its first wrench click is
		// actually recovering that body payment on the server.
		tag.put("CopycatPendingBody", pendingBody.write(registries));
	}

	/** Contraption captures can use the safe form; keep the moving port and pending body. */
	@Override
	public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
		super.writeSafe(tag, registries);
		tag.put("CopycatPort", portMaterial.write(registries));
		tag.put("CopycatPendingBody", pendingBody.write(registries));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		portMaterial.read(tag.getCompound("CopycatPort"), registries);
		pendingBody.read(tag.getCompound("CopycatPendingBody"), registries);
	}
}
