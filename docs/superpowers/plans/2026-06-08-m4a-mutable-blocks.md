# M4a — 가변 블록 (파괴) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 플레이어가 블록을 파괴하면 region이 소유한 청크를 변형하고, 그 청크를 보는 모든(교차region 포함) 플레이어에게 BlockUpdate를 보낸다.

**Architecture:** region이 청크를 소유(`Map<ChunkPos,ChunkColumn>`)·보관·변형. 직렬화는 region 스레드서 뜬 불변 `ChunkSnapshot`을 chunkPool서 처리(레이스 회피, M2 병렬 최적화 유지). 교차region 가시성은 `World`의 청크 구독 레지스트리(`ConcurrentHashMap<ChunkPos,Set<Player>>`)로 — 블록 변경을 구독자에게만 전송.

**Tech Stack:** Java 21, JUnit 5, Netty. M3d(main) 위. 브랜치 `m4a-blocks`.

**spec:** `docs/superpowers/specs/2026-06-08-m4a-mutable-blocks-design.md`

---

## File Structure

| 파일 | 책임 |
|---|---|
| `net/io/Types.java` | (modify) `writeBlockPos`/`readBlockPos` Position 패킹 |
| `net/protocol/play/BlockUpdate.java` | (create) 단일 블록 변경 clientbound |
| `world/ChunkColumn.java` | (modify) `getBlock` 추가 |
| `world/ChunkSnapshot.java` | (create) 직렬화용 불변 섹션 카피 |
| `net/protocol/play/ChunkSerializer.java` | (modify) `ChunkSnapshot` 입력 |
| `world/World.java` | (modify) 청크 구독 레지스트리 |
| `world/Region.java` | (modify) 청크 소유 + `breakBlock` + streamLoads 스냅샷/구독 |
| `net/ConnectionHandler.java` | (modify) Player Action 파싱 + 블록 region 마샬 |

**패킷 ID:** Player Action(~0x24)·BlockUpdate(~0x09)는 M1 관례대로 server.jar 데이터제너레이터로 확정. 아래 코드 값은 예상치 — T6 실클라 전에 datagen 대조.

---

## Task 1: Types — Position 패킹

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/io/Types.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/TypesBlockPosTest.java`

- [ ] **Step 1: 실패 테스트**

`TypesBlockPosTest.java`:
```java
// Position 패킹/언패킹 라운드트립(음수 좌표 포함) 검증
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class TypesBlockPosTest {

    @Test
    void blockPos_roundTrips() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeBlockPos(buf, 100, -60, -200);
        int[] p = Types.readBlockPos(buf);
        assertArrayEquals(new int[]{100, -60, -200}, p);
        buf.release();
    }

    @Test
    void blockPos_handlesZeroAndMaxY() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeBlockPos(buf, 0, 319, 0);
        int[] p = Types.readBlockPos(buf);
        assertArrayEquals(new int[]{0, 319, 0}, p);
        buf.release();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*TypesBlockPosTest"`
Expected: 컴파일 실패 — `writeBlockPos` 없음.

- [ ] **Step 3: Types에 추가**

`writeAngle` 아래에 추가:
```java
    /** 블록 좌표를 바닐라 Position long으로 패킹. */
    public static void writeBlockPos(ByteBuf buf, int x, int y, int z) {
        buf.writeLong(((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF));
    }

    /** Position long 언패킹 → {x, y, z}. 각 필드 부호 확장. */
    public static int[] readBlockPos(ByteBuf buf) {
        long v = buf.readLong();
        int x = (int) (v >> 38);
        int y = (int) (v << 52 >> 52);
        int z = (int) (v << 26 >> 38);
        return new int[]{x, y, z};
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*TypesBlockPosTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add block Position packing to Types" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: BlockUpdate 패킷

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/BlockUpdate.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/BlockUpdateTest.java`

- [ ] **Step 1: 실패 테스트**

