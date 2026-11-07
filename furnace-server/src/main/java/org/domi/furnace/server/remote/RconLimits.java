// RCON 연결 한도: 동시 연결(전체·IP당)과 인증 실패 한도. 기본값은 지금까지의 고정값(16 / 4 / 5 / 10)이고
// furnace.properties 의 rcon.* 키로 바꿀 수 있다. 바닐라 RCON 에는 이런 한도가 없다(보안 보강).
package org.domi.furnace.server.remote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

public record RconLimits(int maxClients, int maxClientsPerIp, int maxFailsPerConnection, int maxFailsPerIp) {

    public static final int DEFAULT_MAX_CLIENTS = 16;
    public static final int DEFAULT_MAX_CLIENTS_PER_IP = 4;
    public static final int DEFAULT_MAX_FAILS_PER_CONNECTION = 5;
    public static final int DEFAULT_MAX_FAILS_PER_IP = 10;

    public static final RconLimits DEFAULT = new RconLimits(DEFAULT_MAX_CLIENTS, DEFAULT_MAX_CLIENTS_PER_IP,
            DEFAULT_MAX_FAILS_PER_CONNECTION, DEFAULT_MAX_FAILS_PER_IP);

    public static Set<String> knownKeys() {
        return Set.of("rcon.max-clients", "rcon.max-clients-per-ip", "rcon.max-fails-per-connection", "rcon.max-fails-per-ip");
    }

    public RconLimits withMaxClientsPerIp(int n) {
        return new RconLimits(maxClients, n, maxFailsPerConnection, maxFailsPerIp);
    }

    public RconLimits withMaxClients(int n) {
        return new RconLimits(n, maxClientsPerIp, maxFailsPerConnection, maxFailsPerIp);
    }

    /** furnace.properties 를 읽는다(파일이 없으면 기본값). 잘못된 값은 IllegalArgumentException. */
    public static RconLimits load(Path file) {
        if (!Files.exists(file)) return DEFAULT;
        return fromProperties(org.domi.furnace.server.config.FurnaceProps.read(file));
    }

    public static RconLimits fromProperties(Properties p) {
        return new RconLimits(
                intKey(p, "rcon.max-clients", DEFAULT_MAX_CLIENTS),
                intKey(p, "rcon.max-clients-per-ip", DEFAULT_MAX_CLIENTS_PER_IP),
                intKey(p, "rcon.max-fails-per-connection", DEFAULT_MAX_FAILS_PER_CONNECTION),
                intKey(p, "rcon.max-fails-per-ip", DEFAULT_MAX_FAILS_PER_IP));
    }

    private static int intKey(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        long n;
        try {
            n = Long.parseLong(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 숫자가 아님: " + v, e);
        }
        if (n < 1 || n > 10_000) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 " + n + " 잘못됨(1~10000)");
        }
        return (int) n;
    }
}
