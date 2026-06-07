# M3a — 플레이어 이동 + 멀티플레이 가시성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 여러 바닐라 1.21.1 클라가 접속해 걸어다니고 서로를 보며(스폰·이동·디스폰), 이동에 따라 청크가 따라 로드/언로드된다 — M0 RegionThread가 처음으로 플레이어를 소유하고 20Hz 틱(S1→S2).

**Architecture:** 단일 `World`(region 소유자)가 플레이어를 소유하고 `RegionThread.perTick`로 20Hz 틱. IO 스레드는 이동 패킷을 파싱해 `region().execute`로 World에 마샬, 틱이 청크 diff·가시성 diff·이동 브로드캐스트 처리, 전송은 각 대상 `channel.eventLoop`로 마샬. 청크 직렬화는 M2 `ChunkWorkerPool` 재사용.

**Tech Stack:** Java 21, Netty 4.1.115, JUnit 5. M0 스케줄러(`RegionThread.perTick`), M1 네트워킹, M2 청크(`ChunkWorkerPool`/`ChunkSerializer`/`WorldSource`).

**1차 출처:** 패킷 id·엔티티타입은 1.21.1(767) `packets.json`/`registries.json`서 확정. clientbound: player_info_update=0x3E, add_entity=0x01, move_entity_pos=0x2E, move_entity_pos_rot=0x2F, move_entity_rot=0x30, teleport_entity=0x70, rotate_head=0x48, remove_entities=0x42, forget_level_chunk=0x21. serverbound: move_player_pos=0x1A, move_player_pos_rot=0x1B, move_player_rot=0x1C. player 엔티티 타입 id=128. 최종 검증 = Task 9 2클라 실접속.

**M2와의 연결:** M2가 onEnterPlay서 고정 (0,0) 청크를 직접 스트림했다. M3a는 청크 스트리밍을 `World`로 옮겨 **플레이어 위치 따라** 로드/언로드한다. onEnterPlay는 join 패킷(LoginPlay/SyncPos/SetDefaultSpawn/GameEvent13)만 보내고 플레이어를 World에 등록, 첫 틱부터 World가 청크 처리.

**핵심 설계 결정(spec 출처):** region 틱 스레드 소유(락 없음) · 틱 기반 처리(이동 패킷은 위치만 저장) · IO↔region 큐 마샬 · 클라 권위 위치(서버 중계). spec §3.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `world/ChunkPos.java` | 청크 좌표 value type(diff·Set 키) |
| `entity/EntityIdAllocator.java` | 전역 엔티티 id 발급 |
| `net/io/Types.java` | (modify) `writeAngle` 추가 |
| `entity/Player.java` | 플레이어 상태(region 소유) + 채널 send |
| `world/World.java` | region 소유자: players, tick(청크/가시성/이동) |
| `net/protocol/play/PlayerInfoUpdate.java` | 클라 플레이어 레지스트리 등록 |
| `net/protocol/play/SpawnEntity.java` | 엔티티 스폰 |
| `net/protocol/play/RemoveEntities.java` | 디스폰 |
| `net/protocol/play/MoveEntityPos.java` | 이동 델타 |
| `net/protocol/play/MoveEntityPosRot.java` | 이동+회전 델타 |
| `net/protocol/play/MoveEntityRot.java` | 회전 델타 |
| `net/protocol/play/TeleportEntity.java` | 큰 이동 절대좌표 |
| `net/protocol/play/SetHeadRotation.java` | 머리 yaw |
| `net/protocol/play/UnloadChunk.java` | 청크 언로드 |
| `net/ConnectionHandler.java` | (modify) addPlayer, 이동 파싱·마샬, 인액티브 정리 |
| `net/NetworkServer.java` | (modify) World 주입 |
| `sched/FurnaceSchedulerImpl.java` | (modify) settable perTick → world.tick |
| `FurnaceMain.java` | (modify) World 생성·배선 |

---

## Task 1: 기초 — ChunkPos, EntityIdAllocator, Types.writeAngle

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkPos.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/entity/EntityIdAllocator.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/io/Types.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/FoundationsTest.java`

Angle = byte(도 → 256분할): `(int)(deg * 256 / 360) & 0xFF`.

- [ ] **Step 1: 실패하는 테스트 작성**

`FoundationsTest.java`:
```java
// ChunkPos 동등성, 엔티티 id 발급, Angle 인코딩 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class FoundationsTest {

    @Test
    void chunkPos_equalsAndHashWork_inSet() {
        java.util.Set<ChunkPos> set = new java.util.HashSet<>();
        set.add(new ChunkPos(1, -2));
        assertTrue(set.contains(new ChunkPos(1, -2)));
        assertFalse(set.contains(new ChunkPos(1, 2)));
    }

    @Test
    void entityId_increments_from1() {
        EntityIdAllocator alloc = new EntityIdAllocator();
        assertEquals(1, alloc.next());
        assertEquals(2, alloc.next());
    }

    @Test
    void writeAngle_maps360To256() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeAngle(buf, 0f);
        Types.writeAngle(buf, 90f);
        Types.writeAngle(buf, 180f);
        assertEquals(0, buf.readByte() & 0xFF);
        assertEquals(64, buf.readByte() & 0xFF);   // 90/360*256
        assertEquals(128, buf.readByte() & 0xFF);  // 180/360*256
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*FoundationsTest"`
Expected: 컴파일 실패 — 클래스/메서드 없음.

