package org.bukkit.craftbukkit.generator;

import java.util.UUID;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;

/**
 * Paper CraftWorldInfo — 월드가 생기기 전(생성기를 만들 때 {@code getBiomes} 를 부른다)에도 쓸 수 있는 WorldInfo(T-264 P2c-3, 옛
 * BukkitBiomeProviders$LateWorldInfo). 월드가 생기면 {@link #bind} 로 그 월드를 따른다(CraftBukkit 은 그 CraftWorld 를 넘긴다). 보통 월드(T-210)는
 * 서버가 생성기를 만들기 직전 {@link #prepare} 로 실제 값(이미 있는 월드는 level.dat 시드)을 채우고, UID 는 {@code CraftWorld#getUID} 와 같은 식
 * (레벨 키)이다. 레벨 키를 모르는 임시 월드는 이름에서 만든 값. 생성자·bind·prepare 는 Furnace 전용이라 패키지 전용 — 호환 계층은
 * {@code BukkitBiomeProviders} 를 거친다.
 */
public class CraftWorldInfo implements WorldInfo {
    private volatile String name;
    private volatile String levelKey;
    private volatile World.Environment environment;
    private volatile long seed;
    private volatile int minHeight, maxHeight;
    /** 약하게 — 공급자는 생성기가 쥐고 생성기는 서버가 쥐므로, 세게 쥐면 버린 레벨이 GC 되지 않는다. */
    private volatile java.lang.ref.WeakReference<WorldInfo> bound;

    CraftWorldInfo(String name, World.Environment environment, long seed, int minHeight, int maxHeight) {
        this.name = name;
        this.environment = environment;
        this.seed = seed;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    void bind(WorldInfo world) { this.bound = new java.lang.ref.WeakReference<>(world); }

    /** 서버가 준 월드 정보로 채운다(생성기를 만들기 직전, 월드 스레드). */
    void prepare(org.domi.furnace.api.worldgen.BiomeProvider.Context c) {
        this.name = c.worldName();
        this.levelKey = c.levelKey();
        // enum switch 는 합성 CraftWorldInfo$1 을 만들어 Paper 의 같은 이름(익명 BiomeProvider)과 부딪힌다 — if 사슬로
        var e = c.environment();
        this.environment = e == org.domi.furnace.api.world.WorldEnvironment.NETHER ? World.Environment.NETHER
                : e == org.domi.furnace.api.world.WorldEnvironment.THE_END ? World.Environment.THE_END
                : World.Environment.NORMAL;
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
