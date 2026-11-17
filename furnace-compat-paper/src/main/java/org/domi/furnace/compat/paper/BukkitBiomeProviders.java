// Bukkit BiomeProvider → Furnace 바이옴 공급자(T-203 3단계) — CraftBukkit CustomWorldChunkManager·CraftBiomeParameterPoint 와 같은 일.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeParameterPoint;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.domi.furnace.api.worldgen.Climate;

public final class BukkitBiomeProviders {

    private BukkitBiomeProviders() {}

    /**
     * 월드가 생기기 전(생성기를 만들 때 {@code getBiomes} 를 부른다)에도 쓸 수 있는 WorldInfo — 월드가 생기면 {@link #bind} 로 그 월드를 따른다
     * (CraftBukkit 은 그 CraftWorld 를 넘긴다). 보통 월드(T-210)는 서버가 생성기를 만들기 직전 {@link #prepare} 로 실제 값(이미 있는 월드는
     * level.dat 시드)을 채우고, UID 는 {@code CraftWorld#getUID} 와 같은 식(레벨 키)이다. 레벨 키를 모르는 임시 월드는 이름에서 만든 값.
     */
    public static final class LateWorldInfo implements WorldInfo {
        private volatile String name;
        private volatile String levelKey;
        private volatile World.Environment environment;
        private volatile long seed;
        private volatile int minHeight, maxHeight;
        /** 약하게 — 공급자는 생성기가 쥐고 생성기는 서버가 쥐므로, 세게 쥐면 버린 레벨이 GC 되지 않는다. */
        private volatile java.lang.ref.WeakReference<WorldInfo> bound;