- [ ] **Step 3: `ChunkPos.java` 작성**

```java
// 청크 좌표(블록>>4). diff·로드셋 키로 쓰이는 value type.
package org.domi.furnace.server.world;

public record ChunkPos(int x, int z) {

    /** 블록 절대좌표 → 청크 좌표. */
    public static ChunkPos ofBlock(double blockX, double blockZ) {
        return new ChunkPos(Math.floorDiv((int) Math.floor(blockX), 16),
                Math.floorDiv((int) Math.floor(blockZ), 16));
    }
}
```

- [ ] **Step 4: `EntityIdAllocator.java` 작성**

```java
// 서버 전역 엔티티 id 발급(1부터 증가). join이 IO 스레드서 올 수 있어 atomic.
package org.domi.furnace.server.entity;

import java.util.concurrent.atomic.AtomicInteger;

public final class EntityIdAllocator {

    private final AtomicInteger next = new AtomicInteger(0);

    public int next() {
        return next.incrementAndGet();
    }
}
```

- [ ] **Step 5: `Types.writeAngle` 추가**

`Types.java`의 `writeUuid` 메서드 아래에 추가:
```java
    /** 회전 각도를 256분할 byte로. degrees * 256 / 360. */
    public static void writeAngle(ByteBuf buf, float degrees) {
        buf.writeByte((int) (degrees * 256.0f / 360.0f) & 0xFF);
    }
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*FoundationsTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(entity): add ChunkPos, entity id allocator, angle encoding" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Player 엔티티

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/entity/Player.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/entity/PlayerTest.java`

`Player`는 region 스레드 소유. 채널 ref로 전송하되 `channel.eventLoop().execute`로 마샬(IO 스레드서 실제 write).

- [ ] **Step 1: 실패하는 테스트 작성**

`PlayerTest.java`:
```java
// Player 위치 갱신·청크좌표 계산 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class PlayerTest {

    @Test
    void applyMove_updatesPositionAndChunk() {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "Dom", ch);
        p.applyMove(33.0, -60.0, -17.0, 90f, 0f, true);
        assertEquals(33.0, p.x());
        assertEquals(90f, p.yaw());
        assertEquals(new ChunkPos(2, -2), p.chunkPos()); // floor(33/16)=2, floor(-17/16)=-2
        ch.finishAndReleaseAll();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayerTest"`
Expected: 컴파일 실패 — `Player` 없음.

- [ ] **Step 3: `Player.java` 작성**

```java
// 플레이어 엔티티 상태(region 스레드 소유). 채널로 패킷 전송(eventLoop 마샬).
package org.domi.furnace.server.entity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkPos;

public final class Player {

    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final Channel channel;

    private double x, y, z;
    private float yaw, pitch;
    private boolean onGround;

    private ChunkPos lastCenter;                 // 마지막으로 SetCenterChunk 보낸 청크(null=미전송)
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    private final Map<Integer, double[]> tracked = new HashMap<>(); // 관찰중 엔티티 → 마지막 전송 {x,y,z}

    public Player(int entityId, UUID uuid, String name, Channel channel) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.name = name;
        this.channel = channel;
    }

    public void applyMove(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public void applyRotation(float yaw, float pitch, boolean onGround) {
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public ChunkPos chunkPos() {
        return ChunkPos.ofBlock(x, z);
    }

    /** 패킷을 이 플레이어 채널로 전송(IO eventLoop 마샬). region 스레드서 호출 안전. */
    public void send(Packet packet) {
        channel.eventLoop().execute(() -> {
            ByteBuf buf = channel.alloc().buffer();
            Types.writeVarInt(buf, packet.id());
            packet.write(buf);
            channel.writeAndFlush(buf);
        });
    }

    public int entityId() { return entityId; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean onGround() { return onGround; }
    public ChunkPos lastCenter() { return lastCenter; }
    public void lastCenter(ChunkPos c) { this.lastCenter = c; }
    public Set<ChunkPos> loadedChunks() { return loadedChunks; }
    public Map<Integer, double[]> tracked() { return tracked; }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayerTest"`