`BlockUpdateTest.java`:
```java
// BlockUpdate가 Position + stateId를 와이어에 쓰는지 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class BlockUpdateTest {

    @Test
    void write_emitsPosAndState() {
        BlockUpdate p = new BlockUpdate(10, -60, 20, 0);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        int[] pos = Types.readBlockPos(buf);
        assertArrayEquals(new int[]{10, -60, 20}, pos);
        assertEquals(0, Types.readVarInt(buf)); // air
        buf.release();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockUpdateTest"`
Expected: 컴파일 실패 — `BlockUpdate` 없음.

- [ ] **Step 3: BlockUpdate.java 작성**

```java
// 단일 블록 변경 알림(clientbound). Position + 블록 state id.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BlockUpdate(int x, int y, int z, int stateId) implements Packet {

    public static final int ID = 0x09; // datagen 확정 대상

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeBlockPos(buf, x, y, z);
        Types.writeVarInt(buf, stateId);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockUpdateTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add BlockUpdate clientbound packet" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: ChunkColumn.getBlock + ChunkSnapshot

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkSnapshot.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkSnapshotTest.java`

- [ ] **Step 1: 실패 테스트**

`ChunkSnapshotTest.java`:
```java
// ChunkSnapshot이 ChunkColumn 섹션을 불변 카피로 뜨는지 + getBlock 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSnapshotTest {

    @Test
    void of_copiesAndIsIndependent() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        int grass = reg.defaultStateId("minecraft:grass_block");
        assertEquals(grass, col.getBlock(0, -61, 0)); // superflat grass
        ChunkSnapshot snap = ChunkSnapshot.of(2, 3, col);
        assertEquals(2, snap.chunkX());
        assertEquals(3, snap.chunkZ());
        // 스냅샷 후 원본 변형해도 스냅샷 불변
        col.setBlock(0, -61, 0, ChunkSection.AIR);
        int sectionIdx = (-61 - ChunkColumn.MIN_Y) >> 4;
        int local = (-61 - ChunkColumn.MIN_Y) & 15;
        assertEquals(grass, snap.sections()[sectionIdx].blocks()[local * 256 + 0 * 16 + 0]);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSnapshotTest"`
Expected: 컴파일 실패 — `getBlock`/`ChunkSnapshot` 없음.

- [ ] **Step 3: ChunkColumn.getBlock 추가**

`setBlock` 아래에 추가:
```java
    /** worldY 절대좌표 블록 조회. */
    public int getBlock(int x, int worldY, int z) {
        int local = worldY - MIN_Y;
        return sections[local >> 4].getBlock(x, local & 15, z);
    }
```

- [ ] **Step 4: ChunkSnapshot.java 작성**

```java
// 직렬화용 불변 청크 카피. region 스레드가 떠서 chunkPool이 락프리로 직렬화한다.
package org.domi.furnace.server.world;

public record ChunkSnapshot(int chunkX, int chunkZ, long[] heightmap, Section[] sections) {

    public record Section(short nonAirCount, int[] blocks, int[] biomes) {}

    /** region 스레드서 호출 — 섹션 블록/바이옴 배열을 카피해 불변 스냅샷 생성. */
    public static ChunkSnapshot of(int chunkX, int chunkZ, ChunkColumn col) {
        ChunkSection[] src = col.sections();
        Section[] out = new Section[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = new Section(src[i].nonAirCount(), src[i].blocks().clone(), src[i].biomes().clone());
        }
        return new ChunkSnapshot(chunkX, chunkZ, col.motionBlockingHeightmap(), out);
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSnapshotTest"`
Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add ChunkColumn.getBlock and ChunkSnapshot" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: World 청크 구독 레지스트리

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkSubscriberTest.java`

- [ ] **Step 1: 실패 테스트**

`ChunkSubscriberTest.java`:
```java
// 청크 구독 등록/해제/조회 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSubscriberTest {

    @Test
    void subscribe_unsubscribe() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7);
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        ChunkPos cp = new ChunkPos(0, 0);
        assertTrue(w.subscribers(cp).isEmpty());
        w.subscribeChunk(cp, p);
        assertEquals(Set.of(p), w.subscribers(cp));
        w.unsubscribeChunk(cp, p);
        assertTrue(w.subscribers(cp).isEmpty());
        sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSubscriberTest"`
