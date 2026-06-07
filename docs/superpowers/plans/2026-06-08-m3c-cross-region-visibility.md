# M3c — 교차지역 가시성 (스냅샷) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서로 다른 region의 플레이어가 view거리 안이면 region 무관하게 서로 보이게 — 각 region이 매 틱 불변 `EntityView` 스냅샷을 공개하고 가시성 계산이 인접 region 스냅샷을 락프리로 읽는다.

**Architecture:** `Region.tick`이 자기 플레이어를 불변 `EntityView` 리스트로 만들어 `World`의 `ConcurrentHashMap<RegionPos,List<EntityView>>`에 공개. `updateVisibility`를 `Player`→`EntityView` 후보 기반으로 리팩터해, 관찰자 region이 인접 region 스냅샷(view 닿는 반경)을 모아 가시성을 계산. 소유권 무위반(교차region은 불변 읽기만).

**Tech Stack:** Java 21, JUnit 5. M3b(`Region`/`World`/`Player`).

**M3b와의 연결:** M3b `Region.updateVisibility`는 `players.values()`(같은 region Player)만 순회 → 교차region 안 보임. M3c는 후보를 인접 region 스냅샷까지 확장.

**핵심 결정(spec 출처):** 불변 스냅샷 락프리 · 1틱 stale 허용 · EntityView 통일 · 반경 ceil(vd/regionSize)+1. spec §3. 원자 블록연산은 M4.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `entity/EntityView.java` | 교차region 가시성용 불변 엔티티 스냅샷 |
| `world/World.java` | (modify) 스냅샷 레지스트리: publishSnapshot/candidatesAround/neighborRadius |
| `world/Region.java` | (modify) tick서 스냅샷 공개 + updateVisibility를 EntityView 후보 기반으로 |

---

## Task 1: EntityView — 불변 엔티티 스냅샷

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/entity/EntityView.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/entity/EntityViewTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`EntityViewTest.java`:
```java
// EntityView가 Player 상태를 불변 스냅샷으로 담는지 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class EntityViewTest {

    @Test
    void ofPlayer_capturesState() {
        UUID id = UUID.randomUUID();
        Player p = new Player(7, id, "Dom", new EmbeddedChannel());
        p.applyMove(10, -60, 20, 90f, 5f, true);
        EntityView v = EntityView.of(p);
        assertEquals(7, v.entityId());
        assertEquals(id, v.uuid());
        assertEquals("Dom", v.name());
        assertEquals(10, v.x());
        assertEquals(90f, v.yaw());
        assertTrue(v.onGround());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*EntityViewTest"`
Expected: 컴파일 실패 — `EntityView` 없음.

- [ ] **Step 3: `EntityView.java` 작성**

```java
// 교차region 가시성용 불변 엔티티 스냅샷. 다른 region 스레드가 락프리로 읽는다.
package org.domi.furnace.server.entity;

import java.util.UUID;

public record EntityView(int entityId, UUID uuid, String name,
                         double x, double y, double z, float yaw, float pitch, boolean onGround) {

    public static EntityView of(Player p) {
        return new EntityView(p.entityId(), p.uuid(), p.name(),
                p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.onGround());
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*EntityViewTest"`
Expected: PASS (1 test).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(entity): add immutable EntityView snapshot" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: World 스냅샷 레지스트리

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/SnapshotRegistryTest.java`

`publishSnapshot(pos, views)` 덮어쓰기, `candidatesAround(center, radius)` 반경 내 스냅샷 union, `neighborRadius()` 사전계산.

- [ ] **Step 1: 실패하는 테스트 작성**

`SnapshotRegistryTest.java`:
```java
// 스냅샷 공개/반경 수집, neighborRadius 계산 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class SnapshotRegistryTest {

    private static World world(FurnaceSchedulerImpl sched, int regionSize, int viewDistance) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), regionSize, viewDistance, reg.directBits(), 7);
    }

    @Test
    void candidatesAround_collectsNeighbors() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched, 16, 5);
        EntityView v = new EntityView(1, UUID.randomUUID(), "A", 0, -60, 0, 0, 0, true);
        w.publishSnapshot(new RegionPos(0, 0), List.of(v));
        w.publishSnapshot(new RegionPos(1, 0), List.of(
                new EntityView(2, UUID.randomUUID(), "B", 0, -60, 0, 0, 0, true)));
        List<EntityView> got = w.candidatesAround(new RegionPos(0, 0), 1);
        assertEquals(2, got.size()); // (0,0)+(1,0) 둘 다 수집
        sched.close();
    }

    @Test
    void neighborRadius_coversViewDistance() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = world(sched, 4, 10); // vd 10, regionSize 4 → ceil(10/4)+1 = 4
        assertEquals(4, w.neighborRadius());
        sched.close();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*SnapshotRegistryTest"`
