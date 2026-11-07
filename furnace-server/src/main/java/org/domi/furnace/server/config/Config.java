// 서버 런타임 설정 값
package org.domi.furnace.server.config;

/**
 * velocitySecret: 비어 있지 않으면 Velocity 모던 포워딩을 켠다(Paper proxies.velocity.secret).
 * bungeecord: BungeeCord 레거시 IP 포워딩(Spigot settings.bungeecord, 기본 꺼짐). Velocity 포워딩과 함께 켤 수 없다.
 * regionSectionShift·regionMergeRadius: 동적 region(Folia 방식)의 구역 크기(2^shift 청크)와 합치기 반경(구역 단위),
 * regionThreads: region 을 돌리는 작업 스레드 수. 기본(shared-region-pool=true)은 서버 전체 공유 풀의 크기, false 면 차원마다 이만큼.
 */
public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance, int entityViewDistance, int regionSectionShift, int regionMergeRadius, int regionThreads,
                     String levelName, long levelSeed, int ioThreads, int autosaveTicks,
                     int chunkEvictionGraceTicks, int spawnChunkRadius, int chunksPerTick,
                     String velocitySecret, boolean bungeecord) {

    /** chunks-per-tick=auto: 고정 상한 없이 클라이언트가 알린 속도(바닐라 PlayerChunkSender, 최대 64)를 따른다. */
    public static final int CHUNKS_PER_TICK_AUTO = 0;

    /** view-distance 하한 — 서버가 LoginPlay로 알리는 최소 반경(클라 렌더 거리 하한과 같다). */
    public static final int MIN_VIEW_DISTANCE = 2;
    /** view-distance 상한 — 클라이언트 렌더 거리 최대. 이보다 큰 서버 값은 이 값으로 조정된다. */
    public static final int MAX_VIEW_DISTANCE = 32;

    /** furnace.properties 에 있어도 무시되는(server.properties 가 정하는) 키. */
    public static final java.util.Set<String> VANILLA_KEYS =
            java.util.Set.of("server-name", "port", "compression-threshold", "view-distance", "level-name", "level-seed");

    /**
     * server.properties 값으로 바닐라와 겹치는 항목(motd·포트·압축·시야·월드 이름·시드)을 덮는다.
     * view-distance 는 바닐라 ChunkMap 처럼 2..32 로 조정하고, 음수 압축 임계값은 끔(-1)으로 본다.
     */
    public Config withServerProperties(ServerProperties sp, int portOverride, String worldOverride, long seed) {
        int port = portOverride >= 0 ? portOverride : sp.serverPort;
        int compression = sp.networkCompressionThreshold < 0 ? -1 : sp.networkCompressionThreshold;
        int view = Math.clamp(sp.viewDistance, MIN_VIEW_DISTANCE, MAX_VIEW_DISTANCE);
        String level = worldOverride != null ? worldOverride : sp.levelName;
        return new Config(sp.motd, tickNanos, port, compression, view, entityViewDistance, regionSectionShift, regionMergeRadius, regionThreads,
                level, seed, ioThreads, autosaveTicks, chunkEvictionGraceTicks, spawnChunkRadius, chunksPerTick, velocitySecret, bungeecord);
    }

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5, 8, 4, 1,
                    Runtime.getRuntime().availableProcessors(),
                    "world", 0L, 4, 6000, 100, 2, CHUNKS_PER_TICK_AUTO, "", false);
}
