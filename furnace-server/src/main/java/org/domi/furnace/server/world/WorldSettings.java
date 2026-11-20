// furnace.properties 의 다중 월드·차원 시드 키(모두 기본 꺼짐 = 바닐라).
//   dimension-seed.the_nether=     비우면 주 월드 시드(바닐라). 숫자 또는 글자(글자는 바닐라 level-seed 처럼 hashCode)
//   dimension-seed.the_end=        위와 같다
//   worlds.autoload=               서버 시작 때 불러올 추가 월드 이름(쉼표로). 그 폴더 level.dat 의 설명서(furnace:world)로 연다
//   worldgen-hooks.overworld=      기본 차원의 월드젠 단계 훅 키(플러그인이 WORLD_GENERATION 에서 등록한 것, W3). 비우면 바닐라
//   worldgen-hooks.the_nether=     위와 같다
//   worldgen-hooks.the_end=        위와 같다
//   world-generator.overworld=     기본 차원의 생성기 플러그인 "플러그인[:id]"(Bukkit bukkit.yml worlds.<이름>.generator, T-219). 비우면 바닐라
//   world-generator.the_nether=    위와 같다(the_end 도)
//   world-biome-provider.overworld= 기본 차원의 바이옴 공급자 플러그인 "플러그인[:id]"(bukkit.yml worlds.<이름>.biome-provider). 나머지 차원도 같다
//   world-generator.name.<월드 이름>=        그 이름의 월드에 쓸 생성기 플러그인(T-222 — Paper 는 플러그인이 그 이름으로 생성기 없이 createWorld 할 때 bukkit.yml
//                                            worlds.<이름>.generator 를 쓴다). 이름은 정확히 같아야 한다. 기본 세 월드의 이름(level-name 등)도 되지만 차원 키가 이긴다
//   world-biome-provider.name.<월드 이름>=   위와 같다(바이옴 공급자)
package org.domi.furnace.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;