Expected: 컴파일 실패 — `subscribeChunk` 없음.

- [ ] **Step 3: World 수정**

import 추가:
```java
import java.util.Set;
import org.domi.furnace.server.entity.Player;
```
(`java.util.concurrent.ConcurrentHashMap`·`org.domi.furnace.server.entity.EntityView`는 이미 있음.)

`snapshots` 필드 아래에 추가:
```java
    // 청크별 구독자(그 청크를 로드한 플레이어). 블록 변경 시 이들에게만 전송.
    private final ConcurrentHashMap<ChunkPos, Set<Player>> chunkSubscribers = new ConcurrentHashMap<>();
```

`neighborRadius` 아래에 메서드 추가:
```java
    /** 플레이어가 청크를 로드 — 블록 변경 수신 등록. */
    public void subscribeChunk(ChunkPos cp, Player p) {
        chunkSubscribers.computeIfAbsent(cp, k -> ConcurrentHashMap.newKeySet()).add(p);
    }

    /** 플레이어가 청크를 언로드 — 등록 해제. */
    public void unsubscribeChunk(ChunkPos cp, Player p) {
        Set<Player> s = chunkSubscribers.get(cp);
        if (s != null) s.remove(p);
    }

    /** 청크 구독자(블록 변경 브로드캐스트 대상). 비었으면 빈 Set. */
    public Set<Player> subscribers(ChunkPos cp) {
        Set<Player> s = chunkSubscribers.get(cp);
        return s == null ? Set.of() : s;
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSubscriberTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add chunk subscriber registry" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Region 청크 소유 + 스냅샷 직렬화 + 구독 배선

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/ChunkSerializer.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/ChunkSerializerTest.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/RegionChunkOwnershipTest.java`

ChunkSerializer 시그니처 변경이 유일 호출자(Region.streamLoads)를 깨므로 한 Task서 원자 갱신.

- [ ] **Step 1: 실패 테스트**

`RegionChunkOwnershipTest.java`:
```java
// region이 청크를 1회 로드 후 보관(동일 인스턴스)하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionChunkOwnershipTest {

    @Test
    void chunkAt_loadsOnceAndRetains() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7);
        RegionPos rp = new RegionPos(0, 0);
        ChunkColumn[] seen = new ChunkColumn[2];
        ChunkPos cp = new ChunkPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            seen[0] = r.chunkAt(cp);
            seen[1] = r.chunkAt(cp);
        }).get();
        assertNotNull(seen[0]);
        assertSame(seen[0], seen[1]); // 보관 — 두 번째 호출 동일 인스턴스
        sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionChunkOwnershipTest"`
Expected: 컴파일 실패 — `chunkAt` 없음.

- [ ] **Step 3: ChunkSerializer를 ChunkSnapshot 입력으로 변경**

