# M3b — region 분할 + 병렬 틱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 월드를 고정 그리드 region으로 나눠 N개 region 스레드 풀에서 병렬 틱하고, 플레이어가 region 경계를 넘으면 소유권을 이양하며, 잘못된 스레드의 region 접근은 assert로 터진다(S2→S3).

**Architecture:** `RegionGrid`가 블록→RegionPos, RegionPos→스레드 인덱스를 결정적으로 매핑. `FurnaceSchedulerImpl`이 N개 `RegionThread`를 들고 `region(pos)`를 해당 스레드로 라우팅. M3a의 단일 `World` 틱 로직을 `Region`(그리드 셀)으로 옮기고, `World`는 스레드별 region 컨테이너 + 공유 자원이 된다. 이동은 락프리 volatile 위치 발행, 퇴장은 volatile 플래그로 소유 region이 정리, 핸드오프는 `region(newPos).execute(adopt)`.

**Tech Stack:** Java 21, Netty 4.1.115, JUnit 5. M0 스케줄러(`RegionThread`/`ThreadOwner`), M3a(`World`/`Player`/`ChunkWorkerPool`).

**M3a와의 연결:** M3a는 단일 `World`가 모든 플레이어를 소유하고 `setRegionTick(world::tick)`로 1개 region 스레드서 틱했다. M3b는 `World.tick`의 per-player 로직(updateChunks/updateVisibility/broadcastMove)을 `Region`으로 옮기고, N개 스레드가 각자 맡은 Region들을 병렬 틱한다.

**핵심 설계 결정(spec 출처):** 고정 그리드 · region은 플레이어만 소유(청크 순수생성) · 락프리 위치 발행 · 단일엔티티 핸드오프 · 교차 가시성은 M3c. spec §3.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `world/RegionGrid.java` | 블록→RegionPos, RegionPos→스레드 인덱스 결정적 매핑(정적) |
| `entity/Player.java` | (modify) volatile 위치 발행/드레인, disconnected 플래그, regionPos |
| `sched/FurnaceSchedulerImpl.java` | (modify) N region 스레드, region(pos) 라우팅, setRegionTick(IntConsumer) |
| `world/Region.java` | 그리드 셀: 플레이어 소유 + tick(드레인/정리/핸드오프/청크/가시성) |
| `world/World.java` | (refactor) 스레드별 region 컨테이너 + 공유 자원 + tickThread |
| `config/Config.java`, `ConfigLoader.java` | (modify) region-size, region-threads |
| `net/ConnectionHandler.java` | (modify) 이동→publishPosition, 진입 adopt, 인액티브 markDisconnected |
| `FurnaceMain.java` | (modify) N스레드·World 배선 |

---

## Task 1: RegionGrid — 결정적 매핑

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/RegionGrid.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/RegionGridTest.java`

블록좌표 → 청크(floor/16) → region(floorDiv/regionSize). 스레드 인덱스 = floorMod(pos.x*31 + pos.z, n).

- [ ] **Step 1: 실패하는 테스트 작성**

`RegionGridTest.java`:
```java
// 블록→RegionPos, RegionPos→스레드 인덱스 매핑 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.api.sched.RegionPos;
import org.junit.jupiter.api.Test;

class RegionGridTest {

    @Test
    void blockToRegion_flooring() {
        // regionSize=16청크=256블록. (0,0)→region(0,0), (-1,-1)→region(-1,-1)
        assertEquals(new RegionPos(0, 0), RegionGrid.regionPos(0, 0, 16));
        assertEquals(new RegionPos(0, 0), RegionGrid.regionPos(255, 255, 16));
        assertEquals(new RegionPos(-1, -1), RegionGrid.regionPos(-1, -1, 16));
        assertEquals(new RegionPos(1, 0), RegionGrid.regionPos(256, 0, 16));
    }

