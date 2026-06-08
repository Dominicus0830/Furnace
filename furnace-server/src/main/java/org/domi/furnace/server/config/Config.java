// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance, int entityViewDistance, int regionSize, int regionThreads,
                     String levelName, int ioThreads, int autosaveTicks, int chunkEvictionGraceTicks) {

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5, 8, 16,
                    Runtime.getRuntime().availableProcessors(),
                    "world", 4, 6000, 100);
}
