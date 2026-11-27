package net.minecraft.world.level.biome;

import java.util.Optional;

/**
 * 바이옴 색·효과(Mojang 이름 shim): 안개·물·물속 안개·하늘 색, 잎·풀 색 고정값, 풀 색 보정. 값은 서버의 바이옴 데이터(바닐라 JSON 또는 플러그인 정의)에서
 * 만들 때 한 번 읽은 값이다. 소리·입자·음악 설정은 없다(없는 멤버 — 호출 위치에서 실패).
 */
class BiomeSpecialEffects {

    private final int fogColor;
    private final int waterColor;
    private final int waterFogColor;
    private final int skyColor;
    private final Optional<Integer> foliageColorOverride;
    private final Optional<Integer> grassColorOverride;
    private final GrassColorModifier grassColorModifier;

    /** 호환 모듈 전용. */
    public BiomeSpecialEffects(int fogColor, int waterColor, int waterFogColor, int skyColor, Optional<Integer> foliageColorOverride,
                               Optional<Integer> grassColorOverride, GrassColorModifier grassColorModifier) {
        this.fogColor = fogColor;
        this.waterColor = waterColor;
        this.waterFogColor = waterFogColor;
        this.skyColor = skyColor;
        this.foliageColorOverride = foliageColorOverride;
        this.grassColorOverride = grassColorOverride;
        this.grassColorModifier = grassColorModifier;
    }

    public int getFogColor() { return fogColor; }

    public int getWaterColor() { return waterColor; }

    public int getWaterFogColor() { return waterFogColor; }

    public int getSkyColor() { return skyColor; }

    public Optional<Integer> getFoliageColorOverride() { return foliageColorOverride; }

    public Optional<Integer> getGrassColorOverride() { return grassColorOverride; }

    public GrassColorModifier getGrassColorModifier() { return grassColorModifier; }

    /** 바닐라 풀 색 보정 — 식별 이름과 식은 바닐라 1.21.1 과 같다. */
    public enum GrassColorModifier implements net.minecraft.util.StringRepresentable {
        NONE("none") {
            @Override
            public int modifyColor(double x, double z, int color) { return color; }
        },
        DARK_FOREST("dark_forest") {
            @Override
            public int modifyColor(double x, double z, int color) { return (color & 16711422) + 2634762 >> 1; }
        },
        SWAMP("swamp") {
            @Override
            public int modifyColor(double x, double z, int color) {
                double d = org.domi.furnace.compat.nms.NmsChunks.bridge().biomeInfoNoise(x * 0.0225, z * 0.0225);
                return d < -0.1 ? 5011004 : 6975545;
            }
        };

        private final String name;

        GrassColorModifier(String name) { this.name = name; }

        public abstract int modifyColor(double x, double z, int color);

        public String getName() { return name; }

        @Override
        public String getSerializedName() { return name; }

        /** 호환 모듈 전용: 서버 데이터의 이름("none"·"dark_forest"·"swamp")으로. 모르는 이름은 NONE. */
        public static GrassColorModifier furnaceOf(String name) {
            for (GrassColorModifier m : values()) if (m.name.equals(name)) return m;
            return NONE;
        }
    }
}