    @Test
    void threadIndex_deterministic_andInRange() {
        RegionPos p = new RegionPos(3, -5);
        int a = RegionGrid.threadIndex(p, 8);
        int b = RegionGrid.threadIndex(new RegionPos(3, -5), 8);
        assertEquals(a, b);                 // 결정적
        assertTrue(a >= 0 && a < 8);        // 범위 내
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionGridTest"`
Expected: 컴파일 실패 — `RegionGrid` 없음.

- [ ] **Step 3: `RegionGrid.java` 작성**

```java
// 블록좌표→RegionPos, RegionPos→스레드 인덱스 결정적 매핑. World와 스케줄러가 공유.
package org.domi.furnace.server.world;

import org.domi.furnace.api.sched.RegionPos;

public final class RegionGrid {

    private RegionGrid() {}

    /** 블록 절대좌표 → region 좌표(청크 단위 regionSize 그리드). */
    public static RegionPos regionPos(double blockX, double blockZ, int regionSize) {
        int chunkX = Math.floorDiv((int) Math.floor(blockX), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(blockZ), 16);
        return new RegionPos(Math.floorDiv(chunkX, regionSize), Math.floorDiv(chunkZ, regionSize));
    }

    /** RegionPos → 스레드 인덱스 [0, n). 결정적. */
    public static int threadIndex(RegionPos pos, int n) {
        return Math.floorMod(pos.x() * 31 + pos.z(), n);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionGridTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add region grid mapping" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: FurnaceSchedulerImpl — N region 스레드

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/sched/FurnaceSchedulerImpl.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/sched/SchedulerTickHookTest.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/RegionRoutingTest.java`

N개 `RegionThread`. `region(pos)` = `regions[RegionGrid.threadIndex(pos, N)]`. `setRegionTick`은 이제 `IntConsumer`(스레드 인덱스 전달). `FurnaceSchedulerImpl(tickNanos)`는 N=1로 백호환.

- [ ] **Step 1: 라우팅 테스트 작성**

`RegionRoutingTest.java`:
```java
// region(pos)가 결정적으로 스레드에 매핑되고, setRegionTick이 인덱스별로 도는지 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;
import org.junit.jupiter.api.Test;

class RegionRoutingTest {

    @Test
    void region_mapsDeterministically() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        TaskScheduler a = sched.region(new RegionPos(1, 2));
        TaskScheduler b = sched.region(new RegionPos(1, 2));
        assertSame(a, b);  // 같은 pos → 같은 스레드
        sched.close();
    }

    @Test
    void setRegionTick_passesThreadIndex() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        sched.setRegionTick(seen::add);
        Thread.sleep(120);
        sched.close();
        assertEquals(Set.of(0, 1, 2), seen); // 3개 인덱스 모두 틱
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionRoutingTest"`
Expected: 컴파일 실패 — 2-인자 생성자/`IntConsumer` setRegionTick 없음.

- [ ] **Step 3: `FurnaceSchedulerImpl.java` 교체**

```java
// FurnaceScheduler 파사드 구현. M3b: 전역 owner 1개 + region owner N개(고정 그리드).
package org.domi.furnace.server.sched;

import java.util.function.IntConsumer;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.server.world.RegionGrid;

public final class FurnaceSchedulerImpl implements FurnaceScheduler, AutoCloseable {

    private final GlobalThread global;
    private final RegionThread[] regions;
    private volatile IntConsumer regionTick = i -> {};

    /** 백호환: region 스레드 1개. */
    public FurnaceSchedulerImpl(long tickNanos) {
        this(tickNanos, 1);
    }

    public FurnaceSchedulerImpl(long tickNanos, int regionThreads) {
        this.global = new GlobalThread("furnace-global");
        this.regions = new RegionThread[regionThreads];
        for (int i = 0; i < regionThreads; i++) {
            int idx = i;
            this.regions[i] = new RegionThread("furnace-region-" + i, tickNanos,
                    () -> regionTick.accept(idx));
        }
    }

    @Override
    public GlobalScheduler global() {
        return global;
    }

    @Override
    public TaskScheduler region(RegionPos pos) {
        return regions[RegionGrid.threadIndex(pos, regions.length)];
    }

    /** region 틱 콜백(스레드 인덱스 전달). World.tickThread 연결용. */
    public void setRegionTick(IntConsumer tick) {
        this.regionTick = tick;
    }

    public int regionThreadCount() {
        return regions.length;
    }

    @Override
    public void close() {
        for (RegionThread r : regions) r.close();
        global.close();
    }
}
```

> `regionThread()`(M0 테스트용 단일 접근자)를 쓰는 곳이 있으면 `regions[0]` 반환 메서드로 유지한다. 현재 사용처 확인: `FurnaceMain`의 TpsHeartbeat가 `scheduler.regionThread().measuredTps()`를 쓴다 → 아래 메서드 추가:
```java
    /** M0/테스트용 — 첫 region 스레드 접근. */
    public RegionThread regionThread() {
        return regions[0];
    }
```

- [ ] **Step 4: `SchedulerTickHookTest` 갱신** (Runnable → IntConsumer)

기존 `sched.setRegionTick(ticks::incrementAndGet);`를 교체:
```java
        sched.setRegionTick(i -> ticks.incrementAndGet());
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionRoutingTest" --tests "*SchedulerTickHookTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(sched): N region threads with deterministic pos routing" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Player — 락프리 위치 발행 + 퇴장 플래그

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/entity/Player.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/entity/PlayerPublishTest.java`

IO 스레드가 `publishPosition`으로 불변 스냅샷을 volatile에 발행, region 스레드가 `drainPending`으로 적용. `disconnected` volatile 플래그.

- [ ] **Step 1: 테스트 작성**

`PlayerPublishTest.java`:
```java
// 위치 발행/드레인 라운드트립 + regionPos + 퇴장 플래그 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.api.sched.RegionPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class PlayerPublishTest {

    @Test
    void publishThenDrain_appliesPosition() {
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        p.publishPosition(100, -60, 200, 90f, 10f, true);
        assertEquals(0, p.x()); // 드레인 전엔 미적용
        p.drainPending();
        assertEquals(100, p.x());
        assertEquals(90f, p.yaw());
        assertEquals(new RegionPos(0, 0), p.regionPos(16)); // chunk(6,12) → region(0,0)
    }

    @Test
    void disconnect_flag() {
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        assertFalse(p.isDisconnected());
        p.markDisconnected();
        assertTrue(p.isDisconnected());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayerPublishTest"`
Expected: 컴파일 실패 — 메서드 없음.

- [ ] **Step 3: `Player.java`에 추가** (기존 필드/메서드 유지, 아래 추가)

`Player` 클래스 안, 기존 필드 아래에 추가:
```java
    private volatile double[] pending;          // {x,y,z,yaw,pitch,onGround} IO→region 발행
    private volatile boolean disconnected;
    private volatile RegionPos currentRegion;   // 현재 소유 region(adopt가 설정)
```
import 추가:
```java
import org.domi.furnace.api.sched.RegionPos;
```
메서드 추가:
```java
    /** IO 스레드: 들어온 위치를 발행(단일 writer). */
    public void publishPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.pending = new double[]{x, y, z, yaw, pitch, onGround ? 1 : 0};
    }

    /** region 스레드: 발행된 위치를 권위 필드에 적용(단일 reader). 변경 있으면 true. */
    public boolean drainPending() {
        double[] p = this.pending;
        if (p == null) return false;
        this.pending = null;
        applyMove(p[0], p[1], p[2], (float) p[3], (float) p[4], p[5] != 0);
        return true;
    }

    public RegionPos regionPos(int regionSize) {
        return org.domi.furnace.server.world.RegionGrid.regionPos(x(), z(), regionSize);
    }

    public void markDisconnected() { this.disconnected = true; }
    public boolean isDisconnected() { return disconnected; }
    public RegionPos currentRegion() { return currentRegion; }
    public void currentRegion(RegionPos r) { this.currentRegion = r; }
```

> `applyMove`는 M3a에 이미 있다(x,y,z,yaw,pitch,onGround). 재사용.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayerPublishTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(entity): lock-free position publish and disconnect flag" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Region — 그리드 셀 + 틱 로직

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- (Task 5에서 World 리팩터 후 통합 테스트)

M3a `World`의 per-player 로직(updateChunks/streamLoads/updateVisibility/broadcastMove/within)을 `Region`으로 옮긴다. `tick()`은 드레인→퇴장정리→핸드오프수집→청크→가시성. `Region`은 공유 자원을 `World`에서 읽는다.

- [ ] **Step 1: `Region.java` 작성** (World 접근자는 Task 5서 추가 — 본 Task는 Region 본문, Task 5서 World와 함께 컴파일)

```java
// region 그리드 셀: 자기 플레이어를 소유하고 매 틱 청크/가시성/핸드오프를 처리한다.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.MoveEntityRot;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;

public final class Region {

    private final RegionPos pos;
    private final World world;
    private final Map<Integer, Player> players = new HashMap<>();

    public Region(RegionPos pos, World world) {
        this.pos = pos;
        this.world = world;
    }

    public RegionPos pos() { return pos; }
    public Map<Integer, Player> players() { return players; }

    /** 소유 스레드서 플레이어 편입. */
    public void adopt(Player p) {
        world.assertOwns(pos);
        p.currentRegion(pos);
        players.put(p.entityId(), p);
    }

    /** 소유 스레드서 매 틱 호출. */
    public void tick() {
        world.assertOwns(pos);
        List<Player> handoff = new ArrayList<>();
        var it = players.values().iterator();
        while (it.hasNext()) {
            Player p = it.next();
            if (p.isDisconnected()) {
                it.remove();
                broadcastRemove(p.entityId());
                continue;
            }
            p.drainPending();
            RegionPos now = p.regionPos(world.regionSize());
            if (!now.equals(pos)) {     // 경계 넘음 → 핸드오프
                it.remove();
                broadcastRemove(p.entityId());
                handoff.add(p);
                continue;
            }
            updateChunks(p);
        }
        for (Player p : players.values()) {
            updateVisibility(p);
        }
        for (Player p : handoff) {
            RegionPos target = p.regionPos(world.regionSize());
            world.scheduler().region(target).execute(() -> world.regionAt(target).adopt(p));
        }
    }

    /** 이 region의 타 플레이어들이 eid를 추적 중이면 디스폰. */
    private void broadcastRemove(int eid) {
        for (Player other : players.values()) {
            if (other.tracked().remove(eid) != null) {
                other.send(new RemoveEntities(java.util.List.of(eid)));
            }
        }
    }

    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        if (center.equals(p.lastCenter())) return;
        int vd = world.viewDistance();
        List<ChunkPos> toLoad = new ArrayList<>();
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                ChunkPos cp = new ChunkPos(center.x() + dx, center.z() + dz);
                if (!p.loadedChunks().contains(cp)) toLoad.add(cp);
            }
        }
        List<ChunkPos> toUnload = new ArrayList<>();
        for (ChunkPos cp : p.loadedChunks()) {
            if (Math.abs(cp.x() - center.x()) > vd || Math.abs(cp.z() - center.z()) > vd) {
                toUnload.add(cp);
            }
        }
        p.send(new SetCenterChunk(center.x(), center.z()));
        streamLoads(p, toLoad);
        for (ChunkPos cp : toUnload) {
            p.send(new UnloadChunk(cp.x(), cp.z()));
            p.loadedChunks().remove(cp);
        }
        p.lastCenter(center);
    }

    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.chunkPool().submit(() -> {
                ChunkColumn col = world.worldSource().generate(cp.x(), cp.z());
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, cp.x(), cp.z(), col,
                        world.blockDirectBits(), world.biomeDirectBits());
                return body;
            }).thenAccept(body -> p.send(new LevelChunkWithLight(body)));
        }
    }

    private void updateVisibility(Player a) {
        double range = world.viewDistance() * 16.0;
        for (Player b : players.values()) {
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

    private static boolean within(Player a, Player b, double range) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz <= range * range;
    }

    private void broadcastMove(Player a, Player b, double[] last) {
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
}
```

> 이 파일은 Task 5의 World 리팩터(접근자 `assertOwns`/`scheduler`/`regionAt`/`regionSize`/`viewDistance`/`worldSource`/`chunkPool`/`blockDirectBits`/`biomeDirectBits`)가 있어야 컴파일된다. Task 4·5는 한 커밋으로 묶는다(아래 Task 5 Step에서 함께 빌드·커밋).

- [ ] **Step 2: (빌드는 Task 5와 함께)** — Region 단독으론 컴파일 불가. Task 5로 진행.

---

## Task 5: World 리팩터 — 스레드별 region 컨테이너

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java` (전면 교체)
- Delete(이전): `furnace-server/src/test/java/org/domi/furnace/server/world/WorldChunkTest.java`, `WorldVisibilityTest.java` (로직이 Region으로 이동 — Region 통합 테스트로 대체)
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/RegionTickTest.java`

`World`는 이제 스레드별 region 맵(`Map<RegionPos,Region>[]`) + 공유 자원 + 스레드별 틱 디스패치. 각 스레드의 맵은 그 스레드만 접근(스레드 확정, 락 없음).

- [ ] **Step 1: `World.java` 전면 교체**

```java
// region 컨테이너 + 공유 자원. 스레드별 region 맵을 들고 매 틱 자기 region들을 틱한다.
package org.domi.furnace.server.world;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.gen.WorldSource;

public final class World {

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final FurnaceScheduler scheduler;
    private final int regionThreads;
    private final int regionSize;
    private final int viewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;

    // 인덱스 i 맵은 region 스레드 i만 접근(스레드 확정). 동기화 불필요.
    private final Map<RegionPos, Region>[] regionsByThread;

    @SuppressWarnings("unchecked")
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance,
                 int blockDirectBits, int biomeDirectBits) {
        this.worldSource = worldSource;
        this.chunkPool = chunkPool;
        this.scheduler = scheduler;
        this.regionThreads = regionThreads;
        this.regionSize = regionSize;
        this.viewDistance = viewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
        this.regionsByThread = new Map[regionThreads];
        for (int i = 0; i < regionThreads; i++) regionsByThread[i] = new HashMap<>();
    }

    /** region 스레드 i가 매 틱 호출 — 자기 region들 틱. */
    public void tickThread(int index) {
        for (Region r : regionsByThread[index].values()) {
            try {
                r.tick();
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger(World.class)
                        .error("[TICK] region {} 실패", r.pos(), t);
            }
        }
    }

    /** pos의 region을 그 소유 스레드서 get/create. */
    public Region regionAt(RegionPos pos) {
        int idx = RegionGrid.threadIndex(pos, regionThreads);
        return regionsByThread[idx].computeIfAbsent(pos, p -> new Region(p, this));
    }

    /** 디버그 소유권 가드: pos를 소유한 스레드서 호출 중인지 확인. */
    public void assertOwns(RegionPos pos) {
        assert scheduler.region(pos).isOwnerThread()
                : "region " + pos + " 를 비소유 스레드서 접근: " + Thread.currentThread().getName();
    }

    public FurnaceScheduler scheduler() { return scheduler; }
    public WorldSource worldSource() { return worldSource; }
    public ChunkWorkerPool chunkPool() { return chunkPool; }
    public int regionSize() { return regionSize; }
    public int viewDistance() { return viewDistance; }
    public int blockDirectBits() { return blockDirectBits; }
    public int biomeDirectBits() { return biomeDirectBits; }
}
```

> `TaskScheduler`(`region(pos)` 반환형)는 `isOwnerThread()`를 가진 `ThreadOwner`도 구현하는가? `RegionThread`는 `ThreadOwner`를 구현한다. 하지만 `region(pos)` 반환형은 `TaskScheduler`다. `assertOwns`에서 `isOwnerThread`를 쓰려면 캐스팅 필요: `((org.domi.furnace.api.concurrent.ThreadOwner) scheduler.region(pos)).isOwnerThread()`. `RegionThread`가 둘 다 구현하므로 안전. 아래로 교체:
```java
    public void assertOwns(RegionPos pos) {
        org.domi.furnace.api.concurrent.ThreadOwner owner =
                (org.domi.furnace.api.concurrent.ThreadOwner) scheduler.region(pos);
        assert owner.isOwnerThread()
                : "region " + pos + " 를 비소유 스레드서 접근: " + Thread.currentThread().getName();
    }
```

- [ ] **Step 2: M3a World 테스트 삭제**

```bash
git rm furnace-server/src/test/java/org/domi/furnace/server/world/WorldChunkTest.java
git rm furnace-server/src/test/java/org/domi/furnace/server/world/WorldVisibilityTest.java
```
> 이유: 테스트하던 per-player 청크/가시성 로직이 `Region`으로 이동했다. 아래 `RegionTickTest`가 region 스레드 경유로 대체 검증한다.

- [ ] **Step 3: `RegionTickTest.java` 작성** (region 스레드 경유로 adopt+tick → 청크 로드 검증, 소유권 가드 검증)

```java
// region 스레드서 플레이어 편입·틱 시 청크 로드, 비소유 스레드 접근 시 assert 검증
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

class RegionTickTest {

    private static World world(FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                sched, sched.regionThreadCount(), 16, 1, reg.directBits(), 7);
    }

    @Test
    void adoptAndTick_onRegionThread_loadsChunks() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched);
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        p.publishPosition(0, -60, 0, 0, 0, true);
        RegionPos rp = p.regionPos(16);
        // region 스레드서 adopt + tick
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(9, p.loadedChunks().size()); // viewDistance 1 → 3x3
        sched.close();
    }

    @Test
    void mutatingRegion_fromWrongThread_assertFails() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched);
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        // 메인 스레드서 직접 adopt → 비소유 → AssertionError(-ea)
        assertThrows(Throwable.class, () -> w.regionAt(rp).adopt(p));
        sched.close();
    }
}
```

> `regionAt`은 비소유 스레드서 호출해도 맵 생성은 일어난다(computeIfAbsent). 두 번째 테스트는 `adopt` 내부의 `assertOwns`가 터지는 걸 본다. 단, `regionAt` 자체도 비소유 스레드서 호출 시 다른 스레드의 맵을 건드린다 — 테스트 목적상 허용(assert가 adopt서 먼저 터짐). 실제 코드 경로는 항상 `region(pos).execute`로 진입하므로 안전.

- [ ] **Step 4: Region·World·테스트 함께 빌드**

Run: `./gradlew :furnace-server:compileJava :furnace-server:compileTestJava`
Expected: 컴파일 성공(Task 4 Region + Task 5 World 함께).

- [ ] **Step 5: 테스트 실행**

Run: `./gradlew :furnace-server:test --tests "*RegionTickTest"`
Expected: PASS (2 tests). (assert 테스트는 `-ea`로 동작 — gradle test는 기본 활성.)

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): split per-player tick into Region; World becomes thread-keyed container" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: ConnectionHandler — 위치 발행·진입·퇴장

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`

이동 패킷 → `player.publishPosition`(region 라우팅 제거). onEnterPlay → 스폰 region에 adopt. channelInactive → `markDisconnected`.

- [ ] **Step 1: `onEnterPlay` 교체** (M3a 버전 → region adopt)

기존 `onEnterPlay` 본문에서 플레이어 등록 부분을 교체. 전체 메서드:
```java
    private void onEnterPlay(ChannelHandlerContext ctx) {
        int eid = idAllocator.next();
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex, world.viewDistance()));
        send(ctx, new SyncPlayerPosition(0, -60, 0, 1));
        send(ctx, new SetDefaultSpawnPosition(0, -60, 0));
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));

        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        p.publishPosition(0, -60, 0, 0f, 0f, true);
        this.player = p;
        RegionPos spawn = RegionGrid.regionPos(0, 0, world.regionSize());
        scheduler.region(spawn).execute(() -> world.regionAt(spawn).adopt(p));
        scheduleKeepAlive();
    }
