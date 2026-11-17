package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class CopycatHoseRouteTest {
    public static void main(String[] args) {
        HoseRoute route = new HoseRoute(UUID.randomUUID());
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        route.copycat.put("Material", state);
        CompoundTag payment = new CompoundTag();
        payment.putString("id", "minecraft:stone");
        payment.putInt("count", 1);
        route.copycat.put("Item", payment);
        route.revision = 17;
        route.size = 4;
        route.copycatRoute = true;
        HoseRoute restored = HoseRoute.read(route.write());
        if (restored == null || !restored.id.equals(route.id) || restored.revision != 17
            || restored.size != 4 || !restored.copycatRoute || !restored.copycat.equals(route.copycat))
            throw new AssertionError("Route material lost on reload");
        restored.copycat.getCompound("Item").putInt("count", 2);
        if (route.copycat.getCompound("Item").getInt("count") != 1)
            throw new AssertionError("Endpoint snapshot shares payment storage");
        var legacy = route.write();
        legacy.remove("Copycat");
        legacy.remove("Size");
        legacy.remove("CopycatRoute");
        HoseRoute legacyRoute = HoseRoute.read(legacy);
        if (!legacyRoute.copycat.isEmpty()) throw new AssertionError("Legacy route gained material");
        if (legacyRoute.size != 8) throw new AssertionError("Legacy route did not default to size 8");
        if (legacyRoute.copycatRoute) throw new AssertionError("Legacy route gained a copycat marker");
        if (!CopycatFireHose.bodyFilled(restored)) throw new AssertionError("Filled route reported empty");
        if (CopycatFireHose.bodyFilled(legacyRoute)) throw new AssertionError("Empty route reported filled");
        var sized = route.write();
        sized.putInt("Size", 6);
        if (HoseRoute.read(sized).size != 6) throw new AssertionError("Route size lost on reload");
        System.out.println("Copycat hose route persistence, size and snapshot isolation passed");
    }
}
