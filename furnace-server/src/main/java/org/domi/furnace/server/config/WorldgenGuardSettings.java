// 생성 조절기(T-172): region 틱이 밀리면 동시 생성 수를 줄인다. 기본 꺼짐(결정 W-2) — 끄면 생성 대기열은 예전과 같다.
package org.domi.furnace.server.config;

import org.domi.furnace.server.i18n.Lang;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * furnace.properties 키 {@code worldgen.tick-guard} (기본 false): true 면 가장 느린 region 의 최근 1초 TPS 가 19 아래일 때 동시에 도는
 * 청크 생성 수를 하나씩 줄이고(1 까지) 19.8 이상이면 하나씩 늘린다(생성 스레드 수까지). TPS 바닥을 지키는 대신 새 땅 생성이 느려진다
 * (W60 실측 1판: TPS 최저 4~6 → 9.8, 생성 −35%). 생성과 무관한 렉(다른 차원 농장 등)이어도 모든 생성을 묶는다. 시스템 속성 {@code -Dfurnace.gen.tickGuard=true|false} 가 있으면 그 값이 이긴다.
 */
public record WorldgenGuardSettings(boolean tickGuard) {

    public static final String KEY = "worldgen.tick-guard";
    public static final WorldgenGuardSettings DEFAULT = new WorldgenGuardSettings(false);

    public static WorldgenGuardSettings load(Path file, Consumer<String> warn) {
        return fromProperties(FurnaceProps.read(file), System.getProperty("furnace.gen.tickGuard"), warn);
    }

    static WorldgenGuardSettings fromProperties(Properties p, String sysOverride, Consumer<String> warn) {
        boolean on = DEFAULT.tickGuard;
        String v = p.getProperty(KEY);
        if (v != null) on = parse(KEY, v);
        if (sysOverride != null) {
            boolean sys = parse("-Dfurnace.gen.tickGuard", sysOverride);
            if (v != null && sys != on) warn.accept(Lang.t("cfg.sys-overrides-file", "-Dfurnace.gen.tickGuard=" + sys, KEY + "=" + on));
            on = sys;
        }
        return new WorldgenGuardSettings(on);
    }

    private static boolean parse(String key, String v) {
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(Lang.t("cfg.invalid-bool", key, v));
        }
        return Boolean.parseBoolean(v);
    }
}