```
import 추가:
```java
import org.domi.furnace.server.world.RegionGrid;
```
(`RegionPos`, `Player`, `World`는 M3a서 이미 import. `world.addPlayer`는 더 이상 없음.)

- [ ] **Step 2: `handlePlay` 이동 케이스 교체** (region().execute → publishPosition)

`handlePlay`의 0x1A/0x1B/0x1C 케이스와 `marshalMove`를 교체:
```java
            case 0x1A -> { // move_player_pos
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null) p.publishPosition(x, y, z, p.yaw(), p.pitch(), g);
            }
            case 0x1B -> { // move_player_pos_rot
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null) p.publishPosition(x, y, z, yaw, pitch, g);
            }
            case 0x1C -> { // move_player_rot
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null) p.publishPosition(p.x(), p.y(), p.z(), yaw, pitch, g);
            }
```
> `marshalMove` 메서드는 삭제. `0x1C`에서 `p.x()/y()/z()`를 읽는 건 IO 스레드지만 권위 위치는 region 스레드가 씀 → 살짝 stale 가능하나 회전 패킷의 위치는 직전값 근사로 충분(다음 pos 패킷이 보정). 정확성 무관.

- [ ] **Step 3: `channelInactive` 교체**

```java
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Player p = player;
        if (p != null) p.markDisconnected(); // 소유 region이 다음 틱에 정리(라우팅 불필요)
    }