`serialize` 메서드 시그니처/본문 교체:
```java
    public static void serialize(ByteBuf buf, ChunkSnapshot snap,
                                 int blockDirectBits, int biomeDirectBits) {
        buf.writeInt(snap.chunkX());
        buf.writeInt(snap.chunkZ());

        // heightmap NBT
        NbtWriter nbt = new NbtWriter(buf);
        nbt.beginRootCompound();
        nbt.writeLongArray("MOTION_BLOCKING", snap.heightmap());
        nbt.endCompound();

        // 섹션 데이터(별도 버퍼 후 길이 prefix)
        ByteBuf data = Unpooled.buffer();
        for (ChunkSnapshot.Section s : snap.sections()) {
            data.writeShort(s.nonAirCount());
            Palette.write(data, s.blocks(), blockDirectBits, 4, 8);  // 블록
            Palette.write(data, s.biomes(), biomeDirectBits, 1, 3);  // 바이옴
        }
        Types.writeVarInt(buf, data.readableBytes());
        buf.writeBytes(data);
        data.release();

        // 블록 엔티티 0개
        Types.writeVarInt(buf, 0);

        // --- 라이트 ---
        long allSectionsMask = (1L << LIGHT_SECTIONS) - 1; // 26비트
        writeBitSet(buf, new long[]{allSectionsMask});  // skyLightMask
        writeBitSet(buf, new long[0]);                  // blockLightMask
        writeBitSet(buf, new long[0]);                  // emptySkyLightMask
        writeBitSet(buf, new long[]{allSectionsMask});  // emptyBlockLightMask

        Types.writeVarInt(buf, LIGHT_SECTIONS); // skyLight 배열 수
        byte[] full = new byte[2048];
        java.util.Arrays.fill(full, (byte) 0xFF);
        for (int i = 0; i < LIGHT_SECTIONS; i++) {
            Types.writeVarInt(buf, 2048);
            buf.writeBytes(full);
        }
        Types.writeVarInt(buf, 0); // blockLight 배열 수
    }
```
import 교체: `import org.domi.furnace.server.world.ChunkSection;` 제거, `import org.domi.furnace.server.world.ChunkSnapshot;` 추가. (`ChunkColumn` import는 `LIGHT_SECTIONS` 위해 유지.)

- [ ] **Step 4: ChunkSerializerTest 갱신**

`serialize_writesCoordsAndIsNonEmpty`의 직렬화 호출 교체:
```java
        ChunkColumn col = new SuperflatWorldSource(reg, 6).generate(3, -5);
        ByteBuf buf = Unpooled.buffer();
        ChunkSerializer.serialize(buf, ChunkSnapshot.of(3, -5, col), reg.directBits(), 7);
```
import 추가: `import org.domi.furnace.server.world.ChunkSnapshot;`

- [ ] **Step 5: Region에 청크 소유 + chunkAt + streamLoads 교체**

신규 import 불필요(Region은 world 패키지 — ChunkColumn/ChunkSnapshot/ChunkPos 동일 패키지. `world.worldSource()`로 generate 접근).

`players` 필드 아래에 추가:
```java
    private final Map<ChunkPos, ChunkColumn> chunks = new HashMap<>();
```

`adopt` 위(또는 적당한 위치)에 메서드 추가:
```java
    /** 소유 스레드서 청크 get-or-load(보관). */
    public ChunkColumn chunkAt(ChunkPos cp) {
        return chunks.computeIfAbsent(cp, p -> world.worldSource().generate(p.x(), p.z()));
    }
```

`streamLoads` 교체:
```java
    private void streamLoads(Player p, List<ChunkPos> toLoad) {
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            world.subscribeChunk(cp, p);
            ChunkSnapshot snap = ChunkSnapshot.of(cp.x(), cp.z(), chunkAt(cp)); // region 스레드서 카피
            world.chunkPool().submit(() -> {
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, snap, world.blockDirectBits(), world.biomeDirectBits());
                return body;
            }).thenAccept(body -> p.send(new LevelChunkWithLight(body)));
        }
    }
```
(`ChunkColumn`/`ChunkSnapshot` import 불필요 — 동일 패키지.)

- [ ] **Step 6: 언로드/퇴장 구독 해제**

`updateChunks`의 언로드 루프에 해제 추가:
```java
        for (ChunkPos cp : toUnload) {
            p.send(new UnloadChunk(cp.x(), cp.z()));
            p.loadedChunks().remove(cp);
            world.unsubscribeChunk(cp, p);
        }
```
`tick`의 disconnected 정리 분기에 전체 해제 추가:
```java
            if (p.isDisconnected()) {
                it.remove();
                for (ChunkPos cp : p.loadedChunks()) world.unsubscribeChunk(cp, p);
                broadcastRemove(p.entityId());
                continue;
            }
```
> 핸드오프(경계 넘음)는 구독 유지 — 플레이어 객체가 청크를 계속 보므로(구독 키=플레이어). 새 region이 updateChunks로 재평가.

