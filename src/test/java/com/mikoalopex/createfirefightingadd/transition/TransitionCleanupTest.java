package com.mikoalopex.createfirefightingadd.transition;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.nbt.*;

/** Entirely synthetic NBT and temporary files; never starts Minecraft or opens a player's world. */
public final class TransitionCleanupTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path base = Path.of("build", "transition-fixtures").toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path temp = Files.createTempDirectory(base, "test-");
        try {
            dataScope();
            containers(temp);
            closedWorld(temp);
            System.out.println("Transition cleanup passed: " + assertions + " assertions (synthetic only)");
        } finally {
            if (!temp.startsWith(base) || temp.equals(base)) throw new AssertionError("Bad fixture path");
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static void dataScope() {
        UUID rope = UUID.randomUUID(), foreignRope = UUID.randomUUID();
        ConnectionDataCleaner cleaner = new ConnectionDataCleaner();
        CompoundTag own = block("createfirefightingadd:physical_hose");
        own.putUUID("HasRopeAttached", rope); own.putUUID("BracketStrand", rope);
        own.putBoolean("OwnStrand", true); own.putUUID("PartnerEndpointId", UUID.randomUUID());
        own.putString("Appearance", "createfirefightingadd:fire_hose");
        own.putString("Inventory", "sentinel"); own.putString("Tank", "water-sentinel");
        CompoundTag nativeEnd = block("simulated:rope_wincher");
        nativeEnd.putUUID("HasRopeAttached", rope); nativeEnd.put("Strand", strand(rope));
        CompoundTag foreign = block("simulated:rope_wincher"); foreign.put("Strand", strand(foreignRope));
        CompoundTag foreignBefore = foreign.copy();
        CompoundTag root = new CompoundTag(); ListTag blocks = new ListTag();
        blocks.add(nativeEnd); blocks.add(foreign); blocks.add(own); root.put("block_entities", blocks);
        root.putString("uuid", "substructure-kept"); root.putString("pose", "pose-kept");
        ListTag palette = new ListTag(); CompoundTag state = new CompoundTag();
        state.putString("Name", "createfirefightingadd:physical_hose"); palette.add(state); root.put("palette", palette);
        ListTag records = new ListTag(); records.add(constraint("createfirefightingadd:ball_coupling"));
        records.add(constraint("createfirefightingadd:fluid_coupling")); records.add(constraint("other:weld")); root.put("records", records);
        // Discover and clean can operate in opposite file orders.
        cleaner.discover(own); cleaner.clean(root);
        check(blocks.size() == 3, "No block removed"); check(palette.getCompound(0).equals(state), "Palette preserved");
        check(root.getString("uuid").equals("substructure-kept") && root.getString("pose").equals("pose-kept"), "Body identity and pose preserved");
        check(!own.contains("HasRopeAttached") && !own.contains("PartnerEndpointId"), "Own connection removed");
        check(own.getString("Inventory").equals("sentinel") && own.getString("Tank").equals("water-sentinel"), "Inventory/fluid retained");
        check(!nativeEnd.contains("Strand") && !nativeEnd.contains("HasRopeAttached"), "Native counterpart cleared");
        check(foreign.equals(foreignBefore), "Unrelated native rope preserved");
        check(records.size() == 1 && records.getCompound(0).equals(constraint("other:weld")), "Only our constraints removed");
        CompoundTag mounted = new CompoundTag(); mounted.putString("type", "createfirefightingadd:fire_hose");
        mounted.putString("partner_endpoint_id", "partner"); mounted.putString("partner_sublevel", "sublevel");
        mounted.putString("fluid", "water"); mounted.putInt("capacity", 1000); mounted.putBoolean("controller", true);
        cleaner.clean(mounted);
        check(!mounted.contains("partner_endpoint_id") && !mounted.contains("partner_sublevel"), "Moving endpoint disconnected");
        check(mounted.getString("fluid").equals("water") && mounted.getInt("capacity") == 1000 && !mounted.getBoolean("controller"), "Moving endpoint fluid retained");
        CompoundTag data = new CompoundTag(); data.put("data", root.copy());
        CompoundTag untouched = data.copy(); cleaner.cleanDocument("createfirefightingadd_firefighters.dat", data);
        check(data.equals(untouched), "Unrelated SavedData retained");
        cleaner.cleanDocument("createfirefightingadd_fluid_coupling_couplings.dat", data);
        check(data.getCompound("data").isEmpty(), "Actual fluid-coupling queue name cleared");
        CompoundTag hooks = new CompoundTag(), hookData = new CompoundTag(); ListTag hookRopes = new ListTag();
        hookRopes.add(StringTag.valueOf(foreignRope.toString())); hookData.put("Ropes", hookRopes); hooks.put("data", hookData);
        cleaner.discoverDocument("createfirefightingadd_rope_hook_attachments.dat", hooks); cleaner.clean(foreign);
        check(!foreign.contains("Strand"), "Unloaded hook rope found through SavedData");
        CompoundTag item = new CompoundTag(), components = new CompoundTag();
        components.putString("createfirefightingadd:smart_rope_selection", "selected"); components.putString("minecraft:custom_name", "keep");
        item.putString("id", "create:andesite_casing"); item.put("components", components); cleaner.clean(item);
        check(!components.contains("createfirefightingadd:smart_rope_selection") && components.contains("minecraft:custom_name"), "Only our item selection cleared");
    }
    private static void containers(Path temp) throws Exception {
        CompoundTag root = block("createfirefightingadd:ball_coupling"); root.putUUID("CouplingPartner", UUID.randomUUID());
        for (int compression : new int[]{1, 2, 3}) {
            byte[] input = nbt(root, compression);
            check(Arrays.equals(input, TransitionContainers.payload(input, compression, ignored -> {})), "No-op preserves compressed bytes");
            byte[] output = TransitionContainers.payload(input, compression, tag -> new ConnectionDataCleaner().clean(tag));
            check(!read(output, compression).contains("CouplingPartner"), "NBT changed for compression " + compression);
            byte[] anvil = region(input, compression, 4096, 8192);
            ByteBuffer.wrap(anvil).putInt(4096, 123456); // Preserve timestamps, including unchanged slots.
            byte[] next = TransitionContainers.region(anvil, 4096, 8192, false, tag -> new ConnectionDataCleaner().clean(tag));
            check(ByteBuffer.wrap(next).getInt(4096) == 123456, "Anvil timestamps retained");
            check(!readRecord(next, 4096, compression).contains("CouplingPartner"), "Anvil body cleaned");
        }
        for (int sector : new int[]{128, 4096}) for (int compression : new int[]{1, 3}) {
            byte[] sable = region(nbt(root, compression), 0, sector, 4096);
            byte[] next = TransitionContainers.region(sable, sector, 4096, true, tag -> new ConnectionDataCleaner().clean(tag));
            check(!readRecord(next, sector, compression).contains("CouplingPartner"), "Sable gzip/raw supported");
        }
        byte[] broken = region(nbt(root, 1), 0, 128, 4096); ByteBuffer.wrap(broken).putInt(4, ByteBuffer.wrap(broken).getInt(0));
        expectIOException(() -> TransitionContainers.region(broken, 128, 4096, true, ignored -> {}), "Overlapping sectors rejected");
        byte[] truncated = Arrays.copyOf(region(nbt(root, 1), 0, 128, 4096), 4100);
        expectIOException(() -> TransitionContainers.region(truncated, 128, 4096, true, ignored -> {}), "Truncated payload rejected");
        check(TransitionContainers.region(new byte[0], 128, 4096, true, ignored -> {}).length == 0, "Empty Sable container valid");
        byte[] external = region(new byte[0], 16, 128, 4096); Path container = temp.resolve("0.slvlr"); Files.write(container, external);
        expectIOException(() -> TransitionContainers.transform(container, ignored -> {}), "Missing external Sable record rejected");
        Files.createDirectories(temp.resolve("0.r")); Files.write(temp.resolve("0.r/0.slvl"), nbt(root, 1));
        check(Arrays.equals(external, TransitionContainers.transform(container, ignored -> {})), "Valid external span retained");
        byte[] changed = TransitionContainers.transform(temp.resolve("0.r/0.slvl"), tag -> new ConnectionDataCleaner().clean(tag));
        check(!read(changed, 1).contains("CouplingPartner"), "External data cleaned independently");
        expectIOException(() -> TransitionContainers.region(region(new byte[0], 130, 4096, 8192), 4096, 8192, false, ignored -> {}), "External Anvil format rejected");
    }
    private static void closedWorld(Path temp) throws Exception {
        Path source = temp.resolve("success/Original"); fixture(source);
        Map<Path, byte[]> original = snapshot(source);
        var result = TransitionCleanupJob.run(source);
        check(result.success(), "Closed-world job: " + result.reason());
        check(result.output().getFileName().toString().startsWith("CFFA-029-CLEANED-"), "Unambiguous output name");
        check(result.backup() != null && Files.isRegularFile(result.backup()), "Verified backup exists");
        check(sameSnapshot(original, snapshot(source)), "Every source byte is unchanged");
        CompoundTag output = read(Files.readAllBytes(result.output().resolve("level.dat")), 1);
        check(output.getCompound("Data").getString("LevelName").equals(result.output().getFileName().toString()), "World display name equals output directory");
        check(!output.getCompound("testBlock").contains("CouplingPartner"), "Completed output is cleaned");
        check(output.getCompound("testBlock").getString("id").equals("createfirefightingadd:ball_coupling"), "Block retained in completed output");
        try (ZipFile zip = new ZipFile(result.backup().toFile())) {
            check(Arrays.equals(original.get(Path.of("level.dat")), zip.getInputStream(zip.getEntry("level.dat")).readAllBytes()), "Backup has original connected data");
        }
        Path invalid = temp.resolve("unsupported/Original"); fixture(invalid); Files.write(invalid.resolve("unknown.bin"), new byte[]{1, 2, 3});
        Map<Path, byte[]> beforeInvalid = snapshot(invalid);
        var failure = TransitionCleanupJob.run(invalid);
        check(!failure.success() && failure.output() == null, "Unknown container never publishes a world");
        check(failure.backup() != null, "Backup completed before unsupported data processing");
        check(sameSnapshot(beforeInvalid, snapshot(invalid)), "Failure preserves original");
        Path large = temp.resolve("large/Original"); fixture(large);
        var tooLarge = TransitionCleanupJob.run(large, 1);
        check(!tooLarge.success() && tooLarge.backup() == null, "Size limit aborts before backup");
        check(!Files.exists(large.getParent().resolve("CFFA-TRANSITION-BACKUPS")), "Oversize check creates no output directory");
        Path busy = temp.resolve("busy/Original"); fixture(busy);
        try (FileChannel channel = FileChannel.open(busy.resolve("session.lock"), StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
            var locked = TransitionCleanupJob.run(busy);
            check(!locked.success() && locked.backup() == null, "Locked world cannot be processed");
            check(!Files.exists(busy.getParent().resolve("CFFA-TRANSITION-BACKUPS")), "Locked world produces no output");
        }
    }
    private static void fixture(Path source) throws IOException {
        Files.createDirectories(source); Files.write(source.resolve("session.lock"), new byte[]{1, 2, 3});
        CompoundTag root = new CompoundTag(), data = new CompoundTag(); data.putString("LevelName", "Original"); root.put("Data", data);
        CompoundTag block = block("createfirefightingadd:ball_coupling"); block.putUUID("CouplingPartner", UUID.randomUUID()); root.put("testBlock", block);
        Files.write(source.resolve("level.dat"), nbt(root, 1)); Files.write(source.resolve("level.dat_old"), nbt(root, 1));
        Files.writeString(source.resolve("options.txt"), "keep");
    }
    private static Map<Path, byte[]> snapshot(Path root) throws IOException {
        Map<Path, byte[]> result = new HashMap<>();
        try (var paths = Files.walk(root)) { for (Path path : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(path), Files.readAllBytes(path)); }
        return result;
    }
    private static boolean sameSnapshot(Map<Path, byte[]> a, Map<Path, byte[]> b) {
        return a.keySet().equals(b.keySet()) && a.keySet().stream().allMatch(key -> Arrays.equals(a.get(key), b.get(key)));
    }
    private static CompoundTag block(String id) { CompoundTag c = new CompoundTag(); c.putString("id", id); return c; }
    private static CompoundTag strand(UUID id) { CompoundTag c = new CompoundTag(); c.putUUID("uuid", id); return c; }
    private static CompoundTag constraint(String system) {
        CompoundTag owner = new CompoundTag(); owner.putString("type", "synaxis:system"); owner.putString("system", system);
        CompoundTag key = new CompoundTag(); key.put("owner", owner); CompoundTag result = new CompoundTag(); result.put("key", key); return result;
    }
    private static byte[] nbt(CompoundTag root, int type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStream output = type == 1 ? new GZIPOutputStream(bytes) : type == 2 ? new DeflaterOutputStream(bytes) : bytes;
        try (DataOutputStream out = new DataOutputStream(output)) { NbtIo.write(root, out); } return bytes.toByteArray();
    }
    private static CompoundTag read(byte[] data, int type) throws IOException {
        InputStream bytes = new ByteArrayInputStream(data);
        InputStream input = type == 1 ? new GZIPInputStream(bytes) : type == 2 ? new InflaterInputStream(bytes) : bytes;
        try (DataInputStream in = new DataInputStream(input)) { return NbtIo.read(in, NbtAccounter.unlimitedHeap()); }
    }
    private static byte[] region(byte[] body, int type, int sector, int header) {
        int count = (body.length + 5 + sector - 1) / sector;
        // Intentionally omit unused final-sector padding: Sable legitimately writes this way.
        byte[] bytes = new byte[header + body.length + 5]; ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt((header / sector << 8) | count); buffer.position(header); buffer.putInt(body.length + 1); buffer.put((byte) type); buffer.put(body); return bytes;
    }
    private static CompoundTag readRecord(byte[] raw, int sector, int compression) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(raw); int pos = (in.getInt(0) >>> 8) * sector, length = in.getInt(pos);
        return read(Arrays.copyOfRange(raw, pos + 5, pos + 4 + length), compression);
    }
    private interface IoAction { void run() throws IOException; }
    private static void expectIOException(IoAction action, String label) throws IOException {
        try { action.run(); } catch (IOException expected) { check(true, label); return; } throw new AssertionError(label);
    }
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
}
