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
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT.withMaxClientsPerIp(8)), "기본값 4 보다 높습니다"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT.withMaxClientsPerIp(16)), "전부 차지"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", RconLimits.DEFAULT.withMaxClientsPerIp(2)), "NAT"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", new RconLimits(16, 4, 5, 50)), "인증 실패 한도를 기본값"));
        assertTrue(has(StartupWarnings.rcon(true, "a-long-secret", new RconLimits(16, 4, 1, 10)), "매우 낮습니다"));
        assertTrue(has(StartupWarnings.rcon(true, "short", RconLimits.DEFAULT), "8자 미만"));
    }

    @Test
    void proxyWarnings() {
        assertEquals(List.of(), StartupWarnings.proxy(true, false, "", false, true));
        assertTrue(has(StartupWarnings.proxy(false, true, "", false, true), "서명이 없습니다"));
        List<String> v = StartupWarnings.proxy(true, false, "abc", false, true);
        assertTrue(has(v, "12자 미만"));
        assertTrue(has(v, "online-mode=true 는 쓰이지 않습니다"));
        assertEquals(List.of(), StartupWarnings.proxy(false, false, "a-strong-velocity-secret", false, true));
        List<String> both = StartupWarnings.proxy(false, true, "", true, true);
        assertTrue(has(both, "함께 켜져"));
        assertTrue(has(both, "trusted 가 비어"));
        assertEquals(List.of(), StartupWarnings.proxy(false, false, "", true, true));   // PROXY 만 단독: NetworkServer 가 따로 경고
    }
}
