// 플러그인이 등록하는 바이옴 정의. 클라이언트가 받는 기후·색·소리와 서버가 쓰는 몹 스폰 설정을 한 곳에 모은다.
// 필드 이름은 마인크래프트 버전과 상관없이 유지된다(버전 중립) — 새 버전이 필드를 더하면 빌더에 더하고 기본값은 이전 동작을 지킨다.
// 값 객체라 equals 로 같은 정의인지 비교한다(핫 리로드에서 달라졌는지 알아낼 때 쓴다).
package org.domi.furnace.api.worldgen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * @param hasPrecipitation 비·눈이 내리는가(false 면 사막처럼 날씨가 와도 하늘만 변한다)
 * @param temperature      기본 온도. 0.15 미만이면 눈, 아니면 비(높이 보정 있음)
 * @param temperatureModifier 온도 보정(FROZEN 은 얼어붙은 바다처럼 잡음으로 일부를 0.2 로 낮춘다)
 * @param downfall         강수량(풀·잎 색 계산용)
 * @param fogColor         안개 색(RGB)
 * @param waterColor       물 색(RGB)
 * @param waterFogColor    물속 안개 색(RGB)
 * @param skyColor         하늘 색(RGB)
 * @param foliageColor     잎 색 고정값(없으면 온도·강수량으로 계산)
 * @param grassColor       풀 색 고정값(없으면 온도·강수량으로 계산)
 * @param grassColorModifier 풀 색 보정
 * @param ambientParticle  주변 입자(없으면 null)
 * @param ambientSound     주변 소리 id(없으면 null) — 리소스 팩의 소리도 된다
 * @param moodSound        동굴 소리(없으면 null)
 * @param additionsSound   무작위 추가 소리(없으면 null)
 * @param music            배경 음악(없으면 null)
 * @param creatureSpawnProbability 청크 생성 때 동물이 나올 확률(0 이상 1 미만, 바닐라 기본 0.1)
 * @param spawners         종류별 스폰 목록(자연 스폰이 이 목록을 가중치로 고른다)
 * @param spawnCosts       몹 종류별 스폰 비용(밀집 제한용, 바닐라 spawn_costs)
 */
