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

    /**
     * level.dat → 없거나 깨졌으면 level.dat_old. 둘 다 없으면 null. 둘 다 깨졌으면 예외.
     * 이 서버와 맞지 않는 판(더 새 DataVersion, 다른 판 계열)의 월드면 {@link IncompatibleWorldException} — 월드는 건드리지 않는다(A05-03).
     */
    public LevelData loadLevelData() throws IOException {
        Path main = dir.resolve(LEVEL_DAT);
        Path old = dir.resolve(LEVEL_DAT_OLD);
        if (!Files.exists(main) && !Files.exists(old)) return null;
        try {
            return read(main);
        } catch (IncompatibleWorldException e) {
            throw e;   // 판이 맞지 않는 것은 깨진 것이 아니다 — level.dat_old 로 되돌리지 않는다
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to load world data from {}", main, e);
            log.info("Attempting to use fallback");
            try {
                LevelData d = read(old);
                restoreFromOld(main, old);
                return d;
            } catch (IncompatibleWorldException e2) {
                throw e2;
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
            NbtCompound data = root.getCompound("Data");
            checkVersion(p, data);
            return LevelData.fromNbt(data);
        }
    }

    /** 판이 맞지 않아 열지 않는 월드. 로그는 던지기 전에 남긴다. */
    public static final class IncompatibleWorldException extends IOException {
        IncompatibleWorldException(String message) { super(message); }
    }

    /**
     * 바닐라 1.21.1 Main: {@code LevelSummary.isCompatible()}(판 계열 Version.Series 가 "main" 과 같은가)이 거짓이면
     * "This world was created by an incompatible version." 를 남기고 기동하지 않는다.
     * 바닐라는 계열만 보고 더 새 판 월드를 그대로 열어 모르는 블록을 공기로 지운다 — Paper 는 그런 청크를 만나면 서버를 멈추므로
     * (Do-not-let-the-server-load-chunks-from-newer-version.patch) Furnace 는 그 결과를 기동 때 level.dat 판으로 미리 낸다.
     * 탈출구도 Paper 와 같다(-DPaper.ignoreWorldDataVersion=true, 계열 검사는 바닐라처럼 끌 수 없다).
     */
    static void checkVersion(Path p, NbtCompound data) throws IncompatibleWorldException {
        NbtCompound version = data.tags().get("Version") instanceof NbtCompound v ? v : null;
        String series = version != null && version.tags().get("Series") instanceof String s ? s : "main";
        String name = version != null && version.tags().get("Name") instanceof String n ? n : "?";
        if (!"main".equals(series)) {
            log.info("This world was created by an incompatible version.");
            log.error("{}: 판 계열 \"{}\"({}) 은 이 서버(main, 1.21.1)와 맞지 않아 월드를 열지 않는다 — 월드 파일은 건드리지 않았다", p, series, name);
            throw new IncompatibleWorldException("판 계열이 다른 월드: " + series + " (" + name + ")");
        }
        int dataVersion = data.tags().get("DataVersion") instanceof Number n ? n.intValue()
                : version != null && version.tags().get("Id") instanceof Number id ? id.intValue() : -1;
        int current = org.domi.furnace.server.world.anvil.NewerDataVersionException.CURRENT;
        if (dataVersion > current && !org.domi.furnace.server.world.anvil.NewerDataVersionException.ignored()) {
            log.info("This world was created by an incompatible version.");
            log.error("{}: 이 월드는 더 새 판({}, 데이터 버전 {})에서 저장됐다. 이 서버는 1.21.1(데이터 버전 {})이라 열면 모르는 블록·아이템이 "
                    + "지워지므로 기동하지 않는다 — 월드 파일은 건드리지 않았다. 1.21.1 에서 만든 월드나 백업을 쓰거나, 손실을 감수하고 "
                    + "-D{}=true 로 강제로 열 수 있다", p, name, dataVersion, current,
                    org.domi.furnace.server.world.anvil.NewerDataVersionException.IGNORE_PROPERTY);
            throw new IncompatibleWorldException("더 새 판 월드: 데이터 버전 " + dataVersion + " > " + current + " (" + name + ")");
        }
        // A05-04: 옛 판 월드는 청크·플레이어·엔티티를 올리는 데이터 픽서가 아직 없어 그대로 열면 아이템 개수·마법 부여·블록이 사라진다
        if (dataVersion < current && !org.domi.furnace.server.world.anvil.OlderDataVersionException.ignored()) {
            log.info("This world was created by an incompatible version.");
            log.error("{}: 이 월드는 더 옛 판({}, 데이터 버전 {})에서 저장됐다. 이 서버는 1.21.1(데이터 버전 {})이고 옛 판 청크·플레이어를 올리는 "
                    + "픽서가 아직 없어 열면 아이템 개수·마법 부여·블록이 조용히 사라지므로 기동하지 않는다 — 월드 파일은 건드리지 않았다. "
                    + "바닐라 1.21.1 로 한 번 열어 저장한 월드를 쓰거나, 손실을 감수하고 -D{}=true 로 강제로 열 수 있다",
                    p, name, dataVersion, current, org.domi.furnace.server.world.anvil.OlderDataVersionException.IGNORE_PROPERTY);
            throw new IncompatibleWorldException("더 옛 판 월드: 데이터 버전 " + dataVersion + " < " + current + " (" + name + ")");
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