```

- [ ] **Step 4: 빌드**

Run: `./gradlew :furnace-server:compileJava`
Expected: 성공.

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): publish movement lock-free; adopt to spawn region; mark disconnect" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Config + FurnaceMain 배선 + 기존 테스트 갱신

**Files:**
- Modify: `config/Config.java`, `ConfigLoader.java`, `ConfigLoaderTest.java`
- Modify: `FurnaceMain.java`
- Modify: `net/NetworkServerTest.java`, `net/StatusPingTest.java`

- [ ] **Step 1: `Config`에 regionThreads 추가** (regionSize는 World 생성 시 config서; viewDistance 기존)

`Config.java` 교체:
```java
// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance, int regionSize, int regionThreads) {

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5, 16,
                    Runtime.getRuntime().availableProcessors());
}
```

- [ ] **Step 2: `ConfigLoader`에 로드 추가**

`return new Config(...)` 직전 교체:
```java
        int viewDistance = Integer.parseInt(
                props.getProperty("view-distance",
                        Integer.toString(Config.DEFAULT.viewDistance())));
        int regionSize = Integer.parseInt(
                props.getProperty("region-size",
                        Integer.toString(Config.DEFAULT.regionSize())));
        int regionThreads = Integer.parseInt(
                props.getProperty("region-threads",
                        Integer.toString(Config.DEFAULT.regionThreads())));
        return new Config(name, tick, port, compression, viewDistance, regionSize, regionThreads);