Expected: PASS (1 test).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(entity): add player entity with channel send" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: clientbound 패킷 — 스폰/디스폰

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/PlayerInfoUpdate.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/SpawnEntity.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/RemoveEntities.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/SpawnPacketsTest.java`

PlayerInfoUpdate(0x3E): `Byte actions`(비트 0=add_player,3=update_listed → 0x09), `VarInt count=1`, `UUID`, add_player: `String name`+`VarInt props=0`, update_listed: `Boolean true`.
SpawnEntity(0x01): `VarInt id, UUID, VarInt type=128, Double x,y,z, Angle pitch, Angle yaw, Angle headYaw, VarInt data=0, Short velX,velY,velZ`.
RemoveEntities(0x42): `VarInt count, VarInt[] ids`.

- [ ] **Step 1: 실패하는 테스트 작성**

`SpawnPacketsTest.java`:
```java
// 스폰/디스폰 패킷 id·와이어 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class SpawnPacketsTest {

    @Test
    void playerInfoUpdate_addPlayer() {
        UUID id = UUID.randomUUID();
        PlayerInfoUpdate p = new PlayerInfoUpdate(id, "Dom");
        assertEquals(0x3E, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(0x09, buf.readByte());        // actions add_player|update_listed
        assertEquals(1, Types.readVarInt(buf));     // count
        assertEquals(id, Types.readUuid(buf));
        assertEquals("Dom", Types.readString(buf, 16));
        assertEquals(0, Types.readVarInt(buf));     // props
        assertEquals(1, buf.readByte());            // listed = true
        buf.release();
    }

    @Test
    void spawnEntity_writesTypeAndPos() {
        UUID id = UUID.randomUUID();
        SpawnEntity p = new SpawnEntity(5, id, 1.0, -60.0, 2.0, 0f, 90f);
        assertEquals(0x01, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(5, Types.readVarInt(buf));     // entityId
        assertEquals(id, Types.readUuid(buf));
        assertEquals(128, Types.readVarInt(buf));   // player type
        assertEquals(1.0, buf.readDouble());
        buf.release();
    }

    @Test
    void removeEntities_writesIds() {
        RemoveEntities p = new RemoveEntities(java.util.List.of(5, 9));
        assertEquals(0x42, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(2, Types.readVarInt(buf));
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(9, Types.readVarInt(buf));
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*SpawnPacketsTest"`
Expected: 컴파일 실패 — 클래스 없음.

- [ ] **Step 3: `PlayerInfoUpdate.java` 작성**

```java
// S→C 플레이어 레지스트리 등록(add_player + update_listed). 엔티티 스폰 전 필수(1.20.2+).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerInfoUpdate(UUID uuid, String name) implements Packet {

    public static final int ID = 0x3E;
    private static final int ACTIONS = 0x01 | 0x08; // add_player | update_listed

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(ACTIONS);
        Types.writeVarInt(buf, 1);     // 플레이어 1명
        Types.writeUuid(buf, uuid);
        Types.writeString(buf, name);  // add_player: name
        Types.writeVarInt(buf, 0);     // add_player: property count
        buf.writeBoolean(true);        // update_listed
    }
}
```

- [ ] **Step 4: `SpawnEntity.java` 작성**

```java
// S→C 엔티티 스폰(M3a는 플레이어, type=128). 헤드yaw·속도 포함.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SpawnEntity(int entityId, UUID uuid, double x, double y, double z,
                          float yaw, float pitch) implements Packet {

    public static final int ID = 0x01;
    private static final int PLAYER_TYPE = 128;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeUuid(buf, uuid);
        Types.writeVarInt(buf, PLAYER_TYPE);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        Types.writeAngle(buf, pitch);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, yaw); // head yaw
        Types.writeVarInt(buf, 0);  // data
        buf.writeShort(0);          // velX
        buf.writeShort(0);          // velY
        buf.writeShort(0);          // velZ
    }
}
```

- [ ] **Step 5: `RemoveEntities.java` 작성**

```java
// S→C 엔티티 제거(디스폰). entityId 목록.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RemoveEntities(List<Integer> entityIds) implements Packet {

    public static final int ID = 0x42;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityIds.size());
        for (int id : entityIds) Types.writeVarInt(buf, id);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*SpawnPacketsTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add player spawn/info/remove packets" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: clientbound 패킷 — 이동/회전/언로드

**Files:**
- Create: `MoveEntityPos.java`, `MoveEntityPosRot.java`, `MoveEntityRot.java`, `TeleportEntity.java`, `SetHeadRotation.java`, `UnloadChunk.java` (모두 `net/protocol/play/`)
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/MovePacketsTest.java`

델타: `(short)((newPos*4096) - (oldPos*4096))`. 범위 ±8블록(short ±32767). 초과 시 TeleportEntity.
MoveEntityPos(0x2E): `VarInt id, Short dx,dy,dz, Boolean onGround`.
MoveEntityPosRot(0x2F): `VarInt id, Short dx,dy,dz, Angle yaw, Angle pitch, Boolean onGround`.
MoveEntityRot(0x30): `VarInt id, Angle yaw, Angle pitch, Boolean onGround`.
TeleportEntity(0x70): `VarInt id, Double x,y,z, Angle yaw, Angle pitch, Boolean onGround`.
SetHeadRotation(0x48): `VarInt id, Angle headYaw`.
UnloadChunk(0x21): `Int chunkX, Int chunkZ`.

- [ ] **Step 1: 실패하는 테스트 작성**

`MovePacketsTest.java`:
```java
// 이동/회전/텔레포트/언로드 패킷 id·와이어 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class MovePacketsTest {

    @Test
    void moveEntityPos_writesDeltas() {
        MoveEntityPos p = new MoveEntityPos(5, (short) 100, (short) 0, (short) -50, true);
        assertEquals(0x2E, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(100, buf.readShort());
        assertEquals(0, buf.readShort());
        assertEquals(-50, buf.readShort());
        assertEquals(1, buf.readByte());
        buf.release();
    }

    @Test
    void teleportEntity_writesAbsolute() {
        TeleportEntity p = new TeleportEntity(5, 10.0, -60.0, 20.0, 90f, 0f, true);
        assertEquals(0x70, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(10.0, buf.readDouble());
        buf.release();
    }

    @Test
    void unloadChunk_writesCoords() {
        UnloadChunk p = new UnloadChunk(3, -5);
        assertEquals(0x21, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(3, buf.readInt());
        assertEquals(-5, buf.readInt());
        buf.release();
    }

    @Test
    void headRotation_andRotOnly_ids() {
        assertEquals(0x48, new SetHeadRotation(5, 0f).id());
        assertEquals(0x30, new MoveEntityRot(5, 0f, 0f, true).id());
        assertEquals(0x2F, new MoveEntityPosRot(5, (short)1,(short)1,(short)1, 0f,0f, true).id());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*MovePacketsTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: `MoveEntityPos.java`**

```java
// S→C 엔티티 이동 델타(±8블록 내). 델타 = (new-old)*4096 short.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveEntityPos(int entityId, short dx, short dy, short dz, boolean onGround)
        implements Packet {

    public static final int ID = 0x2E;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeShort(dx);
        buf.writeShort(dy);
        buf.writeShort(dz);
        buf.writeBoolean(onGround);
    }
}
```

- [ ] **Step 4: `MoveEntityPosRot.java`**

```java
// S→C 엔티티 이동+회전 델타.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveEntityPosRot(int entityId, short dx, short dy, short dz,
                               float yaw, float pitch, boolean onGround) implements Packet {

    public static final int ID = 0x2F;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeShort(dx);
        buf.writeShort(dy);
        buf.writeShort(dz);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, pitch);
        buf.writeBoolean(onGround);
    }
}
```

- [ ] **Step 5: `MoveEntityRot.java`**

```java
// S→C 엔티티 회전만.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveEntityRot(int entityId, float yaw, float pitch, boolean onGround)
        implements Packet {

    public static final int ID = 0x30;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, pitch);
        buf.writeBoolean(onGround);
    }
}
```

- [ ] **Step 6: `TeleportEntity.java`**

```java
// S→C 엔티티 절대좌표 텔레포트(델타 범위 초과 시).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record TeleportEntity(int entityId, double x, double y, double z,
                             float yaw, float pitch, boolean onGround) implements Packet {

    public static final int ID = 0x70;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, pitch);
        buf.writeBoolean(onGround);
    }
}
```

- [ ] **Step 7: `SetHeadRotation.java`**

```java
// S→C 엔티티 머리 yaw(몸통 yaw와 별도).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetHeadRotation(int entityId, float headYaw) implements Packet {

    public static final int ID = 0x48;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeAngle(buf, headYaw);
    }
}
```

- [ ] **Step 8: `UnloadChunk.java`**

```java
// S→C 청크 언로드(플레이어 시야 벗어남).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record UnloadChunk(int chunkX, int chunkZ) implements Packet {

    public static final int ID = 0x21;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
    }
}
```

- [ ] **Step 9: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*MovePacketsTest"`
Expected: PASS (4 tests).

