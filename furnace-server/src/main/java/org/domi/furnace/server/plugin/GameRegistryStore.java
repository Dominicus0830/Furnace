// 플러그인이 등록한 게임 레지스트리 항목(바이옴·차원 타입) 저장소. 서버 시작 때 GAME_REGISTRIES 훅에서 채워지고, 그 결과로
// 클라이언트에 보낼 RegistrySet 이 만들어진다(바닐라 항목 뒤에 등록 순서로 붙는다 — 바닐라 인덱스는 그대로).
// 항목은 고정된 뒤 지워지지 않는다: 플러그인이 꺼져도 인덱스(= 클라이언트가 받은 id 공간)는 그대로 두고 비활성으로만 표시하며,
// 그 항목이 서버 쪽에 켜 둔 상태(날씨 성질·스폰 목록)는 걷어 낸다. 핫 리로드로 다시 켜지면 같은 키가 다시 활성이 된다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.domi.furnace.api.plugin.lifecycle.GameRegistrar;
import org.domi.furnace.api.worldgen.BiomeDefinition;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;
import org.domi.furnace.server.entity.spawn.BiomeSpawns;
import org.domi.furnace.server.entity.spawn.MobCategory;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BiomeWeather;

public final class GameRegistryStore {

    public static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    public static final String DIMENSION_TYPE_REGISTRY = "minecraft:dimension_type";

    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** 항목 하나. owner 는 등록한 플러그인 이름, active 는 그 플러그인이 지금 켜져 있는가. */
    public static final class Entry<T> {
        private final String key;
        private final String owner;
        private volatile T definition;
        private volatile boolean active = true;

        Entry(String key, String owner, T definition) {
            this.key = key;
            this.owner = owner;
            this.definition = definition;
        }

        public String key() { return key; }

        public String owner() { return owner; }

        public T definition() { return definition; }

        public boolean active() { return active; }
    }

    private final Map<String, Entry<BiomeDefinition>> biomes = new LinkedHashMap<>();
    private final Map<String, Entry<DimensionTypeDefinition>> dimensionTypes = new LinkedHashMap<>();
    private RegistrySet vanilla;
    private boolean frozen;

    /** 시작 때 훅이 돌기 전에 바닐라 세트를 알려 준다(키 중복·hasBiome 판단용). */
    synchronized void begin(RegistrySet vanillaSet) {
        this.vanilla = vanillaSet;
        this.frozen = false;
    }

    /** 훅이 끝났다 — 이후 등록은 핫 리로드 규칙(이미 있는 자기 키만 갱신)이다. */
    synchronized void freeze() { frozen = true; }

    synchronized boolean isFrozen() { return frozen; }

    /** 바닐라 세트 뒤에 플러그인 항목을 붙인 새 세트(전송 데이터 포함). 바닐라 + 비어 있으면 같은 세트. */
    public synchronized RegistrySet extend(RegistrySet base) {
        List<RegistrySet.Extra> extras = new ArrayList<>();
        biomes.values().forEach(e -> extras.add(new RegistrySet.Extra(BIOME_REGISTRY, e.key, GameRegistryCodec.biome(e.definition))));
        dimensionTypes.values().forEach(e ->
                extras.add(new RegistrySet.Extra(DIMENSION_TYPE_REGISTRY, e.key, GameRegistryCodec.dimensionType(e.definition))));
        return base.withExtras(extras);
    }

    public synchronized Entry<BiomeDefinition> biome(String key) { return biomes.get(key); }

    public synchronized Entry<DimensionTypeDefinition> dimensionType(String key) { return dimensionTypes.get(key); }

    public synchronized List<String> biomeKeys() { return new ArrayList<>(biomes.keySet()); }

    public synchronized List<String> dimensionTypeKeys() { return new ArrayList<>(dimensionTypes.keySet()); }

    /** 그 플러그인의 항목을 비활성으로 하고 서버 쪽에 켜 둔 상태(날씨 성질·스폰 목록)를 걷어 낸다. 인덱스는 유지한다. */
    synchronized void deactivateOwner(String owner) {
        for (Entry<BiomeDefinition> e : biomes.values()) {
            if (e.owner.equals(owner) && e.active) {
                e.active = false;
                BiomeWeather.removeCustom(e.key);
                BiomeSpawns.removeCustom(e.key);
            }
        }
        for (Entry<DimensionTypeDefinition> e : dimensionTypes.values()) {
            if (e.owner.equals(owner)) e.active = false;
        }
    }

    /** 그 플러그인에 묶인 등록기. close() 뒤에는 쓸 수 없다. */
    Registrar registrar(String owner, String namespace, Logger logger) { return new Registrar(owner, namespace, logger); }

    final class Registrar implements GameRegistrar {
        private final String owner;
        private final String namespace;
        private final Logger logger;
        private volatile boolean open = true;

        Registrar(String owner, String namespace, Logger logger) {
            this.owner = owner;
            this.namespace = namespace;
            this.logger = logger;
        }

