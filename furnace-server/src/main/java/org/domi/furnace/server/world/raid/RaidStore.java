// 바닐라 Raids 의 저장(SavedData "raids" → world/data/raids.dat, gzip {data:{NextAvailableID, Tick, Raids:[...]}, DataVersion}).
// Furnace 는 습격을 region 레벨마다 들고 있으므로 월드 하나에 이 저장소 하나를 두고:
//   - 읽기: 파일의 습격을 중심 청크별로 보류해 두었다가, 그 청크가 어느 region 에 올라올 때 그 레벨로 넘긴다(claim).
//   - 저장: 각 region 이 제 스레드에서 습격 스냅샷을 올리고(publish), 모은 것을 한 파일로 쓴다. 끝난 습격은 지운다(forget).
//   - 중심 청크가 내려가면 습격을 다시 보류로 돌린다(park) — 바닐라는 메모리에 남겨 비활성으로 두는 것과 같은 효과.
// 차이: Tick 은 region 들의 Raids 틱 중 최댓값(바닐라는 차원 하나의 틱 수). 바닐라는 이 값을 setDirty 주기에만 쓴다.
// 번호: 바닐라처럼 NextAvailableID 는 마지막으로 준 번호이고 새 번호는 ++(첫 습격 2).
package org.domi.furnace.server.world.raid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RaidStore {
    private static final Logger LOG = LoggerFactory.getLogger(RaidStore.class);
    private static final int DATA_VERSION = 3955;

    private final Path file;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicInteger tick = new AtomicInteger();
    /** 번호 → 마지막 스냅샷(보류 중인 것 포함). 저장은 이 값들을 번호 순으로 쓴다. */
    private final Map<Integer, NbtCompound> snapshots = new ConcurrentHashMap<>();
    /** 중심 청크 키 → 아직 어느 레벨에도 올라가지 않은 습격. */
    private final Map<Long, List<NbtCompound>> pending = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    private final Object writeLock = new Object();

    private RaidStore(Path file) { this.file = file; }

    /** 파일 없이 메모리에서만(테스트·단독 레벨). */
    public static RaidStore detached() { return new RaidStore(null); }

    /** world/data/raids.dat 을 읽는다(없거나 깨졌으면 빈 저장소). */
    public static RaidStore open(Path worldDir) {
        RaidStore s = new RaidStore(worldDir.resolve("data").resolve("raids.dat"));
        s.load();
        return s;
    }

    private static long key(int cx, int cz) { return (long) cx << 32 | cz & 0xFFFFFFFFL; }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readGzip(in);
            NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
            loadData(data);
        } catch (IOException | RuntimeException e) {
            LOG.warn("raids.dat 읽기 실패 — 습격 없이 시작", e);
        }
    }

    /** Raids.load: NextAvailableID·Tick·Raids. */
    void loadData(NbtCompound data) {
        nextId.set(data.getIntOr0("NextAvailableID"));
        tick.set(data.getIntOr0("Tick"));
        if (data.tags().get("Raids") instanceof NbtList list) {
            for (Object o : list.items()) {
                if (!(o instanceof NbtCompound r)) continue;
                snapshots.put(r.getIntOr0("Id"), r);
                pending.computeIfAbsent(key(r.getIntOr0("CX") >> 4, r.getIntOr0("CZ") >> 4), k -> new ArrayList<>()).add(r);
            }
        }
    }

    /** Raids.save. */
    NbtCompound saveData() {
        NbtCompound data = new NbtCompound();
        data.putInt("NextAvailableID", nextId.get());
        data.putInt("Tick", tick.get());
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (NbtCompound r : new TreeMap<>(snapshots).values()) list.add(r);
        data.putList("Raids", list);
        return data;
    }

    /** Raids.getUniqueId: ++NextAvailableID. */
    public int nextId() {
        dirty = true;
        return nextId.incrementAndGet();
    }

    int tick() { return tick.get(); }

    void reportTick(int t) { tick.accumulateAndGet(t, Math::max); }

    /** 그 청크가 중심인 보류 습격을 꺼낸다(없으면 빈 목록). */
    List<NbtCompound> claim(int cx, int cz) {
        List<NbtCompound> l = pending.remove(key(cx, cz));
        return l == null ? List.of() : l;
    }

    /** 습격을 보류로 돌린다(중심 청크가 내려갈 때). */
    void park(Raid r) {
        NbtCompound tag = r.save();
        snapshots.put(r.id(), tag);
        pending.computeIfAbsent(key(r.center().x() >> 4, r.center().z() >> 4), k -> new ArrayList<>()).add(tag);
        dirty = true;
    }

    /** 레벨에 있는 습격의 최신 스냅샷을 올린다(그 region 스레드에서 뜬 것). */
    void publish(Raid r) {
        snapshots.put(r.id(), r.save());
        dirty = true;
    }

    /** 끝난(멈춘) 습격을 지운다. */
    void forget(int id) {
        if (snapshots.remove(id) != null) dirty = true;
    }

    /** 이 번호의 습격을 저장소가 아는가(레벨에 있거나 보류 중). */
    public boolean knows(int id) { return snapshots.containsKey(id); }

    /** 보류 중인 습격 수(테스트용). */
    public int pendingCount() {
        int n = 0;
        for (List<NbtCompound> l : pending.values()) n += l.size();
        return n;
    }

    /** 바뀐 것이 있으면 raids.dat 을 쓴다. 어느 스레드에서든(스냅샷은 region 스레드에서 이미 올라와 있어야 한다). */
    public void saveIfDirty() {
        if (file == null || !dirty) return;
        synchronized (writeLock) {
            dirty = false;
            NbtCompound root = new NbtCompound();
            root.putCompound("data", saveData());
            root.putInt("DataVersion", DATA_VERSION);
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    NbtIo.writeGzip(os, root, "");
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                dirty = true;
                LOG.error("raids.dat 저장 실패", e);
            }
        }
    }
}