- [ ] **Step 10: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add entity move/rotate/teleport and unload chunk packets" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: World — region 소유자 + 청크 스트리밍

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/WorldChunkTest.java`

`World`는 플레이어를 소유하고 `tick()`서 처리. M2의 청크 스트리밍(SetCenterChunk + 배치 + worker 직렬화)을 여기로 이전하되 **플레이어 센터 기준 + 언로드** 추가. Task 5는 청크 diff까지, 가시성은 Task 6.

청크 diff: 플레이어 `chunkPos()`가 `lastCenter`와 다르면 → 새 반경(센터±viewDistance) 중 `loadedChunks`에 없는 것 로드, `loadedChunks` 중 새 반경 밖인 것 언로드.

- [ ] **Step 1: 실패하는 테스트 작성**

`WorldChunkTest.java`:
```java
// 첫 틱서 플레이어 주변 청크 로드, 이동 후 diff(새 로드+언로드) 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class WorldChunkTest {

    private static World world(int viewDistance) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                viewDistance, reg.directBits(), 7);
    }

    @Test
    void firstTick_loadsChunksAroundPlayer() {
        World w = world(1); // 3x3 = 9 청크
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "Dom", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        w.addPlayer(p);
        w.tick();
        assertEquals(9, p.loadedChunks().size());
        assertEquals(new ChunkPos(0, 0), p.lastCenter());
        ch.finishAndReleaseAll();
    }

    @Test
    void moveCrossingChunk_loadsNewUnloadsOld() {
        World w = world(1);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "Dom", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        w.addPlayer(p);
        w.tick();                       // 센터 (0,0), 9 청크
        p.applyMove(16, -60, 0, 0, 0, true); // 센터 (1,0)
        w.tick();
        // 여전히 3x3=9 청크, 센터만 이동
        assertEquals(9, p.loadedChunks().size());
        assertEquals(new ChunkPos(1, 0), p.lastCenter());
        assertTrue(p.loadedChunks().contains(new ChunkPos(2, 0)));   // 새로 로드
        assertFalse(p.loadedChunks().contains(new ChunkPos(-1, 0))); // 언로드됨
        ch.finishAndReleaseAll();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*WorldChunkTest"`
Expected: 컴파일 실패 — `World` 없음.

- [ ] **Step 3: `World.java` 작성 (청크 diff까지, 가시성은 Task 6서 추가)**

```java
// region 소유자: 플레이어를 소유하고 매 틱 청크/가시성/이동을 처리한다. M3a는 region 1개.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;
import org.domi.furnace.server.world.gen.WorldSource;

public final class World {

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final int viewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;

    private final Map<Integer, Player> players = new HashMap<>();

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, int viewDistance,
                 int blockDirectBits, int biomeDirectBits) {
        this.worldSource = worldSource;
        this.chunkPool = chunkPool;
        this.viewDistance = viewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
    }

    public void addPlayer(Player p) { players.put(p.entityId(), p); }

    public void removePlayer(int entityId) { players.remove(entityId); }

    public Map<Integer, Player> players() { return players; }

    /** region 스레드서 매 틱 호출. */
    public void tick() {
        for (Player p : players.values()) {
            updateChunks(p);
        }
    }

    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        if (center.equals(p.lastCenter())) return; // 센터 안 바뀌면 no-op

        // 새 반경 집합
        List<ChunkPos> toLoad = new ArrayList<>();
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                ChunkPos cp = new ChunkPos(center.x() + dx, center.z() + dz);
                if (!p.loadedChunks().contains(cp)) toLoad.add(cp);
            }
        }
        // 언로드: 새 반경 밖
        List<ChunkPos> toUnload = new ArrayList<>();
        for (ChunkPos cp : p.loadedChunks()) {
            if (Math.abs(cp.x() - center.x()) > viewDistance
                    || Math.abs(cp.z() - center.z()) > viewDistance) {
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
        if (toLoad.isEmpty()) return;
        p.send(new ChunkBatchStart());
        for (ChunkPos cp : toLoad) {
            p.loadedChunks().add(cp);
            chunkPool.submit(() -> {
                ChunkColumn col = worldSource.generate(cp.x(), cp.z());
                ByteBuf body = Unpooled.buffer();
                ChunkSerializer.serialize(body, cp.x(), cp.z(), col, blockDirectBits, biomeDirectBits);
                return body;
            }).thenAccept(body -> p.send(new LevelChunkWithLight(body)));
        }
        p.send(new ChunkBatchFinished(toLoad.size()));
    }
}
```

> 주의: `streamLoads`의 ChunkBatchFinished는 worker 완료 전에 전송될 수 있으나, 클라는 배치 크기만 참고하므로 순서상 문제없다(배치 내 청크는 비동기 도착). 첫 로드(스폰)도 이 경로로 처리된다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*WorldChunkTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add World owner with player-following chunk streaming" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: World.tick — 가시성 diff + 이동 브로드캐스트

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/WorldVisibilityTest.java`

매 틱, 각 플레이어 A에 대해: 시야거리(블록 = viewDistance*16) 내 타 플레이어 집합 계산.
- A.tracked에 없고 visible → PlayerInfoUpdate(B)+SpawnEntity(B)+SetHeadRotation(B). tracked에 B의 현재 위치 기록.
- A.tracked에 있고 not visible → RemoveEntities([B]). tracked서 제거.
- A.tracked 유지 + B 이동 → 델타(≤±8블록 MoveEntityPosRot) 또는 TeleportEntity + SetHeadRotation. tracked 위치 갱신.

- [ ] **Step 1: 실패하는 테스트 작성**

`WorldVisibilityTest.java`:
```java
// 두 플레이어 가시성: 등록 시 서로 추적, 이동 시 추적 위치 갱신
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class WorldVisibilityTest {

    private static World world() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                4, reg.directBits(), 7);
    }

    @Test
    void twoNearbyPlayers_trackEachOther() {
        World w = world();
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        b.applyMove(5, -60, 5, 0, 0, true);
        w.addPlayer(a); w.addPlayer(b);
        w.tick();
        assertTrue(a.tracked().containsKey(2)); // A가 B 추적
        assertTrue(b.tracked().containsKey(1)); // B가 A 추적
    }

    @Test
    void farPlayer_notTracked() {
        World w = world(); // viewDistance 4 → 64블록
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        b.applyMove(1000, -60, 1000, 0, 0, true);
        w.addPlayer(a); w.addPlayer(b);
        w.tick();
        assertFalse(a.tracked().containsKey(2));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*WorldVisibilityTest"`
Expected: 실패 — `tick`이 가시성 미처리.

- [ ] **Step 3: `World.tick`에 가시성 추가**

`tick()` 교체:
```java
    public void tick() {
        for (Player p : players.values()) {
            updateChunks(p);
        }
        for (Player p : players.values()) {
            updateVisibility(p);
        }
    }
```

import 추가:
```java
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
```

메서드 추가:
```java
    private void updateVisibility(Player a) {
        double range = viewDistance * 16.0;
        for (Player b : players.values()) {
            if (b.entityId() == a.entityId()) continue;
            boolean near = within(a, b, range);
            boolean tracking = a.tracked().containsKey(b.entityId());
            if (near && !tracking) {            // 신규 진입 → 스폰
                a.send(new PlayerInfoUpdate(b.uuid(), b.name()));
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                a.tracked().put(b.entityId(), new double[]{b.x(), b.y(), b.z()});
            } else if (!near && tracking) {     // 이탈 → 제거
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                a.tracked().remove(b.entityId());
            } else if (near) {                  // 유지 → 이동 갱신
                double[] last = a.tracked().get(b.entityId());
                broadcastMove(a, b, last);
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
        boolean big = Math.abs(ddx) >= 8 || Math.abs(ddy) >= 8 || Math.abs(ddz) >= 8;
        if (big) {
            a.send(new TeleportEntity(b.entityId(), b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.onGround()));
        } else if (ddx != 0 || ddy != 0 || ddz != 0) {
            short sdx = (short) Math.round(ddx * 4096);
            short sdy = (short) Math.round(ddy * 4096);
            short sdz = (short) Math.round(ddz * 4096);
            a.send(new MoveEntityPosRot(b.entityId(), sdx, sdy, sdz, b.yaw(), b.pitch(), b.onGround()));
        }
        a.send(new SetHeadRotation(b.entityId(), b.yaw()));
        last[0] = b.x(); last[1] = b.y(); last[2] = b.z();
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*WorldVisibilityTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): entity visibility tracking and movement broadcast" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: 스케줄러 perTick 훅

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/sched/FurnaceSchedulerImpl.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/SchedulerTickHookTest.java`

World는 scheduler에 의존(마샬)하고 scheduler 틱은 world.tick을 부르는 순환을 끊기 위해 **settable perTick**을 둔다.

- [ ] **Step 1: 실패하는 테스트 작성**

`SchedulerTickHookTest.java`:
```java
// setRegionTick으로 등록한 콜백이 region 틱마다 실행되는지 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SchedulerTickHookTest {

    @Test
    void setRegionTick_runsEachTick() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L); // 10ms 틱
        AtomicInteger ticks = new AtomicInteger();
        sched.setRegionTick(ticks::incrementAndGet);
        Thread.sleep(120); // ~12틱
        sched.close();
        assertTrue(ticks.get() >= 3, "ticks=" + ticks.get());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*SchedulerTickHookTest"`
Expected: 컴파일 실패 — `setRegionTick` 없음.

- [ ] **Step 3: `FurnaceSchedulerImpl` 수정**

생성자와 필드 교체:
```java
    private final GlobalThread global;
    private final RegionThread region;
    private volatile Runnable regionTick = () -> {};

    public FurnaceSchedulerImpl(long tickNanos) {
        this.global = new GlobalThread("furnace-global");
        this.region = new RegionThread("furnace-region-0", tickNanos, () -> regionTick.run());
    }

    /** region 틱마다 실행할 콜백 등록(World.tick 연결용). */
    public void setRegionTick(Runnable tick) {
        this.regionTick = tick;
    }
```

> `() -> regionTick.run()`는 항상 최신 `regionTick`을 부른다. RegionThread 생성 시점엔 no-op, 이후 setRegionTick로 교체된다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*SchedulerTickHookTest"`
Expected: PASS (1 test).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(sched): settable region tick hook" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: 배선 — ConnectionHandler/NetworkServer/FurnaceMain

M2의 onEnterPlay 청크 스트림을 제거하고, 플레이어를 World에 등록 + 이동 패킷을 region 스레드로 마샬한다.

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/NetworkServer.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/net/NetworkServerTest.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/net/StatusPingTest.java`

- [ ] **Step 1: `NetworkServer` — World·scheduler 주입, 청크 인자 제거**

`NetworkServer`는 이제 청크 파라미터(worldSource/viewDistance/직접비트) 대신 `World`와 `EntityIdAllocator`를 받는다. 필드/생성자 교체:
```java
    private final int port;
    private final int compressionThreshold;
    private final String serverName;
    private final FurnaceScheduler scheduler;
    private final World world;
    private final org.domi.furnace.server.entity.EntityIdAllocator idAllocator;

    public NetworkServer(int port, int compressionThreshold, String serverName,
                         FurnaceScheduler scheduler, World world,
                         org.domi.furnace.server.entity.EntityIdAllocator idAllocator) {
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
        this.scheduler = scheduler;
        this.world = world;
        this.idAllocator = idAllocator;
    }
```
import 교체(`world.gen.WorldSource` 제거, 추가):
```java
import org.domi.furnace.server.world.World;
```
`ChunkWorkerPool` 필드/생성/`stop()`의 `chunkPool.shutdown()` 제거(풀은 이제 World가 소유 — FurnaceMain서 생성). `stop()`에서 `chunkPool.shutdown();` 줄 삭제.
`initChannel` handler 생성 교체:
```java
                                .addLast("handler",
                                        new ConnectionHandler(serverName, compressionThreshold,
                                                scheduler, world, idAllocator));
```

- [ ] **Step 2: `ConnectionHandler` — 필드/생성자 교체**

기존 청크 관련 필드(worldSource/chunkPool/viewDistance/blockDirectBits/biomeDirectBits) 제거, 교체:
```java
    private final String serverName;
    private final int compressionThreshold;
    private final FurnaceScheduler scheduler;
    private final World world;
    private final EntityIdAllocator idAllocator;

    private volatile ChannelHandlerContext ctx;
    private final AtomicBoolean awaitingKeepAlive = new AtomicBoolean(false);
    private volatile long lastKeepAliveId;

    private ConnectionState state = ConnectionState.HANDSHAKE;
    private String playerName;
    private UUID playerUuid;
    private Player player;        // play 진입 후 생성

    public ConnectionHandler(String serverName, int compressionThreshold, FurnaceScheduler scheduler,
                             World world, EntityIdAllocator idAllocator) {
        this.serverName = serverName;
        this.compressionThreshold = compressionThreshold;
        this.scheduler = scheduler;
        this.world = world;
        this.idAllocator = idAllocator;
    }
```
import 정리: 추가
```java
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.World;
import org.domi.furnace.api.sched.RegionPos;
```
제거(이제 ConnectionHandler서 직접 안 씀): `ChunkColumn`, `ChunkSerializer`, `LevelChunkWithLight`, `SetCenterChunk`, `ChunkBatchStart`, `ChunkBatchFinished`, `WorldSource`, `Unpooled`, `ChunkWorkerPool` import. (`GameEvent`, `LoginPlay`, `SyncPlayerPosition`, `SetDefaultSpawnPosition`는 유지.)

- [ ] **Step 3: `onEnterPlay` — 청크 스트림 제거, 플레이어 등록**

`onEnterPlay`와 `sendChunks` 메서드를 교체:
```java
    private void onEnterPlay(ChannelHandlerContext ctx) {
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new LoginPlay(idAllocator.next(), "minecraft:overworld", dimIndex));
        // 위 LoginPlay의 entityId를 재사용해야 하므로 별도 발급 대신 플레이어 생성에 같은 값 사용.
        // → 아래처럼 먼저 id를 뽑아 LoginPlay와 Player가 공유한다.
    }
