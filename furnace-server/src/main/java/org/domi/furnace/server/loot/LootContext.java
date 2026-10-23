// 바닐라 LootContext — 매개변수·난수·행운·월드, 그리고 표/조건/수정자 순환 방지.
package org.domi.furnace.server.loot;

import java.util.LinkedHashSet;
import java.util.Set;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public final class LootContext {

    private final LootData data;
    private final LootWorld world;
    private final LootParams params;
    private final NoiseRandom random;
    private final Set<Object> visited = new LinkedHashSet<>();

    public LootContext(LootData data, LootWorld world, LootParams params, NoiseRandom random) {
        this.data = data;
        this.world = world;
        this.params = params;
        this.random = random;
    }

    public LootData data() { return data; }

    public LootRegistries registries() { return data.registries(); }

    public LootWorld world() { return world; }

    public LootParams params() { return params; }

    public NoiseRandom random() { return random; }

    public float luck() { return params.luck(); }

    public <T> T param(LootParams.Key<T> key) { return params.get(key); }

    public boolean hasParam(LootParams.Key<?> key) { return params.has(key); }

    boolean pushVisited(Object o) { return visited.add(o); }

    void popVisited(Object o) { visited.remove(o); }
}