Expected: 컴파일 실패 — 메서드 없음.

- [ ] **Step 3: `World.java`에 추가**

import 추가:
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.entity.EntityView;
```
필드 추가(`regionsByThread` 아래):
```java
    private final ConcurrentHashMap<RegionPos, List<EntityView>> snapshots = new ConcurrentHashMap<>();
```
메서드 추가:
```java
    /** region이 자기 플레이어 스냅샷을 공개(덮어쓰기). views는 불변이어야 한다. */
    public void publishSnapshot(RegionPos pos, List<EntityView> views) {
        snapshots.put(pos, views);
    }

    /** center±radius region들의 스냅샷을 모아 반환. */
    public List<EntityView> candidatesAround(RegionPos center, int radius) {
        List<EntityView> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                List<EntityView> s = snapshots.get(new RegionPos(center.x() + dx, center.z() + dz));
                if (s != null) out.addAll(s);
            }
        }
        return out;
    }

    /** view거리가 닿는 인접 region 반경(region 단위). */
    public int neighborRadius() {
        return (int) Math.ceil((double) viewDistance / regionSize) + 1;
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*SnapshotRegistryTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add cross-region snapshot registry" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Region — 스냅샷 공개 + EntityView 가시성

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/CrossRegionVisibilityTest.java`

`tick`에 스냅샷 공개 추가. `updateVisibility(Player)`→`updateVisibility(Player, List<EntityView>)`. `broadcastMove`의 `Player b`→`EntityView b`. `within`도 `EntityView`.

- [ ] **Step 1: 실패하는 테스트 작성**

`CrossRegionVisibilityTest.java`:
```java
// 인접 region 스냅샷에 있는 플레이어가 가시성 후보로 spawn되는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class CrossRegionVisibilityTest {

    @Test
    void neighborRegionPlayer_isTracked() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 1, 5, reg.directBits(), 7); // regionSize 1 → 16블록
        // A는 region(0,0), B는 region(1,0) — 다른 region이지만 16블록 거리(view 5청크=80블록 내)
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        a.publishPosition(0, -60, 0, 0, 0, true);
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        b.publishPosition(16, -60, 0, 0, 0, true);
        RegionPos ra = a.regionPos(1), rb = b.regionPos(1);
        assertNotEquals(ra, rb); // 다른 region 확인
        sched.region(ra).execute(() -> w.regionAt(ra).adopt(a)).get();
        sched.region(rb).execute(() -> w.regionAt(rb).adopt(b)).get();
        // B 틱 먼저(스냅샷 공개), 그 다음 A 틱(B를 후보로 봄)
        sched.region(rb).execute(() -> w.regionAt(rb).tick()).get();
        sched.region(ra).execute(() -> w.regionAt(ra).tick()).get();
        assertTrue(a.tracked().containsKey(2), "A가 인접 region의 B를 추적해야"); // 교차region 가시성
        sched.close();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*CrossRegionVisibilityTest"`
Expected: 실패 — 교차region 미지원(B가 A 후보에 없음).

- [ ] **Step 3: `Region.tick`에 스냅샷 공개 + 가시성 후보 교체**

import 추가:
```java
import org.domi.furnace.server.entity.EntityView;
```
`tick()`의 가시성 루프 부분을 교체. 기존:
```java
        for (Player p : players.values()) {
            updateVisibility(p);
        }
        for (Player p : handoff) {
```
교체:
```java
        // 자기 플레이어 스냅샷 공개(불변 리스트).
        List<EntityView> views = new ArrayList<>(players.size());
        for (Player p : players.values()) views.add(EntityView.of(p));
        world.publishSnapshot(pos, List.copyOf(views));
        // 인접 region 스냅샷까지 후보로 가시성 계산.
        List<EntityView> candidates = world.candidatesAround(pos, world.neighborRadius());
        for (Player p : players.values()) {
            updateVisibility(p, candidates);
        }
        for (Player p : handoff) {
```

- [ ] **Step 4: `updateVisibility`·`within`·`broadcastMove`를 EntityView로 교체**

