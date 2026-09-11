package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** A visual path. The first and last nodes remain the only fluid endpoints. */
public final class HoseRoute {
    public final UUID id;
    public final List<Node> nodes = new ArrayList<>();
    public ResourceLocation appearance = FireHoseAppearances.DEFAULT;
    public long revision;

    public HoseRoute(UUID id) {
        this.id = id;
    }

    public int index(UUID nodeId) {
        for (int i = 0; i < nodes.size(); i++)
            if (nodes.get(i).id.equals(nodeId))
                return i;
        return -1;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("Revision", revision);
        tag.putString("Appearance", appearance.toString());
        ListTag list = new ListTag();
        for (Node node : nodes)
            list.add(node.write());
        tag.put("Nodes", list);
        return tag;
    }

    public static HoseRoute read(CompoundTag tag) {
        if (!tag.hasUUID("Id"))
            return null;
        HoseRoute route = new HoseRoute(tag.getUUID("Id"));
        route.revision = tag.getLong("Revision");
        ResourceLocation appearance = ResourceLocation.tryParse(tag.getString("Appearance"));
        if (appearance != null)
            route.appearance = appearance;
        for (Tag entry : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            CompoundTag node = (CompoundTag) entry;
            if (node.hasUUID("Id"))
                route.nodes.add(Node.read(node));
        }
        return route;
    }

    public record Node(UUID id, BlockPos pos, UUID subLevel, boolean endpoint,
                       Direction face, float angle, boolean reversed) {
        public static Node of(FireHoseBlockEntity hose) {
            return new Node(hose.getFireHoseEndpointId(), hose.getBlockPos(),
                SableStructureCompat.containingSubLevelId(hose.getLevel(), hose.getBlockPos()),
                true, hose.getFacingDirection(), 0, false);
        }

        public static Node of(HoseBracketBlockEntity bracket) {
            return new Node(bracket.nodeId, bracket.getBlockPos(),
                SableStructureCompat.containingSubLevelId(bracket.getLevel(), bracket.getBlockPos()),
                false, bracket.getBlockState().getValue(HoseBracketBlock.FACING),
                bracket.angle(), bracket.reversed);
        }

        public BlockEntity resolve(Level level) {
            if (!level.isLoaded(pos))
                return null;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FireHoseBlockEntity hose && endpoint && id.equals(hose.getFireHoseEndpointId()))
                return hose;
            if (be instanceof HoseBracketBlockEntity bracket && !endpoint && id.equals(bracket.nodeId))
                return bracket;
            return null;
        }

        public Vec3 normal() {
            if (endpoint)
                return Vec3.atLowerCornerOf(face.getNormal());
            return HoseBracketBlock.rotate(face, angle, new Vec3(0, 0, reversed ? -1 : 1));
        }

        public Vec3 center() {
            return pos.getCenter();
        }

        public Vec3 up() {
            if (endpoint)
                return face.getAxis().isVertical() ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
            return HoseBracketBlock.rotate(face, angle, new Vec3(0, 1, 0));
        }

        public Vec3 port(boolean outgoing) {
            if (endpoint)
                return center().add(normal().scale(-0.25));
            return center().add(normal().scale(outgoing ? 0.25 : -0.25));
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            SableStructureCompat.writeLinkedBlock(tag, "Pos", "SubLevel", pos, subLevel);
            tag.putBoolean("Endpoint", endpoint);
            tag.putInt("Face", face.get3DDataValue());
            tag.putFloat("Angle", angle);
            tag.putBoolean("Reversed", reversed);
            return tag;
        }

        private static Node read(CompoundTag tag) {
            var ref = SableStructureCompat.readLinkedBlock(tag, "Pos", "SubLevel");
            return new Node(tag.getUUID("Id"), ref.pos() == null ? BlockPos.ZERO : ref.pos(), ref.subLevelId(),
                tag.getBoolean("Endpoint"), Direction.from3DDataValue(tag.getInt("Face")),
                tag.getFloat("Angle"), tag.getBoolean("Reversed"));
        }
    }
}
