package com.mikoalopex.createfirefightingadd.compatibility;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class HoseRelayCompatibilityTest {
    public static final class ForeignEndpoint {
        CompoundTag data = new CompoundTag();
        public CompoundTag createAdditions$getHoseState(Direction face, BlockEntity requester) {
            return face == Direction.NORTH ? data.copy() : null;
        }
    }
    public static final class ForeignPump {
        public float CreateFireFightingAdd$getFluidPressure() { return 384f; }
        public int CreateFireFightingAdd$getPumpRange() { return 257; }
    }
    public static void main(String[] args) {
        // Repeated capability feedback must not steal either side's discovered pump/relay.
        for (int distance : new int[] {1, 2, 16, 257}) for (boolean backTraffic : new boolean[] {false, true})
            for (boolean partnerTraffic : new boolean[] {false, true}) {
                if (HoseRelayCompatibility.externalSourceSide(distance, Integer.MAX_VALUE, backTraffic, partnerTraffic) != -1
                    || HoseRelayCompatibility.externalSourceSide(Integer.MAX_VALUE, distance, backTraffic, partnerTraffic) != -1)
                    throw new AssertionError("Capability feedback displaced a discovered source");
            }
        if (HoseRelayCompatibility.externalSourceSide(Integer.MAX_VALUE, Integer.MAX_VALUE, true, false) != 0
            || HoseRelayCompatibility.externalSourceSide(Integer.MAX_VALUE, Integer.MAX_VALUE, false, true) != 1
            || HoseRelayCompatibility.externalSourceSide(Integer.MAX_VALUE, Integer.MAX_VALUE, false, false) != -1
            || HoseRelayCompatibility.externalSourceSide(Integer.MAX_VALUE, Integer.MAX_VALUE, true, true) != 0)
            throw new AssertionError("External-only input fallback or original local priority changed");

        if (HoseRelayCompatibility.read(new Object(), Direction.NORTH, null) != null)
            throw new AssertionError("Unrelated objects must not supply pressure");
        if (HoseRelayCompatibility.isEndpoint(new Object())) throw new AssertionError("Foreign target captured");
        ForeignEndpoint endpoint = new ForeignEndpoint();
        endpoint.data.putInt("Version", 1);
        endpoint.data.putBoolean("Relay", true);
        endpoint.data.putFloat("Pressure", 384f);
        for (int range : new int[] {1,16,257,8192}) {
            endpoint.data.putInt("Range",range);
            for (boolean pulling : new boolean[] {false,true}) for (boolean external : new boolean[] {false,true}) {
                endpoint.data.putBoolean("Pulling",pulling);endpoint.data.putBoolean("ExternalFill",external);
                var state=HoseRelayCompatibility.read(endpoint,Direction.NORTH,null);
                if (state==null || state.range()!=range || state.pressure()!=384f || state.pulling()!=pulling
                    || state.externalFill()!=external || !state.relay()) throw new AssertionError("Relay metadata changed");
            }
        }
        if (HoseRelayCompatibility.read(endpoint,Direction.SOUTH,null)!=null) throw new AssertionError("Wrong face accepted");
        endpoint.data.putBoolean("Relay", false);
        if (HoseRelayCompatibility.read(endpoint,Direction.NORTH,null).relay()) throw new AssertionError("Input became relay source");
        endpoint.data.putFloat("Pressure",Float.NaN);
        if (HoseRelayCompatibility.read(endpoint,Direction.NORTH,null)!=null) throw new AssertionError("NaN pressure accepted");
        endpoint.data.putFloat("Pressure",384f);endpoint.data.putInt("Version",2);
        if (HoseRelayCompatibility.read(endpoint,Direction.NORTH,null)!=null) throw new AssertionError("Unknown protocol accepted");
        var pump = new ForeignPump();
        if (HoseRelayCompatibility.pumpValue(pump,"CreateFireFightingAdd$getPumpRange").intValue()!=257
            || HoseRelayCompatibility.pumpValue(pump,"CreateFireFightingAdd$getFluidPressure").floatValue()!=384f)
            throw new AssertionError("Custom pump range/pressure changed");
        if (HoseRelayCompatibility.pumpValue(new Object(),"CreateFireFightingAdd$getPumpRange")!=null)
            throw new AssertionError("Absent pump extension must be optional");
        System.out.println("Optional hose relay: absence, direction, exact range, source kind and custom pump passed");
    }
}
