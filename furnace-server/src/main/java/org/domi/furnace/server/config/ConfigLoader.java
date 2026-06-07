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
        return new Config(name, tick, port, compression);
    }
}