```
> 위 메서드는 id 공유를 위해 아래 최종본으로 교체한다(LoginPlay와 Player가 같은 entityId를 써야 클라가 자기 엔티티를 인식):
```java
    private void onEnterPlay(ChannelHandlerContext ctx) {
        int eid = idAllocator.next();
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex));
        send(ctx, new SyncPlayerPosition(0, -60, 0, 1));
        send(ctx, new SetDefaultSpawnPosition(0, -60, 0));
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));

        this.player = new Player(eid, playerUuid, playerName, ctx.channel());
        this.player.applyMove(0, -60, 0, 0f, 0f, true);
        scheduler.region(new RegionPos(0, 0)).execute(() -> world.addPlayer(player));
        scheduleKeepAlive();
    }
```

> `new RegionPos(0, 0)` 상수가 없으면 `new RegionPos(0,0)` 등 기존 생성자를 쓴다(파일 확인 후 맞춘다). M0 region은 pos 무시.

- [ ] **Step 4: `handlePlay` — 이동 패킷 파싱·마샬**

`handlePlay`의 switch에 이동 케이스 추가(기존 0x00/0x18 유지):
```java
    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        switch (packetId) {
            case 0x00 -> { /* Confirm Teleportation */ }
            case 0x18 -> { // Keep Alive
                long echoed = frame.readLong();
                if (echoed == lastKeepAliveId) awaitingKeepAlive.set(false);
            }
            case 0x1A -> { // move_player_pos: Double x,y,z, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                boolean g = frame.readBoolean();
                marshalMove(x, y, z, player == null ? 0 : player.yaw(),
                        player == null ? 0 : player.pitch(), g);
            }
            case 0x1B -> { // move_player_pos_rot: Double x,y,z, Float yaw,pitch, Boolean onGround
                double x = frame.readDouble(), y = frame.readDouble(), z = frame.readDouble();
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                marshalMove(x, y, z, yaw, pitch, g);
            }
            case 0x1C -> { // move_player_rot: Float yaw,pitch, Boolean onGround
                float yaw = frame.readFloat(), pitch = frame.readFloat();
                boolean g = frame.readBoolean();
                Player p = player;
                if (p != null) scheduler.region(new RegionPos(0, 0)).execute(() -> p.applyRotation(yaw, pitch, g));
            }
            default -> { /* 그 외 무시 */ }
        }
    }

    private void marshalMove(double x, double y, double z, float yaw, float pitch, boolean g) {
        Player p = player;
        if (p != null) scheduler.region(new RegionPos(0, 0)).execute(() -> p.applyMove(x, y, z, yaw, pitch, g));
    }