- [ ] **Step 7: 표적 테스트 + 회귀**

Run: `./gradlew :furnace-server:test --tests "*RegionChunkOwnershipTest" --tests "*ChunkSerializerTest" --tests "*RegionTickTest"`
Expected: PASS.

- [ ] **Step 8: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): region chunk ownership with snapshot serialization" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Region.breakBlock + ConnectionHandler Player Action

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/BlockBreakTest.java`

- [ ] **Step 1: 실패 테스트**

`BlockBreakTest.java`:
```java
// breakBlock이 소유 청크를 air로 + 교차region 구독자에게 BlockUpdate 전송하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class BlockBreakTest {

    @Test
    void breakBlock_clearsAndBroadcastsToSubscriber() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player viewer = new Player(1, UUID.randomUUID(), "V", ch);
        w.subscribeChunk(cp, viewer); // viewer가 (0,0) 청크 구독
        int grass = reg.defaultStateId("minecraft:grass_block");

        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            assertEquals(grass, r.chunkAt(cp).getBlock(0, -61, 0)); // 부수기 전 grass
            r.breakBlock(0, -61, 0);
            assertEquals(ChunkSection.AIR, r.chunkAt(cp).getBlock(0, -61, 0)); // air로
        }).get();

        // viewer 채널이 BlockUpdate를 받았는지(eventLoop 마샬 → flush 처리)
        ch.runPendingTasks();
        ByteBuf out = ch.readOutbound();
        assertNotNull(out, "BlockUpdate 전송돼야");
        assertEquals(BlockUpdate.ID, Types.readVarInt(out));
        int[] pos = Types.readBlockPos(out);
        assertArrayEquals(new int[]{0, -61, 0}, pos);
        assertEquals(ChunkSection.AIR, Types.readVarInt(out));
        out.release();
        sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockBreakTest"`
Expected: 컴파일 실패 — `breakBlock` 없음.

- [ ] **Step 3: Region.breakBlock 추가**

import 추가:
```java
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
```
`chunkAt` 아래에 추가:
```java
    /** 소유 스레드서 블록 파괴(air) + 청크 구독자에게 BlockUpdate. */
    public void breakBlock(int bx, int by, int bz) {
        world.assertOwns(pos);
        ChunkPos cp = ChunkPos.ofBlock(bx, bz);
        chunkAt(cp).setBlock(bx & 15, by, bz & 15, ChunkSection.AIR);
        BlockUpdate pkt = new BlockUpdate(bx, by, bz, ChunkSection.AIR);
        for (Player sub : world.subscribers(cp)) sub.send(pkt);
    }
```
(`ChunkPos`/`ChunkSection` 동일 패키지 — import 불필요.)

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockBreakTest"`
Expected: PASS.

- [ ] **Step 5: ConnectionHandler에 Player Action 파싱**

`handlePlay`의 `default` 위에 case 추가:
```java
            case 0x24 -> { // player_action (digging)
                int status = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                frame.readByte();        // face
                Types.readVarInt(frame); // sequence(예측 ack) — 읽고 무시
                if (status == 0) {       // start digging = 크리에이티브 즉시 파괴
                    int bx = bp[0], by = bp[1], bz = bp[2];
                    RegionPos br = RegionGrid.regionPos(bx, bz, world.regionSize());
                    scheduler.region(br).execute(() -> world.regionAt(br).breakBlock(bx, by, bz));
                }
            }
```
(`RegionPos`·`RegionGrid`·`Types`는 이미 import.)

- [ ] **Step 6: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): block break with cross-region broadcast" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: 수동 검증 — 블록 파괴 (M4a 최종 verify)

