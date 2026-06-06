// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos) {

    public static final Config DEFAULT = new Config("Furnace Server", 50_000_000L);
}