```

- [ ] **Step 5: `channelInactive` — 플레이어 제거**

클래스에 메서드 추가(`exceptionCaught` 옆):
```java
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Player p = player;
        if (p != null) {
            scheduler.region(new RegionPos(0, 0)).execute(() -> world.removePlayerAndBroadcast(p.entityId()));
        }
    }
```

- [ ] **Step 6: `World`에 `removePlayerAndBroadcast` 추가**

`World.java`에 메서드 추가(나간 플레이어를 관찰 중이던 모두에게 RemoveEntities):
```java
    /** 플레이어 제거 + 그를 추적하던 모두에게 디스폰 전송(region 스레드). */
    public void removePlayerAndBroadcast(int entityId) {
        players.remove(entityId);
        for (Player other : players.values()) {
            if (other.tracked().remove(entityId) != null) {
                other.send(new RemoveEntities(java.util.List.of(entityId)));
            }
        }
    }
```

- [ ] **Step 7: `FurnaceMain` — World 생성·배선**

`NetworkServer network = ...` 블록 교체:
```java
        BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        org.domi.furnace.server.net.ChunkWorkerPool chunkPool =
                new org.domi.furnace.server.net.ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
        org.domi.furnace.server.world.World world = new org.domi.furnace.server.world.World(
                new SuperflatWorldSource(blockRegistry, plainsBiome), chunkPool,
                config.viewDistance(), blockRegistry.directBits(), biomeDirectBits);
        scheduler.setRegionTick(world::tick);
        org.domi.furnace.server.entity.EntityIdAllocator idAllocator =
                new org.domi.furnace.server.entity.EntityIdAllocator();
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName(), scheduler,
                world, idAllocator);
