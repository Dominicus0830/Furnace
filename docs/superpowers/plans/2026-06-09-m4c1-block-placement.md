# M4c-1 크리에이티브 블록 설치 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 크리에이티브 모드서 우클릭으로 블록 설치 — 든 아이템→블록 해석, 면 방향 위치 계산, 플레이어 충돌 거부, 설치+교차region 브로드캐스트(M4a 경로), M4b dirty 영속.

**Architecture:** 든-아이템 상태(hotbar/selectedSlot)는 ConnectionHandler(Netty 스레드)서 추적. `use_item_on` 수신 시 든 블록 state + 설치 위치 계산 → 소유 region에 `placeBlock` 마샬 → 충돌검사 후 setBlock+BlockUpdate(breakBlock 미러).

**Tech Stack:** Java 21, Netty, JUnit 5. M4a(파괴 인프라)·M4b(영속)·M2(BlockRegistry) 위. 브랜치 `m4c1-placement`.

**spec:** `docs/superpowers/specs/2026-06-09-m4c1-block-placement-design.md`

**상수(datagen 확정):** serverbound play — set_carried_item(Set Held Item)=0x2F, set_creative_mode_slot=0x32, use_item_on=0x38. use_item_on 필드(1.21.1)=VarInt hand, BlockPos pos, VarInt face, Float cursorX/Y/Z, Boolean insideBlock, VarInt sequence (worldBorderHit는 1.21.2+라 없음). 면 인덱스: 0=down,1=up,2=north(z-1),3=south(z+1),4=west(x-1),5=east(x+1).

---

## File Structure

| 파일 | 책임 |
|---|---|
| `world/ItemRegistry.java` | (create) 아이템 숫자 id→이름 |
| `resources/items/1.21.1.txt` | (create) datagen서 추출한 item id↔name |
| `world/World.java` | (modify) BlockRegistry+ItemRegistry 보유, `itemToBlockState(itemId)` |
| `world/Region.java` | (modify) `placeBlock` + 면 오프셋 헬퍼 |
| `net/ConnectionHandler.java` | (modify) 든-아이템 추적 + 0x2F/0x32/0x38 처리 → placeBlock 마샬 |
| `FurnaceMain.java` | (modify) ItemRegistry 로드 + world.setRegistries |

---

## Task 1: ItemRegistry + items 리소스

