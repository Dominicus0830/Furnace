// region 디렉토리의 .mca 핸들 캐시(LRU 256개, 바닐라 RegionFileStorage 와 같다) + 파일별 락 직렬화. 여러 region 스레드가 공유 안전.
package org.domi.furnace.server.world.anvil;

import java.io.IOException;
import java.nio.file.Path;

public final class RegionFileStore {

    private final Path regionDir;
    /** 바닐라 RegionFileStorage.MAX_CACHE_SIZE. 넘으면 가장 오래 안 쓴 파일을 닫는다(탐험이 길면 fd 가 끝없이 늘던 것, F-12). */
    static final int MAX_OPEN = 256;
    private final java.util.LinkedHashMap<Long, RegionFile> open = new java.util.LinkedHashMap<>(16, 0.75f, true);

    public RegionFileStore(Path worldDir) {
        this(worldDir, "region");
    }

    public Path regionDir() { return regionDir; }

    /** 다른 하위 디렉토리(엔티티는 "entities"). */
    public RegionFileStore(Path worldDir, String subDir) {
        this(worldDir, subDir, false);
    }

    /** sync = server.properties sync-chunk-writes(바닐라 기본 true). */
    public RegionFileStore(Path worldDir, String subDir, boolean sync) {
        this.regionDir = worldDir.resolve(subDir);
        this.sync = sync;
    }

    private final boolean sync;

    private static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    // 파일(키)별 잠금 — 핸들과 분리해 둔다. 같은 파일을 쓰는 작업·새로 열기·내보낸 핸들 닫기는 모두 이 잠금 안에서만 한다.
    // 그래서 한 파일에 열린 핸들이 동시에 둘이 될 수 없다(내보낸 핸들로 쓰는 중에 새 핸들이 낡은 섹터 표를 읽던 경합).
    // 잠금 객체는 파일마다 하나라 탐험한 region 수만큼만 는다.
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();
    // LRU 가 맵에서 뺐지만 아직 안 닫은 핸들. 자기 키 잠금 밖에서(다른 키 잠금을 쥐지 않은 채) 닫는다 — 잠금 순서 교착 방지.
    private final java.util.concurrent.ConcurrentLinkedQueue<java.util.Map.Entry<Long, RegionFile>> toClose = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private Object lock(long k) { return locks.computeIfAbsent(k, x -> new Object()); }

    /** 키 잠금을 쥔 채로 부른다. 맵에 없으면 새로 연다(그 키의 옛 핸들은 같은 잠금 밖에서 쓰일 수 없다). */
    private RegionFile fileLocked(long k, int rx, int rz) throws IOException {
        synchronized (this) {
            RegionFile rf = open.get(k);
            if (rf != null) return rf;
            rf = new RegionFile(regionDir.resolve("r." + rx + "." + rz + ".mca"), sync);
            open.put(k, rf);
            if (open.size() > MAX_OPEN) {
                var it = open.entrySet().iterator();
                var eldest = it.next();
                it.remove();
                toClose.add(java.util.Map.entry(eldest.getKey(), eldest.getValue()));
            }
            return rf;
        }
    }

    /** 내보낸 핸들을 그 키 잠금 안에서 닫는다(쓰는 중이면 끝날 때까지 기다린다). 키 잠금을 하나도 쥐지 않은 채 부른다. */
    private void drainClosed() throws IOException {
        java.util.Map.Entry<Long, RegionFile> e;
        IOException first = null;
        while ((e = toClose.poll()) != null) {
            synchronized (lock(e.getKey())) {
                try { e.getValue().close(); } catch (IOException ex) { if (first == null) first = ex; }
            }
        }
        if (first != null) throw first;
    }

    /** 테스트용: 파일 잠금 안, 작업 직전에 부른다(경합 재현용 협조 지점). */
    volatile Runnable beforeOpHook;

    @FunctionalInterface
    private interface Op<T> { T run(RegionFile rf) throws IOException; }

    /** 그 파일 키 잠금 안에서 실행. */
    private <T> T withFile(int chunkX, int chunkZ, Op<T> op) throws IOException {
        return withFile(chunkX, chunkZ, false, op);
    }

