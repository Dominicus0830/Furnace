// 추가 월드를 만들거나 이미 있는 폴더를 불러오는 설명서(Bukkit WorldCreator 에 대응). 값 객체 — 빌더로 만든다. 버전 중립.
package org.domi.furnace.api.world;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * @param name               월드 이름 = 서버 폴더 안의 폴더 이름({@code <이름>/}, Paper 와 같은 배치). 영문·숫자·{@code _ . -}
 * @param key                레벨(차원) 키. 명령의 {@code /execute in <키>} 와 이벤트의 world() 가 이것이다. 기본 {@code minecraft:<소문자 이름>}(Paper 와 같다)
 * @param environment        저장 폴더 배치와 기본 차원 타입·생성기
 * @param generator          지형 생성기
 * @param dimensionType      차원 타입 키(minecraft:overworld … 또는 플러그인이 등록한 것). null 이면 환경의 기본
 * @param seed               생성 시드. 비면 주 월드 시드(바닐라: 모든 차원이 같은 시드). 이미 있는 월드는 그 level.dat 의 시드가 이긴다
 * @param generateStructures 구조물(마을·요새 …)을 만드는가. 노이즈 생성기에만 해당
 * @param flatLayers         평지 층(아래부터). 비면 바닐라 기본 층. FLAT 에서만
 * @param flatBiome          평지 바이옴. FLAT 에서만
 * @param generationHooks    월드젠 단계 훅 키(LifecycleEvents.WORLD_GENERATION 에서 등록한 것). 빈 문자열이면 없음(바닐라)
 */
public record WorldCreator(String name, String key, WorldEnvironment environment, WorldGenerator generator, String dimensionType,
                           OptionalLong seed, boolean generateStructures, List<FlatLayer> flatLayers, String flatBiome,
                           String generationHooks) {

    /** 훅 없는 설명서(이전 판과 같은 모양). */
    public WorldCreator(String name, String key, WorldEnvironment environment, WorldGenerator generator, String dimensionType,
                        OptionalLong seed, boolean generateStructures, List<FlatLayer> flatLayers, String flatBiome) {
        this(name, key, environment, generator, dimensionType, seed, generateStructures, flatLayers, flatBiome, "");
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** 평지 한 층. block 은 블록 id(minecraft:stone), height 는 1..4064. */
    public record FlatLayer(String block, int height) {
        public FlatLayer {
            block = full("block", block);
            if (height < 1 || height > 4064) throw new IllegalArgumentException("층 높이는 1..4064: " + height);
        }
    }

    public WorldCreator {
        Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("월드 이름은 영문·숫자·_ . - 1~64자: " + name);
        }
        key = full("key", key == null ? "minecraft:" + name.toLowerCase(Locale.ROOT) : key);
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(generator, "generator");
        if ((generator == WorldGenerator.LARGE_BIOMES || generator == WorldGenerator.AMPLIFIED || generator == WorldGenerator.SINGLE_BIOME_SURFACE
                || generator == WorldGenerator.DEBUG_ALL_BLOCK_STATES) && environment != WorldEnvironment.NORMAL) {
            throw new IllegalArgumentException(generator + " 생성기는 NORMAL 환경에서만 쓸 수 있다");
        }
        dimensionType = full("dimensionType", dimensionType == null ? environment.defaultDimensionType() : dimensionType);
        Objects.requireNonNull(seed, "seed");
        flatLayers = List.copyOf(flatLayers);
        flatBiome = full("flatBiome", flatBiome);
        generationHooks = generationHooks == null || generationHooks.isEmpty() ? "" : full("generationHooks", generationHooks);
    }

    private static String full(String what, String id) {
        if (id == null) throw new NullPointerException(what);
        String f = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (!KEY.matcher(f).matches()) throw new IllegalArgumentException(what + " 형식이 잘못됐다(namespace:path): " + id);
        return f;
    }

    /** 이름만 정한 빌더(NORMAL·DEFAULT·주 월드 시드·구조물 켬). */
    public static Builder named(String name) { return new Builder(name); }

    public Builder toBuilder() {
        Builder b = new Builder(name).key(key).environment(environment).generator(generator).dimensionType(dimensionType)
                .generateStructures(generateStructures).flatLayers(flatLayers).flatBiome(flatBiome).generationHooks(generationHooks);
        if (seed.isPresent()) b.seed(seed.getAsLong());
        return b;
    }

    public static final class Builder {
        private final String name;
        private String key;
        private WorldEnvironment environment = WorldEnvironment.NORMAL;
        private WorldGenerator generator = WorldGenerator.DEFAULT;
        private String dimensionType;
        private OptionalLong seed = OptionalLong.empty();
        private boolean generateStructures = true;
        private List<FlatLayer> flatLayers = List.of();
        private String flatBiome = "minecraft:plains";
        private String generationHooks = "";

        private Builder(String name) { this.name = name; }

        public Builder key(String key) { this.key = key; return this; }

        public Builder environment(WorldEnvironment environment) { this.environment = environment; return this; }

        public Builder generator(WorldGenerator generator) { this.generator = generator; return this; }

        /** 차원 타입 키(바닐라 또는 플러그인 등록). null 이면 환경의 기본. */
        public Builder dimensionType(String key) { this.dimensionType = key; return this; }

        public Builder seed(long seed) { this.seed = OptionalLong.of(seed); return this; }

        /** 주 월드 시드를 쓴다(기본). */
        public Builder sameSeedAsMainWorld() { this.seed = OptionalLong.empty(); return this; }

        public Builder generateStructures(boolean v) { this.generateStructures = v; return this; }

        public Builder flatLayers(List<FlatLayer> layers) { this.flatLayers = layers; return this; }

        public Builder flatBiome(String biome) { this.flatBiome = biome; return this; }

        /**
         * 월드젠 단계 훅 키({@link org.domi.furnace.api.plugin.lifecycle.LifecycleEvents#WORLD_GENERATION} 에서 등록한 것).
         * null·빈 문자열이면 없음. level.dat 설명서에 남아 다시 불러올 때도 같은 키를 쓴다 — 그때 키가 등록돼 있지 않으면 바닐라로 생성한다.
         */
        public Builder generationHooks(String key) { this.generationHooks = key == null ? "" : key; return this; }

        public WorldCreator build() {
            return new WorldCreator(name, key, environment, generator, dimensionType, seed, generateStructures, flatLayers, flatBiome,
                    generationHooks);
        }
    }
}
