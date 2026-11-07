// 플러그인이 등록하는 차원 타입 정의(minecraft:dimension_type 의 항목). 클라이언트가 하늘·높이 범위·환경을 정하는 데 쓰고,
// 서버는 같은 값으로 침대·리스폰 앵커·물 증발·몹 스폰 밝기를 정한다. 필드 이름은 버전 중립이다.
// 값 객체라 equals 로 같은 정의인지 비교한다.
package org.domi.furnace.api.worldgen;

import java.util.OptionalLong;

/**
 * @param fixedTime        고정 시각(틱, 없으면 낮밤이 돈다)
 * @param hasSkyLight      하늘빛이 있는가(없으면 비·번개·낮 하늘 없음)
 * @param hasCeiling       천장이 있는가(네더처럼 높이 128 위에 기반암 천장, 구름·날씨 없음)
 * @param ultraWarm        물이 증발하고 용암이 빨리 흐르는가
 * @param natural          나침반·리스폰 앵커·침대가 바닐라 오버월드처럼 동작하는 "자연" 차원인가(네더 포털 생성, 좀비 피글린 스폰에 관여)
 * @param coordinateScale  포털 좌표 비율(1e-5..3e7)
 * @param bedWorks         침대로 잘 수 있는가
 * @param respawnAnchorWorks 리스폰 앵커가 동작하는가
 * @param minY             가장 낮은 블록 높이(16의 배수, -2032..2031)
 * @param height           전체 높이(16의 배수, 16..4064), minY + height 는 2032 이하
 * @param logicalHeight    텔레포트·포털·구근 같은 것이 허용하는 높이(0..height)
 * @param infiniburn       영원히 타는 블록 태그(예: minecraft:infiniburn_overworld, '#' 없이)
 * @param effects          하늘 효과 id(minecraft:overworld · minecraft:the_nether · minecraft:the_end 또는 리소스 팩의 id)
 * @param ambientLight     환경광(0..1, 네더는 0.1)
 * @param piglinSafe       피글린이 좀비화하지 않는가
 * @param hasRaids         습격이 일어나는가
 * @param monsterSpawnLightMin 몬스터가 나오는 밝기 상한 범위의 아래(포함)
 * @param monsterSpawnLightMax 몬스터가 나오는 밝기 상한 범위의 위(포함, 15 이하) — 둘이 같으면 고정값
 * @param monsterSpawnBlockLightLimit 블록 빛이 이 값보다 밝으면 몬스터가 나오지 않는다(0..15)
 */