```

- [ ] **Step 3: `ConfigLoaderTest` 갱신**

기본값 테스트에 추가:
```java
        assertEquals(16, c.regionSize());
        assertTrue(c.regionThreads() >= 1);
```
오버라이드 테스트의 `Files.writeString`에 `region-size=8\nregion-threads=2\n` 추가하고 단언:
```java
        assertEquals(8, c.regionSize());
        assertEquals(2, c.regionThreads());
```
(오버라이드 문자열 끝에 `region-size=8\nregion-threads=2\n` 이어붙임.)

- [ ] **Step 4: `FurnaceMain` 배선 교체**

scheduler 생성과 World 생성 교체:
```java
        FurnaceSchedulerImpl scheduler = new FurnaceSchedulerImpl(config.tickNanos(), config.regionThreads());
        BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        ChunkWorkerPool chunkPool = new ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
        World world = new World(new SuperflatWorldSource(blockRegistry, plainsBiome), chunkPool,
                scheduler, config.regionThreads(), config.regionSize(), config.viewDistance(),
                blockRegistry.directBits(), biomeDirectBits);
        scheduler.setRegionTick(world::tickThread);
        EntityIdAllocator idAllocator = new EntityIdAllocator();
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName(), scheduler,
                world, idAllocator);
```
> 기존 `scheduler` 생성 한 줄(`new FurnaceSchedulerImpl(config.tickNanos())`)을 위 첫 줄로 교체(중복 생성 주의 — 한 번만).

- [ ] **Step 5: `NetworkServerTest`/`StatusPingTest` World 생성 갱신**

두 테스트의 World 생성을 교체(생성자 시그니처 변경):
```java
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, reg.directBits(), 7);
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched,
                world, new EntityIdAllocator());
