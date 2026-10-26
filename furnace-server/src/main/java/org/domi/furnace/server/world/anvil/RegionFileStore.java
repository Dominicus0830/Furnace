// region 디렉토리의 .mca 핸들 캐시 + 파일별 락 직렬화. 여러 region 스레드가 공유 안전.
package org.domi.furnace.server.world.anvil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class RegionFileStore {

    private final Path regionDir;
    private final Map<Long, RegionFile> open = new HashMap<>();

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

    private synchronized RegionFile file(int chunkX, int chunkZ) throws IOException {
        int rx = chunkX >> 5, rz = chunkZ >> 5;
        long k = key(rx, rz);
        RegionFile rf = open.get(k);
        if (rf == null) {
            rf = new RegionFile(regionDir.resolve("r." + rx + "." + rz + ".mca"));
            open.put(k, rf);
        }
        return rf;
    }

    /** 압축해제된 NBT 바이트(없으면 null). 파일별 락 직렬화. */
    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        RegionFile rf = file(chunkX, chunkZ);
        synchronized (rf) {
            return rf.readChunk(chunkX, chunkZ);
        }
    }

    /** 디스크에 그 청크가 있는가(region 파일이 없으면 만들지 않고 거짓). */
    public boolean hasChunk(int chunkX, int chunkZ) throws IOException {
        if (!java.nio.file.Files.exists(regionDir.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca"))) return false;
        RegionFile rf = file(chunkX, chunkZ);
        synchronized (rf) {
            return rf.hasChunk(chunkX, chunkZ);
        }
    }

    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        RegionFile rf = file(chunkX, chunkZ);
        synchronized (rf) {
            rf.writeChunk(chunkX, chunkZ, nbtBytes);
        }
    }

    public synchronized void closeAll() throws IOException {
        for (RegionFile rf : open.values()) rf.close();
        open.clear();
    }
}
