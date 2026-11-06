// 서버가 띄운 차원 월드들(바닐라 MinecraftServer.levels). 오버월드 → 네더 → 엔드 순서. 등록은 시작 때 한 번, 그 뒤엔 읽기만.
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Dimensions {
    private final Map<String, World> byKey = new ConcurrentHashMap<>();
    private final List<World> ordered = Collections.synchronizedList(new ArrayList<>());
    private volatile java.util.function.Predicate<String> enabled = d -> true;

    /** 월드를 올린다 — 같은 서버 상태(접속자·시계·엔티티 id)를 첫 월드(오버월드)와 공유하게 묶는다. */
    public void register(World w) {
        if (byKey.putIfAbsent(w.dimension(), w) != null) throw new IllegalStateException("이미 있는 차원: " + w.dimension());
        if (!ordered.isEmpty()) w.shareServerState(ordered.get(0));
        ordered.add(w);
        w.dimensions(this);
    }

    /** 그 차원의 월드(없으면 null). */
    public World get(String levelKey) { return byKey.get(levelKey); }

    public World overworld() { return ordered.isEmpty() ? null : ordered.get(0); }

    /** 등록 순서(오버월드 먼저) 사본. */
    public List<World> all() {
        synchronized (ordered) {
            return List.copyOf(ordered);
        }
    }

    /** MinecraftServer.isLevelEnabled: 네더는 allow-nether 일 때만 포털로 갈 수 있다. */
    public boolean isLevelEnabled(String levelKey) { return enabled.test(levelKey); }

    public void levelEnabled(java.util.function.Predicate<String> p) { this.enabled = p; }
}
