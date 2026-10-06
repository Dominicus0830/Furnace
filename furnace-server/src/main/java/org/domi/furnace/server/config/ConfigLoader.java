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
        // 잘못된 값이 런타임 한참 뒤(0 나눗셈 등)에 터지지 않게 기동 시점에 거른다.
        require(tick > 0, "tick-nanos", tick, "1 이상");
        require(port >= 0 && port <= 65535, "port", port, "0~65535");
        require(compression >= -1, "compression-threshold", compression, "-1(끔) 이상");
        require(viewDistance >= 2 && viewDistance <= 32, "view-distance", viewDistance, "2~32");
        require(entityViewDistance >= 1, "entity-view-distance", entityViewDistance, "1 이상");
        require(regionSize >= 1, "region-size", regionSize, "1 이상");
        require(regionThreads >= 1, "region-threads", regionThreads, "1 이상");
        return new Config(name, tick, port, compression, viewDistance, entityViewDistance, regionSize, regionThreads);
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
}