        void close() { open = false; }

        private String fullKey(String key) {
            if (key == null) throw new NullPointerException("key");
            String full = key.indexOf(':') < 0 ? namespace + ":" + key : key;
            if (!KEY.matcher(full).matches()) throw new IllegalArgumentException("키 형식이 잘못됐다(namespace:path): " + key);
            if (!full.startsWith(namespace + ":")) {
                throw new IllegalArgumentException("키의 namespace 는 이 플러그인의 '" + namespace + "' 여야 한다: " + key);
            }
            return full;
        }

        @Override
        public String registerBiome(String key, BiomeDefinition definition) {
            if (definition == null) throw new NullPointerException("definition");
            if (!open) throw new IllegalStateException("게임 레지스트리 등록기는 훅이 도는 동안만 쓸 수 있다");
            String full = fullKey(key);
            GameRegistryCodec.validate(definition);
            synchronized (GameRegistryStore.this) {
                Entry<BiomeDefinition> old = biomes.get(full);
                if (!frozen) {
                    if (old != null || vanilla.indexOf(BIOME_REGISTRY, full) >= 0) throw new IllegalArgumentException("이미 있는 바이옴 키: " + full);
                    old = new Entry<>(full, owner, definition);
                    biomes.put(full, old);
                } else {
                    if (old == null || !old.owner.equals(owner)) {
                        throw new IllegalStateException("레지스트리가 고정돼 새 바이옴 '" + full + "' 을 더할 수 없다 — 서버를 다시 시작해야 한다");
                    }
                    if (!old.definition.equals(definition)) {
                        logger.warning("바이옴 '" + full + "' 정의가 바뀌었다 — 서버 쪽은 지금부터 새 정의를 쓰지만 이미 접속한 클라이언트는 다시 접속해야 바뀐 색·소리를 받는다");
                    }
                    old.definition = definition;
                    old.active = true;
                }
                activate(old);
            }
            return full;
        }

        @Override
        public String registerDimensionType(String key, DimensionTypeDefinition definition) {
            if (definition == null) throw new NullPointerException("definition");
            if (!open) throw new IllegalStateException("게임 레지스트리 등록기는 훅이 도는 동안만 쓸 수 있다");
            String full = fullKey(key);
            synchronized (GameRegistryStore.this) {
                Entry<DimensionTypeDefinition> old = dimensionTypes.get(full);
                if (!frozen) {
                    if (old != null || vanilla.indexOf(DIMENSION_TYPE_REGISTRY, full) >= 0) throw new IllegalArgumentException("이미 있는 차원 타입 키: " + full);
                    dimensionTypes.put(full, new Entry<>(full, owner, definition));
                } else {
                    if (old == null || !old.owner.equals(owner)) {
                        throw new IllegalStateException("레지스트리가 고정돼 새 차원 타입 '" + full + "' 을 더할 수 없다 — 서버를 다시 시작해야 한다");
                    }
                    if (!old.definition.equals(definition)) {
                        logger.warning("차원 타입 '" + full + "' 정의가 바뀌었다 — 이미 접속한 클라이언트는 다시 접속해야 받는다");
                    }
                    old.definition = definition;
                    old.active = true;
                }
            }
            return full;
        }

        @Override
        public boolean hasBiome(String key) {
            synchronized (GameRegistryStore.this) {
                return biomes.containsKey(key) || vanilla.indexOf(BIOME_REGISTRY, key) >= 0;
            }
        }

        @Override
        public boolean hasDimensionType(String key) {
            synchronized (GameRegistryStore.this) {
                return dimensionTypes.containsKey(key) || vanilla.indexOf(DIMENSION_TYPE_REGISTRY, key) >= 0;
            }
        }
    }

    /** 서버가 쓰는 쪽 상태(날씨 성질·몹 스폰 목록)를 켠다. */
    private static void activate(Entry<BiomeDefinition> e) {
        BiomeDefinition d = e.definition;
        BiomeWeather.putCustom(e.key, new BiomeWeather.Climate(d.temperature(),
                d.temperatureModifier() == BiomeDefinition.TemperatureModifier.FROZEN, d.hasPrecipitation()));
        Map<MobCategory, List<BiomeSpawns.SpawnerData>> spawners = new java.util.EnumMap<>(MobCategory.class);
        for (var c : d.spawners().entrySet()) {
            List<BiomeSpawns.SpawnerData> list = new ArrayList<>();
            for (var s : c.getValue()) list.add(new BiomeSpawns.SpawnerData(s.entityType(), s.weight(), s.minCount(), s.maxCount()));
            spawners.put(MobCategory.byKey(c.getKey().key()), list);
        }
        Map<String, BiomeSpawns.SpawnCost> costs = new LinkedHashMap<>();
        d.spawnCosts().forEach((type, c) -> costs.put(type, new BiomeSpawns.SpawnCost(c.energyBudget(), c.charge())));
        BiomeSpawns.putCustom(e.key, d.creatureSpawnProbability(), spawners, costs);
    }
}
