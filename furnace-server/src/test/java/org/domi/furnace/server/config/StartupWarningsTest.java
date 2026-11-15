package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.domi.furnace.server.remote.RconLimits;
import org.junit.jupiter.api.Test;

class StartupWarningsTest {

    private static boolean has(List<String> w, String part) {
        return w.stream().anyMatch(x -> x.contains(part));
    }

    @Test
    void rconDefaultsAndGoodPasswordAreQuiet() {
        assertEquals(List.of(), StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT));
        assertEquals(List.of(), StartupWarnings.rcon(false, "x", new RconLimits(16, 100, 1, 1)));   // 꺼져 있으면 보지 않는다
    }

    @Test
    void rconWarnsOnRaisedAndLoweredLimits() {
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT.withMaxClientsPerIp(8)), "is higher than the default 4"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT.withMaxClientsPerIp(16)), "can take every connection"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT.withMaxClientsPerIp(2)), "NAT"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", new RconLimits(16, 4, 5, 50)), "failure limits are above the defaults"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", new RconLimits(16, 4, 1, 10)), "are very low"));
        assertTrue(has(StartupWarnings.rcon(true, "short", RconLimits.DEFAULT), "shorter than 8 characters"));
    }

    @Test
    void proxyWarnings() {
        assertEquals(List.of(), StartupWarnings.proxy(true, false, "", false, true));
        assertTrue(has(StartupWarnings.proxy(false, true, "", false, true), "has no signature"));
        List<String> v = StartupWarnings.proxy(true, false, "abc", false, true);
        assertTrue(has(v, "shorter than 12 characters"));
        assertTrue(has(v, "online-mode=true in server.properties is not used"));
        assertEquals(List.of(), StartupWarnings.proxy(false, false, "a-strong-velocity-secret", false, true));
        List<String> both = StartupWarnings.proxy(false, true, "", true, true);
        assertTrue(has(both, "are both on"));
        assertTrue(has(both, "trusted is empty"));
        assertEquals(List.of(), StartupWarnings.proxy(false, false, "", true, true));   // PROXY 만 단독: NetworkServer 가 따로 경고
    }
}