```
> `SuperflatWorldSource` import는 이미 있다. `chunkPool` 셧다운은 종료 훅에 추가: 종료 람다의 `scheduler.close();` 위에 `chunkPool.shutdown();`.

- [ ] **Step 8: 기존 테스트 2개 생성자 갱신**

`NetworkServerTest`·`StatusPingTest`의 `new NetworkServer(...)`를 교체. import 교체(`SuperflatWorldSource`/`BlockRegistry` 유지하고 `World`/`EntityIdAllocator` 추가):
```java
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.World;
```
생성 부분 교체(두 테스트 동일):
```java
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0),
                new ChunkWorkerPool(1), 2, reg.directBits(), 7);
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched,
                world, new EntityIdAllocator());
```

- [ ] **Step 9: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 전체 테스트 통과(M0~M3a).

- [ ] **Step 10: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): wire player join, movement marshaling, world tick" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: 수동 검증 — 2 클라 실접속 (M3a 최종 verify)

- [ ] **Step 1: 서버 기동**

Run: `./gradlew :furnace-server:run`

- [ ] **Step 2: 클라 1 접속**

바닐라 1.21.1 → `localhost`. 즉시 로딩, grass 위 스폰.

- [ ] **Step 3: 걸어다니기**

이동 → **청크가 따라 로드**되고 멀어진 청크 언로드. void 안 빠짐.

- [ ] **Step 4: 클라 2 접속(다른 계정/오프라인)**

두 번째 클라 접속 → 두 화면서 **서로 플레이어가 보임**. 한 명 움직이면 다른 화면서 따라 움직임. 머리 회전 반영.

- [ ] **Step 5: 한 명 퇴장**

한 클라 종료 → 다른 화면서 **사라짐**(RemoveEntities).

- [ ] **Step 6: 워커/스레드 확인**

청크 직렬화 = `chunk-worker-*`, 플레이어 처리 = `furnace-region-0` 스레드. (필요시 `World.tick` 진입에 임시 로그 후 제거.)

- [ ] **문제 해결 가이드**
  - 다른 플레이어 안 보임 → PlayerInfoUpdate(add_player) 누락/필드 오류. 스폰 전 PlayerInfoUpdate 먼저 가는지 확인.
  - 플레이어 위치 튐 → 델타 ±8블록 경계 오류. `broadcastMove`의 big 판정/4096 스케일 확인.
  - 청크 언로드 안 됨 → forget_level_chunk(0x21) 필드순서(Int X, Int Z) 의심. minecraft.wiki 1.21.1 대조.
  - 자기 자신이 복제돼 보임 → LoginPlay entityId와 Player entityId 불일치. 같은 `eid` 쓰는지 확인.

- [ ] **Step 7: M3a 완료 커밋(수정 있었다면)**

```bash
git add furnace-server
git commit -m "fix(net): correct M3a wire issues per vanilla client" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모(작성자 → 실행자)