```
> `sched`는 두 테스트가 만드는 `FurnaceSchedulerImpl`. `regionThreadCount()`로 일치.

- [ ] **Step 6: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 전체 통과(M0~M3b).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat: wire N region threads via config (region-size, region-threads)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: 수동 검증 — 병렬 region (M3b 최종 verify)

- [ ] **Step 1: region 작게 설정**(병렬 쉽게 보이게) — `furnace-server/furnace.properties`:
```
view-distance=10
region-size=4
region-threads=4
```
> region-size=4청크=64블록이면 조금만 걸어도 region 경계 넘음 → 핸드오프·병렬 관찰 쉬움.

- [ ] **Step 2: 서버 기동**

Run: `./gradlew :furnace-server:run`

- [ ] **Step 3: 클라 1 — 경계 넘나들기**

걸어서 여러 region 경계 통과 → 핸드오프 동작(청크 계속 따라옴, void/멈춤 없음). 서버 로그에 `[TICK] region ... 실패` 없어야.

- [ ] **Step 4: 클라 2 — 멀리 떨어져 접속**

두 번째 클라를 첫 클라서 멀리(다른 region) 스폰/이동. 각자 청크 정상. (같은 region 들어오면 서로 보임, 다른 region이면 안 보임 — M3b 정상.)

- [ ] **Step 5: 병렬 스레드 확인**

두 플레이어가 다른 region일 때 처리가 `furnace-region-0`·`furnace-region-1` 등 **다른 스레드**서 도는지 확인(임시 로그 또는 디버거). 단일 region 스레드(M3a)와 달리 N개 가동.

- [ ] **문제 해결 가이드**
  - 경계서 플레이어 사라짐/중복 → 핸드오프 remove/adopt 순서. `Region.tick`의 handoff 수집·처리 확인.
  - `AssertionError: region ... 비소유 스레드` (런타임) → 어떤 경로가 `region(pos).execute` 없이 Region을 만짐. 스택트레이스로 추적.
  - 경계 왕복 시 깜빡임 → 핸드오프마다 region 바뀌며 재spawn. M3b 한계(교차 가시성 M3c). 같은 region 내면 안정.

- [ ] **Step 6: 완료 커밋(수정 있었다면)**

```bash
git add furnace-server
git commit -m "fix: M3b region handoff issues from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모(작성자 → 실행자)

