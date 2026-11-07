// furnace.properties 의 다중 월드·차원 시드 키(모두 기본 꺼짐 = 바닐라).
//   dimension-seed.the_nether=     비우면 주 월드 시드(바닐라). 숫자 또는 글자(글자는 바닐라 level-seed 처럼 hashCode)
//   dimension-seed.the_end=        위와 같다
//   worlds.autoload=               서버 시작 때 불러올 추가 월드 이름(쉼표로). 그 폴더 level.dat 의 설명서(furnace:world)로 연다
//   worldgen-hooks.overworld=      기본 차원의 월드젠 단계 훅 키(플러그인이 WORLD_GENERATION 에서 등록한 것, W3). 비우면 바닐라
//   worldgen-hooks.the_nether=     위와 같다
//   worldgen-hooks.the_end=        위와 같다
package org.domi.furnace.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;

public record WorldSettings(OptionalLong netherSeed, OptionalLong endSeed, List<String> autoload, java.util.Map<String, String> hooks) {

    public static final WorldSettings DEFAULT = new WorldSettings(OptionalLong.empty(), OptionalLong.empty(), List.of(), java.util.Map.of());

    public WorldSettings {
        autoload = List.copyOf(autoload);
        hooks = java.util.Map.copyOf(hooks);
    }

    public WorldSettings(OptionalLong netherSeed, OptionalLong endSeed, List<String> autoload) {
        this(netherSeed, endSeed, autoload, java.util.Map.of());
    }

    /** 그 기본 차원(minecraft:overworld · the_nether · the_end)에 걸 월드젠 훅 키(없으면 빈 문자열). */
    public String generationHooks(String levelKey) { return hooks.getOrDefault(levelKey, ""); }

    /** 그 기본 차원(minecraft:the_nether · the_end)의 생성 시드 — 정하지 않았으면 주 월드 시드. */
    public long seedFor(String levelKey, long mainSeed) {
        OptionalLong s = switch (levelKey) {
            case "minecraft:the_nether" -> netherSeed;
            case "minecraft:the_end" -> endSeed;
            default -> OptionalLong.empty();
        };
        return s.orElse(mainSeed);
    }

    public static WorldSettings load(Path file) {
        if (!Files.exists(file)) return DEFAULT;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return DEFAULT;
        }
        return from(p);
    }

    static WorldSettings from(Properties p) {
        List<String> names = new ArrayList<>();
        for (String s : p.getProperty("worlds.autoload", "").split(",")) {
            String n = s.strip();
            if (!n.isEmpty() && !names.contains(n)) names.add(n);
        }
        java.util.Map<String, String> hooks = new java.util.HashMap<>();
        for (String dim : List.of("overworld", "the_nether", "the_end")) {
            String k = p.getProperty("worldgen-hooks." + dim, "").strip();
            if (!k.isEmpty()) hooks.put("minecraft:" + dim, k);
        }
        return new WorldSettings(seed(p.getProperty("dimension-seed.the_nether")), seed(p.getProperty("dimension-seed.the_end")), names, hooks);
    }

    /** 바닐라 WorldOptions.parseSeed: 비면 없음, 숫자면 그 값, 아니면 글자의 hashCode. */
    static OptionalLong seed(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return OptionalLong.of(s.hashCode());
        }
    }
}
