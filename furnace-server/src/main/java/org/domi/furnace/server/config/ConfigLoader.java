// properties 파일에서 설정을 읽되 없거나 누락된 키는 기본값으로 채우는 로더
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private ConfigLoader() {}

    public static Config load(Path path) {
        return load(path, log::warn);
    }

    /** warn: 값을 조정(clamp)했을 때의 경고 수신자(테스트에서 수집). */
    static Config load(Path path, Consumer<String> warn) {
        if (!Files.exists(path)) {
            return Config.DEFAULT;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("설정 로드 실패: " + path, e);
        }
        String name = props.getProperty("server-name", Config.DEFAULT.serverName());
        long tick = parse(props, "tick-nanos", Config.DEFAULT.tickNanos());
        int port = parseInt(props, "port", Config.DEFAULT.port());
        int compression = parseInt(props, "compression-threshold", Config.DEFAULT.compressionThreshold());
        int viewDistance = parseInt(props, "view-distance", Config.DEFAULT.viewDistance());
        int entityViewDistance = parseInt(props, "entity-view-distance", Config.DEFAULT.entityViewDistance());
        int sectionShift = parseInt(props, "region-section-shift", Config.DEFAULT.regionSectionShift());
        int mergeRadius = parseInt(props, "region-merge-radius", Config.DEFAULT.regionMergeRadius());
        int regionThreads = parseInt(props, "region-threads", Config.DEFAULT.regionThreads());
        String levelName = props.getProperty("level-name", Config.DEFAULT.levelName());
        long levelSeed = parseSeed(props.getProperty("level-seed", Long.toString(Config.DEFAULT.levelSeed())));
        int ioThreads = parseInt(props, "io-threads", Config.DEFAULT.ioThreads());
        int autosaveTicks = parseInt(props, "autosave-ticks", Config.DEFAULT.autosaveTicks());
        int evictionGrace = parseInt(props, "chunk-eviction-grace-ticks", Config.DEFAULT.chunkEvictionGraceTicks());
        int spawnChunkRadius = parseInt(props, "spawn-chunk-radius", Config.DEFAULT.spawnChunkRadius());
        int chunksPerTick = parseInt(props, "chunks-per-tick", Config.DEFAULT.chunksPerTick());
        String velocitySecret = props.getProperty("velocity-secret", Config.DEFAULT.velocitySecret()).strip();
        // 잘못된 값이 런타임 한참 뒤(0 나눗셈, 빈 경로 등)에 터지지 않게 기동 시점에 거르고 어느 키가 문제인지 알려준다.
        require(tick > 0, "tick-nanos", tick, "1 이상");
        require(port >= 0 && port <= 65535, "port", port, "0~65535");
        require(compression >= -1, "compression-threshold", compression, "-1(끔) 이상");
        // 하한(<2)은 거부: 선언 반경(LoginPlay는 2 이상)과 실제 전송 반경이 어긋나는 무효값이다.
        // 상한(>32)은 거부하지 않고 32로 조정한다: 클라 렌더 거리 최대라 청크 전송은 어차피 32로 포화되고, 이전에 기동되던
        // 설정을 깨지 않는다. 엔티티 가시 거리(min(entity-view-distance, view-distance))에는 별도 상한이 없으므로
        // 조정은 반드시 여기(설정 값 자체)에서 해야 한다.
        require(viewDistance >= Config.MIN_VIEW_DISTANCE, "view-distance", viewDistance,
                Config.MIN_VIEW_DISTANCE + " 이상(전송 반경 하한)");
        if (viewDistance > Config.MAX_VIEW_DISTANCE) {
            warn.accept("설정 'view-distance' 값 " + viewDistance + " 는 클라이언트 최대 " + Config.MAX_VIEW_DISTANCE
                    + " 를 넘어 " + Config.MAX_VIEW_DISTANCE + " 로 조정합니다");
            viewDistance = Config.MAX_VIEW_DISTANCE;
        }
        require(entityViewDistance >= 1, "entity-view-distance", entityViewDistance, "1 이상");
        require(sectionShift >= 1 && sectionShift <= 8, "region-section-shift", sectionShift, "1~8(구역 = 2^값 청크)");
        require(mergeRadius >= 1, "region-merge-radius", mergeRadius, "1 이상(이웃 구역끼리는 늘 합쳐야 한다)");
        require(regionThreads >= 1, "region-threads", regionThreads, "1 이상");
        require(!levelName.isBlank(), "level-name", 0, "비어 있지 않은 이름(빈 값은 작업 디렉터리를 월드로 쓰게 된다)");
        require(ioThreads >= 1, "io-threads", ioThreads, "1 이상");
        require(autosaveTicks >= 1, "autosave-ticks", autosaveTicks, "1 이상");
        require(evictionGrace >= 1, "chunk-eviction-grace-ticks", evictionGrace, "1 이상");
        require(spawnChunkRadius >= 0, "spawn-chunk-radius", spawnChunkRadius, "0 이상");
        require(chunksPerTick >= 1, "chunks-per-tick", chunksPerTick, "1 이상");
        return new Config(name, tick, port, compression, viewDistance, entityViewDistance,
                sectionShift, mergeRadius, regionThreads, levelName, levelSeed, ioThreads, autosaveTicks, evictionGrace,
                spawnChunkRadius, chunksPerTick, velocitySecret);
    }

    /** 파일에 있지만 server.properties 가 정해서 무시되는 키들(기동 시 경고용). */
    public static java.util.List<String> ignoredVanillaKeys(Path path) {
        if (!Files.exists(path)) return java.util.List.of();
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            return java.util.List.of();
        }
        return Config.VANILLA_KEYS.stream().filter(props::containsKey).sorted().toList();
    }

    private static int parseInt(Properties props, String key, int def) {
        long v = parse(props, key, def);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 " + v + " 가 int 범위를 벗어남");
        }
        return (int) v;
    }

    private static long parse(Properties props, String key, long def) {
        String v = props.getProperty(key);
        if (v == null) return def;
        try {
            return Long.parseLong(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 숫자가 아님: " + v, e);
        }
    }

    private static void require(boolean ok, String key, long value, String expected) {
        if (!ok) throw new IllegalArgumentException("설정 '" + key + "' 값 " + value + " 잘못됨(" + expected + ")");
    }

    private static long parseSeed(String raw) {
        String seed = raw == null ? "" : raw.trim();
        if (seed.isEmpty()) return 0L;
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException ignored) {
            return seed.hashCode();
        }
    }
}