- **spec 커버리지:** §4 매핑=T1, §5.4 N스레드=T2, §5.5 위치발행=T3, §5.2 Region=T4, §5.3 World/§5.6 핸드오프=T5, §5.5 진입·퇴장=T6, §8 config·배선=T7, §7 verify=T8. 전 항목 태스크 존재.
- **소유권(spec §3·§6):** `World.assertOwns`(T5) — `region(pos)`를 `ThreadOwner`로 캐스팅해 `isOwnerThread`. `RegionThread`가 `ThreadOwner` 구현 확인 필요(M0). T5 Step1 주석에 캐스팅 명시.
- **타입 일관성:** `World(worldSource, chunkPool, scheduler, regionThreads, regionSize, viewDistance, blockDirectBits, biomeDirectBits)`(T5 정의; T7/테스트 사용) · `Region(pos, world)` + `adopt`/`tick`/`pos`/`players`(T4 정의; World/ConnectionHandler 사용) · `Player.publishPosition`/`drainPending`/`regionPos`/`markDisconnected`/`currentRegion`(T3 정의; Region/ConnectionHandler 사용) · `FurnaceSchedulerImpl(tickNanos, n)`/`regionThreadCount`/`setRegionTick(IntConsumer)`(T2 정의; T5·T7 사용) · `RegionGrid.regionPos`/`threadIndex`(T1 정의; 전역 사용).
- **위험:** 핸드오프 중복/유실(T8 verify), 소유권 위반 런타임(assert), 교차 region 가시성 없음(의도 — M3c).
- **M3a 회귀:** World 단일 틱 → Region별 틱 이전. WorldChunkTest/WorldVisibilityTest 삭제 후 RegionTickTest로 대체(T5). ConnectionHandler 이동 마샬 → 위치발행(T6).
