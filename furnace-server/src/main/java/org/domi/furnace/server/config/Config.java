// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance, int entityViewDistance, int regionSize, int regionThreads,
                     String levelName, long levelSeed, int ioThreads, int autosaveTicks,
                     int chunkEvictionGraceTicks, int spawnChunkRadius, int chunksPerTick) {

    /** view-distance 하한 — 서버가 LoginPlay로 알리는 최소 반경(클라 렌더 거리 하한과 같다). */
    public static final int MIN_VIEW_DISTANCE = 2;
    /** view-distance 상한 — 클라이언트 렌더 거리 최대. 이보다 큰 서버 값은 이 값으로 조정된다. */
    public static final int MAX_VIEW_DISTANCE = 32;

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5, 8, 16,
                    Runtime.getRuntime().availableProcessors(),
                    "world", 0L, 4, 6000, 100, 2, 16);
}
