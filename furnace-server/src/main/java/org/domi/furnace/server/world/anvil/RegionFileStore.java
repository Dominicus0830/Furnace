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

    /** 다른 하위 디렉토리(엔티티는 "entities"). */
    public RegionFileStore(Path worldDir, String subDir) {
        this.regionDir = worldDir.resolve(subDir);
    }

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
            rf = new RegionFile(regionDir.resolve("r." + rx + "." + rz + ".mca"));
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
        int rx = chunkX >> 5, rz = chunkZ >> 5;
        long k = key(rx, rz);
        T result;
        synchronized (lock(k)) {
            RegionFile rf = fileLocked(k, rx, rz);
            Runnable h = beforeOpHook;
            if (h != null) h.run();
            result = op.run(rf);
        }
        drainClosed();
        return result;
    }

    /** 압축해제된 NBT 바이트(없으면 null). 파일별 락 직렬화. */
    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        return withFile(chunkX, chunkZ, rf -> rf.readChunk(chunkX, chunkZ));
    }

    /** 디스크에 그 청크가 있는가(region 파일이 없으면 만들지 않고 거짓). */
    public boolean hasChunk(int chunkX, int chunkZ) throws IOException {
        if (!java.nio.file.Files.exists(regionDir.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca"))) return false;
        return withFile(chunkX, chunkZ, rf -> rf.hasChunk(chunkX, chunkZ));
    }

    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        if (nbtBytes == null) throw new IllegalArgumentException("nbtBytes null");
        // F-01: deflate 는 순수 CPU 라 파일 잠금 밖에서 한다. 잠금 안에는 섹터 할당·기록만 남는다(같은 파일 병렬도↑).
        byte[] comp = Compression.deflate(nbtBytes);
        withFile(chunkX, chunkZ, rf -> { rf.writeCompressed(chunkX, chunkZ, comp); return null; });
    }

    /** 테스트용: 지금 열린 핸들 수. */
    synchronized int openCount() { return open.size(); }

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
