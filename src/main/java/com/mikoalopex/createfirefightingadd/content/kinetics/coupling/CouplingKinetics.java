package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.*;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

/** Bridges Create kinetic networks while the corresponding physical coupling remains active. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class CouplingKinetics {
    private record Edge(BallCouplingBlockEntity a, BallCouplingBlockEntity b) {}
    private record Totals(float capacity, float stress) {}
    private record Step(BallCouplingBlockEntity node, float factor) {}
    private static final Map<MinecraftServer, CouplingKinetics> SERVERS = new IdentityHashMap<>();
    private final Map<BallCouplingBlockEntity, Edge> edges = new IdentityHashMap<>();
    private final Map<BallCouplingBlockEntity, Float> drives = new IdentityHashMap<>();
    private final Map<KineticNetwork, Totals> additions = new IdentityHashMap<>();
    private boolean nativeRead;
    private boolean updating;
    private final Set<BallCouplingBlockEntity> conflicts = Collections.newSetFromMap(new IdentityHashMap<>());

    private static CouplingKinetics of(KineticBlockEntity be) {
        return be.getLevel() == null || be.getLevel().isClientSide ? null : SERVERS.get(be.getLevel().getServer());
    }
    public static float generated(BallCouplingBlockEntity be) {
        if (!StressCouplingCompatibility.enabled()) return 0;
        CouplingKinetics service = of(be);
        return service == null || service.nativeRead ? 0 : service.drives.getOrDefault(be, 0f);
    }
    public static float extra(KineticNetwork network, boolean capacity) {
        for (CouplingKinetics service : SERVERS.values()) {
            if (service.nativeRead) continue;
            Totals value = service.additions.get(network);
            if (value != null) return capacity ? value.capacity : value.stress;
        }
        return 0;
    }
    public static void writeLocalNetwork(KineticBlockEntity be, net.minecraft.nbt.CompoundTag tag) {
        CouplingKinetics service = of(be);
        if (service == null || !be.hasNetwork() || !tag.contains("Network")) return;
        KineticNetwork network = be.getOrCreateNetwork();
        if (!service.additions.containsKey(network)) return;
        service.nativeRead = true;
        try {
            tag.getCompound("Network").putFloat("Capacity", network.calculateCapacity());
            tag.getCompound("Network").putFloat("Stress", network.calculateStress());
        } finally { service.nativeRead = false; }
    }
    public static boolean protectConflict(KineticBlockEntity be) {
        CouplingKinetics service = of(be);
        if (service == null) return false;
        boolean found = false;
        for (Edge edge : service.edges.values()) {
            if (sameNetwork(edge.a, be) || sameNetwork(edge.b, be)
                || edge.a.getLevel() == be.getLevel() && edge.a.getBlockPos().distManhattan(be.getBlockPos()) <= 1
                || edge.b.getLevel() == be.getLevel() && edge.b.getBlockPos().distManhattan(be.getBlockPos()) <= 1) {
                service.conflicts.add(edge.a); service.conflicts.add(edge.b); found = true;
            }
        }
        if (found) {
            be.updateSpeed = true;
            if (!service.updating) for (var endpoint : new ArrayList<>(service.conflicts)) service.stop(endpoint);
        }
        return found;
    }
    public static void connect(BallCouplingBlockEntity a, BallCouplingBlockEntity b) {
        if (!StressCouplingCompatibility.enabled() || !a.active() || !b.active()
                || a.getLevel() == null || a.getLevel().isClientSide) return;
        CouplingKinetics service = SERVERS.computeIfAbsent(a.getLevel().getServer(), s -> new CouplingKinetics());
        service.edges.put(a, new Edge(a,b));
    }
    public static void disconnect(BallCouplingBlockEntity endpoint) {
        CouplingKinetics service = of(endpoint);
        if (service == null) return;
        Set<BallCouplingBlockEntity> affected = new HashSet<>();
        affected.add(endpoint);
        boolean expanded;
        do {
            expanded = false;
            for (Edge edge : service.edges.values()) {
                if (affected.stream().anyMatch(n -> sameNetwork(n, edge.a) || sameNetwork(n, edge.b))) {
                    expanded |= affected.add(edge.a);
                    expanded |= affected.add(edge.b);
                }
            }
        } while (expanded);
        Set<KineticNetwork> networks = new HashSet<>();
        affected.forEach(be -> { if (be.hasNetwork()) networks.add(be.getOrCreateNetwork()); });
        boolean removed = service.edges.values().removeIf(e -> e.a == endpoint || e.b == endpoint);
        if (removed) {
            networks.forEach(service.additions::remove);
            affected.forEach(service::stop);
            networks.forEach(KineticNetwork::updateNetwork);
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        CouplingKinetics service = SERVERS.get(event.getServer());
        if (service != null) service.tick();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingAlignment.clear();
        SERVERS.remove(event.getServer());
        BallCouplingIndex.clear(event.getServer());
    }
    private static boolean same(float a, float b) { return Math.abs(a-b) <= Math.max(0.001f,Math.max(Math.abs(a),Math.abs(b))*0.0001f); }
    private static boolean live(KineticBlockEntity be) { return !be.isRemoved() && be.getLevel()!=null && be.getLevel().getBlockEntity(be.getBlockPos())==be; }
    private static boolean sameNetwork(KineticBlockEntity a,KineticBlockEntity b) {
        return a==b || a.hasNetwork() && b.hasNetwork() && a.getOrCreateNetwork()==b.getOrCreateNetwork();
    }
    private boolean realPower(KineticBlockEntity be) {
        nativeRead = true;
        try { return be.hasNetwork() && new ArrayList<>(be.getOrCreateNetwork().sources.keySet()).stream()
            .anyMatch(source -> !source.isRemoved() && source.getGeneratedSpeed()!=0); }
        finally { nativeRead = false; }
    }
    private void drive(BallCouplingBlockEntity be,float speed) {
        float previous = drives.getOrDefault(be,0f);
        if (drives.containsKey(be) && same(previous,speed)) return;
        be.detachKinetics(); be.setNetwork(null); be.clearKineticInformation();
        drives.put(be,speed); be.setSpeed(speed); be.setNetwork(be.getBlockPos().asLong());
        be.attachKinetics(); be.onSpeedChanged(previous); be.setChanged(); be.sendData();
    }
    private void stop(BallCouplingBlockEntity be) {
        Float previous = drives.get(be);
        if (previous == null) return;
        if (!live(be)) { drives.remove(be); return; }
        be.detachKinetics(); be.setNetwork(null); drives.remove(be); be.clearKineticInformation();
        be.setSpeed(0); be.attachKinetics(); be.onSpeedChanged(previous); be.setChanged(); be.sendData();
    }
    private void tick() {
        updating = true;
        conflicts.clear();
        try { updateAll(); }
        finally { updating = false; }
    }
    private void updateAll() {
        edges.values().removeIf(e -> !live(e.a)||!live(e.b)||!e.a.active()||!e.b.active());
        Set<BallCouplingBlockEntity> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<KineticNetwork, Totals> next = new IdentityHashMap<>();
        List<Edge> remaining = new ArrayList<>(edges.values());
        while (!remaining.isEmpty()) {
            List<Edge> component = new ArrayList<>(); component.add(remaining.removeFirst());
            Set<BallCouplingBlockEntity> nodes = new LinkedHashSet<>();
            nodes.add(component.getFirst().a); nodes.add(component.getFirst().b);
            boolean expanded;
            do {
                expanded = false;
                for (Edge e : new ArrayList<>(remaining)) if (nodes.stream().anyMatch(n -> sameNetwork(n,e.a)||sameNetwork(n,e.b))) {
                    component.add(e); nodes.add(e.a); nodes.add(e.b); remaining.remove(e); expanded=true;
                }
            } while (expanded);
            update(component,nodes,keep,next);
        }
        for (var be : new ArrayList<>(drives.keySet())) if (!keep.contains(be)) stop(be);
        Set<KineticNetwork> changed = new HashSet<>(additions.keySet()); changed.addAll(next.keySet());
        changed.removeIf(n -> Objects.equals(additions.get(n),next.get(n)));
        additions.clear(); additions.putAll(next);
        changed.forEach(KineticNetwork::updateNetwork);
    }
    private void update(List<Edge> links,Set<BallCouplingBlockEntity> nodes,Set<BallCouplingBlockEntity> keep,Map<KineticNetwork,Totals> totals) {
        Map<BallCouplingBlockEntity,List<Step>> graph=new IdentityHashMap<>();
        nodes.forEach(n -> graph.put(n,new ArrayList<>()));
        for (Edge e:links) {
            // Create speeds use the positive axis, independent of the block's facing sign.
            float ratio = -e.a.getBlockState().getValue(BallCouplingBlock.FACING).getAxisDirection().getStep()
                * e.b.getBlockState().getValue(BallCouplingBlock.FACING).getAxisDirection().getStep();
            graph.get(e.a).add(new Step(e.b,ratio)); graph.get(e.b).add(new Step(e.a,ratio));
        }
        Map<KineticNetwork,BallCouplingBlockEntity> representatives=new IdentityHashMap<>();
        for (var n:nodes) if(n.hasNetwork()) {
            var first=representatives.putIfAbsent(n.getOrCreateNetwork(),n);
            if(first!=null) {
                float a=first.getTheoreticalSpeed(),b=n.getTheoreticalSpeed(); float ratio=a!=0&&b!=0?b/a:1;
                graph.get(first).add(new Step(n,ratio));graph.get(n).add(new Step(first,1/ratio));
            }
        }
        Map<BallCouplingBlockEntity,Float> factors=new IdentityHashMap<>();
        ArrayDeque<BallCouplingBlockEntity> queue=new ArrayDeque<>();
        var root=nodes.iterator().next(); factors.put(root,1f);queue.add(root);
        while(!queue.isEmpty()) {
            var node=queue.remove();
            for(Step step:graph.get(node)) {
                float factor=factors.get(node)*step.factor;
                Float previous=factors.putIfAbsent(step.node,factor);
                if(previous==null) queue.add(step.node); else if(!same(previous,factor)) return;
            }
        }
        Float speed=null;
        for(var n:nodes) if(realPower(n)) {
            float value=n.getTheoreticalSpeed()/factors.get(n);
            if(value==0) continue;
            if(speed!=null&&!same(speed,value)) return;
            speed=value;
        }
        if(speed==null) return;
        Set<KineticNetwork> powered=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var n:nodes) if(realPower(n)&&n.hasNetwork()) powered.add(n.getOrCreateNetwork());
        for(var n:nodes) {
            if(n.hasNetwork()&&powered.contains(n.getOrCreateNetwork())) continue;
            drive(n,speed*factors.get(n)); keep.add(n);
            if (nodes.stream().anyMatch(conflicts::contains)) {
                keep.removeAll(nodes);
                return;
            }
            if(n.hasNetwork()) powered.add(n.getOrCreateNetwork());
        }
        for(var n:nodes) if(drives.containsKey(n) && !realPower(n)) keep.add(n);
        Map<KineticNetwork,Totals> nativeTotals=new IdentityHashMap<>();
        float capacity=0,stress=0;
        nativeRead=true;
        try {
            for(var n:nodes) if(n.hasNetwork()&&!nativeTotals.containsKey(n.getOrCreateNetwork())) {
                var network=n.getOrCreateNetwork(); var value=new Totals(network.calculateCapacity(),network.calculateStress());
                nativeTotals.put(network,value);capacity+=value.capacity;stress+=value.stress;
            }
        } finally { nativeRead=false; }
        for(var e:nativeTotals.entrySet()) totals.put(e.getKey(),new Totals(Math.max(0,capacity-e.getValue().capacity),Math.max(0,stress-e.getValue().stress)));
    }
}
