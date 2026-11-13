package com.mikoalopex.createfirefightingadd.transition;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Closed-world processing only. Source files are read, never replaced or deleted. */
public final class TransitionCleanupJob {
    public static final long MAX_WORLD_BYTES = 512L * 1024 * 1024;
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_FILES = 100_000;
    public record Result(boolean success, String reason, Path output, Path backup, Path report) {}
    private record Entry(Path relative, long size) {}
    private TransitionCleanupJob() {}

    public static Result run(Path source) { return run(source, MAX_WORLD_BYTES); }
    static Result run(Path input, long limit) {
        Path source = input.toAbsolutePath().normalize();
        Path backup = null, report = null;
        try {
            if (Files.isSymbolicLink(source) || !source.equals(source.toRealPath()))
                throw new IOException("Linked world path is unsupported; use the local tool on a normal copy.");
            Path lockPath = source.resolve("session.lock");
            if (Files.isSymbolicLink(lockPath)) throw new IOException("Linked session.lock");
            // No CREATE: a live or incompletely closed world must not be made to look unlocked.
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("World is still open / 存档仍被占用");
                List<Entry> entries = inventory(source, limit);
                long total = entries.stream().mapToLong(Entry::size).sum();
                if (Files.getFileStore(source).getUsableSpace() < total * 3 + MAX_FILE_BYTES)
                    throw new IOException("Not enough free space for backup and copy / 备份与副本所需空间不足; use the local tool.");
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
                Path receipts = source.getParent().resolve("CFFA-TRANSITION-BACKUPS");
                Files.createDirectories(receipts);
                if (Files.isSymbolicLink(receipts)) throw new IOException("Linked backup directory");
                report = receipts.resolve(stamp + "-result.txt");
                Path zipPart = receipts.resolve(stamp + "-backup.zip.part");
                Map<Path, byte[]> hashes = new LinkedHashMap<>();
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPart, StandardOpenOption.CREATE_NEW))) {
                    for (Entry entry : entries) {
                        byte[] bytes = Files.readAllBytes(source.resolve(entry.relative));
                        hashes.put(entry.relative, digest(bytes));
                        zip.putNextEntry(new ZipEntry(entry.relative.toString().replace('\\', '/')));
                        zip.write(bytes); zip.closeEntry();
                    }
                }
                // Read back every entry before any cleaned copy is created.
                try (ZipFile zip = new ZipFile(zipPart.toFile())) {
                    for (var entry : hashes.entrySet()) {
                        ZipEntry saved = zip.getEntry(entry.getKey().toString().replace('\\', '/'));
                        try (InputStream in = zip.getInputStream(saved)) {
                            if (!Arrays.equals(entry.getValue(), digest(in.readAllBytes())))
                                throw new IOException("Backup verification failed");
                        }
                    }
                }
                backup = receipts.resolve(stamp + "-backup.zip");
                Files.move(zipPart, backup, StandardCopyOption.ATOMIC_MOVE);
                ConnectionDataCleaner cleaner = new ConnectionDataCleaner();
                for (Entry entry : entries) {
                    String name = entry.relative.getFileName().toString();
                    TransitionContainers.transform(source.resolve(entry.relative), root -> cleaner.discoverDocument(name, root));
                }
                // Staging lives outside the selectable world directory and is never called a success.
                Path staging = receipts.resolve(stamp + "-INCOMPLETE");
                Files.createDirectory(staging);
                String title = "CFFA-029-CLEANED-" + stamp;
                for (Entry entry : entries) {
                    Path target = staging.resolve(entry.relative);
                    Files.createDirectories(target.getParent());
                    String name = entry.relative.getFileName().toString();
                    byte[] converted = TransitionContainers.transform(source.resolve(entry.relative), root -> {
                        cleaner.cleanDocument(name, root);
                        if (entry.relative.getNameCount() == 1 && (name.equals("level.dat") || name.equals("level.dat_old")))
                            root.getCompound("Data").putString("LevelName", title);
                    });
                    Files.write(target, converted, StandardOpenOption.CREATE_NEW);
                }
                if (!entries.equals(inventory(source, limit))) throw new IOException("Source file list changed during cleanup");
                for (var entry : hashes.entrySet())
                    if (!Arrays.equals(entry.getValue(), digest(Files.readAllBytes(source.resolve(entry.getKey())))))
                        throw new IOException("Source changed during cleanup: " + entry.getKey());
                Path output = source.getParent().resolve(title);
                String detail = "SUCCESS / 清理副本生成成功\nOriginal (unchanged) / 原存档未修改: " + source
                    + "\nCleaned world / 请测试此副本: " + output + "\nVerified backup / 已校验备份: " + backup
                    + "\nDisconnected records / 连接记录: " + cleaner.disconnectedRecords
                    + "\nRemoved owned constraints / 本模组约束: " + cleaner.removedConstraints
                    + "\nBlocks and substructures were retained. / 方块与子结构均保留。"
                    + "\nReplace the integrated mod with BOTH split mods before opening this copy."
                    + "\n先换装拆分后的两个模组，再打开此副本；不要用整合版重开，否则可能重建连接。"
                    + "\nThis cleans connection data; it does not remap moved block IDs or certify runtime compatibility."
                    + "\n此操作清理连接，不迁移已拆分方块的注册 ID，也不代表游戏运行验收通过。"
                    + "\nIf it still fails, keep the original and use the local tool on a copy to remove the identified blocks/providers."
                    + "\n若仍失败，保留原存档，用本地工具在副本中清除已定位的问题方块及约束提供者。"
                    + "\nBackup omits only session.lock (recreated by Minecraft). / 备份仅排除由游戏重建的会话锁。\n";
                Files.writeString(staging.resolve("CFFA-TRANSITION-RESULT.txt"), detail);
                Files.writeString(report, "PENDING / 尚未发布，请等待最终结果。\n");
                Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
                try { Files.writeString(report, detail); }
                catch (IOException ignored) { report = output.resolve("CFFA-TRANSITION-RESULT.txt"); }
                return new Result(true, detail, output, backup, report);
            }
        } catch (Exception ex) {
            String reason = "NOT COMPLETED / 未完成清理；请勿将不完整目录用于游戏。原存档未修改。\n"
                + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                + "\nUse the separately downloadable local cleanup tool on a copy. / 请下载本地清理工具处理副本。"
                + "\nBackup / 备份: " + (backup == null ? "not completed / 未完成" : backup);
            if (report != null) try { Files.writeString(report, reason); } catch (IOException ignored) {}
            return new Result(false, reason, null, backup, report);
        }
    }
    private static List<Entry> inventory(Path source, long limit) throws IOException {
        List<Entry> entries = new ArrayList<>();
        long[] total = {0};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || attrs.isOther() || !dir.toRealPath().equals(dir.toAbsolutePath().normalize()))
                    throw new IOException("Linked directory is unsupported: " + dir);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Unsupported linked/special file: " + file);
                Path relative = source.relativize(file);
                if (relative.toString().equals("session.lock")) return FileVisitResult.CONTINUE;
                if (attrs.size() > MAX_FILE_BYTES || (total[0] += attrs.size()) > limit || entries.size() >= MAX_FILES)
                    throw new IOException("World too large for built-in cleanup (512 MiB world, 64 MiB/file, 100000 files). "
                        + "存档过大；自动处理可能长时间占用退出界面或内存，请改用本地工具。 ");
                entries.add(new Entry(relative, attrs.size()));
                return FileVisitResult.CONTINUE;
            }
        });
        if (entries.stream().noneMatch(e -> e.relative.toString().equals("level.dat"))) throw new IOException("Missing level.dat");
        entries.sort(Comparator.comparing(e -> e.relative.toString()));
        return entries;
    }
    private static byte[] digest(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
}