        public LateWorldInfo(String name, World.Environment environment, long seed, int minHeight, int maxHeight) {
            this.name = name;
            this.environment = environment;
            this.seed = seed;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        public void bind(WorldInfo world) { this.bound = new java.lang.ref.WeakReference<>(world); }

        /** 서버가 준 월드 정보로 채운다(생성기를 만들기 직전, 월드 스레드). */
        void prepare(org.domi.furnace.api.worldgen.BiomeProvider.Context c) {
            this.name = c.worldName();
            this.levelKey = c.levelKey();
            this.environment = switch (c.environment()) {
                case NORMAL -> World.Environment.NORMAL;
                case NETHER -> World.Environment.NETHER;
                case THE_END -> World.Environment.THE_END;
            };
            this.seed = c.seed();
            this.minHeight = c.minY();
            this.maxHeight = c.maxY();
        }

        private WorldInfo bound() {
            var r = bound;
            return r == null ? null : r.get();
        }

        @Override public String getName() { WorldInfo b = bound(); return b != null ? b.getName() : name; }
        @Override public UUID getUID() {
            WorldInfo b = bound();
            if (b != null) return b.getUID();
            String k = levelKey;
            return k != null ? org.bukkit.craftbukkit.CraftWorld.uidOf(k)
                             : UUID.nameUUIDFromBytes(("furnace-world-info:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        @Override public World.Environment getEnvironment() { return environment; }
        @Override public long getSeed() { return seed; }
        @Override public int getMinHeight() { return minHeight; }
        @Override public int getMaxHeight() { return maxHeight; }
    }

    /** Bukkit 공급자를 Furnace 공급자로. info 는 공급자에 넘길 월드 정보. */
    public static org.domi.furnace.api.worldgen.BiomeProvider adapt(BiomeProvider provider, WorldInfo info) {
        java.util.Objects.requireNonNull(provider, "provider");
        return adapt(() -> provider, info);
    }

    /**
     * 공급자를 약하게 쥐는 판 — 따로 떨어진 레벨(임시 월드)용. 공급자는 그 레벨이 쥔다: 플러그인 공급자가 레벨을 되짚어 쥐어도(FAWE 의 내부 클래스 공급자)
     * 서버 생성기가 레벨을 붙잡지 않아 버린 레벨이 GC 된다. 레벨이 거둬진 뒤 부르면 IllegalStateException.
     */
    public static org.domi.furnace.api.worldgen.BiomeProvider adaptWeak(BiomeProvider provider, WorldInfo info) {
        var ref = new java.lang.ref.WeakReference<>(java.util.Objects.requireNonNull(provider, "provider"));
        return adapt(() -> {
            BiomeProvider p = ref.get();
            if (p == null) throw new IllegalStateException("the level that owned this biome provider was discarded");
            return p;
        }, info);
    }

    private static org.domi.furnace.api.worldgen.BiomeProvider adapt(java.util.function.Supplier<BiomeProvider> providerRef, WorldInfo info) {
        return new Adapted(providerRef, info);
    }

    /** Bukkit 공급자를 감싼 Furnace 공급자. {@link #bukkit()} 으로 원래 공급자(CraftWorld#getBiomeProvider)를 돌려준다. */
    public static final class Adapted implements org.domi.furnace.api.worldgen.BiomeProvider {
        private final java.util.function.Supplier<BiomeProvider> providerRef;
        private final WorldInfo info;
        private final String name;

        private Adapted(java.util.function.Supplier<BiomeProvider> providerRef, WorldInfo info) {
            this.providerRef = providerRef;
            this.info = info;
            this.name = providerRef.get().getClass().getName();
        }

        /** 감싼 Bukkit 공급자(약하게 쥔 판이 거둬졌으면 null). */
        public BiomeProvider bukkit() {
            try {
                return providerRef.get();
            } catch (IllegalStateException e) {
                return null;
            }
        }

        /** 공급자에 넘기는 WorldInfo. */
        public WorldInfo worldInfo() { return info; }

        @Override
        public String name() { return name; }

        @Override
        public void prepare(org.domi.furnace.api.worldgen.BiomeProvider.Context world) {
            if (info instanceof LateWorldInfo late) late.prepare(world);
        }

        @Override
        public String biome(int x, int y, int z, Climate climate) {
            Biome b = providerRef.get().getBiome(info, x, y, z, new Point(climate));
            if (b == Biome.CUSTOM) throw new IllegalArgumentException("Cannot set the biome to " + b);
            return b == null ? null : b.getKey().toString();
        }

        @Override
        public List<String> biomes() {
            List<Biome> list = providerRef.get().getBiomes(info);
            if (list == null) throw new IllegalArgumentException("BiomeProvider#getBiomes returned null");
            List<String> out = new ArrayList<>(list.size());
            for (Biome b : list) {
                if (b == null || b == Biome.CUSTOM) throw new IllegalArgumentException("Cannot use the biome " + b);
                out.add(b.getKey().toString());
            }
            return out;
        }
    }

    /** CraftBiomeParameterPoint: 값은 그 칸의 기후(처음 읽을 때 계산), 범위는 기후 밀도 함수의 범위. */
    private record Point(Climate c) implements BiomeParameterPoint {
        @Override public double getTemperature() { return c.value(Climate.Parameter.TEMPERATURE); }
        @Override public double getMaxTemperature() { return c.max(Climate.Parameter.TEMPERATURE); }
        @Override public double getMinTemperature() { return c.min(Climate.Parameter.TEMPERATURE); }
        @Override public double getHumidity() { return c.value(Climate.Parameter.HUMIDITY); }
        @Override public double getMaxHumidity() { return c.max(Climate.Parameter.HUMIDITY); }
        @Override public double getMinHumidity() { return c.min(Climate.Parameter.HUMIDITY); }
        @Override public double getContinentalness() { return c.value(Climate.Parameter.CONTINENTALNESS); }
        @Override public double getMaxContinentalness() { return c.max(Climate.Parameter.CONTINENTALNESS); }
        @Override public double getMinContinentalness() { return c.min(Climate.Parameter.CONTINENTALNESS); }
        @Override public double getErosion() { return c.value(Climate.Parameter.EROSION); }
        @Override public double getMaxErosion() { return c.max(Climate.Parameter.EROSION); }
        @Override public double getMinErosion() { return c.min(Climate.Parameter.EROSION); }
        @Override public double getDepth() { return c.value(Climate.Parameter.DEPTH); }
        @Override public double getMaxDepth() { return c.max(Climate.Parameter.DEPTH); }
        @Override public double getMinDepth() { return c.min(Climate.Parameter.DEPTH); }
        @Override public double getWeirdness() { return c.value(Climate.Parameter.WEIRDNESS); }
        @Override public double getMaxWeirdness() { return c.max(Climate.Parameter.WEIRDNESS); }
        @Override public double getMinWeirdness() { return c.min(Climate.Parameter.WEIRDNESS); }
    }
}
