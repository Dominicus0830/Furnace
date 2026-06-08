# 스폰 청크 배치 게이트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Region.streamLoads`가 보내는 청크들을 `ChunkBatchStart`/`ChunkBatchFinished`로 감싸, 클라이언트가 스폰 배치 도착까지 물리를 정지해 접속/재접속 시 1칸 낙하를 없앤다.

**Architecture:** 각 로드 셋 시작에 `ChunkBatchStart` 전송 → 청크들 비동기 로드·직렬화·전송(현행 유지) → 모든 전송 future를 `allOf`로 모아 완료 후 `ChunkBatchFinished(count)` 전송. per-청크 `exceptionally`로 실패를 흡수해 배치가 항상 닫히게(클라 무한 로딩 방지) 한다.

**Tech Stack:** Java 21, Netty, JUnit 5, CompletableFuture. M4b(비동기 청크 로드) 위. 브랜치 `spawn-batch-gate`.

**spec:** `docs/superpowers/specs/2026-06-08-spawn-chunk-batch-gate-design.md`

**상수(검증용 패킷 ID):** ChunkBatchStart=0x0D, ChunkBatchFinished=0x0C, LevelChunkWithLight=0x27, SetCenterChunk=0x54.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `world/Region.java` | (modify) `streamLoads` — 로드 셋을 배치 마커로 감싸고 완료 후 Finished 전송 |
| `world/SpawnBatchTest.java` | (create) EmbeddedChannel로 배치 마커 순서·개수 검증 |

---

## Task 1: streamLoads 배치 게이트 + 테스트

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java` (`streamLoads`, 현재 ~211행; import 추가)
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/SpawnBatchTest.java`

- [ ] **Step 1: 실패 테스트 작성**

`SpawnBatchTest.java`:
```java
// 스폰 청크 전송이 ChunkBatchStart…청크…ChunkBatchFinished(count)로 감싸지는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class SpawnBatchTest {

    private static final int CHUNK_BATCH_START = 0x0D;
    private static final int CHUNK_BATCH_FINISHED = 0x0C;
    private static final int LEVEL_CHUNK = 0x27;

    @Test
    void spawnChunks_wrappedInBatch() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        // viewDistance 1 → 3x3=9 청크, regionSize 16 → 전부 region(0,0) 소유.
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 1, 1, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "A", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();

        // 비동기 청크 파이프라인 완료까지 폴링하며 outbound 패킷 id 수집.
        List<Integer> ids = new ArrayList<>();
        int finishedSize = -1;
        for (int i = 0; i < 300 && finishedSize < 0; i++) {
            ch.runPendingTasks();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                ByteBuf b = (ByteBuf) o;
                int id = Types.readVarInt(b);
                ids.add(id);
                if (id == CHUNK_BATCH_FINISHED) finishedSize = Types.readVarInt(b);
                b.release();
            }
            if (finishedSize < 0) Thread.sleep(10);
        }

        int start = ids.indexOf(CHUNK_BATCH_START);
        int finished = ids.lastIndexOf(CHUNK_BATCH_FINISHED);
        long chunkCount = ids.stream().filter(id -> id == LEVEL_CHUNK).count();
        int firstChunk = ids.indexOf(LEVEL_CHUNK);
        int lastChunk = ids.lastIndexOf(LEVEL_CHUNK);

        assertTrue(start >= 0, "ChunkBatchStart 전송돼야");
        assertTrue(finished >= 0, "ChunkBatchFinished 전송돼야");
        assertEquals(9, chunkCount, "9개 청크 전송");
        assertEquals(9, finishedSize, "Finished count == 청크 수");
        assertTrue(start < firstChunk, "Start가 첫 청크 앞");
        assertTrue(lastChunk < finished, "Finished가 마지막 청크 뒤");

        io.shutdown();
        sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*SpawnBatchTest"`
Expected: FAIL — ChunkBatchStart/Finished 미전송이라 `start >= 0` 단언 실패(현 streamLoads는 배치 마커 안 보냄).

- [ ] **Step 3: Region import 추가**

`Region.java` import 블록(다른 `net.protocol.play.*` import 근처)에 추가:
```java
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
```
(`java.util.concurrent.CompletableFuture`는 M4b서 이미 import됨. `java.util.ArrayList`·`java.util.List`도 이미 있음.)

