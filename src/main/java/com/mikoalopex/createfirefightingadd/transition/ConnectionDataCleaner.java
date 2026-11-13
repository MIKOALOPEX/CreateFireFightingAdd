package com.mikoalopex.createfirefightingadd.transition;

import java.util.*;
import net.minecraft.nbt.*;

/** Two passes: discover our ropes, then remove only connection state. Never edits block palettes. */
public final class ConnectionDataCleaner {
    private static final String MOD = "createfirefightingadd:";
    private static final Set<String> LINKS = Set.of(
        "PartnerPos", "PartnerSubLevel", "PartnerEndpointId", "PartnerMoving", "ManagedByConnectorPos",
        "ManagedByConnectorSubLevel", "ManagedByConnectorId", "HoseRoute", "FireHoseConnection",
        "CouplingPartner", "CouplingPair", "CouplingEstablished", "CouplingAligning", "CouplingReference",
        "RenderLink", "FluidRenderLink", "FluidConnected", "HoseDeployment", "OwnedRopes", "ExternalRopes",
        "ConnectionCount", "OwnStrand", "HasRopeAttached", "Strand", "BracketStrand", "BracketFirstLength", "PhysicalAnchors",
        "PhysicalOwner", "PhysicalOwnerSubLevel", "PhysicalEndpoint", "PhysicalStrand", "PhysicalSegment", "PhysicalLength", "PhysicalAngle",
        "PinnedRope", "PinnedNode", "TailSegments", "SlidingTailLength", "RopeOwner", "SearchUntil",
        "ManagedRope", "ClaimedEndpoint", "Partner", "PartnerStructure", "Selected",
        "AttachedPos", "AttachedSubLevel", "AttachedEndpointId", "CachedPos", "CachedSubLevel", "CachedEndpointId");
    private final Set<UUID> ropes = new HashSet<>();
    public int removedConstraints;
    public int disconnectedRecords;

    private static boolean own(CompoundTag tag) { return tag.getString("id").startsWith(MOD); }
    private static UUID uuid(CompoundTag tag, String key) {
        if (tag.hasUUID(key)) return tag.getUUID(key);
        try { return UUID.fromString(tag.getString(key)); } catch (IllegalArgumentException ex) { return null; }
    }
    private void collectRopes(Tag tag) {
        if (tag instanceof CompoundTag c) {
            for (String key : List.of("HasRopeAttached", "PinnedRope", "BracketStrand", "PhysicalStrand", "ManagedRope")) {
                UUID id = uuid(c, key); if (id != null) ropes.add(id);
            }
            UUID strand = uuid(c.getCompound("Strand"), "uuid"); if (strand != null) ropes.add(strand);
            for (String key : List.of("OwnedRopes", "ExternalRopes"))
                for (Tag entry : c.getList(key, Tag.TAG_COMPOUND)) {
                    UUID id = uuid((CompoundTag) entry, "Id"); if (id != null) ropes.add(id);
                }
            for (String key : c.getAllKeys()) collectRopes(c.get(key));
        } else if (tag instanceof ListTag list) for (Tag child : list) collectRopes(child);
    }
    public void discover(Tag tag) {
        if (tag instanceof CompoundTag c) {
            if (own(c)) collectRopes(c);
            for (String key : c.getAllKeys()) discover(c.get(key));
        } else if (tag instanceof ListTag list) for (Tag child : list) discover(child);
    }
    public void discoverDocument(String name, CompoundTag root) {
        if (name.equals("createfirefightingadd_rope_hook_attachments.dat") || name.equals("createfirefightingadd_rope_hook_attachments.dat_old")) {
            for (Tag entry : root.getCompound("data").getList("Ropes", Tag.TAG_STRING)) {
                try { ropes.add(UUID.fromString(entry.getAsString())); } catch (IllegalArgumentException ignored) {}
            }
        }
        discover(root);
    }
    public void cleanDocument(String name, CompoundTag root) {
        clean(root);
        String base = name.replaceFirst("\\.dat(?:_old)?$", "");
        if (Set.of("createfirefightingadd_rope_hook_attachments", "createfirefightingadd_hose_routes",
                "createfirefightingadd_couplings", "createfirefightingadd_fluid_coupling_couplings").contains(base))
            root.put("data", new CompoundTag());
    }
    private boolean ourConstraint(CompoundTag record) {
        CompoundTag owner = record.getCompound("key").getCompound("owner");
        return owner.getString("type").equals("synaxis:system") && Set.of(
            MOD + "ball_coupling", MOD + "fluid_coupling").contains(owner.getString("system"));
    }
    public void clean(Tag tag) {
        if (tag instanceof CompoundTag c) {
            // Create's mounted-storage codec is dispatched by type, not block-entity id.
            if (c.getString("type").equals(MOD + "fire_hose")) {
                boolean changed = false;
                for (String key : List.of("partner", "partner_sublevel", "partner_moving", "partner_endpoint_id"))
                    if (c.contains(key)) { c.remove(key); changed = true; }
                if (changed) { c.putBoolean("controller", false); disconnectedRecords++; }
            }
            ListTag records = c.getList("records", Tag.TAG_COMPOUND);
            for (int i = records.size() - 1; i >= 0; i--) if (ourConstraint(records.getCompound(i))) {
                records.remove(i); removedConstraints++;
            }
            UUID attached = uuid(c, "HasRopeAttached"), strand = uuid(c.getCompound("Strand"), "uuid");
            if (own(c)) {
                boolean changed = false;
                for (String key : LINKS) if (c.contains(key)) { c.remove(key); changed = true; }
                if (c.contains("Controller")) c.putBoolean("Controller", false);
                if (changed) disconnectedRecords++;
            } else if ((attached != null && ropes.contains(attached)) || (strand != null && ropes.contains(strand))) {
                c.remove("OwnStrand"); c.remove("HasRopeAttached"); c.remove("Strand"); disconnectedRecords++;
            }
            CompoundTag components = c.getCompound("components");
            components.remove(MOD + "smart_rope_selection");
            for (String key : new ArrayList<>(c.getAllKeys())) clean(c.get(key));
        } else if (tag instanceof ListTag list) for (Tag child : list) clean(child);
    }
}
