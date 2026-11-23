// Bukkit BiomeProvider → Furnace 바이옴 공급자(T-203 3단계) — CraftBukkit CustomWorldChunkManager·CraftBiomeParameterPoint 와 같은 일.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.domi.furnace.api.worldgen.Climate;

public final class BukkitBiomeProviders {

    private BukkitBiomeProviders() {}

    /** 월드가 생기기 전에도 쓸 수 있는 WorldInfo(CraftWorldInfo, 생성자는 패키지 전용). */
    public static org.bukkit.craftbukkit.generator.CraftWorldInfo worldInfo(String name, World.Environment environment, long seed, int minHeight, int maxHeight) {
        return CraftAccess.newWorldInfo(name, environment, seed, minHeight, maxHeight);
    }

    /** 월드가 생기면 그 월드를 따르게 한다(CraftWorldInfo#bind). */
    public static void bind(org.bukkit.craftbukkit.generator.CraftWorldInfo info, WorldInfo world) { CraftAccess.worldInfoBind(info, world); }

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
            if (info instanceof org.bukkit.craftbukkit.generator.CraftWorldInfo late) CraftAccess.worldInfoPrepare(late, world);
        }

        @Override
        public String biome(int x, int y, int z, Climate climate) {
            Biome b = providerRef.get().getBiome(info, x, y, z, CraftAccess.newBiomeParameterPoint(climate));
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
}