public record BiomeDefinition(
        boolean hasPrecipitation, float temperature, TemperatureModifier temperatureModifier, float downfall,
        int fogColor, int waterColor, int waterFogColor, int skyColor,
        OptionalInt foliageColor, OptionalInt grassColor, GrassColorModifier grassColorModifier,
        AmbientParticle ambientParticle, String ambientSound, MoodSound moodSound, AdditionsSound additionsSound, Music music,
        float creatureSpawnProbability, Map<SpawnCategory, List<Spawner>> spawners, Map<String, SpawnCost> spawnCosts) {

    public enum TemperatureModifier {
        NONE("none"), FROZEN("frozen");

        private final String key;

        TemperatureModifier(String key) { this.key = key; }

        public String key() { return key; }
    }

    public enum GrassColorModifier {
        NONE("none"), DARK_FOREST("dark_forest"), SWAMP("swamp");

        private final String key;

        GrassColorModifier(String key) { this.key = key; }

        public String key() { return key; }
    }

    /** 입자 종류(minecraft:ash)와 틱당 확률. 옵션이 필요한 입자(먼지·블록·아이템 등)는 쓸 수 없다. */
    public record AmbientParticle(String type, float probability) {
        public AmbientParticle {
            type = ResourceKeys.reference("입자 종류", type);
            if (!(probability >= 0f && probability <= 1f)) throw new IllegalArgumentException("입자 확률은 0..1: " + probability);
        }
    }

    /** 동굴 소리: 어두운 곳에서 tickDelay 틱마다 blockSearchExtent 칸 안 어두운 칸을 찾아 offset 만큼 띄운 곳에서 낸다. */
    public record MoodSound(String sound, int tickDelay, int blockSearchExtent, double offset) {
        public MoodSound {
            sound = ResourceKeys.reference("소리", sound);
            ResourceKeys.finite("offset", offset);
        }

        /** 바닐라 동굴 소리 기본값(6000틱, 8칸, 2.0). */
        public static MoodSound of(String sound) { return new MoodSound(sound, 6000, 8, 2.0); }
    }

    /** 무작위 추가 소리: 틱마다 tickChance 확률. */
    public record AdditionsSound(String sound, double tickChance) {
        public AdditionsSound {
            sound = ResourceKeys.reference("소리", sound);
            ResourceKeys.finite("tickChance", tickChance);
        }
    }

    /** 배경 음악. 대기 시간은 틱. */
    public record Music(String sound, int minDelay, int maxDelay, boolean replaceCurrentMusic) {
        public Music {
            sound = ResourceKeys.reference("소리", sound);
            if (minDelay < 0 || maxDelay < minDelay) throw new IllegalArgumentException("음악 대기 시간은 0 <= min <= max: " + minDelay + ", " + maxDelay);
        }
    }

    /** 스폰 목록 한 줄: 몹 종류(minecraft:cow), 가중치, 한 번에 나오는 수. */
    public record Spawner(String entityType, int weight, int minCount, int maxCount) {
        public Spawner {
            entityType = ResourceKeys.reference("몹 종류", entityType);
            if (entityType.equals("minecraft:player")) throw new IllegalArgumentException("플레이어는 스폰 목록에 넣을 수 없다");
            if (weight < 0) throw new IllegalArgumentException("가중치는 0 이상: " + weight);
            if (minCount < 1 || maxCount < minCount) throw new IllegalArgumentException("수는 1 <= min <= max: " + minCount + ", " + maxCount);
        }
    }

    /** 스폰 비용: 이 몹이 있으면 주변 에너지 예산을 charge 만큼 쓴다(영혼 모래 골짜기 같은 밀집 제한). */
    public record SpawnCost(double energyBudget, double charge) {
        public SpawnCost {
            ResourceKeys.finite("energyBudget", energyBudget);
            ResourceKeys.finite("charge", charge);
        }
    }

    public BiomeDefinition {
        if (temperatureModifier == null || grassColorModifier == null) throw new NullPointerException("modifier");
        ResourceKeys.finite("temperature", temperature);
        ResourceKeys.finite("downfall", downfall);
        if (ambientSound != null) ambientSound = ResourceKeys.reference("소리", ambientSound);
        if (!(creatureSpawnProbability >= 0f && creatureSpawnProbability < 1f)) {
            throw new IllegalArgumentException("동물 생성 확률은 0 이상 1 미만: " + creatureSpawnProbability);
        }
        Map<SpawnCategory, List<Spawner>> sp = new EnumMap<>(SpawnCategory.class);
        for (var e : spawners.entrySet()) sp.put(e.getKey(), List.copyOf(e.getValue()));
        spawners = java.util.Collections.unmodifiableMap(sp);
        Map<String, SpawnCost> costs = new LinkedHashMap<>();
        for (var e : spawnCosts.entrySet()) costs.put(ResourceKeys.reference("몹 종류", e.getKey()), e.getValue());
        spawnCosts = java.util.Collections.unmodifiableMap(costs);
    }

    /** 평원과 비슷한 기본값으로 시작하는 빌더(온도 0.8, 강수량 0.4, 비 내림, 바닐라 평원의 안개·물·하늘 색). */
    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.hasPrecipitation = hasPrecipitation;
        b.temperature = temperature;
        b.temperatureModifier = temperatureModifier;
        b.downfall = downfall;
        b.fogColor = fogColor;
        b.waterColor = waterColor;
        b.waterFogColor = waterFogColor;
        b.skyColor = skyColor;
        b.foliageColor = foliageColor;
        b.grassColor = grassColor;
        b.grassColorModifier = grassColorModifier;
        b.ambientParticle = ambientParticle;
        b.ambientSound = ambientSound;
        b.moodSound = moodSound;
        b.additionsSound = additionsSound;
        b.music = music;
        b.creatureSpawnProbability = creatureSpawnProbability;
        spawners.forEach((k, v) -> b.spawners.put(k, new ArrayList<>(v)));
        b.spawnCosts.putAll(spawnCosts);
        return b;
    }

    public Optional<AmbientParticle> particle() { return Optional.ofNullable(ambientParticle); }

    public static final class Builder {
        boolean hasPrecipitation = true;
        float temperature = 0.8f;
        TemperatureModifier temperatureModifier = TemperatureModifier.NONE;
        float downfall = 0.4f;
        int fogColor = 12638463, waterColor = 4159204, waterFogColor = 329011, skyColor = 7907327;
        OptionalInt foliageColor = OptionalInt.empty(), grassColor = OptionalInt.empty();
        GrassColorModifier grassColorModifier = GrassColorModifier.NONE;
        AmbientParticle ambientParticle;
        String ambientSound;
        MoodSound moodSound;
        AdditionsSound additionsSound;
        Music music;
        float creatureSpawnProbability = 0.1f;
        final Map<SpawnCategory, List<Spawner>> spawners = new EnumMap<>(SpawnCategory.class);
        final Map<String, SpawnCost> spawnCosts = new LinkedHashMap<>();

        private Builder() {}

        public Builder hasPrecipitation(boolean v) { hasPrecipitation = v; return this; }

        public Builder temperature(float v) { temperature = v; return this; }

        public Builder temperatureModifier(TemperatureModifier v) { temperatureModifier = v; return this; }

        public Builder downfall(float v) { downfall = v; return this; }

        public Builder fogColor(int rgb) { fogColor = rgb; return this; }

        public Builder waterColor(int rgb) { waterColor = rgb; return this; }

        public Builder waterFogColor(int rgb) { waterFogColor = rgb; return this; }

        public Builder skyColor(int rgb) { skyColor = rgb; return this; }

        public Builder foliageColor(int rgb) { foliageColor = OptionalInt.of(rgb); return this; }

        public Builder grassColor(int rgb) { grassColor = OptionalInt.of(rgb); return this; }

        public Builder grassColorModifier(GrassColorModifier v) { grassColorModifier = v; return this; }

        public Builder ambientParticle(String type, float probability) { ambientParticle = new AmbientParticle(type, probability); return this; }

        public Builder ambientSound(String sound) { ambientSound = sound; return this; }

        public Builder moodSound(MoodSound v) { moodSound = v; return this; }

        public Builder additionsSound(AdditionsSound v) { additionsSound = v; return this; }

        public Builder music(Music v) { music = v; return this; }

        public Builder creatureSpawnProbability(float v) { creatureSpawnProbability = v; return this; }

        public Builder spawner(SpawnCategory category, Spawner spawner) {
            spawners.computeIfAbsent(java.util.Objects.requireNonNull(category, "category"), k -> new ArrayList<>())
                    .add(java.util.Objects.requireNonNull(spawner, "spawner"));
            return this;
        }

        public Builder spawner(SpawnCategory category, String entityType, int weight, int minCount, int maxCount) {
            return spawner(category, new Spawner(entityType, weight, minCount, maxCount));
        }

        public Builder spawnCost(String entityType, double energyBudget, double charge) {
            spawnCosts.put(entityType, new SpawnCost(energyBudget, charge));
            return this;
        }

        public BiomeDefinition build() {
            return new BiomeDefinition(hasPrecipitation, temperature, temperatureModifier, downfall, fogColor, waterColor, waterFogColor,
                    skyColor, foliageColor, grassColor, grassColorModifier, ambientParticle, ambientSound, moodSound, additionsSound, music,
                    creatureSpawnProbability, spawners, spawnCosts);
        }
    }
}
