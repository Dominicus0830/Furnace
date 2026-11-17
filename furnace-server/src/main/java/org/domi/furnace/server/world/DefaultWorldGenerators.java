// T-219 기본 세 월드의 생성기·바이옴 공급자 플러그인(furnace.properties world-generator.<차원>·world-biome-provider.<차원>,
// Bukkit bukkit.yml worlds.<이름>.generator·biome-provider). 월드는 플러그인보다 먼저 바닐라 생성기로 조립해 두고, 월드 전 플러그인
// (Bukkit load: STARTUP)을 켠 뒤 형식 어댑터에게 물어 아직 한 청크도 만들지 않은 생성기를 바꿔 끼운다. 설계 docs/feature-parity/compat-t219-*.md
package org.domi.furnace.server.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.worldgen.BiomeProvider;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.domi.furnace.api.worldgen.DefaultWorldGeneration;
import org.domi.furnace.server.i18n.Lang;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.ChunkDecorators;
import org.domi.furnace.server.world.gen.GenHooks;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultWorldGenerators {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    private DefaultWorldGenerators() {}

    /** 기본 차원 하나 — worldName 은 Bukkit 식 이름(level-name, _nether, _the_end). */
    public record Target(World world, String levelKey, String worldName, WorldEnvironment environment, long seed, int minY, int maxY) {

        DefaultWorldGeneration.Request request(WorldSettings s) {
            return new DefaultWorldGeneration.Request(worldName, levelKey, environment, seed, minY, maxY, s.generator(levelKey), s.biomeProvider(levelKey));
        }
    }

    /** Bukkit 식 기본 월드 이름(CraftBukkit loadWorld0: 오버월드는 level-name, 나머지는 level-name + "_" + 환경 소문자). */
    public static String worldName(String levelName, String levelKey) {
        return switch (levelKey) {
            case "minecraft:the_nether" -> levelName + "_nether";
            case "minecraft:the_end" -> levelName + "_the_end";
            default -> levelName;
        };
    }

    /** 기본 차원의 환경. */
    public static WorldEnvironment environmentOf(String levelKey) {
        return switch (levelKey) {
            case "minecraft:the_nether" -> WorldEnvironment.NETHER;
            case "minecraft:the_end" -> WorldEnvironment.THE_END;
            default -> WorldEnvironment.NORMAL;
        };
    }

    /**
     * 생성기·공급자 플러그인을 정한 기본 차원(키 순서는 오버월드 → 네더 → 엔드). 같은 차원에 Furnace 훅 키(worldgen-hooks.*)가 있으면 그것이
     * 이기고 여기서 뺀다(경고 한 줄 — 둘을 겹쳐 걸 수 없다).
     */
    public static List<String> pending(WorldSettings s, String levelName) {
        List<String> out = new java.util.ArrayList<>();
        for (String dim : WorldSettings.DIMENSIONS) {
            String key = "minecraft:" + dim;
            if (!s.pluginGenerated(key)) continue;
            if (!s.generationHooks(key).isEmpty()) {
                log.warn(Lang.t("world.default-generator-hooks-win", worldName(levelName, key), dim));
                continue;
            }
            out.add(key);
        }
        return out;
    }

    /**
     * 차원마다 형식 어댑터에게 묻고(ask — 전역 스레드에서 부를 것) 생성기를 바꿔 끼운다. 공급자가 있으면 같은 설정·시드의 생성기를 공급자로 다시
     * 만들어 바탕을 바꾸고, 훅 객체는 그 바탕에 건 뒤 {@link ChunkGenerationHooks#attached} 를 부른다. 이미 그 차원 청크를 읽거나 만들었으면
     * 바닐라로 둔다(오류 한 줄). 돌려준 값은 실제로 건 훅 객체(차원 키 → 객체 — 고정 스폰을 물을 때 쓴다).
     */
    public static Map<String, ChunkGenerationHooks> attach(List<Target> targets, WorldSettings settings,
                                                         Function<DefaultWorldGeneration.Request, DefaultWorldGeneration> ask,
                                                         BlockRegistry blocks, RegistrySet registries) {
        Map<String, ChunkGenerationHooks> attached = new LinkedHashMap<>();
        for (Target t : targets) {
            DefaultWorldGeneration.Request r = t.request(settings);
            DefaultWorldGeneration a = ask.apply(r);
            ChunkGenerationHooks hooks = a == null ? null : a.hooks();
            BiomeProvider provider = a == null ? null : a.biomeProvider();
            if (hooks == null && provider == null) continue;   // 어댑터(또는 서버)가 이유를 이미 알렸다 — 바닐라
            if (!(t.world().worldSource() instanceof AnvilWorldSource anvil) || anvil.baseUsed()) {
                log.error(Lang.t("world.default-generator-too-late", t.worldName()));
                continue;
            }
            WorldSource base = anvil.base();
            WorldSource next = base;
            if (provider != null) {
                provider.prepare(r.context());   // 생성기를 만들기 전에(공급자 biomes() 가 그때 불린다) — T-210 과 같다
                if (base instanceof NoiseWorldSource n) {
                    NoiseWorldSource fresh = n.freshCopy(n.seed(), provider);   // 같은 설정·시드, 바이옴 원천만 공급자(생성 풀 공유)
                    for (var d : n.chunkDecorators().list()) fresh.chunkDecorators().add(d);   // 그 사이 단 장식기는 옮긴다
                    ChunkDecorators.bind(fresh, t.levelKey(), t.seed(), blocks, registries);
                    next = fresh;
                } else {
                    log.error(Lang.t("world.default-provider-needs-noise", t.worldName()));   // 디버그 월드 등 — 바이옴 원천을 바꿀 생성기가 아니다
                    continue;
                }
            }
            if (hooks != null) {
                hooks.prepare(r.context());
                if (GenHooks.attachObject(next, hooks, t.levelKey(), t.seed(), blocks, registries) == null) continue;   // 걸 수 없는 생성기(디버그) — attach 가 알렸다
            }
            if (next != base && !anvil.replaceBase(next)) {
                log.error(Lang.t("world.default-generator-too-late", t.worldName()));
                continue;
            }
            if (hooks != null) {
                hooks.attached(t.levelKey());   // Bukkit initWorld: 기본 BlockPopulator
                attached.put(t.levelKey(), hooks);
            }
            log.info(Lang.t("world.default-generator-on", t.worldName(),
                    String.join(" + ", java.util.stream.Stream.of(hooks == null ? null : hooks.name(), provider == null ? null : provider.name())
                            .filter(java.util.Objects::nonNull).toList())));
        }
        return attached;
    }

    /**
     * 생성기 플러그인을 붙인 새 오버월드의 스폰(T-219, CraftBukkit setInitialSpawn): 고정 스폰이 있으면 그 칸·yaw(다른 월드면 Paper 문구의
     * IllegalStateException — 시작이 멈춘다), 없으면 지금 생성기로 바닐라 찾기(평지는 맨 위 층 바로 위).
     */
    public static SpawnPoint initialSpawn(World world, String levelName, ChunkGenerationHooks hooks) {
        var fixed = hooks == null ? null : hooks.fixedSpawn("minecraft:overworld");
        if (fixed != null) {
            if (!levelName.equals(fixed.world())) {
                throw new IllegalStateException("Cannot set spawn point for " + levelName + " to be in another world (" + fixed.world() + ")");
            }
            return new SpawnPoint((int) Math.floor(fixed.x()), (int) Math.floor(fixed.y()), (int) Math.floor(fixed.z()), fixed.yaw());
        }
        NoiseWorldSource gen = NoiseWorldSource.of(world.worldSource());
        if (gen != null && gen.isFlat()) return new SpawnPoint(0, gen.flatSurfaceY(), 0, 0f);
        if (gen != null) return gen.defaultSpawn();
        return world.defaultSpawn();
    }
}
