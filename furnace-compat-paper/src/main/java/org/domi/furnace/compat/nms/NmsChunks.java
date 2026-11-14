package org.domi.furnace.compat.nms;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.World;

/**
 * NMS 청크 shim(N2a)의 서버 다리 — {@link ChunkInternals}(번호 단위 읽기)를 한 번 꺼내 두고, 바이옴 번호 ↔ {@code Holder<Biome>} 표를 만든다.
 * 쓰기는 여기 없다: 블록 쓰기는 {@code World.editBlocksNow}(바닐라 setBlock 플래그와 같은 뜻)로 간다.
 */
public final class NmsChunks {

    private NmsChunks() {}

    /** 서버마다 다리 하나(시험은 한 JVM 에서 서버를 여러 번 띄운다). */
    private record Cached(org.domi.furnace.api.Furnace server, ChunkInternals bridge) {}

    private static volatile Cached cached;
    private static volatile BiomeTable biomes;

    /** 서버의 청크 내부 다리(서버마다 처음 한 번 꺼낸다). */
    public static ChunkInternals bridge() {
        var server = org.domi.furnace.api.Furnace.server();
        Cached c = cached;
        if (c == null || c.server != server) cached = c = new Cached(server, server.internals().chunks());
        return c.bridge;
    }

    private record BiomeTable(org.domi.furnace.api.Furnace server, List<String> keys, List<Holder.Reference<Biome>> holders, java.util.Map<String, Holder.Reference<Biome>> byKey) {}

    private static BiomeTable biomeTable() {
        BiomeTable t = biomes;
        return t != null && t.server == org.domi.furnace.api.Furnace.server() ? t : rebuildBiomes();
    }

    /** 서버 바이옴 목록으로 표를 다시 만든다(처음, 또는 모르는 번호·키가 나왔을 때 — 데이터 팩 바이옴이 늘었을 수 있다). 같은 키는 같은 객체를 잇는다. */
    private static synchronized BiomeTable rebuildBiomes() {
        var server = org.domi.furnace.api.Furnace.server();
        BiomeTable t = biomes;
        List<String> keys = bridge().biomeKeys();
        if (t != null && t.server == server && t.keys.equals(keys)) return t;
        java.util.ArrayList<Holder.Reference<Biome>> hs = new java.util.ArrayList<>(keys.size());
        java.util.HashMap<String, Holder.Reference<Biome>> by = new java.util.HashMap<>();
        for (String k : keys) {
            Holder.Reference<Biome> old = t == null ? null : t.byKey.get(k);   // 같은 키는 같은 객체(== 비교)
            if (old == null) {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(k));
                old = new Holder.Reference<>(key, new Biome(key));
            }
            hs.add(old);
            by.put(k, old);
        }
        biomes = t = new BiomeTable(server, List.copyOf(keys), List.copyOf(hs), java.util.Map.copyOf(by));
        return t;
    }

    /** 바이옴 레지스트리 번호의 holder. 모르는 번호면 IllegalStateException(서버가 준 번호라 생기지 않아야 한다). */
    public static Holder.Reference<Biome> biome(int id) {
        var hs = biomeTable().holders;
        if (id >= hs.size()) hs = rebuildBiomes().holders;
        if (id < 0 || id >= hs.size()) throw new IllegalStateException("모르는 바이옴 번호: " + id);
        return hs.get(id);
    }

    /** holder 의 바이옴 레지스트리 번호. 이 서버의 바이옴이 아니면 IllegalArgumentException. */
    public static int biomeId(Holder<Biome> holder) {
        if (holder != null && holder.value() instanceof Biome b) {
            String k = b.furnaceKey().location().toString();
            int i = biomeTable().keys.indexOf(k);
            if (i < 0) i = rebuildBiomes().keys.indexOf(k);
            if (i >= 0) return i;
        }
        throw new IllegalArgumentException("이 서버의 바이옴이 아니다: " + holder);
    }

    /** 바이옴 키의 holder(없으면 null). */
    public static Holder.Reference<Biome> biome(String key) { return biomeTable().byKey.get(key); }

    /** 서버 청크 번호 → BlockState shim. */
    public static net.minecraft.world.level.block.state.BlockState state(int id) {
        return net.minecraft.world.level.block.Block.stateById(id);
    }

    /** 청크가 로드돼 있지 않을 때 — Paper 는 주 스레드에서 동기로 불러오지만 Furnace 는 동기로 불러오지 않는다. */
    public static IllegalStateException notLoaded(World w, int cx, int cz, String what) {
        return new IllegalStateException(what + ": 청크 " + cx + ", " + cz + " (" + w.dimension() + ") 가 로드돼 있지 않다 — Furnace 는 청크를 동기로 불러오지 않는다"
                + "(Paper 의 getChunk 는 주 스레드에서 불러온다). getChunkIfLoaded·getChunkAtIfLoadedImmediately 로 확인하거나 Bukkit World#getChunkAtAsync 를 쓴다");
    }
}