기존 `updateVisibility(Player a)` 메서드 전체를 교체:
```java
    private void updateVisibility(Player a, List<EntityView> candidates) {
        double range = world.viewDistance() * 16.0;
        for (EntityView b : candidates) {
            if (b.entityId() == a.entityId()) continue;
            boolean near = within(a, b, range);
            boolean tracking = a.tracked().containsKey(b.entityId());
            if (near && !tracking) {
                a.send(new PlayerInfoUpdate(b.uuid(), b.name()));
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                a.tracked().put(b.entityId(), new double[]{b.x(), b.y(), b.z(), b.yaw(), b.pitch()});
            } else if (!near && tracking) {
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                a.tracked().remove(b.entityId());
            } else if (near) {
                broadcastMove(a, b, a.tracked().get(b.entityId()));
            }
        }
    }

    private static boolean within(Player a, EntityView b, double range) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz <= range * range;
    }

    private void broadcastMove(Player a, EntityView b, double[] last) {
        double ddx = b.x() - last[0], ddy = b.y() - last[1], ddz = b.z() - last[2];
        boolean posChanged = ddx != 0 || ddy != 0 || ddz != 0;
        boolean rotChanged = b.yaw() != last[3] || b.pitch() != last[4];
        if (!posChanged && !rotChanged) return;
        boolean big = Math.abs(ddx) >= 8 || Math.abs(ddy) >= 8 || Math.abs(ddz) >= 8;
        if (big) {
            a.send(new TeleportEntity(b.entityId(), b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.onGround()));
        } else if (posChanged) {
            short sdx = (short) Math.round(ddx * 4096);
            short sdy = (short) Math.round(ddy * 4096);
            short sdz = (short) Math.round(ddz * 4096);
            a.send(new MoveEntityPosRot(b.entityId(), sdx, sdy, sdz, b.yaw(), b.pitch(), b.onGround()));
        } else {
            a.send(new MoveEntityRot(b.entityId(), b.yaw(), b.pitch(), b.onGround()));
        }
        a.send(new SetHeadRotation(b.entityId(), b.yaw()));
        last[0] = b.x(); last[1] = b.y(); last[2] = b.z(); last[3] = b.yaw(); last[4] = b.pitch();
    }
```

> `broadcastRemove`(핸드오프/퇴장 시 같은 region 디스폰)는 그대로 둔다 — 교차region 디스폰은 스냅샷 부재로 자동 처리되지만, 같은 region 즉시 정리는 유지(멱등).

- [ ] **Step 5: 빌드 + 테스트**

Run: `./gradlew :furnace-server:test --tests "*CrossRegionVisibilityTest" --tests "*RegionTickTest"`
Expected: PASS. (RegionTickTest는 기존대로 통과 — updateVisibility 시그니처 바뀌었지만 tick 경유라 무관.)

- [ ] **Step 6: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): cross-region entity visibility via snapshots" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 수동 검증 — 교차region 가시성 (M3c 최종 verify)

- [ ] **Step 1: 작은 region**(교차 쉽게) — `furnace-server/furnace.properties`:
```
view-distance=8
region-size=4
region-threads=4
```

- [ ] **Step 2: 서버 기동 + 2클라**

Run: `./gradlew :furnace-server:run`
두 클라 접속.

- [ ] **Step 3: 다른 region·view거리 내 확인**

두 플레이어를 **다른 region(>64블록 떨어져 다른 셀)이지만 view거리(8청크=128블록) 안**에 위치 → **서로 보여야**(M3b선 안 보였음).

- [ ] **Step 4: 경계 왕복**

한 명이 region 경계를 왔다갔다 → 상대 화면서 **깜빡임 없이** 계속 보임(M3b 깜빡임 해소 확인).

- [ ] **Step 5: 멀어지기/퇴장**

view거리 밖으로 멀어지면 despawn. 퇴장 시 사라짐.

- [ ] **문제 해결 가이드**
  - 다른 region 플레이어 안 보임 → `neighborRadius` 부족 또는 스냅샷 미공개. World.neighborRadius/candidatesAround, Region.tick 스냅샷 공개 확인.
  - 1틱 잔상/유령 → stale 스냅샷. 다음 틱 정리되면 정상.

- [ ] **Step 6: 완료 커밋(수정 있었다면)**

```bash
git add furnace-server
git commit -m "fix(world): M3c cross-region visibility from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모

- **spec 커버리지:** §5.1 EntityView=T1, §5.2 World 레지스트리=T2, §5.3·§5.4 Region 공개+가시성=T3, §7 verify=T4. 전 항목 존재.
- **타입 일관성:** `EntityView.of(Player)`+게터(T1; Region/World 사용) · `World.publishSnapshot`/`candidatesAround`/`neighborRadius`(T2; Region 사용) · `updateVisibility(Player, List<EntityView>)`/`within(Player, EntityView)`/`broadcastMove(Player, EntityView, double[])`(T3 정의; tick 호출).
- **위험:** stale 잔상(멱등 정리), 반경 부족(neighborRadius 테스트), 빈 region 스냅샷 공개로 떠난 플레이어 자연 despawn(빈 region도 틱하며 빈 리스트 공개).
- **M3b 회귀:** updateVisibility 시그니처 변경 — tick 내부 호출만이라 외부 영향 없음. broadcastRemove 유지.
