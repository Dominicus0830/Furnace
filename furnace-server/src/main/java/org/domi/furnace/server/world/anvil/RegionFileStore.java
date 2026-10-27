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

    private RegionFile file(int chunkX, int chunkZ) throws IOException {
        int rx = chunkX >> 5, rz = chunkZ >> 5;
        long k = key(rx, rz);
        RegionFile evicted = null;
        RegionFile rf;
        synchronized (this) {
            rf = open.get(k);
            if (rf == null) {
                rf = new RegionFile(regionDir.resolve("r." + rx + "." + rz + ".mca"));
                open.put(k, rf);
                if (open.size() > MAX_OPEN) {
                    var it = open.values().iterator();
                    evicted = it.next();
                    it.remove();
                }
            }
        }
        // 닫기는 store 잠금 밖에서, 그 파일 잠금 안에서 — 진행 중인 읽기·쓰기가 끝난 뒤에 닫힌다
        if (evicted != null) {
            synchronized (evicted) { evicted.close(); }
        }
        return rf;
    }

    @FunctionalInterface
    private interface Op<T> { T run(RegionFile rf) throws IOException; }

    /** 파일 잠금 안에서 실행. 가져온 뒤 잠그기 전에 LRU 로 닫혔으면 다시 연다. */
    private <T> T withFile(int chunkX, int chunkZ, Op<T> op) throws IOException {
        while (true) {
            RegionFile rf = file(chunkX, chunkZ);
            synchronized (rf) {
                if (rf.isClosed()) continue;
                return op.run(rf);
            }
        }
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
        withFile(chunkX, chunkZ, rf -> { rf.writeChunk(chunkX, chunkZ, nbtBytes); return null; });
    }

    /** 테스트용: 지금 열린 핸들 수. */
    synchronized int openCount() { return open.size(); }

    public synchronized void closeAll() throws IOException {
        for (RegionFile rf : open.values()) {
            synchronized (rf) { rf.close(); }
        }
        open.clear();
    }
}
