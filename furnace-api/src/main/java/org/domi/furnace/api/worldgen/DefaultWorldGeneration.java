package org.domi.furnace.api.worldgen;

/**
 * 기본 월드(오버월드·네더·엔드) 하나에 붙일 생성기·바이옴 공급자 — 서버 설정({@code furnace.properties} 의
 * {@code world-generator.<차원>}·{@code world-biome-provider.<차원>}, 값 {@code 플러그인[:id]}, Bukkit {@code bukkit.yml} 의
 * {@code worlds.<이름>.generator}·{@code biome-provider} 와 같은 뜻)로 형식 어댑터가 고른 것. T-219.
 *
 * @param hooks             월드젠 훅 객체(없으면 null — 바닐라 생성기)
 * @param biomeProvider     바이옴 공급자(없으면 null — 생성기의 바닐라 바이옴)
 * @param generatorClaimed  요청의 생성기 플러그인이 이 형식의 것이라 이 답이 맡았다(실패해도 이유는 이 형식이 이미 알렸다)
 * @param providerClaimed   요청의 공급자 플러그인이 이 형식의 것이라 이 답이 맡았다
 */
public record DefaultWorldGeneration(ChunkGenerationHooks hooks, BiomeProvider biomeProvider, boolean generatorClaimed, boolean providerClaimed) {

    /**
     * 기본 월드 하나에 대한 물음.
     *
     * @param worldName     Bukkit 식 월드 이름({@code level-name}, {@code <level-name>_nether}, {@code <level-name>_the_end})
     * @param levelKey      차원 키({@code minecraft:overworld} 등)
     * @param generator     설정의 생성기 {@code 플러그인[:id]}(없으면 빈 문자열)
     * @param biomeProvider 설정의 공급자 {@code 플러그인[:id]}(없으면 빈 문자열)
     */
    public record Request(String worldName, String levelKey, org.domi.furnace.api.world.WorldEnvironment environment, long seed, int minY, int maxY,
                          String generator, String biomeProvider) {

        public Request {
            generator = generator == null ? "" : generator.strip();
            biomeProvider = biomeProvider == null ? "" : biomeProvider.strip();
        }

        /** 생성기·공급자에게 넘길 월드 정보. */
        public BiomeProvider.Context context() { return new BiomeProvider.Context(worldName, levelKey, environment, seed, minY, maxY); }

        /** {@code 플러그인[:id]} 의 플러그인 이름(콜론 앞, Bukkit 처럼 한 번만 나눈다). */
        public static String pluginOf(String spec) {
            int i = spec.indexOf(':');
            return i < 0 ? spec : spec.substring(0, i);
        }

        /** {@code 플러그인[:id]} 의 id(없으면 null). */
        public static String idOf(String spec) {
            int i = spec.indexOf(':');
            return i < 0 ? null : spec.substring(i + 1);
        }
    }
}
