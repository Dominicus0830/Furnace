// properties 파일에서 설정을 읽되 없거나 누락된 키는 기본값으로 채우는 로더
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static Config load(Path path) {
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
        int regionSize = parseInt(props, "region-size", Config.DEFAULT.regionSize());
        int regionThreads = parseInt(props, "region-threads", Config.DEFAULT.regionThreads());
        String levelName = props.getProperty("level-name", Config.DEFAULT.levelName());
        long levelSeed = parseSeed(props.getProperty("level-seed", Long.toString(Config.DEFAULT.levelSeed())));
        int ioThreads = parseInt(props, "io-threads", Config.DEFAULT.ioThreads());
        int autosaveTicks = parseInt(props, "autosave-ticks", Config.DEFAULT.autosaveTicks());
        int evictionGrace = parseInt(props, "chunk-eviction-grace-ticks", Config.DEFAULT.chunkEvictionGraceTicks());
        int spawnChunkRadius = parseInt(props, "spawn-chunk-radius", Config.DEFAULT.spawnChunkRadius());
        int chunksPerTick = parseInt(props, "chunks-per-tick", Config.DEFAULT.chunksPerTick());
        // 잘못된 값이 런타임 한참 뒤(0 나눗셈, 빈 경로 등)에 터지지 않게 기동 시점에 거르고 어느 키가 문제인지 알려준다.
        require(tick > 0, "tick-nanos", tick, "1 이상");
        require(port >= 0 && port <= 65535, "port", port, "0~65535");
        require(compression >= -1, "compression-threshold", compression, "-1(끔) 이상");
        require(viewDistance >= 2 && viewDistance <= 32, "view-distance", viewDistance, "2~32(클라이언트 최대 32)");
        require(entityViewDistance >= 1, "entity-view-distance", entityViewDistance, "1 이상");
        require(regionSize >= 1, "region-size", regionSize, "1 이상");
        require(regionThreads >= 1, "region-threads", regionThreads, "1 이상");
        require(!levelName.isBlank(), "level-name", 0, "비어 있지 않은 이름(빈 값은 작업 디렉터리를 월드로 쓰게 된다)");
        require(ioThreads >= 1, "io-threads", ioThreads, "1 이상");
        require(autosaveTicks >= 1, "autosave-ticks", autosaveTicks, "1 이상");
        require(evictionGrace >= 1, "chunk-eviction-grace-ticks", evictionGrace, "1 이상");
        require(spawnChunkRadius >= 0, "spawn-chunk-radius", spawnChunkRadius, "0 이상");
        require(chunksPerTick >= 1, "chunks-per-tick", chunksPerTick, "1 이상");
        return new Config(name, tick, port, compression, viewDistance, entityViewDistance,
                regionSize, regionThreads, levelName, levelSeed, ioThreads, autosaveTicks, evictionGrace,
                spawnChunkRadius, chunksPerTick);
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
