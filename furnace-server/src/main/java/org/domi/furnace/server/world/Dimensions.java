// 서버가 띄운 차원 월드들(바닐라 MinecraftServer.levels). 오버월드 → 네더 → 엔드 → 추가 월드(올린 순서).
// 기본 세 차원은 시작 때 한 번 올리고, 추가 월드(다중 월드, W1)는 실행 중에 올리고 내린다. 읽기는 아무 스레드.
package org.domi.furnace.server.world;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Dimensions {
    private final Map<String, World> byKey = new ConcurrentHashMap<>();
    // 순회가 잦고(틱마다 모든 차원) 바뀌는 일은 드물다 — 쓰기 때 복사
    private final List<World> ordered = new CopyOnWriteArrayList<>();
    private volatile java.util.function.Predicate<String> enabled = d -> true;

    /** 월드를 올린다 — 같은 서버 상태(접속자·시계·엔티티 id)를 첫 월드(오버월드)와 공유하게 묶는다. */
    public synchronized void register(World w) {
        if (byKey.putIfAbsent(w.dimension(), w) != null) throw new IllegalStateException("이미 있는 차원: " + w.dimension());
        if (!ordered.isEmpty()) w.shareServerState(ordered.get(0));
        ordered.add(w);
        w.dimensions(this);
    }

    /** 월드를 목록에서 뗀다(추가 월드 내리기). 첫 월드(오버월드)는 뗄 수 없다. 떼었으면 true. */
    public synchronized boolean unregister(World w) {
        if (!ordered.isEmpty() && ordered.get(0) == w) throw new IllegalArgumentException("오버월드는 내릴 수 없다");
        if (!byKey.remove(w.dimension(), w)) return false;
        ordered.remove(w);
        return true;
    }

    /** 그 차원의 월드(없으면 null). */
    public World get(String levelKey) { return byKey.get(levelKey); }

    public World overworld() { return ordered.isEmpty() ? null : ordered.get(0); }

    /** 등록 순서(오버월드 먼저) 사본. */
    public List<World> all() { return List.copyOf(ordered); }

    /** 레벨 키들(등록 순서) — LoginPlay 의 차원 이름 목록(바닐라 levelKeys). */
    public List<String> levelKeys() {
        List<String> out = new java.util.ArrayList<>(ordered.size());
        for (World w : ordered) out.add(w.dimension());
        return out;
    }

    /** MinecraftServer.isLevelEnabled: 네더는 allow-nether 일 때만 포털로 갈 수 있다. */
    public boolean isLevelEnabled(String levelKey) { return enabled.test(levelKey); }

    public void levelEnabled(java.util.function.Predicate<String> p) { this.enabled = p; }
}
