// 바닐라 LevelStorageSource.LevelStorageAccess 의 서버 몫 — session.lock 을 쥐고 level.dat 을 읽고(실패 시 level.dat_old)
// 임시 파일에 쓴 뒤 level.dat_old 백업과 함께 교체한다(Util.safeReplaceFile).
package org.domi.furnace.server.world.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LevelStorage implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LevelStorage.class);
    public static final String LEVEL_DAT = "level.dat";
    public static final String LEVEL_DAT_OLD = "level.dat_old";

    private final Path dir;
    private final DirectoryLock lock;

    private LevelStorage(Path dir, DirectoryLock lock) {
        this.dir = dir;
        this.lock = lock;
    }

    /** 월드 폴더를 만들고 잠근다. 이미 잠겨 있으면 DirectoryLock.LockException. */
    public static LevelStorage open(Path dir) throws IOException {
        return new LevelStorage(dir, DirectoryLock.create(dir));
    }

    public Path dir() { return dir; }

    public boolean hasWorldData() {
        return Files.exists(dir.resolve(LEVEL_DAT)) || Files.exists(dir.resolve(LEVEL_DAT_OLD));
    }

    /** level.dat → 없거나 깨졌으면 level.dat_old. 둘 다 없으면 null. 둘 다 깨졌으면 예외. */
    public LevelData loadLevelData() throws IOException {
        Path main = dir.resolve(LEVEL_DAT);
        Path old = dir.resolve(LEVEL_DAT_OLD);
        if (!Files.exists(main) && !Files.exists(old)) return null;
        try {
            return read(main);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to load world data from {}", main, e);
            log.info("Attempting to use fallback");
            try {
                LevelData d = read(old);
                restoreFromOld(main, old);
                return d;
            } catch (IOException | RuntimeException e2) {
                log.error("Failed to load world data from {}", old, e2);
                log.error("Failed to load world data from {} and {}. World files may be corrupted. Shutting down.", main, old);
                throw new IOException("level.dat 와 level.dat_old 모두 읽기 실패", e2);
            }
        }
    }

    private static LevelData read(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            NbtCompound root = NbtIo.readGzip(in);
            if (!root.contains("Data")) throw new IOException("Data 태그 없음: " + p);
            return LevelData.fromNbt(root.getCompound("Data"));
        }
    }

    /** 바닐라 restoreLevelDataFromOld: level.dat_old 를 level.dat 으로 되돌린다. */
    private static void restoreFromOld(Path main, Path old) {
        try {
            Path broken = main.resolveSibling(LEVEL_DAT + "_corrupted_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
            if (Files.exists(main)) Files.move(main, broken, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(old, main, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("level.dat_old 복원 실패", e);
        }
    }

    public synchronized void saveLevelData(LevelData data) throws IOException {
        NbtCompound root = new NbtCompound();
        root.putCompound("Data", data.toNbt(System.currentTimeMillis()));
        Path tmp = Files.createTempFile(dir, "level", ".dat");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            NbtIo.writeGzip(out, root, "");
        }
        safeReplaceFile(dir.resolve(LEVEL_DAT), tmp, dir.resolve(LEVEL_DAT_OLD));
    }

    /** Util.safeReplaceFile: 기존 파일을 백업으로 옮기고 새 파일을 제자리에. */
    static void safeReplaceFile(Path target, Path source, Path backup) throws IOException {
        if (Files.exists(target)) Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean checkLock() { return lock.isValid(); }

    @Override
    public void close() throws IOException {
        lock.close();
    }
}
