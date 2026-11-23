package net.minecraft.world.level.biome;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * 바이옴 shim(Mojang 이름, N2a): 청크 섹션의 바이옴 칸({@code Holder<Biome>})이 가리키는 값. 기후(온도·강수량)와 색({@link BiomeSpecialEffects})은
 * 만들 때 서버의 바이옴 데이터(플러그인 정의가 먼저, 없으면 바닐라 JSON)에서 한 번 읽는다 — 같은 키가 다시 정의돼도 이 값은 그대로다(가정: 바이옴 정의는
 * 서버를 다시 켜야 바뀐다). 생성 설정·스폰·바이옴 안 소리는 없다(없는 멤버 — 호출 위치에서 실패). 같은 바이옴은 같은 객체다.
 *
 * <p>첫 비정적 필드는 바닐라처럼 {@code climateSettings} 다 — 플러그인(squaremap)이 이 필드와 그 값의 float 접근자 순서를 리플렉션으로 읽는다.
 */
public final class Biome {

    private final ClimateSettings climateSettings;
    private final BiomeSpecialEffects specialEffects;
    private final ResourceKey<Biome> key;

    /** 바닐라 Biome.TemperatureModifier(이름만). */
    public enum TemperatureModifier implements net.minecraft.util.StringRepresentable {
        // 상수마다 몸통(바닐라처럼 $1·$2 가 상수의 클래스 — 바깥이 final 이 아니다). modifyTemperature 는 아직 동작 없음(껍데기 멤버로 합쳐진다) — T-261
        NONE("none") {},
        FROZEN("frozen") {};

        private final String name;

        TemperatureModifier(String name) { this.name = name; }

        public String getName() { return name; }

        @Override
        public String getSerializedName() { return name; }
    }

    /**
     * 바닐라 Biome.ClimateSettings 의 값(has_precipitation·temperature·temperature_modifier·downfall) — 접근자 이름·서술자는 바닐라 record 와 같다.
     * 바닐라는 record 지만 shim 은 일반 클래스다: squaremap 이 이 값의 "두 번째 float 접근자"를 리플렉션으로 불러 강수량을 읽는데, 선언 순서가
     * JVM 마다 달라질 수 있어 실제로 들고 있는 하위 클래스({@link Settings})가 선언한 float 접근자 둘이 모두 강수량으로 답한다(순서와 상관없이 같은 값).
     */
    public static class ClimateSettings {

        private final boolean hasPrecipitation;
        private final float temperature;
        private final TemperatureModifier temperatureModifier;
        private final float downfall;

        public ClimateSettings(boolean hasPrecipitation, float temperature, TemperatureModifier temperatureModifier, float downfall) {
            this.hasPrecipitation = hasPrecipitation;
            this.temperature = temperature;
            this.temperatureModifier = temperatureModifier;
            this.downfall = downfall;
        }

        public boolean hasPrecipitation() { return hasPrecipitation; }

        public float temperature() { return temperature; }

        public TemperatureModifier temperatureModifier() { return temperatureModifier; }

        public float downfall() { return downfall; }

        @Override
        public boolean equals(Object o) {
            return o instanceof ClimateSettings c && c.hasPrecipitation == hasPrecipitation && Float.compare(c.temperature, temperature) == 0
                    && c.temperatureModifier == temperatureModifier && Float.compare(c.downfall, downfall) == 0;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(hasPrecipitation, temperature, temperatureModifier, downfall); }

        @Override
        public String toString() {
            return "ClimateSettings[hasPrecipitation=" + hasPrecipitation + ", temperature=" + temperature + ", temperatureModifier=" + temperatureModifier
                    + ", downfall=" + downfall + "]";
        }
    }

    /** 실제로 들고 있는 값: 선언한 float 접근자가 둘뿐이고 둘 다 강수량이다(위 설명). 온도는 상위 클래스의 접근자로 그대로 읽힌다. */
    private static final class Settings extends ClimateSettings {

        private final float rainfall;

        Settings(boolean hasPrecipitation, float temperature, TemperatureModifier temperatureModifier, float downfall) {
            super(hasPrecipitation, temperature, temperatureModifier, downfall);
            this.rainfall = downfall;
        }

        @Override
        public float downfall() { return rainfall; }

        public float rainfallTwin() { return rainfall; }
    }

    /** 호환 모듈 전용 — 바이옴 표(NmsChunks)가 만든다. */
    public Biome(ResourceKey<Biome> key) {
        this.key = java.util.Objects.requireNonNull(key, "key");
        org.domi.furnace.api.internal.ChunkInternals.BiomeInfo info = null;
        try {
            info = org.domi.furnace.compat.nms.NmsChunks.bridge().biomeInfo(key.location().toString());
        } catch (RuntimeException noServer) {
            // 서버 없이 만든 시험 값 — 바닐라 기본값(평원 같은 0.5·0.5, 물 4159204)
        }
        if (info == null) {
            this.climateSettings = new Settings(true, 0.5F, TemperatureModifier.NONE, 0.5F);
            this.specialEffects = new BiomeSpecialEffects(12638463, 4159204, 329011, 7907327, Optional.empty(), Optional.empty(),
                    BiomeSpecialEffects.GrassColorModifier.NONE);
        } else {
            this.climateSettings = new Settings(info.hasPrecipitation(), info.temperature(),
                    info.frozen() ? TemperatureModifier.FROZEN : TemperatureModifier.NONE, info.downfall());
            this.specialEffects = new BiomeSpecialEffects(info.fogColor(), info.waterColor(), info.waterFogColor(), info.skyColor(),
                    info.foliageColor() < 0 ? Optional.empty() : Optional.of(info.foliageColor()),
                    info.grassColor() < 0 ? Optional.empty() : Optional.of(info.grassColor()),
                    BiomeSpecialEffects.GrassColorModifier.furnaceOf(info.grassColorModifier()));
        }
    }

    /** Furnace: 이 바이옴의 레지스트리 키. */
    public ResourceKey<Biome> furnaceKey() { return key; }

    /** 바닐라 getBaseTemperature: 높이 보정 전 기본 온도. */
    public float getBaseTemperature() { return climateSettings.temperature(); }

    public BiomeSpecialEffects getSpecialEffects() { return specialEffects; }

    @Override
    public String toString() { return "Biome[" + key.location() + "]"; }
}
