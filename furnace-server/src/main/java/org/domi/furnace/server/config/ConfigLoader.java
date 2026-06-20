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
        long tick = Long.parseLong(
                props.getProperty("tick-nanos", Long.toString(Config.DEFAULT.tickNanos())));
        int port = Integer.parseInt(
                props.getProperty("port", Integer.toString(Config.DEFAULT.port())));
        int compression = Integer.parseInt(
                props.getProperty("compression-threshold",
                        Integer.toString(Config.DEFAULT.compressionThreshold())));
        int viewDistance = Integer.parseInt(
                props.getProperty("view-distance",
                        Integer.toString(Config.DEFAULT.viewDistance())));
        int entityViewDistance = Integer.parseInt(
                props.getProperty("entity-view-distance",
                        Integer.toString(Config.DEFAULT.entityViewDistance())));
        int regionSize = Integer.parseInt(
                props.getProperty("region-size",
                        Integer.toString(Config.DEFAULT.regionSize())));
        int regionThreads = Integer.parseInt(
                props.getProperty("region-threads",
                        Integer.toString(Config.DEFAULT.regionThreads())));
        String levelName = props.getProperty("level-name", Config.DEFAULT.levelName());
        long levelSeed = parseSeed(props.getProperty("level-seed", Long.toString(Config.DEFAULT.levelSeed())));
        int ioThreads = Integer.parseInt(
                props.getProperty("io-threads", Integer.toString(Config.DEFAULT.ioThreads())));
        int autosaveTicks = Integer.parseInt(
                props.getProperty("autosave-ticks", Integer.toString(Config.DEFAULT.autosaveTicks())));
        int evictionGrace = Integer.parseInt(
                props.getProperty("chunk-eviction-grace-ticks",
                        Integer.toString(Config.DEFAULT.chunkEvictionGraceTicks())));
        int spawnChunkRadius = Integer.parseInt(
                props.getProperty("spawn-chunk-radius",
                        Integer.toString(Config.DEFAULT.spawnChunkRadius())));
        int chunksPerTick = Integer.parseInt(
                props.getProperty("chunks-per-tick",
                        Integer.toString(Config.DEFAULT.chunksPerTick())));
        return new Config(name, tick, port, compression, viewDistance, entityViewDistance,
                regionSize, regionThreads, levelName, levelSeed, ioThreads, autosaveTicks, evictionGrace,
                spawnChunkRadius, chunksPerTick);
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
