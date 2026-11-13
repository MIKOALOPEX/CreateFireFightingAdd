package com.mikoalopex.createfirefightingadd.transition;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.zip.*;
import net.minecraft.nbt.*;

/** Validates containers before copying; unsupported formats fail without publishing a result. */
public final class TransitionContainers {
    private static final int LIMIT = 64 * 1024 * 1024;
    private TransitionContainers() {}
    public static byte[] transform(Path path, Consumer<CompoundTag> action) throws IOException {
        long size = Files.size(path);
        if (size > LIMIT) throw new IOException("File exceeds 64 MiB processing limit: " + path.getFileName());
        byte[] raw = Files.readAllBytes(path);
        String name = path.getFileName().toString();
        if (name.endsWith(".mca")) return region(raw, 4096, 8192, false, action);
        if (name.endsWith(".slvlr") || name.endsWith(".slvls")) {
            boolean small = name.endsWith(".slvlr");
            Path external = path.resolveSibling(name.substring(0, name.length() - 6) + (small ? ".r" : ".s"));
            return region(raw, small ? 128 : 4096, 4096, true, action,
                slot -> Files.isRegularFile(external.resolve(slot + ".slvl"), LinkOption.NOFOLLOW_LINKS));
        }
        if (name.endsWith(".dat") || name.endsWith(".dat_old") || name.endsWith(".nbt") || name.endsWith(".slvl"))
            return payload(raw, raw.length > 1 && raw[0] == (byte)0x1f && raw[1] == (byte)0x8b ? 1 : 3, action);
        if (name.matches(".*\\.(json|toml|txt|png|jpg|jpeg|log|lock|zip|mcmeta)$") || name.equals(".DS_Store")) return raw;
        throw new IOException("Unsupported container: " + name);
    }
    static byte[] payload(byte[] raw, int compression, Consumer<CompoundTag> action) throws IOException {
        InputStream input = new ByteArrayInputStream(raw);
        input = switch (compression) {
            case 1 -> new GZIPInputStream(input);
            case 2 -> new InflaterInputStream(input);
            case 3 -> input;
            default -> throw new IOException("Unsupported compression " + compression);
        };
        CompoundTag root;
        try (DataInputStream in = new DataInputStream(input)) {
            root = NbtIo.read(in, NbtAccounter.create(LIMIT));
            if (in.read() != -1) throw new IOException("Trailing NBT bytes");
        }
        CompoundTag before = root.copy();
        action.accept(root);
        if (root.equals(before)) return raw;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputStream compressed = switch (compression) {
            case 1 -> new GZIPOutputStream(buffer);
            case 2 -> new DeflaterOutputStream(buffer);
            default -> buffer;
        };
        try (DataOutputStream out = new DataOutputStream(compressed)) { NbtIo.write(root, out); }
        return buffer.toByteArray();
    }
    static byte[] region(byte[] raw, int sector, int header, boolean sable, Consumer<CompoundTag> action) throws IOException {
        return region(raw, sector, header, sable, action, slot -> false);
    }
    private static byte[] region(byte[] raw, int sector, int header, boolean sable, Consumer<CompoundTag> action,
            IntPredicate externalExists) throws IOException {
        if (raw.length == 0) return raw;
        if (raw.length < header) throw new IOException("Truncated region header");
        byte[] table = Arrays.copyOf(raw, header);
        ByteBuffer in = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer locations = ByteBuffer.wrap(table).order(ByteOrder.BIG_ENDIAN);
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.write(table);
        BitSet used = new BitSet(); used.set(0, header / sector);
        boolean changed = false;
        for (int slot = 0; slot < 1024; slot++) {
            int span = in.getInt(slot * 4);
            if (span == 0) continue;
            int offset = span >>> 8, count = span & 255;
            long startLong = (long) offset * sector;
            if (count == 0 || startLong + 5 > raw.length || used.nextSetBit(offset) >= 0 && used.nextSetBit(offset) < offset + count)
                throw new IOException("Invalid/overlapping region span " + slot);
            used.set(offset, offset + count);
            int start = (int) startLong, length = in.getInt(start), type = raw[start + 4] & 255;
            if (length < 1 || (long) start + 4 + length > raw.length || (long) length + 4 > count * sector)
                throw new IOException("Truncated region payload " + slot);
            byte[] body = Arrays.copyOfRange(raw, start + 5, start + 4 + length);
            boolean external = sable && type == 16;
            if (external && (body.length != 0 || !externalExists.test(slot))) throw new IOException("Missing/invalid external Sable record " + slot);
            if (sable && type != 0 && type != 16) throw new IOException("Unknown Sable flags");
            // External .slvl is processed once as a normal file in the all-files pass.
            byte[] next = external ? body : payload(body, sable ? (body.length > 1 && body[0] == (byte)0x1f && body[1] == (byte)0x8b ? 1 : 3) : type, action);
            changed |= !Arrays.equals(body, next);
            int sectors = (next.length + 5 + sector - 1) / sector;
            if (sectors > 255) throw new IOException("Region record requires external storage");
            locations.putInt(slot * 4, (result.size() / sector << 8) | sectors);
            DataOutputStream out = new DataOutputStream(result);
            out.writeInt(next.length + 1); out.writeByte(type); out.write(next);
            out.write(new byte[sectors * sector - next.length - 5]);
        }
        if (!changed) return raw;
        byte[] output = result.toByteArray(); System.arraycopy(table, 0, output, 0, header); return output;
    }
}
