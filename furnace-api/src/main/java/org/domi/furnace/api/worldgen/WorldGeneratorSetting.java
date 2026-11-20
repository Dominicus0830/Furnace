package org.domi.furnace.api.worldgen;

/**
 * 서버 설정이 월드 이름 하나에 정해 둔 생성기·바이옴 공급자 플러그인 — {@code furnace.properties} 의
 * {@code world-generator.name.<월드 이름>}·{@code world-biome-provider.name.<월드 이름>}(값 {@code 플러그인[:id]}),
 * Bukkit {@code bukkit.yml} 의 {@code worlds.<이름>.generator}·{@code biome-provider} 와 같은 뜻. T-222.
 *
 * @param generator     생성기 {@code 플러그인[:id]}(없으면 빈 문자열)
 * @param biomeProvider 바이옴 공급자 {@code 플러그인[:id]}(없으면 빈 문자열)
 */
public record WorldGeneratorSetting(String generator, String biomeProvider) {

    /** 설정 없음 — 바닐라. */
    public static final WorldGeneratorSetting NONE = new WorldGeneratorSetting("", "");

    public WorldGeneratorSetting {
        generator = generator == null ? "" : generator;
        biomeProvider = biomeProvider == null ? "" : biomeProvider;
    }
}