**선행:** Player Action(~0x24)·BlockUpdate(~0x09) 패킷 ID를 server.jar 데이터제너레이터(`packets.json`)로 대조. 틀리면 `BlockUpdate.ID`·handlePlay case 값 수정 후 커밋.

- [ ] **Step 1: 설정** — `furnace-server/furnace.properties`:
```
view-distance=8
entity-view-distance=8
region-size=4
region-threads=4
```

- [ ] **Step 2: 서버 기동 + 2클라**

Run: `./gradlew :furnace-server:run`
두 클라 접속(크리에이티브로 스폰됨 — LoginPlay gamemode=1).

- [ ] **Step 3: 기본 파괴**

한 클라가 바닥 블록 좌클릭(파괴) → **양쪽 화면서 블록 사라짐**.

- [ ] **Step 4: 교차region 파괴**

두 클라를 **다른 region(>64블록)이지만 서로의 청크를 보는 거리**에 두고, 한쪽이 경계 근처 블록 파괴 → **다른 region 클라도 사라짐 봄**(구독 레지스트리 동작).

- [ ] **Step 5: 세션 내 영속**

블록 파괴 후 멀리 가서 그 청크 언로드 → 되돌아와 재로드 → **여전히 파괴된 채**(region이 편집 청크 보관·재직렬화).

- [ ] **Step 6: 재시작 미보존 확인**

서버 재시작 → 파괴 블록 복구됨(= 정상, 영속성은 M4b).

- [ ] **문제 해결 가이드**
  - 클라 무반응/킥 → 패킷 ID 오류. datagen으로 player_action/block_update ID 재확인.
  - 파괴 후 블록 깜빡이며 복구 → 예측 ack 미전송 영향. Acknowledge Block Change 패킷 추가 검토(spec §4.2 노트).
  - 다른 region 클라가 못 봄 → 구독 미등록. streamLoads `subscribeChunk` 확인.
  - 재로드 시 블록 복구 → 청크 미보관. `chunkAt` computeIfAbsent 확인(매번 generate 아님).

- [ ] **Step 7: 완료 커밋(수정 있었다면)**

```bash
git add furnace-server
git commit -m "fix(world): M4a block break from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모

- **spec 커버리지:** §3.1 청크소유=T3(스냅샷)+T5(chunkAt/streamLoads), §3.2 구독레지스트리=T4+T5(배선), §3.3 파괴흐름=T6, §4.1 PlayerAction=T6, §4.2 BlockUpdate=T2, §4.3 Position=T1, §5 컴포넌트 전부, §7 테스트 전부. 누락 없음.
- **타입 일관성:** `Types.writeBlockPos/readBlockPos`(T1) · `BlockUpdate(x,y,z,stateId)`/`BlockUpdate.ID`(T2; T6 사용) · `ChunkColumn.getBlock`/`ChunkSnapshot.of`/`ChunkSnapshot.Section`(T3; T5 serializer·streamLoads 사용) · `ChunkSerializer.serialize(buf, ChunkSnapshot, int, int)`(T5; streamLoads·테스트 호출) · `World.subscribeChunk/unsubscribeChunk/subscribers`(T4; T5·T6 사용) · `Region.chunkAt`/`breakBlock`(T5/T6).
- **빌드 단위:** ChunkSerializer 시그니처 변경(T5 Step3)은 유일 호출자 streamLoads(T5 Step5)+테스트(T5 Step4)와 같은 Task서 갱신 → 태스크 경계서 컴파일 유지.
- **회귀:** streamLoads가 이제 chunkAt(보관)+스냅샷 경유 — 와이어 포맷 무변경(serialize 본문 동일, 입력만 스냅샷). RegionTickTest는 tick→updateChunks→streamLoads 경유라 통과.
- **위험:** (1) 스냅샷 카피 alloc(섹션당 int[4096]) — 정확성 우선, air섹션 공유 최적화는 후속. (2) 예측 ack 미전송 깜빡임 — verify Step 문제해결에 명시. (3) 청크 무한보관 — M4b 퇴출.
