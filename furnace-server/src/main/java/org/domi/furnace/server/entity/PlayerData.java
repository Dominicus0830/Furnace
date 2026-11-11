// playerdata/<uuid>.dat (gzip NBT) read/write — 바닐라 플레이어 NBT 형식(Pos·Rotation·UUID·Inventory{Slot:byte,id,count}).
// 저장 시 기존 파일을 바탕으로 서버가 아는 필드만 덮어써서 아직 다루지 않는 바닐라 필드(체력·허기·경험치 등)를 보존하고,
// 바닐라 PlayerDataStorage 처럼 이전 파일을 <uuid>.dat_old 로 남기고, <uuid>.dat 이 없거나 깨졌으면 <uuid>.dat_old 를 읽는다(A05-02).
package org.domi.furnace.server.entity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class PlayerData {

    /** @param root 읽은 NBT 전체(생존 상태 등 다른 필드를 읽는 데 쓴다) */
    public record Snapshot(double x, double y, double z, float yaw, float pitch,
                           boolean onGround, ItemStack[] slots, NbtCompound root) {
        public Snapshot(double x, double y, double z, float yaw, float pitch, boolean onGround, ItemStack[] slots) {
            this(x, y, z, yaw, pitch, onGround, slots, null);
        }
    }

    private PlayerData() {}

    /** 바닐라 문자열 아이템 id 변환용. null 이면(일부 테스트) 초기 Furnace 형식으로 쓴다. */
    private static volatile org.domi.furnace.server.world.ItemRegistry items;

    public static void itemRegistry(org.domi.furnace.server.world.ItemRegistry registry) { items = registry; }

    private static final int DATA_VERSION = 3955;
    private static final String FURNACE_COMPONENTS = "furnace:components";
    /** 바닐라 인벤토리에 자리가 없는 제작 칸(창 슬롯 1..4) 아이템 — 바닐라는 접속 종료 때 인벤토리로 돌려준다. */
    private static final String FURNACE_CRAFTING = "furnace:crafting";

    // 플레이어 데이터 읽기·쓰기 전용 단일 스레드(D-14). 청크 IO 풀과 분리해 대기열 지연을 받지 않고,
    // 제출 순서대로 직렬이라 퇴장 저장 → 같은 UUID 재접속 읽기 순서가 보장된다. 종료 경로는 awaitIo 로 비운다.
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furnace-playerdata-io");
        t.setDaemon(true);
        return t;
    });

    public static java.util.concurrent.Executor io() { return IO; }

    /**
     * 지금까지 io() 에 제출된 작업이 끝날 때까지 기다린다(서버 종료·테스트). 시간 안에 끝나면 true. 잡힌 퇴장 저장(플레이어 인벤토리 줄에서
     * 뜨는 중, reserveSave)이 있으면 그것이 io() 에 들어가기를 먼저 기다린다 — region 스레드에서 부르지 않는다.
     */
    public static boolean awaitIo(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        if (!awaitPendingSaves(timeout, unit)) return false;
        try {
            IO.submit(() -> { }).get(Math.max(1L, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return true;
        }
    }

    /**
     * 퇴장 저장 자리(A04-02): 퇴장 저장은 플레이어 region 줄에 남은 패킷 작업이 다 끝난 뒤 그 줄에서 뜬다. 그 사이 같은 UUID 로 다시 들어오면
     * 읽기가 아직 제출되지 않은 저장을 앞질러 옛 데이터를 읽을 수 있으므로, 끊길 때 자리를 잡고 저장을 IO 에 넣은 뒤 푼다.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Void>> PENDING_SAVES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static String pendingKey(Path worldDir, UUID id) {
        return (worldDir == null ? "" : worldDir.toAbsolutePath().normalize().toString()) + '|' + id;
    }

    /** 퇴장 저장 자리를 잡는다(그 월드 폴더의 그 UUID). 돌려준 것을 저장을 io() 에 넣은 뒤(또는 포기할 때) 한 번 부른다. 여러 번 불러도 된다. */
    public static Runnable reserveSave(Path worldDir, UUID id) {
        String key = pendingKey(worldDir, id);
        java.util.concurrent.CompletableFuture<Void> mine = new java.util.concurrent.CompletableFuture<>();
        PENDING_SAVES.merge(key, mine, (old, m) -> java.util.concurrent.CompletableFuture.allOf(old, m));
        return () -> {
            mine.complete(null);
            PENDING_SAVES.computeIfPresent(key, (k, v) -> v.isDone() ? null : v);
        };
    }

    /** 그 월드 폴더의 그 UUID 에 잡힌 퇴장 저장이 아직 io() 에 들어가지 않았는가. */
    public static boolean hasPendingSave(Path worldDir, UUID id) {
        var f = PENDING_SAVES.get(pendingKey(worldDir, id));
        return f != null && !f.isDone();
    }

    /**
     * 잡힌 퇴장 저장이 없으면 task 를 바로 돌리고 거짓, 있으면 그 저장들이 io() 에 들어간 뒤 돌리고 참(그 저장을 푼 스레드에서).
     * 읽기 쪽은 task 에서 io() 에 읽기를 넣는다 — 같은 직렬 실행기라 저장 → 읽기 순서가 지켜진다.
     */
    public static boolean afterPendingSaves(Path worldDir, UUID id, Runnable task) {
        java.util.concurrent.CompletableFuture<Void> f = PENDING_SAVES.get(pendingKey(worldDir, id));
        if (f == null || f.isDone()) {
            task.run();
            return false;
        }
        f.whenComplete((v, t) -> task.run());
        return true;
    }

    /** 지금 잡힌 퇴장 저장 자리가 모두 풀리면(저장이 IO 에 들어가면) 완료된다 — save-all·백업이 방금 나간 플레이어 저장을 빠뜨리지 않게. */
    public static java.util.concurrent.CompletableFuture<Void> pendingSaves() {
        return java.util.concurrent.CompletableFuture.allOf(PENDING_SAVES.values().toArray(new java.util.concurrent.CompletableFuture<?>[0]));
    }

    /** 잡힌 퇴장 저장이 모두 io() 에 들어갈 때까지 기다린다(서버 종료: awaitIo 앞에서). 시간 안에 끝나면 true. */
    public static boolean awaitPendingSaves(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        try {
            pendingSaves().get(timeout, unit);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return true;
        }
    }

    private static Path file(Path worldDir, UUID id) {
        return worldDir.resolve("playerdata").resolve(id + ".dat");
    }

    private static Path oldFile(Path worldDir, UUID id) {
        return worldDir.resolve("playerdata").resolve(id + ".dat_old");
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PlayerData.class);

    /** .dat 과 .dat_old 를 모두 읽지 못했다(둘 중 하나는 있었다). */
    private static final class UnreadablePlayerData extends Exception {
        UnreadablePlayerData(Throwable cause) { super(cause); }
    }

    private static NbtCompound readGzipFile(Path f) throws IOException {
        try (var is = Files.newInputStream(f)) {
            return NbtIo.readGzip(is);
        }
    }

    /**
     * 바닐라 PlayerDataStorage.load(1.21.1 eri.b): {@code <uuid>.dat} 을 읽고, 없거나 깨졌으면 {@code <uuid>.dat_old}.
     * 저장({@link #write})은 ".dat → .dat_old" 다음 "임시 → .dat" 이라 그 사이에 꺼지면 최신본은 .dat_old 에만 있다.
     *
     * <p>quarantine 이면 읽지 못한 파일을 {@code .corrupt} 로 옮겨 둔다 — 그대로 두면 다음 저장이 깨진 .dat 을 .dat_old 로 돌려
     * 마지막 좋은 사본을 덮는다(바닐라는 깨진 파일을 날짜 붙여 복사해 두고 같은 틈이 있다; 원본 바이트는 어느 쪽이든 남는다).
     * 호출자가 {@link #lock} 을 쥔다.
     *
     * @param parse 뿌리를 원하는 값으로 바꾼다. 형식이 틀리면 RuntimeException — 깨진 파일과 같이 다룬다.
     * @return 둘 다 없으면 null
     * @throws UnreadablePlayerData 하나라도 있었는데 어느 것도 읽지 못했다
     */
    private static <T> T readWithFallback(Path worldDir, UUID id, boolean quarantine,
                                          java.util.function.Function<NbtCompound, T> parse) throws UnreadablePlayerData {
        Path dat = file(worldDir, id), old = oldFile(worldDir, id);
        Exception failure = null;
        if (Files.exists(dat)) {
            try {
                return parse.apply(checkedVersion(readGzipFile(dat), id));
            } catch (org.domi.furnace.server.world.anvil.UnsupportedDataVersionException e) {
                throw e;   // 깨진 파일이 아니다 — 격리하지도 .dat_old 로 대체하지도 않는다
            } catch (IOException | RuntimeException e) {
                failure = e;
                LOG.warn("playerdata 읽기 실패 {} — {}.dat_old 로 되살려 본다", dat, id, e);
                if (quarantine) quarantine(dat);
            }
        }
        if (Files.exists(old)) {
            try {
                T t = parse.apply(checkedVersion(readGzipFile(old), id));
                LOG.warn("playerdata {} 를 이전 저장본 {} 에서 읽었다", id, old.getFileName());
                return t;
            } catch (org.domi.furnace.server.world.anvil.UnsupportedDataVersionException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                LOG.warn("playerdata 이전 저장본 읽기 실패 {}", old, e);
                if (quarantine) quarantine(old);
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw new UnreadablePlayerData(failure);
        return null;
    }

    /** 이 서버보다 옛/새 판으로 저장된 플레이어 파일은 열지 않는다(A05-03·04 — 접속이 거부되고 파일은 그대로 남는다). */
    private static NbtCompound checkedVersion(NbtCompound root, UUID id) {
        String what = "playerdata " + id;
        org.domi.furnace.server.world.anvil.OlderDataVersionException.check(root, what);
        if (root.tags().get("DataVersion") instanceof Number n && n.intValue() > DATA_VERSION
                && !org.domi.furnace.server.world.anvil.NewerDataVersionException.ignored()) {
            throw new org.domi.furnace.server.world.anvil.NewerDataVersionException(what, n.intValue());
        }
        return root;
    }

    private static void quarantine(Path f) {
        try {
            Path to = f.resolveSibling(f.getFileName() + ".corrupt");
            if (Files.exists(to)) {   // 앞선 격리본을 덮지 않는다(바닐라는 <uuid>_corrupted_<시각>.dat)
                to = f.resolveSibling(f.getFileName() + ".corrupt-" + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")));
            }
            Files.move(f, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFail) {
            LOG.warn("손상된 playerdata 백업 실패 {}", f, moveFail);
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> LAST_SEQ = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object lock(UUID id) {
        return LOCKS.computeIfAbsent(id, k -> new Object());
    }

    /** 저장 요청 순서표. 스냅샷을 뜨는 시점에 받아 saveSequenced에 넘기면 늦게 도착한 옛 스냅샷이 최신을 덮어쓰지 못한다. */
    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    /** 같은 플레이어의 더 새로운 시퀀스가 이미 기록됐으면 건너뛰는 저장(자동저장·퇴장 저장이 풀에서 뒤바뀌어도 안전). */
    public static void saveSequenced(long seq, Path worldDir, UUID id, double x, double y, double z,
                                     float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        saveSequenced(seq, worldDir, id, x, y, z, yaw, pitch, onGround, slots, null);
    }

    /** @param extra 스냅샷을 뜰 때 함께 떠 둔 나머지 필드(체력·허기·경험치·게임 모드 등). null 이면 기존 값 유지. */
    public static void saveSequenced(long seq, Path worldDir, UUID id, double x, double y, double z,
                                     float yaw, float pitch, boolean onGround, ItemStack[] slots, NbtCompound extra) {
        synchronized (lock(id)) {
            Long last = LAST_SEQ.get(id);
            if (last != null && last > seq) return;
            LAST_SEQ.put(id, seq);
            save(worldDir, id, x, y, z, yaw, pitch, onGround, slots, extra);
        }
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        save(worldDir, id, x, y, z, yaw, pitch, onGround, slots, null);
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround, ItemStack[] slots, NbtCompound extra) {
        synchronized (lock(id)) {
            NbtCompound root = readExisting(worldDir, id);
            if (extra != null) {
                for (String k : VITALS_KEYS) root.tags().remove(k);   // 없어진 선택 필드(이전 게임 모드·스폰 지점 등)를 지운다
                root.tags().putAll(extra.tags());
            }
            fill(root, id, x, y, z, yaw, pitch, onGround, slots);
            write(worldDir, id, root);
        }
    }

    /**
     * RootVehicle 만 덧쓴다(null 이면 지운다). 탈것은 주인 region 스레드가 떠서 같은 실행기로 넘긴다 — 본체 저장은 이 키를 건드리지 않는다.
     * 파일이 없고 지울 것이면 아무것도 쓰지 않는다.
     */
    public static void saveRootVehicle(Path worldDir, UUID id, NbtCompound rootVehicle) {
        synchronized (lock(id)) {
            if (rootVehicle == null && !exists(worldDir, id)) return;
            NbtCompound root = readExisting(worldDir, id);
            if (rootVehicle == null) {
                if (root.tags().remove(PlayerVehicleSave.KEY) == null) return;
            } else {
                root.put(PlayerVehicleSave.KEY, rootVehicle);
            }
            write(worldDir, id, root);
        }
    }

    /** 플레이어 데이터 파일이 있는가(플러그인 OfflinePlayer.hasPlayedBefore). .dat_old 만 남아도 load 가 그것을 읽으므로 있다. */
    public static boolean exists(Path worldDir, UUID id) {
        return Files.exists(file(worldDir, id)) || Files.exists(oldFile(worldDir, id));
    }

    /** playerdata 에 파일이 있는 UUID 모두(디렉터리 읽기). .dat_old 만 남은 플레이어도 넣는다({@link #exists} 와 같은 기준). */
    public static java.util.List<UUID> knownIds(Path worldDir) {
        Path dir = worldDir.resolve("playerdata");
        java.util.Set<UUID> out = new java.util.LinkedHashSet<>();
        if (!Files.isDirectory(dir)) return new java.util.ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.forEach(f -> {
                String n = f.getFileName().toString();
                int cut = n.endsWith(".dat") ? 4 : n.endsWith(".dat_old") ? 8 : -1;
                if (cut < 0) return;
                try {
                    out.add(UUID.fromString(n.substring(0, n.length() - cut)));
                } catch (IllegalArgumentException ignored) {
                    // 다른 파일(임시·백업)
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 목록 읽기 실패", e);
        }
        return new java.util.ArrayList<>(out);
    }

    /**
     * 파일의 NBT 전체(없으면 null). .dat 이 없거나 깨졌으면 .dat_old(load 와 같은 기준). load 와 달리 손상 파일을 옮기지 않고,
     * 둘 다 읽지 못하면 예외를 던진다.
     */
    public static NbtCompound readRoot(Path worldDir, UUID id) {
        synchronized (lock(id)) {
            try {
                return readWithFallback(worldDir, id, false, r -> r);
            } catch (UnreadablePlayerData e) {
                throw new UncheckedIOException("playerdata 읽기 실패 " + id,
                        e.getCause() instanceof IOException io ? io : new IOException(e.getCause()));
            }
        }
    }

    /** {@link #editBukkitValues} 결과. */
    public enum EditResult { EDITED, MISSING, ONLINE }

    /**
     * 오프라인 플레이어의 BukkitValues(플러그인 영속 데이터)를 고쳐 쓴다. 파일 잠금 안에서 offline 을 한 번 더 확인해
     * 그 사이 접속했으면 쓰지 않고 ONLINE 을 돌려준다(호출자가 접속자 쪽으로 다시 보낸다). 파일이 없으면 만들지 않고 MISSING.
     * edit 가 예외를 던지면 아무것도 쓰지 않는다. 비게 되면 BukkitValues 키를 지운다.
     */
    public static EditResult editBukkitValues(Path worldDir, UUID id, java.util.function.BooleanSupplier stillOffline,
                                              java.util.function.Consumer<NbtCompound> edit) {
        synchronized (lock(id)) {
            if (!exists(worldDir, id)) return EditResult.MISSING;
            if (!stillOffline.getAsBoolean()) return EditResult.ONLINE;
            NbtCompound root;
            try {
                root = readWithFallback(worldDir, id, true, r -> r);
            } catch (UnreadablePlayerData e) {
                throw new UncheckedIOException("playerdata 읽기 실패 " + id,
                        e.getCause() instanceof IOException io ? io : new IOException(e.getCause()));
            }
            if (root == null) return EditResult.MISSING;
            NbtCompound values = root.tags().get("BukkitValues") instanceof NbtCompound c ? c : new NbtCompound();
            edit.accept(values);
            if (values.tags().isEmpty()) root.tags().remove("BukkitValues");
            else root.putCompound("BukkitValues", values);
            write(worldDir, id, root);
            return EditResult.EDITED;
        }
    }

    /** PlayerVitals.save 가 쓰는 선택 필드 — 값이 없어지면 파일에서도 지워야 하는 것. */
    private static final String[] VITALS_KEYS = {"previousPlayerGameType", "SpawnX", "SpawnY", "SpawnZ", "SpawnForced",
            "SpawnAngle", "SpawnDimension", "LastDeathLocation", "raid_omen_position", "Tags", "BukkitValues"};

    /**
     * 저장의 바탕(모르는 필드를 보존). .dat 이 없거나 깨졌으면 .dat_old — 저장 이동 사이에 꺼진 뒤에도 이어 쓴다.
     * 깨진 파일은 .corrupt 로 옮겨, 이어지는 {@link #write} 가 그것을 .dat_old 로 돌려 마지막 좋은 사본을 덮지 않게 한다.
     */
    private static NbtCompound readExisting(Path worldDir, UUID id) {
        try {
            NbtCompound root = readWithFallback(worldDir, id, true, r -> r);
            return root != null ? root : new NbtCompound();
        } catch (UnreadablePlayerData e) {
            return new NbtCompound();
        }
    }

    private static void fill(NbtCompound root, UUID id, double x, double y, double z,
                             float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE);
        pos.add(x); pos.add(y); pos.add(z);
        root.putList("Pos", pos);
        NbtList rot = new NbtList(NbtIo.TAG_FLOAT);
        rot.add(yaw); rot.add(pitch);
        root.putList("Rotation", rot);
        root.putByte("OnGround", (byte) (onGround ? 1 : 0));
        root.putIntArray("UUID", uuidToIntArray(id));
        root.putInt("DataVersion", DATA_VERSION);
        if (!root.contains("Dimension")) root.putString("Dimension", "minecraft:overworld");
        root.tags().remove(FURNACE_CRAFTING);
        if (items != null) {
            root.putList("Inventory", vanillaInventoryToNbt(slots));
            NbtList crafting = craftingToNbt(slots);
            if (crafting.size() > 0) root.putList(FURNACE_CRAFTING, crafting);
        } else {
            root.putList("Inventory", inventoryToNbt(slots));
        }
    }

    private static void write(Path worldDir, UUID id, NbtCompound root) {
        try {
            Path f = file(worldDir, id);
            Files.createDirectories(f.getParent());
            // 임시 파일에 쓰고 교체 — 쓰는 도중 크래시/동시 읽기에서도 잘린 파일이 보이지 않는다. 이전 파일은 .dat_old 로.
            Path tmp = Files.createTempFile(f.getParent(), id + "-", ".dat");
            try (var os = Files.newOutputStream(tmp)) {
                NbtIo.writeGzip(os, root, "");
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tmp);   // 쓰다 실패하면 임시 파일을 남기지 않는다
                throw e;
            }
            if (Files.exists(f)) {
                Files.move(f, f.resolveSibling(id + ".dat_old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 저장 실패 " + id, e);
        }
    }

    /**
     * 위치/인벤토리 로드. 바닐라 PlayerDataStorage 처럼 {@code <uuid>.dat} 이 없거나 깨졌으면 {@code <uuid>.dat_old} 를 읽는다.
     * 둘 다 없으면 null. 읽지 못한 파일은 경고 후 `.corrupt` 로 보존하고, 둘 다 못 읽으면 null(새 플레이어로 시작 — 예전엔 예외로
     * 접속이 영구히 막혔다).
     */
    public static Snapshot load(Path worldDir, UUID id) {
        synchronized (lock(id)) {
            try {
                return readWithFallback(worldDir, id, true, PlayerData::snapshotOf);
            } catch (UnreadablePlayerData e) {
                LOG.warn("playerdata {} 를 .dat·.dat_old 어느 쪽에서도 읽지 못했다 — 새 플레이어로 시작한다(원본은 .corrupt)", id);
                return null;
            }
        }
    }

    private static Snapshot snapshotOf(NbtCompound root) {
        NbtList pos = root.getList("Pos");
        NbtList rot = root.getList("Rotation");
        return new Snapshot(
                (Double) pos.get(0), (Double) pos.get(1), (Double) pos.get(2),
                (Float) rot.get(0), (Float) rot.get(1),
                root.contains("OnGround") && root.getByte("OnGround") != 0,
                readInventory(root),
                root
        );
    }

    /** 바닐라 Entity UUID 저장 형식(int[4], 상위 비트부터). */
    static int[] uuidToIntArray(UUID id) {
        long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
        return new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    /** 창 슬롯(InventoryMenu 0..45) → 바닐라 Inventory Slot. 제작 칸(0..4)은 -1(바닐라 인벤토리에 없음). */
    static int windowToVanillaSlot(int window) {
        if (window >= 36 && window <= 44) return window - 36;   // 핫바 0..8
        if (window >= 9 && window <= 35) return window;         // 주 인벤토리 9..35
        if (window >= 5 && window <= 8) return 103 - (window - 5); // 머리 103 · 가슴 102 · 다리 101 · 발 100
        if (window == 45) return -106;                          // 왼손
        return -1;
    }

    public static int vanillaToWindowSlot(int slot) {
        if (slot >= 0 && slot <= 8) return slot + 36;
        if (slot >= 9 && slot <= 35) return slot;
        if (slot >= 100 && slot <= 103) return 5 + (103 - slot);
        if (slot == -106) return 45;
        return -1;
    }

    private static NbtCompound vanillaItem(ItemStack stack) {
        String name = items.nameOf(stack.itemId());
        if (name == null || name.equals("minecraft:air")) return null;
        NbtCompound it = new NbtCompound();
        it.putString("id", name);
        it.putInt("count", stack.count());
        NbtCompound components = org.domi.furnace.server.item.ItemNbt.write(stack.patch());
        if (components != null) it.putCompound(org.domi.furnace.server.item.ItemNbt.KEY, components);
        return it;
    }

    private static NbtList vanillaInventoryToNbt(ItemStack[] slots) {
        NbtList inventory = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null) return inventory;
        // 바닐라 Inventory.save 순서: 주 인벤토리(0..35) → 방어구(100..103) → 왼손(-106)
        int[] order = new int[41];
        int n = 0;
        for (int v = 0; v <= 35; v++) order[n++] = vanillaToWindowSlot(v);
        for (int v = 100; v <= 103; v++) order[n++] = vanillaToWindowSlot(v);
        order[n] = 45;
        for (int window : order) {
            if (window < 0 || window >= slots.length) continue;
            ItemStack stack = slots[window];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound it = vanillaItem(stack);
            if (it == null) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) windowToVanillaSlot(window));
            entry.tags().putAll(it.tags());
            inventory.add(entry);
        }
        return inventory;
    }

    /**
     * 접속 중인 플레이어의 저장 꼴 NBT(/data get entity 용, 바닐라 saveWithoutId 근사): 저장 때 쓰는 필드 + SelectedItem.
     * 차이: 저장 경로와 같은 조각만 담는다(Motion·Fire 같은 엔티티 공통 필드 일부는 없다).
     */
    public static NbtCompound snapshotNbt(Player p) {
        NbtCompound root = p.vitals().saveData();
        ItemStack[] slots = p.inventory().snapshot();
        int selected = p.inventory().selected();
        root.putInt("SelectedItemSlot", selected);
        if (items != null) root.putList("EnderItems", enderItemsToNbt(p.enderChest().snapshot()));
        root.putCompound("recipeBook", p.recipeBook().save());
        root.putString("Dimension", p.dimension());
        root.putBoolean("seenCredits", p.seenCredits);
        p.saveTags(root);
        p.savePersistentData(root);
        p.saveShoulders(root);
        p.saveFire(root);
        p.saveAttributes(root);
        fill(root, p.uuid(), p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.onGround(), slots);
        root.tags().remove("DataVersion");
        root.tags().remove(FURNACE_CRAFTING);
        if (items != null) {
            ItemStack held = slots[PlayerInventory.windowSlot(selected)];
            NbtCompound it = held == null || held.isEmpty() ? null : vanillaItem(held);
            if (it != null) root.putCompound("SelectedItem", it);   // NbtPredicate.getEntityTagToCompare
        }
        return root;
    }

    /** 아이템 레지스트리가 있는가(없으면 EnderItems 를 읽거나 쓰지 않고 파일의 것을 그대로 둔다). */
    public static boolean hasItemRegistry() { return items != null; }

    /** 엔더 상자 27칸 → 바닐라 EnderItems 목록(Slot:byte, id, count, components). 레지스트리가 없으면 빈 목록. */
    public static NbtList enderItemsToNbt(ItemStack[] slots) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null || items == null) return list;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound it = vanillaItem(stack);
            if (it == null) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) i);
            entry.tags().putAll(it.tags());
            list.add(entry);
        }
        return list;
    }

    /** 바닐라 EnderItems 읽기(없거나 레지스트리가 없으면 빈 27칸). */
    public static ItemStack[] enderItemsFromNbt(NbtCompound root, int size) {
        ItemStack[] out = new ItemStack[size];
        Arrays.fill(out, ItemStack.EMPTY);
        if (root == null || items == null || !root.contains("EnderItems")) return out;
        for (Object o : root.getList("EnderItems").items()) {
            if (o instanceof NbtCompound e && e.tags().get("Slot") instanceof Byte b && (b & 0xFF) < size) {
                ItemStack s = vanillaStack(e);
                if (s != null) out[b & 0xFF] = s;
            }
        }
        return out;
    }

    private static NbtList craftingToNbt(ItemStack[] slots) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null) return list;
        for (int window = 1; window <= 4 && window < slots.length; window++) {
            ItemStack stack = slots[window];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound it = vanillaItem(stack);
            if (it == null) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) window);
            entry.tags().putAll(it.tags());
            list.add(entry);
        }
        return list;
    }

    /** 바닐라 형식(Slot:byte, id:string)과 초기 Furnace 형식(Slot:int, Id:int)을 모두 읽는다. */
    private static ItemStack[] readInventory(NbtCompound root) {
        NbtList inv = root.contains("Inventory") ? root.getList("Inventory") : null;
        ItemStack[] slots = inventoryFromNbt(inv);
        if (items == null) return slots;
        if (inv != null) {
            for (Object o : inv.items()) {
                if (o instanceof NbtCompound e && e.tags().get("Slot") instanceof Byte b) {
                    ItemStack s = vanillaStack(e);
                    int window = vanillaToWindowSlot(b);
                    if (s != null && window >= 0) slots[window] = s;
                }
            }
        }
        if (root.contains(FURNACE_CRAFTING)) {
            for (Object o : root.getList(FURNACE_CRAFTING).items()) {
                if (o instanceof NbtCompound e && e.tags().get("Slot") instanceof Byte b && b >= 1 && b <= 4) {
                    ItemStack s = vanillaStack(e);
                    if (s != null) slots[b] = s;
                }
            }
        }
        return slots;
    }

    private static ItemStack vanillaStack(NbtCompound e) {
        if (!(e.tags().get("id") instanceof String name)) return null;
        int itemId = items.idOf(name);
        int count = e.tags().get("count") instanceof Number c ? c.intValue() : 1;
        if (itemId <= 0 || count <= 0) return null;
        var patch = org.domi.furnace.server.item.ItemNbt.read(e, name, FURNACE_COMPONENTS);
        return patch == null ? null : new ItemStack(itemId, count, patch);
    }

    private static NbtList inventoryToNbt(ItemStack[] slots) {
        NbtList inventory = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null) return inventory;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound entry = new NbtCompound();
            entry.putInt("Slot", i);
            entry.putInt("Id", stack.itemId());
            entry.putInt("Count", stack.count());
            NbtCompound components = org.domi.furnace.server.item.ItemNbt.write(stack.patch());
            if (components != null) entry.putCompound(org.domi.furnace.server.item.ItemNbt.KEY, components);
            inventory.add(entry);
        }
        return inventory;
    }

    private static ItemStack[] inventoryFromNbt(NbtList inventory) {
        ItemStack[] slots = emptySlots();
        if (inventory == null) return slots;
        for (Object entryObj : inventory.items()) {
            NbtCompound entry = (NbtCompound) entryObj;
            // 자체 형식({Slot:int, Id:int, Count:int})만 읽는다. 바닐라 형식(Slot:byte, id:string)은 아직 변환하지 않고 건너뛴다.
            if (!(entry.tags().get("Slot") instanceof Integer slotObj)
                    || !(entry.tags().get("Id") instanceof Integer itemId)
                    || !(entry.tags().get("Count") instanceof Integer count)) continue;
            int slot = slotObj;
            if (slot < 0 || slot >= slots.length) continue;
            var patch = org.domi.furnace.server.item.ItemNbt.read(entry, items != null ? items.nameOf(itemId) : null, "Comp");
            if (patch == null) continue;
            slots[slot] = new ItemStack(itemId, count, patch);
        }
        return slots;
    }

    private static ItemStack[] emptySlots() {
        ItemStack[] slots = new ItemStack[46];
        Arrays.fill(slots, ItemStack.EMPTY);
        return slots;
    }
}