public record DimensionTypeDefinition(
        OptionalLong fixedTime, boolean hasSkyLight, boolean hasCeiling, boolean ultraWarm, boolean natural, double coordinateScale,
        boolean bedWorks, boolean respawnAnchorWorks, int minY, int height, int logicalHeight, String infiniburn, String effects,
        float ambientLight, boolean piglinSafe, boolean hasRaids, int monsterSpawnLightMin, int monsterSpawnLightMax,
        int monsterSpawnBlockLightLimit) {

    public static final String EFFECTS_OVERWORLD = "minecraft:overworld";
    public static final String EFFECTS_NETHER = "minecraft:the_nether";
    public static final String EFFECTS_END = "minecraft:the_end";

    public DimensionTypeDefinition {
        if (fixedTime == null) throw new NullPointerException("fixedTime");
        if (!(coordinateScale >= 1.0E-5 && coordinateScale <= 3.0E7)) throw new IllegalArgumentException("coordinateScale 는 1e-5..3e7: " + coordinateScale);
        if (minY < -2032 || minY > 2031 || (minY & 15) != 0) throw new IllegalArgumentException("minY 는 16의 배수, -2032..2031: " + minY);
        if (height < 16 || height > 4064 || (height & 15) != 0) throw new IllegalArgumentException("height 는 16의 배수, 16..4064: " + height);
        if (minY + height > 2032) throw new IllegalArgumentException("minY + height 는 2032 이하: " + (minY + height));
        if (logicalHeight < 0 || logicalHeight > height) throw new IllegalArgumentException("logicalHeight 는 0..height: " + logicalHeight);
        infiniburn = ResourceKeys.reference("infiniburn", infiniburn.startsWith("#") ? infiniburn.substring(1) : infiniburn);
        effects = ResourceKeys.reference("effects", effects);
        ResourceKeys.finite("ambientLight", ambientLight);
        if (monsterSpawnLightMin < 0 || monsterSpawnLightMax > 15 || monsterSpawnLightMin > monsterSpawnLightMax) {
            throw new IllegalArgumentException("monsterSpawnLight 는 0 <= min <= max <= 15: " + monsterSpawnLightMin + ", " + monsterSpawnLightMax);
        }
        if (monsterSpawnBlockLightLimit < 0 || monsterSpawnBlockLightLimit > 15) {
            throw new IllegalArgumentException("monsterSpawnBlockLightLimit 는 0..15: " + monsterSpawnBlockLightLimit);
        }
    }

    /** 바닐라 오버월드 값으로 시작하는 빌더. */
    public static Builder overworld() {
        return new Builder(OptionalLong.empty(), true, false, false, true, 1.0, true, false, -64, 384, 384, "minecraft:infiniburn_overworld",
                EFFECTS_OVERWORLD, 0f, false, true, 0, 7, 0);
    }

    /** 바닐라 네더 값으로 시작하는 빌더. */
    public static Builder nether() {
        return new Builder(OptionalLong.of(18000), false, true, true, false, 8.0, false, true, 0, 256, 128, "minecraft:infiniburn_nether",
                EFFECTS_NETHER, 0.1f, true, false, 7, 7, 15);
    }

    /** 바닐라 엔드 값으로 시작하는 빌더. */
    public static Builder end() {
        return new Builder(OptionalLong.of(6000), false, false, false, false, 1.0, false, false, 0, 256, 256, "minecraft:infiniburn_end",
                EFFECTS_END, 0f, false, true, 0, 7, 0);
    }

    public Builder toBuilder() {
        return new Builder(fixedTime, hasSkyLight, hasCeiling, ultraWarm, natural, coordinateScale, bedWorks, respawnAnchorWorks, minY, height,
                logicalHeight, infiniburn, effects, ambientLight, piglinSafe, hasRaids, monsterSpawnLightMin, monsterSpawnLightMax,
                monsterSpawnBlockLightLimit);
    }

    /** 높이 범위의 위(배타). */
    public int maxY() { return minY + height; }

    public static final class Builder {
        private OptionalLong fixedTime;
        private boolean hasSkyLight, hasCeiling, ultraWarm, natural;
        private double coordinateScale;
        private boolean bedWorks, respawnAnchorWorks;
        private int minY, height, logicalHeight;
        private String infiniburn, effects;
        private float ambientLight;
        private boolean piglinSafe, hasRaids;
        private int monsterSpawnLightMin, monsterSpawnLightMax, monsterSpawnBlockLightLimit;

        private Builder(OptionalLong fixedTime, boolean hasSkyLight, boolean hasCeiling, boolean ultraWarm, boolean natural,
                        double coordinateScale, boolean bedWorks, boolean respawnAnchorWorks, int minY, int height, int logicalHeight,
                        String infiniburn, String effects, float ambientLight, boolean piglinSafe, boolean hasRaids,
                        int monsterSpawnLightMin, int monsterSpawnLightMax, int monsterSpawnBlockLightLimit) {
            this.fixedTime = fixedTime;
            this.hasSkyLight = hasSkyLight;
            this.hasCeiling = hasCeiling;
            this.ultraWarm = ultraWarm;
            this.natural = natural;
            this.coordinateScale = coordinateScale;
            this.bedWorks = bedWorks;
            this.respawnAnchorWorks = respawnAnchorWorks;
            this.minY = minY;
            this.height = height;
            this.logicalHeight = logicalHeight;
            this.infiniburn = infiniburn;
            this.effects = effects;
            this.ambientLight = ambientLight;
            this.piglinSafe = piglinSafe;
            this.hasRaids = hasRaids;
            this.monsterSpawnLightMin = monsterSpawnLightMin;
            this.monsterSpawnLightMax = monsterSpawnLightMax;
            this.monsterSpawnBlockLightLimit = monsterSpawnBlockLightLimit;
        }

        public Builder fixedTime(long ticks) { fixedTime = OptionalLong.of(ticks); return this; }

        public Builder noFixedTime() { fixedTime = OptionalLong.empty(); return this; }

        public Builder hasSkyLight(boolean v) { hasSkyLight = v; return this; }

        public Builder hasCeiling(boolean v) { hasCeiling = v; return this; }

        public Builder ultraWarm(boolean v) { ultraWarm = v; return this; }

        public Builder natural(boolean v) { natural = v; return this; }

        public Builder coordinateScale(double v) { coordinateScale = v; return this; }

        public Builder bedWorks(boolean v) { bedWorks = v; return this; }

        public Builder respawnAnchorWorks(boolean v) { respawnAnchorWorks = v; return this; }

        /** 높이 범위를 한꺼번에 정한다. logicalHeight 는 height 와 같게 둔다(따로 정하려면 logicalHeight 를 뒤에). */
        public Builder heightRange(int minY, int height) {
            this.minY = minY;
            this.height = height;
            this.logicalHeight = height;
            return this;
        }

        public Builder logicalHeight(int v) { logicalHeight = v; return this; }

        public Builder infiniburn(String tag) { infiniburn = tag; return this; }

        public Builder effects(String id) { effects = id; return this; }

        public Builder ambientLight(float v) { ambientLight = v; return this; }

        public Builder piglinSafe(boolean v) { piglinSafe = v; return this; }

        public Builder hasRaids(boolean v) { hasRaids = v; return this; }

        public Builder monsterSpawnLight(int min, int max) { monsterSpawnLightMin = min; monsterSpawnLightMax = max; return this; }

        public Builder monsterSpawnBlockLightLimit(int v) { monsterSpawnBlockLightLimit = v; return this; }

        public DimensionTypeDefinition build() {
            return new DimensionTypeDefinition(fixedTime, hasSkyLight, hasCeiling, ultraWarm, natural, coordinateScale, bedWorks,
                    respawnAnchorWorks, minY, height, logicalHeight, infiniburn, effects, ambientLight, piglinSafe, hasRaids,
                    monsterSpawnLightMin, monsterSpawnLightMax, monsterSpawnBlockLightLimit);
        }
    }
}
