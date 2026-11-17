// 월드 장식 목록(T-217 World#addChunkDecorator — Bukkit World#getPopulators 와 같은 자리). 생성기(노이즈·평지)가 쥐고, 장식 단계 끝에 돈다.
// 생성기와 무관하게 모든 월드에 있다. 목록은 복사 후 쓰기라 생성 스레드가 잠금 없이 읽는다.
package org.domi.furnace.server.world.gen;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.worldgen.ChunkDecorator;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public final class ChunkDecorators {

    private final CopyOnWriteArrayList<ChunkDecorator> list = new CopyOnWriteArrayList<>();
    /** 그 월드의 이름·시드·블록 표(영역 보기를 만든다). 월드를 만들 때 {@link #bind} 로 한 번 정한다 — 그 전에는 돌지 않는다. */
    private volatile GenHooks context;

    public void add(ChunkDecorator d) { list.add(java.util.Objects.requireNonNull(d, "decorator")); }

    public boolean remove(ChunkDecorator d) { return list.remove(d); }

    public List<ChunkDecorator> list() { return List.copyOf(list); }

    public boolean isEmpty() { return list.isEmpty(); }

    /** 원점 청크 장식 끝(바닐라 장식·훅 decorate 뒤). */
    public void run(FeatureWorldView view, int ox, int oz) {
        GenHooks c = context;
        if (c == null || list.isEmpty()) return;
        c.runDecorators(list, view, ox, oz);
    }

    /** 생성기에 월드 정보를 묶는다(노이즈·평지 — 그 밖의 생성기는 장식 단계가 없어 무시). */
    public static void bind(WorldSource gen, String world, long seed, BlockRegistry blocks, org.domi.furnace.server.net.registry.RegistrySet registries) {
        if (gen instanceof AnvilWorldSource a) gen = a.base();
        if (gen instanceof ScratchWorldSource t) gen = t.base();
        List<String> biomes = registries.entries("minecraft:worldgen/biome");
        if (gen instanceof NoiseWorldSource n) {
            n.chunkDecorators().context = new GenHooks("", k -> null, world, n.seed(), blocks, biomes, n.seaLevel());
        } else if (gen instanceof SuperflatWorldSource f) {
            f.decoratorSeed(seed);
            f.chunkDecorators().context = new GenHooks("", k -> null, world, seed, blocks, biomes, -63);
        }
    }
}