- **spec 커버리지:** §5.1 EntityIdAllocator=T1, §5.2 Player=T2, §5.3 World=T5+T6, §5.4 인바운드 이동=T8, §5.5 패킷=T3+T4, perTick 배선=T7, 가시성 diff §6=T6, verify §7=T9. 전 항목 태스크 존재.
- **위험(와이어):** forget_level_chunk 필드순서(Int X,Z 가정), Spawn Entity/PlayerInfoUpdate 필드 — 단위테스트는 자체 인코딩만 검증, 실클라가 최종 판정(T9). teleport_entity 0x70은 1.21.1 고전 포맷(절대좌표) 가정.
- **타입 일관성:** `Player.send(Packet)`(T2 정의, World/T5·T6 사용), `World(WorldSource,ChunkWorkerPool,int,int,int)` 생성자(T5 정의, T8/테스트 사용), `World.removePlayerAndBroadcast`(T8 Step6 추가, ConnectionHandler 사용), `scheduler.setRegionTick`(T7 정의, T8 사용), `new RegionPos(0, 0)`(T8서 사용 — 없으면 `new RegionPos(0,0)`로 대체, 실행 시 RegionPos 시그니처 확인). entityId 공유(LoginPlay eid == Player eid) T8 Step3 명시.
- **M2 회귀:** onEnterPlay의 M2 청크 스트림 제거됨 → 첫 청크 로딩이 World.tick 첫 틱으로 이전. GameEvent13는 onEnterPlay 유지(청크 전). 클라 join 시퀀스 정상.