**Files:**
- Create: `furnace-server/src/main/resources/items/1.21.1.txt`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/ItemRegistry.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ItemRegistryTest.java`

- [ ] **Step 1: items 리소스 생성(datagen registries.json서 추출)**

PowerShell(repo 루트서):
```powershell
$d = Get-Content "build/generated/reports/registries.json" -Raw | ConvertFrom-Json
$e = $d.'minecraft:item'.entries
$lines = $e.PSObject.Properties | ForEach-Object { "$($_.Name) $($_.Value.protocol_id)" } | Sort-Object
New-Item -ItemType Directory -Force furnace-server/src/main/resources/items | Out-Null
Set-Content -Path "furnace-server/src/main/resources/items/1.21.1.txt" -Value $lines -Encoding utf8
```
검증: `Select-String -Path furnace-server/src/main/resources/items/1.21.1.txt -Pattern "^minecraft:stone "` → `minecraft:stone 1`. air=0, diamond_sword=838 존재.

- [ ] **Step 2: 실패 테스트**

`ItemRegistryTest.java`:
```java
// 아이템 레지스트리 id→name 로드 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ItemRegistryTest {

    @Test
    void loadsAndMapsIds() {
        ItemRegistry reg = ItemRegistry.load("items/1.21.1.txt");
        assertEquals("minecraft:stone", reg.nameOf(1));
        assertEquals("minecraft:air", reg.nameOf(0));
        assertEquals("minecraft:air", reg.nameOf(999999)); // 미지원 → air
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ItemRegistryTest"`
Expected: 컴파일 실패 — ItemRegistry 없음.

- [ ] **Step 4: ItemRegistry.java**

```java
// 아이템 숫자 id ↔ 이름(레지스트리). items/<버전>.txt(라인 "<name> <id>") 로드.
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ItemRegistry {

    private final Map<Integer, String> nameById;

    private ItemRegistry(Map<Integer, String> nameById) {
        this.nameById = nameById;
    }

    public static ItemRegistry load(String resourcePath) {
        Map<Integer, String> map = new HashMap<>();
        ClassLoader cl = ItemRegistry.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("아이템 리소스 없음: " + resourcePath);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                map.put(Integer.parseInt(line.substring(sp + 1).strip()), line.substring(0, sp));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ItemRegistry(map);
    }

    /** 아이템 id → 이름. 미지원 id는 "minecraft:air". */
    public String nameOf(int itemId) {
        return nameById.getOrDefault(itemId, "minecraft:air");
    }
}
```

- [ ] **Step 5: 통과 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*ItemRegistryTest"`
Expected: PASS.
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/ItemRegistry.java furnace-server/src/main/resources/items furnace-server/src/test/java/org/domi/furnace/server/world/ItemRegistryTest.java
git commit -m "feat(world): add ItemRegistry + item id table" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: World.itemToBlockState

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ItemToBlockTest.java`

- [ ] **Step 1: 실패 테스트**

`ItemToBlockTest.java`:
```java
// item id → block default state id 해석(설치용)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Executors;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ItemToBlockTest {

    @Test
    void resolvesItemToBlockState() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(blocks, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, blocks.directBits(), 7, io);
        w.setRegistries(blocks, items);
        int stoneItem = 1; // minecraft:stone
        assertEquals(blocks.defaultStateId("minecraft:stone"), w.itemToBlockState(stoneItem));
        assertEquals(0, w.itemToBlockState(838)); // diamond_sword → 블록 아님 → air(0)
        assertEquals(0, w.itemToBlockState(0));   // air
        io.shutdown(); sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ItemToBlockTest"`
Expected: 컴파일 실패 — setRegistries/itemToBlockState 없음.

- [ ] **Step 3: World 수정**

import 추가(World.java import 블록):
```java
// (BlockRegistry, ItemRegistry는 동일 패키지 org.domi.furnace.server.world — import 불필요)
```
필드 추가(`worldDir` 근처):
```java
    private BlockRegistry blockRegistry;
    private ItemRegistry itemRegistry;
```
메서드 추가(`itemToBlockState` + setter; `worldDir()` 게터 근처):
```java
    public void setRegistries(BlockRegistry blocks, ItemRegistry items) {
        this.blockRegistry = blocks;
        this.itemRegistry = items;
    }

    /** 아이템 id → 설치할 블록 default state id. 블록 아닌 아이템/미설정이면 0(air=설치 안 함). */
    public int itemToBlockState(int itemId) {
        if (blockRegistry == null || itemRegistry == null) return 0;
        return blockRegistry.stateIdOrAir(itemRegistry.nameOf(itemId));
    }
```

- [ ] **Step 4: 통과 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*ItemToBlockTest"`
Expected: PASS.
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/World.java furnace-server/src/test/java/org/domi/furnace/server/world/ItemToBlockTest.java
git commit -m "feat(world): resolve item id to placeable block state" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Region.placeBlock + 면 오프셋

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/BlockPlaceTest.java`

- [ ] **Step 1: 실패 테스트**

`BlockPlaceTest.java`:
```java
// placeBlock: 빈 자리 설치+dirty, 플레이어 겹치면 거부; 면 오프셋 계산
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class BlockPlaceTest {

    @Test
    void placeOffset_sixFaces() {
        assertArrayEquals(new int[]{0, -1, 0}, Region.placeOffset(0)); // down
        assertArrayEquals(new int[]{0, 1, 0}, Region.placeOffset(1));  // up
        assertArrayEquals(new int[]{0, 0, -1}, Region.placeOffset(2)); // north
        assertArrayEquals(new int[]{0, 0, 1}, Region.placeOffset(3));  // south
        assertArrayEquals(new int[]{-1, 0, 0}, Region.placeOffset(4)); // west
        assertArrayEquals(new int[]{1, 0, 0}, Region.placeOffset(5));  // east
    }

    @Test
    void place_emptyAndRejectOverlap() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        int stone = reg.defaultStateId("minecraft:stone");
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.applyMove(0, -60, 0, 0, 0, true); // 발밑 블록 (0,-60,0) 점유
        boolean[] res = new boolean[2];
        sched.region(rp).execute(() -> {
            w.regionAt(rp).adopt(p);
            res[0] = w.regionAt(rp).placeBlock(5, -60, 5, stone);   // 빈 자리 → 설치
            res[1] = w.regionAt(rp).placeBlock(0, -60, 0, stone);   // 플레이어 겹침 → 거부
            // 확인
            ChunkColumn far = w.regionAt(rp).chunkAt(new ChunkPos(0, 0));
            assertEquals(stone, far.getBlock(5, -60, 5));            // 설치됨
            assertEquals(ChunkSection.AIR, far.getBlock(0, -60, 0)); // 거부(여전히 air)
            assertTrue(far.dirty());
        }).get();
        assertTrue(res[0], "빈 자리 설치 성공");
        assertFalse(res[1], "플레이어 겹침 거부");
        io.shutdown(); sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockPlaceTest"`
Expected: 컴파일 실패 — placeOffset/placeBlock 없음.

- [ ] **Step 3: Region에 placeOffset + placeBlock 추가**

`breakBlock` 메서드 아래에 추가:
```java
    /** 면 인덱스(0~5) → 설치 위치 오프셋 {dx,dy,dz}. 0down 1up 2north 3south 4west 5east. */
    static int[] placeOffset(int face) {
        return switch (face) {
            case 0 -> new int[]{0, -1, 0};
            case 1 -> new int[]{0, 1, 0};
            case 2 -> new int[]{0, 0, -1};
            case 3 -> new int[]{0, 0, 1};
            case 4 -> new int[]{-1, 0, 0};
            case 5 -> new int[]{1, 0, 0};
            default -> new int[]{0, 0, 0};
        };
    }

    /** 소유 스레드서 블록 설치. 플레이어 바운딩박스와 겹치면 거부(false). 성공 시 setBlock+BlockUpdate. */
    public boolean placeBlock(int bx, int by, int bz, int stateId) {
        world.assertOwns(pos);
        if (stateId == ChunkSection.AIR) return false;
        // 플레이어 충돌 거부: 블록 AABB [bx,bx+1]×[by,by+1]×[bz,bz+1] vs 플레이어(가로±0.3,높이1.8).
        for (Player pl : players.values()) {
            double px = pl.x(), py = pl.y(), pz = pl.z();
            boolean overlap = px + 0.3 > bx && px - 0.3 < bx + 1
                    && py + 1.8 > by && py < by + 1
                    && pz + 0.3 > bz && pz - 0.3 < bz + 1;
            if (overlap) return false;
        }
        ChunkPos cp = ChunkPos.ofBlock(bx, bz);
        chunkAt(cp).setBlock(bx & 15, by, bz & 15, stateId);
        BlockUpdate pkt = new BlockUpdate(bx, by, bz, stateId);
        for (Player sub : world.subscribers(cp)) sub.send(pkt);
        return true;
    }
```

- [ ] **Step 4: 통과 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*BlockPlaceTest"`
Expected: PASS (2 tests).
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/Region.java furnace-server/src/test/java/org/domi/furnace/server/world/BlockPlaceTest.java
git commit -m "feat(world): Region.placeBlock with player-collision rejection" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: ConnectionHandler 패킷 처리 + FurnaceMain 배선

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`

통합(Netty) — 단위테스트 없음, 빌드 + 수동verify(Task 5).

- [ ] **Step 1: ConnectionHandler 든-아이템 필드 추가**

`private int pendingTeleportId = -1;` 아래에 추가:
```java
    private final int[] hotbar = new int[9]; // 핫바 슬롯 아이템 id(0=air). Netty 스레드 전용.
    private int selectedSlot;                // 0~8
```

- [ ] **Step 2: handlePlay에 3개 패킷 처리 추가**

`case 0x24 -> { ... player_action ... }` 블록 뒤(같은 switch 안)에 추가:
```java
            case 0x2F -> { // Set Held Item(serverbound): Short slot(0~8)
                int slot = frame.readShort();
                if (slot >= 0 && slot < 9) selectedSlot = slot;
            }
            case 0x32 -> { // Set Creative Mode Slot: Short slot + Slot
                int slot = frame.readShort();
                int count = Types.readVarInt(frame);   // itemCount
                int itemId = count > 0 ? Types.readVarInt(frame) : 0; // >0이면 item id(components는 무시)
                if (slot >= 36 && slot <= 44) hotbar[slot - 36] = itemId;
            }
            case 0x38 -> { // Use Item On: hand, pos, face, cursorXYZ, insideBlock, sequence
                int hand = Types.readVarInt(frame);
                int[] bp = Types.readBlockPos(frame);
                int face = Types.readVarInt(frame);
                frame.readFloat(); frame.readFloat(); frame.readFloat(); // cursor X/Y/Z
                frame.readBoolean(); // insideBlock
                Types.readVarInt(frame); // sequence
                if (hand != 0) break; // 메인핸드만
                int stateId = world.itemToBlockState(hotbar[selectedSlot]);
                if (stateId == 0) break; // 설치 불가 아이템(블록 아님)
                int[] o = org.domi.furnace.server.world.Region.placeOffset(face);
                int px = bp[0] + o[0], py = bp[1] + o[1], pz = bp[2] + o[2];
                RegionPos rp = RegionGrid.regionPos(px, pz, world.regionSize());
                scheduler.region(rp).execute(() -> world.regionAt(rp).placeBlock(px, py, pz, stateId));
            }
```

- [ ] **Step 3: FurnaceMain — ItemRegistry 로드 + setRegistries**

import 추가:
```java
import org.domi.furnace.server.world.ItemRegistry;
```
`BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");` 아래에 추가:
```java
        ItemRegistry itemRegistry = ItemRegistry.load("items/1.21.1.txt");
```
`world.configurePersistence(...)` 호출 아래(또는 `scheduler.setRegionTick` 전)에 추가:
```java
        world.setRegistries(blockRegistry, itemRegistry);
```

- [ ] **Step 4: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java
git commit -m "feat(net): handle creative block placement (use_item_on)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: 수동 검증

- [ ] **Step 1:** `./gradlew :furnace-server:run` → 크리에이티브 접속(furnace.properties autosave-ticks=200).
- [ ] **Step 2:** 인벤(E)서 블록(돌 등) 핫바에 넣고 선택 → 여러 면에 우클릭 설치. 바닥/벽/천장 방향 맞게 인접칸에 설치되나.
- [ ] **Step 3:** 자기 발밑/머리 위치엔 설치 거부되나(겹침). 비블록 아이템(검) 든 채 우클릭 → 설치 안 됨.
- [ ] **Step 4:** 설치 후 ~12초(autosave) 또는 graceful 종료 → 재접속 → **설치 블록 유지**. 파괴(M4a)와 함께 동작.
- [ ] **Step 5: 수정 있었으면 커밋**
```bash
git add furnace-server
git commit -m "fix(net): M4c-1 placement from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

> 문제 해결: 설치 안 됨 → 패킷 id(0x2F/0x32/0x38) 또는 use_item_on 필드 순서. 엉뚱한 위치 → placeOffset 면 매핑. 든 블록 인식 안 됨 → set_creative_mode_slot Slot 파싱(count/itemId), items/1.21.1.txt 매핑. 재시작 후 소실 → autosave-ticks 설정·dirty(placeBlock이 setBlock 경유라 자동).

---

## Self-Review 메모

- **spec 커버리지:** ItemRegistry+리소스=T1, item→block 해석=T2, placeBlock+충돌+면오프셋=T3, 패킷(0x2F/0x32/0x38)+든아이템추적+배선=T4, 수동verify=T5. 전 항목 존재.
- **타입 일관성:** `ItemRegistry.load/nameOf`(T1) · `World.setRegistries/itemToBlockState`(T2) · `Region.placeOffset(int)→int[]`/`placeBlock(int,int,int,int)→boolean`(T3) · ConnectionHandler `hotbar[9]/selectedSlot`+0x2F/0x32/0x38(T4) · 기존 `Types.readVarInt/readBlockPos`·`ByteBuf.readShort/readFloat/readBoolean`·`RegionGrid.regionPos`·`world.regionAt`·`ChunkPos.ofBlock`·`BlockUpdate`·`world.subscribers`·`Region.players()`·`Player.x/y/z`·`BlockRegistry.defaultStateId/stateIdOrAir`(전부 기존).
- **위험:** 충돌검사 region 내 플레이어만(교차region 경계 근접 드묾, spec 명시). set_creative_mode_slot components 미파싱(itemId만 읽고 프레임 잔여 무시=안전, 프레임은 패킷단위). use_item_on 1.21.1 필드(worldBorderHit 없음) — 1.21.2+ 접속 시 어긋남(대상=1.21.1).
- **회귀:** World 신규 필드 2개+setter(생성자 불변, 기존 테스트 영향 없음). Region 신규 메서드만(기존 무변경). ConnectionHandler switch에 case 추가만.