- [ ] **Step 4: streamLoads 교체**

현재 `streamLoads`(약 211행):
```java
    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.subscribeChunk(cp, p);
            // 청크 소유 region에서 스냅샷을 받아(단일 소유) pool서 직렬화 후 전송.
            world.snapshotChunk(cp).thenCompose(snap -> world.chunkPool().submit(() -> {
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
                return body;
            })).thenAccept(body -> p.send(new LevelChunkWithLight(body)));
        }
    }
```
전체를 아래로 교체:
```java
    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        if (toLoad.isEmpty()) return;
        // 배치로 감싸 클라가 첫 배치 도착까지 물리 정지(스폰 낙하 방지).
        p.send(new ChunkBatchStart());
        List<CompletableFuture<?>> sends = new ArrayList<>(toLoad.size());
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.subscribeChunk(cp, p);
            // 청크 소유 region에서 스냅샷을 받아(단일 소유) pool서 직렬화 후 전송.
            CompletableFuture<?> f = world.snapshotChunk(cp).thenCompose(snap -> world.chunkPool().submit(() -> {
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
                return body;
            })).thenAccept(body -> p.send(new LevelChunkWithLight(body)))
                    .exceptionally(ex -> {           // 실패해도 배치 닫아 클라 무한 로딩 방지
                        org.slf4j.LoggerFactory.getLogger(Region.class)
                                .warn("[CHUNK] 전송 실패 {}", cp, ex);
                        return null;
                    });
            sends.add(f);
        }
        CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]))
                .thenRun(() -> p.send(new ChunkBatchFinished(toLoad.size())));
    }
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*SpawnBatchTest"`
Expected: PASS.

- [ ] **Step 6: 전체 빌드(회귀 확인)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (기존 청크 전송 경로 변경 — ChunkSubscriberTest 등 영향 없어야.)

- [ ] **Step 7: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/Region.java furnace-server/src/test/java/org/domi/furnace/server/world/SpawnBatchTest.java
git commit -m "fix(net): wrap streamed chunks in batch markers to gate spawn physics" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 수동 검증

- [ ] **Step 1:** `./gradlew :furnace-server:run` → 클라 접속(superflat). 블록 몇 개 파괴, 이동, 접속 종료.
- [ ] **Step 2:** 재접속 → **1칸 낙하 없이** 마지막 위치/방향에 안착(이전엔 재접속 시 떨어졌음). ✅ 통과.
- [ ] **Step 3(선택):** 바닐라 월드 로드(디코드 느린 경우)로도 낙하 없는지 — T12-B 환경 재사용.

> 낙하 지속 시: 클라가 배치를 못 받거나 START_WAITING_FOR_CHUNKS 누락. ConnectionHandler onEnterPlay의 GameEvent 전송 + outbound에 ChunkBatchStart/Finished 실제로 가는지 로그 확인.

- [ ] **Step 4: 수정 있었으면 커밋**

```bash
git add furnace-server
git commit -m "fix(net): spawn batch gate adjustments from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모

- **spec 커버리지:** streamLoads 배치 래핑=T1 Step4, 순서보장(Start 먼저/Finished allOf 후)=구현+T1 테스트 단언, 행 방지(exceptionally)=T1 Step4, 빈 배치 스킵=T1 Step4, 수동 낙하 검증=T2. 전 항목 존재.
- **타입 일관성:** `ChunkBatchStart()`/`ChunkBatchFinished(int)`(기존 record), `CompletableFuture.allOf`/`exceptionally`, `Player.send`/`loadedChunks`/`World.snapshotChunk`/`chunkPool`/`subscribeChunk`(전부 기존). 신규 심볼 없음.
- **위험:** 테스트 폴링(최대 3s)이 느린 CI서 빠듯할 수 있음 — 9청크 단일스레드라 충분. allOf가 chunkPool/ioPool/scheduler 스레드 혼합서 완료 — thenRun이 임의 스레드서 p.send(스레드안전 마샬) 호출, 문제없음.
- **회귀:** 이동 시 로드 셋도 배치로 감싸짐(vanilla 동일). UnloadChunk는 배치 밖(현행 유지). 빈 toLoad 시 SetCenterChunk만 가고 배치 마커 없음(정상).