    /** write 면 백업 스냅샷이 걸려 있을 때 그 파일을 먼저 떠 둔다(쓰기 전 복사). */
    private <T> T withFile(int chunkX, int chunkZ, boolean write, Op<T> op) throws IOException {
        int rx = chunkX >> 5, rz = chunkZ >> 5;
        long k = key(rx, rz);
        T result;
        synchronized (lock(k)) {
            if (write) {
                Snapshot s = snapshot;
                if (s != null) s.beforeWrite(k, rx, rz);
            }
            RegionFile rf = fileLocked(k, rx, rz);
            Runnable h = beforeOpHook;
            if (h != null) h.run();
            result = op.run(rf);
        }
        try {
            drainClosed();
        } catch (IOException e) {
            // 내보낸 다른 파일 닫기 실패는 이 작업의 실패가 아니다 — 읽기를 "깨진 청크"로 보이게 하면 멀쩡한 청크를 새로 생성해 덮는다
            org.slf4j.LoggerFactory.getLogger(RegionFileStore.class).warn("[REGION] 내보낸 region 파일 닫기 실패", e);
        }
        return result;
    }

    /** 압축해제된 NBT 바이트(없으면 null). 파일별 락 직렬화. */
    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        // 파일 잠금 안에서는 압축 본문만 읽고, 압축 풀기(순수 CPU)는 잠금 밖에서 — 쓰기 쪽 F-01 과 같은 꼴
        RegionFile.Payload p = readPayload(chunkX, chunkZ);
        return p == null ? null : p.decompress();
    }

    /** 압축 본문만(없으면 null). 압축 풀기는 부르는 쪽이 원하는 스레드에서 {@link RegionFile.Payload#decompress()}. */
    public RegionFile.Payload readPayload(int chunkX, int chunkZ) throws IOException {
        return withFile(chunkX, chunkZ, rf -> rf.readPayload(chunkX, chunkZ));
    }

    /** 디스크에 그 청크가 있는가(region 파일이 없으면 만들지 않고 거짓). */
    public boolean hasChunk(int chunkX, int chunkZ) throws IOException {
        if (!java.nio.file.Files.exists(regionDir.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca"))) return false;
        return withFile(chunkX, chunkZ, rf -> rf.hasChunk(chunkX, chunkZ));
    }

    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        if (nbtBytes == null) throw new IllegalArgumentException("nbtBytes null");
        // F-01: deflate 는 순수 CPU 라 파일 잠금 밖에서 한다. 잠금 안에는 섹터 할당·기록만 남는다(같은 파일 병렬도↑).
        int type = Compression.writeType();
        byte[] comp = Compression.compress(type, nbtBytes);
        withFile(chunkX, chunkZ, true, rf -> { rf.writeCompressed(chunkX, chunkZ, comp, type); return null; });
    }

    // ------------------------------------------------------------------ 백업 스냅샷(쓰기 전 복사)
    //
    // 백업 기준점(cut) 뒤의 첫 쓰기가 그 파일을 덮기 전에 기준점 상태의 파일을 백업 폴더로 복사한다. 기준점은 레인마다 다르다:
    // 레인 marker 가 돈 뒤(active) 의 쓰기만 "기준점 뒤" 다. 같은 파일의 쓰기는 모두 같은 레인이라 이 구분이 정확하다.
    // 나머지(기준점 뒤 한 번도 안 쓴 파일)는 복사기가 copyRemaining 으로 떠 간다. 복사와 쓰기는 같은 파일 키 잠금 안에서만 일어나
    // 서로 엇갈리지 않는다. 기준점 뒤에 새로 생긴 파일은 첫 쓰기가 "없었음" 으로 표시해 백업에 넣지 않는다.

    /** 레인 기준점이 지났는가(region 좌표 → 그 파일 레인의 marker 가 돌았나). */
    @FunctionalInterface
    public interface CutPassed { boolean passed(int regionX, int regionZ); }

    private volatile Snapshot snapshot;

    /** 백업 스냅샷을 건다. dest 는 이 저장소 폴더(region/ 등)에 대응하는 백업 쪽 폴더. 이미 걸려 있으면 예외. */
    public synchronized Snapshot beginSnapshot(Path dest, CutPassed cut) {
        if (snapshot != null) throw new IllegalStateException("이미 백업 스냅샷 중: " + regionDir);
        Snapshot s = new Snapshot(dest, cut);
        snapshot = s;
        return s;
    }

    /** 걸린 스냅샷(없으면 null). */
    public Snapshot snapshot() { return snapshot; }

    /** 한 저장소 폴더의 백업 스냅샷. */
    public final class Snapshot {
        private final Path dest;
        private final CutPassed cut;
        /** 처리한 파일 키(복사했거나 기준점에 없었음). */
        private final java.util.Set<Long> resolved = java.util.concurrent.ConcurrentHashMap.newKeySet();
        // 복사 중인 동안 끝내기(end)가 기다리게 — 복사는 읽기 잠금, 끝내기는 쓰기 잠금
        private final java.util.concurrent.locks.ReentrantReadWriteLock gate = new java.util.concurrent.locks.ReentrantReadWriteLock();
        private volatile boolean ended;
        private final java.util.concurrent.atomic.AtomicLong bytes = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicInteger files = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger copiedOnWrite = new java.util.concurrent.atomic.AtomicInteger();
        private volatile IOException failure;

        private Snapshot(Path dest, CutPassed cut) {
            this.dest = dest;
            this.cut = cut;
        }

        /** 쓰기 직전(그 파일 키 잠금 안): 기준점이 지났고 아직 안 떴으면 지금 뜬다. 실패는 기록만 하고 쓰기는 막지 않는다. */
        void beforeWrite(long k, int rx, int rz) {
            if (ended || resolved.contains(k) || !cut.passed(rx, rz)) return;
            if (copyLocked(k, rx, rz, null)) copiedOnWrite.incrementAndGet();
        }

        /** 키 잠금 안에서: 아직 안 떴으면 .mca 와 그 외부 청크(.mcc)를 복사한다. 복사했으면 참. mcc 가 null 이면 폴더를 훑어 찾는다. */
        private boolean copyLocked(long k, int rx, int rz, java.util.List<Path> mcc) {
            var read = gate.readLock();
            read.lock();
            try {
                if (ended || !resolved.add(k)) return false;
                Path src = regionDir.resolve("r." + rx + "." + rz + ".mca");
                if (!java.nio.file.Files.exists(src)) return false;   // 기준점에 없던 파일
                java.nio.file.Files.createDirectories(dest);
                copy(src);
                for (Path p : mcc != null ? mcc : externalFiles(rx, rz)) {
                    if (java.nio.file.Files.exists(p)) copy(p);
                }
                return true;
            } catch (IOException e) {
                if (failure == null) failure = e;
                return false;
            } finally {
                read.unlock();
            }
        }

        private void copy(Path src) throws IOException {
            Path to = dest.resolve(src.getFileName().toString());
            java.nio.file.Files.copy(src, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            bytes.addAndGet(java.nio.file.Files.size(to));
            files.incrementAndGet();
        }

        /** 그 region 의 외부 청크 파일(c.x.z.mcc). */
        private java.util.List<Path> externalFiles(int rx, int rz) throws IOException {
            java.util.List<Path> out = new java.util.ArrayList<>();
            if (!java.nio.file.Files.isDirectory(regionDir)) return out;
            try (var ds = java.nio.file.Files.newDirectoryStream(regionDir, "c.*.mcc")) {
                for (Path p : ds) {
                    long[] c = parse(p.getFileName().toString(), "c.", ".mcc");
                    if (c != null && (c[0] >> 5) == rx && (c[1] >> 5) == rz) out.add(p);
                }
            }
            return out;
        }

        /**
         * 기준점 뒤 아직 안 쓴 파일을 모두 뜬다(모든 레인 marker 가 끝난 뒤 부른다). 파일마다 그 키 잠금을 잡으므로 그 사이 쓰기는
         * 이 파일 복사가 끝날 때까지 기다린다(파일 하나 길이). 실패한 첫 IO 예외를 던진다.
         */
        public void copyRemaining() throws IOException {
            if (!java.nio.file.Files.isDirectory(regionDir)) return;
            java.util.Map<Long, java.util.List<Path>> ext = new java.util.HashMap<>();
            java.util.List<long[]> regions = new java.util.ArrayList<>();
            try (var ds = java.nio.file.Files.newDirectoryStream(regionDir)) {
                for (Path p : ds) {
                    String n = p.getFileName().toString();
                    long[] c;
                    if ((c = parse(n, "r.", ".mca")) != null) regions.add(c);
                    else if ((c = parse(n, "c.", ".mcc")) != null) ext.computeIfAbsent(key((int) c[0] >> 5, (int) c[1] >> 5), x -> new java.util.ArrayList<>()).add(p);
                }
            }
            for (long[] c : regions) {
                int rx = (int) c[0], rz = (int) c[1];
                long k = key(rx, rz);
                if (resolved.contains(k)) continue;
                synchronized (lock(k)) {
                    copyLocked(k, rx, rz, ext.getOrDefault(k, java.util.List.of()));
                }
            }
            if (failure != null) throw failure;
        }

        /** 스냅샷을 끝낸다(진행 중인 복사가 끝날 때까지 기다린다). 이후 쓰기는 복사하지 않는다. */
        public void end() {
            var w = gate.writeLock();
            w.lock();
            try {
                ended = true;
            } finally {
                w.unlock();
            }
            synchronized (RegionFileStore.this) {
                if (snapshot == this) snapshot = null;
            }
        }

        public long bytes() { return bytes.get(); }

        public int files() { return files.get(); }

        /** 기준점 뒤 쓰기가 먼저 와서 쓰기 직전에 뜬 파일 수(관측·시험). */
        public int copiedOnWrite() { return copiedOnWrite.get(); }

        public IOException failure() { return failure; }
    }

    /** "r.1.-2.mca" → {1,-2}. 형식이 아니면 null. */
    private static long[] parse(String name, String prefix, String suffix) {
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) return null;
        String mid = name.substring(prefix.length(), name.length() - suffix.length());
        int dot = mid.indexOf('.');
        if (dot <= 0 || dot == mid.length() - 1) return null;
        try {
            return new long[] {Integer.parseInt(mid.substring(0, dot)), Integer.parseInt(mid.substring(dot + 1))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 테스트용: 그 청크의 열린 핸들(없으면 null). */
    synchronized RegionFile openHandle(int chunkX, int chunkZ) { return open.get(key(chunkX >> 5, chunkZ >> 5)); }

    /** 테스트용: 지금 열린 핸들 수. */
    synchronized int openCount() { return open.size(); }

    private final java.util.concurrent.atomic.AtomicLong flushedFiles = new java.util.concurrent.atomic.AtomicLong();

    /**
     * 바닐라 RegionFileStorage.flush(save-all flush): 열린 지역 파일마다 force(true). 각 파일은 그 키 잠금 안에서 내린다(쓰기와 엇갈리지 않게).
     * IO 레인의 쓰기가 다 끝난 뒤(World.ioBarrier) 불러야 "지금까지 저장한 것" 이 디스크에 있다. 첫 실패를 던지되 나머지 파일도 내린다.
     */
    public void flush() throws IOException {
        drainClosed();   // LRU 가 내보낸 핸들은 닫으며 force 한다
        java.util.List<java.util.Map.Entry<Long, RegionFile>> all;
        synchronized (this) {
            all = new java.util.ArrayList<>(open.entrySet());
        }
        IOException first = null;
        for (var e : all) {
            synchronized (lock(e.getKey())) {
                RegionFile rf;
                synchronized (this) { rf = open.get(e.getKey()); }
                if (rf != e.getValue()) continue;   // 그 사이 내보내졌다(아래 drainClosed 의 닫기가 force 한다)
                try {
                    rf.flush();
                    flushedFiles.incrementAndGet();
                } catch (IOException ex) {
                    if (first == null) first = ex;
                }
            }
        }
        try {
            drainClosed();
        } catch (IOException ex) {
            if (first == null) first = ex;
        }
        if (first != null) throw first;
    }

    /** 지표·테스트: {@link #flush} 가 force 한 파일 수 누계. */
    public long flushedFiles() { return flushedFiles.get(); }

    public void closeAll() throws IOException {
        drainClosed();
        java.util.List<java.util.Map.Entry<Long, RegionFile>> all;
        synchronized (this) {
            all = new java.util.ArrayList<>(open.entrySet());
            open.clear();
        }
        for (var e : all) {
            synchronized (lock(e.getKey())) { e.getValue().close(); }
        }
    }
}