public record WorldSettings(OptionalLong netherSeed, OptionalLong endSeed, List<String> autoload, java.util.Map<String, String> hooks,
                            java.util.Map<String, String> generators, java.util.Map<String, String> biomeProviders,
                            java.util.Map<String, String> namedGenerators, java.util.Map<String, String> namedBiomeProviders) {

    public static final WorldSettings DEFAULT = new WorldSettings(OptionalLong.empty(), OptionalLong.empty(), List.of(), java.util.Map.of());

    /** 기본 세 차원의 설정 이름(furnace.properties 키 끝) — 차원 키는 "minecraft:" 를 붙인 것. */
    public static final List<String> DIMENSIONS = List.of("overworld", "the_nether", "the_end");

    public WorldSettings {
        autoload = List.copyOf(autoload);
        hooks = java.util.Map.copyOf(hooks);
        generators = java.util.Map.copyOf(generators);
        biomeProviders = java.util.Map.copyOf(biomeProviders);
        namedGenerators = java.util.Map.copyOf(namedGenerators);
        namedBiomeProviders = java.util.Map.copyOf(namedBiomeProviders);
    }

    public WorldSettings(OptionalLong netherSeed, OptionalLong endSeed, List<String> autoload, java.util.Map<String, String> hooks,
                         java.util.Map<String, String> generators, java.util.Map<String, String> biomeProviders) {
        this(netherSeed, endSeed, autoload, hooks, generators, biomeProviders, java.util.Map.of(), java.util.Map.of());
    }

    public WorldSettings(OptionalLong netherSeed, OptionalLong endSeed, List<String> autoload, java.util.Map<String, String> hooks) {
        this(netherSeed, endSeed, autoload, hooks, java.util.Map.of(), java.util.Map.of());
    }

    public WorldSettings(OptionalLong netherSeed, OptionalLong endSeed, List<String> autoload) {
        this(netherSeed, endSeed, autoload, java.util.Map.of());
    }

    /** 그 기본 차원의 생성기 플러그인 "플러그인[:id]"(없으면 빈 문자열, T-219). */
    public String generator(String levelKey) { return generators.getOrDefault(levelKey, ""); }

    /** 그 기본 차원의 바이옴 공급자 플러그인 "플러그인[:id]"(없으면 빈 문자열, T-219). */
    public String biomeProvider(String levelKey) { return biomeProviders.getOrDefault(levelKey, ""); }

    /** 그 이름의 월드에 정한 생성기 플러그인 "플러그인[:id]"(없으면 빈 문자열, T-222). 이름은 정확히 같아야 한다. */
    public String generatorByName(String worldName) { return namedGenerators.getOrDefault(worldName, ""); }

    /** 그 이름의 월드에 정한 바이옴 공급자 플러그인 "플러그인[:id]"(없으면 빈 문자열, T-222). */
    public String biomeProviderByName(String worldName) { return namedBiomeProviders.getOrDefault(worldName, ""); }

    /**
     * 기본 차원의 생성기 — 차원 키(world-generator.overworld 등)가 이기고, 없으면 그 차원의 Bukkit 식 월드 이름(level-name·_nether·_the_end)으로
     * 정한 이름별 키(T-222, Paper 는 이름으로만 찾는다).
     */
    public String generator(String levelKey, String worldName) {
        String g = generator(levelKey);
        return g.isEmpty() ? generatorByName(worldName) : g;
    }

    /** 기본 차원의 바이옴 공급자 — {@link #generator(String, String)} 와 같은 우선순위. */
    public String biomeProvider(String levelKey, String worldName) {
        String b = biomeProvider(levelKey);
        return b.isEmpty() ? biomeProviderByName(worldName) : b;
    }

    /** 생성기나 공급자 플러그인을 정한 기본 차원인가(worldName: 그 차원의 Bukkit 식 월드 이름). */
    public boolean pluginGenerated(String levelKey, String worldName) {
        return !generator(levelKey, worldName).isEmpty() || !biomeProvider(levelKey, worldName).isEmpty();
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
        Properties p;
        try {
            p = org.domi.furnace.server.config.FurnaceProps.read(file);
        } catch (RuntimeException e) {
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
        java.util.Map<String, String> generators = new java.util.HashMap<>();
        java.util.Map<String, String> providers = new java.util.HashMap<>();
        for (String dim : DIMENSIONS) {
            String k = p.getProperty("worldgen-hooks." + dim, "").strip();
            if (!k.isEmpty()) hooks.put("minecraft:" + dim, k);
            String g = p.getProperty("world-generator." + dim, "").strip();
            if (!g.isEmpty()) generators.put("minecraft:" + dim, g);
            String b = p.getProperty("world-biome-provider." + dim, "").strip();
            if (!b.isEmpty()) providers.put("minecraft:" + dim, b);
        }
        java.util.Map<String, String> namedGenerators = new java.util.HashMap<>();
        java.util.Map<String, String> namedProviders = new java.util.HashMap<>();
        for (String key : p.stringPropertyNames()) {
            named(key, p.getProperty(key), GENERATOR_NAME_PREFIX, namedGenerators);
            named(key, p.getProperty(key), PROVIDER_NAME_PREFIX, namedProviders);
        }
        return new WorldSettings(seed(p.getProperty("dimension-seed.the_nether")), seed(p.getProperty("dimension-seed.the_end")), names, hooks,
                generators, providers, namedGenerators, namedProviders);
    }

    /** 이름별 키 접두(T-222) — 뒤는 월드 이름 그대로(점·빈칸 포함). */
    public static final String GENERATOR_NAME_PREFIX = "world-generator.name.";
    public static final String PROVIDER_NAME_PREFIX = "world-biome-provider.name.";

    private static void named(String key, String raw, String prefix, java.util.Map<String, String> out) {
        if (!key.startsWith(prefix) || key.length() == prefix.length()) return;
        String v = raw == null ? "" : raw.strip();
        if (!v.isEmpty()) out.put(key.substring(prefix.length()), v);
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
