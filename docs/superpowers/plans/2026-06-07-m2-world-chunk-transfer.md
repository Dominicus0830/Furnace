# M2 — 월드 데이터 모델 + 청크 전송 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 바닐라 1.21.1(767) 클라가 play 진입 후 superflat 지형(grass 바닥)을 보고 안 빠지게, 청크를 바닐라 와이어 포맷으로 인코딩해 전용 워커풀서 병렬 직렬화·전송한다.

**Architecture:** 월드 데이터 모델(`BlockRegistry`/`BitStorage`/`Palette`/`ChunkSection`/`ChunkColumn`) + superflat `WorldSource` + 최소 NBT write + `ChunkSerializer` → `LevelChunkWithLight` 패킷. `onEnterPlay`서 `SetCenterChunk` 후 반경 내 청크를 `ChunkWorkerPool`(고정 스레드풀)서 생성+직렬화하고 channel eventLoop로 되돌려 전송. M1 파이프라인 그대로, 추가만.

**Tech Stack:** Java 21, Netty 4.1.115.Final, JUnit 5. M0 스케줄러(`FurnaceScheduler`), M1 네트워킹(`Types`/`Packet`/`ConnectionHandler`).

**1차 출처:** 패킷 ID·청크 포맷은 1.21.1(767) 기준. ID는 바닐라 server.jar 데이터제너레이터 `packets.json`서 확정: `level_chunk_with_light`=0x27, `set_chunk_cache_center`=0x54. 블록 state id는 `blocks.json`서: air=0, grass_block=9, dirt=10, bedrock=79, 전체 state 수=26684(direct 15비트). 최종 검증 = Task 10 실 클라 접속.

**M1과의 연결:** M1이 `net/` 패키지(handshake→play, KeepAlive, 압축)를 main에 머지했다. M2는 `world/` 패키지를 추가하고 `ConnectionHandler.onEnterPlay`에 청크 스트림을 배선한다.

**핵심 설계 결정(spec 출처):**
- 팰릿 = 단일+간접+직접 전체 구현(바닐라 정석, 대역폭 최적). spec §5.3.
- 직렬화 = 전용 `ChunkWorkerPool` 병렬, channel eventLoop로 전송. M0 global()은 단일 스레드라 병렬 불가. spec 결정표.
- NBT = heightmap용 write만(reader는 M4). spec §5.5.
- 범위 = superflat만, `WorldSource` 인터페이스로 Anvil(M4) 자리. spec §2.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `furnace-server/src/main/resources/blocks/1.21.1.txt` | blocks.json distill 생성물(블록명→default state id, state 총수) |
| `furnace-server/.../world/BlockRegistry.java` | distill 리소스 로드, 블록명→state id, 전체 state 수 |
| `furnace-server/.../world/BitStorage.java` | 값 N개를 bitsPerEntry로 compacted long[] 팩킹 |
| `furnace-server/.../world/Palette.java` | paletted container 인코딩(단일/간접/직접) |
| `furnace-server/.../world/ChunkSection.java` | 블록 16³ + 바이옴 4³ + 비공기 수 |
| `furnace-server/.../world/ChunkColumn.java` | 24섹션 + heightmap 2종 |
| `furnace-server/.../world/gen/WorldSource.java` | 생성 인터페이스(Anvil 자리) |
| `furnace-server/.../world/gen/SuperflatWorldSource.java` | superflat 레이어 |
| `furnace-server/.../net/io/nbt/NbtWriter.java` | 네트워크 NBT write |
| `furnace-server/.../net/protocol/play/SetCenterChunk.java` | clientbound set_chunk_cache_center 0x54 |
| `furnace-server/.../net/protocol/play/LevelChunkWithLight.java` | clientbound 청크 패킷 0x27 |
| `furnace-server/.../net/protocol/play/ChunkSerializer.java` | ChunkColumn → ByteBuf body |
| `furnace-server/.../net/ChunkWorkerPool.java` | 전용 고정 스레드풀 |
| `furnace-server/.../net/ConnectionHandler.java` | (modify) onEnterPlay 청크 스트림 |
| `furnace-server/.../net/NetworkServer.java` | (modify) WorldSource+pool 주입 |
| `furnace-server/.../config/Config.java`, `ConfigLoader.java` | (modify) view-distance |
| `furnace-server/.../FurnaceMain.java` | (modify) WorldSource/pool 생성 배선 |

---

## Task 1: BlockRegistry — distill 리소스 + 로더

블록 팰릿은 전역 block state id가 필요하다. blocks.json(5.4MB)은 무거우니 datagen에서 distill한 컴팩트 리소스를 둔다.

**Files:**
- Create: `furnace-server/src/main/resources/blocks/1.21.1.txt`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/BlockRegistry.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/BlockRegistryTest.java`

리소스 포맷: 첫 줄 `# states <총개수>`, 이후 한 줄 `<block_id> <default_state_id>`.

- [ ] **Step 1: distill 리소스 생성**

이미 받은 server.jar로 datagen을 돌렸다면 `build/generated/reports/blocks.json`이 있다. 없으면:
```
java -DbundlerMainClass=net.minecraft.data.Main -jar build/minecraft_server.1.21.1.jar --reports
```
아래 PowerShell로 distill 파일 생성(M2에 필요한 최소 = air/bedrock/dirt/grass_block + 총 state 수 헤더. 데이터주도 확장은 전체 블록을 써도 됨):
```powershell
$b = Get-Content "build/generated/reports/blocks.json" -Raw | ConvertFrom-Json
$total = 0; foreach($p in $b.PSObject.Properties){ $total += $p.Value.states.Count }
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# states $total")
foreach($p in $b.PSObject.Properties){
  $def = $p.Value.states | Where-Object { $_.default -eq $true }
  $lines.Add("$($p.Name) $($def.id)")
}
Set-Content "furnace-server/src/main/resources/blocks/1.21.1.txt" $lines -Encoding UTF8
```
> 결과: 첫 줄 `# states 26684`, 이후 모든 블록(예: `minecraft:air 0`, `minecraft:grass_block 9`, `minecraft:dirt 10`, `minecraft:bedrock 79` …). 전체를 두면 데이터주도라 후속 블록 추가 시 코드 무변경.

- [ ] **Step 2: 실패하는 테스트 작성**

`BlockRegistryTest.java`:
```java
// blocks.json distill 로더가 블록명→state id와 전체 state 수를 읽는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlockRegistryTest {

    @Test
    void loads_knownBlockStateIds() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        assertEquals(0, reg.defaultStateId("minecraft:air"));
        assertEquals(9, reg.defaultStateId("minecraft:grass_block"));
        assertEquals(10, reg.defaultStateId("minecraft:dirt"));
        assertEquals(79, reg.defaultStateId("minecraft:bedrock"));
    }

    @Test
    void totalStateCount_isPositive_andDirectBitsAre15() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        assertTrue(reg.totalStateCount() > 20000, "" + reg.totalStateCount());
        assertEquals(15, reg.directBits());
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockRegistryTest"`
Expected: 컴파일 실패 — `BlockRegistry` 없음.

- [ ] **Step 4: `BlockRegistry.java` 작성**

```java
// blocks.json distill 리소스를 로드해 블록명→default state id와 전체 state 수를 제공
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class BlockRegistry {

    private final Map<String, Integer> defaultStateIds;
    private final int totalStateCount;

    private BlockRegistry(Map<String, Integer> ids, int totalStateCount) {
        this.defaultStateIds = ids;
        this.totalStateCount = totalStateCount;
    }

    public static BlockRegistry load(String resourcePath) {
        Map<String, Integer> ids = new HashMap<>();
        int total = 0;
        ClassLoader cl = BlockRegistry.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("블록 리소스 없음: " + resourcePath);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) {
                    // "# states <N>"
                    String[] h = line.split("\\s+");
                    if (h.length == 3 && h[1].equals("states")) {
                        total = Integer.parseInt(h[2]);
                    }
                    continue;
                }
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                ids.put(line.substring(0, sp), Integer.parseInt(line.substring(sp + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BlockRegistry(ids, total);
    }

    public int defaultStateId(String blockId) {
        Integer id = defaultStateIds.get(blockId);
        if (id == null) throw new IllegalArgumentException("미지원 블록: " + blockId);
        return id;
    }

    public int totalStateCount() {
        return totalStateCount;
    }

    /** direct 팰릿 bitsPerEntry = ceil(log2(전체 state 수)). */
    public int directBits() {
        return 32 - Integer.numberOfLeadingZeros(totalStateCount - 1);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*BlockRegistryTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): load block state ids from distilled blocks resource" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: BitStorage — compacted long array 팩킹

바닐라 팰릿 데이터의 주춧돌. N개 값을 `bitsPerEntry`로 long[]에 채우되 값이 long 경계를 넘지 않게(compacted) 한다. `valuesPerLong = 64 / bitsPerEntry`.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/BitStorage.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/BitStorageTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`BitStorageTest.java`:
```java
// compacted long 팩킹 라운드트립 + long 개수 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BitStorageTest {

    @Test
    void roundTrips_4bit() {
        BitStorage s = new BitStorage(4, 4096);
        for (int i = 0; i < 4096; i++) s.set(i, i % 13);
        for (int i = 0; i < 4096; i++) assertEquals(i % 13, s.get(i), "idx " + i);
    }

    @Test
    void longCount_isCompacted_notSpanning() {
        // 4비트 → valuesPerLong=16 → 4096/16=256 long
        BitStorage s = new BitStorage(4, 4096);
        assertEquals(256, s.data().length);
        // 5비트 → valuesPerLong=12 → ceil(4096/12)=342 long
        BitStorage s5 = new BitStorage(5, 4096);
        assertEquals(342, s5.data().length);
    }

    @Test
    void roundTrips_9bit_heightmap() {
        BitStorage s = new BitStorage(9, 256);
        for (int i = 0; i < 256; i++) s.set(i, (i * 3) & 0x1FF);
        for (int i = 0; i < 256; i++) assertEquals((i * 3) & 0x1FF, s.get(i));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*BitStorageTest"`
Expected: 컴파일 실패 — `BitStorage` 없음.

- [ ] **Step 3: `BitStorage.java` 작성**

```java
// 값 N개를 bitsPerEntry로 long 배열에 compacted 방식으로 팩킹(값이 long 경계를 넘지 않음)
package org.domi.furnace.server.world;

public final class BitStorage {

    private final int bitsPerEntry;
    private final int valuesPerLong;
    private final long mask;
    private final int size;
    private final long[] data;

    public BitStorage(int bitsPerEntry, int size) {
        if (bitsPerEntry < 1 || bitsPerEntry > 32) {
            throw new IllegalArgumentException("bitsPerEntry 범위 밖: " + bitsPerEntry);
        }
        this.bitsPerEntry = bitsPerEntry;
        this.valuesPerLong = 64 / bitsPerEntry;
        this.mask = (1L << bitsPerEntry) - 1;
        this.size = size;
        int longCount = (size + valuesPerLong - 1) / valuesPerLong;
        this.data = new long[longCount];
    }

    public void set(int index, int value) {
        int longIndex = index / valuesPerLong;
        int offset = (index % valuesPerLong) * bitsPerEntry;
        long v = data[longIndex];
        v &= ~(mask << offset);
        v |= ((long) value & mask) << offset;
        data[longIndex] = v;
    }

    public int get(int index) {
        int longIndex = index / valuesPerLong;
        int offset = (index % valuesPerLong) * bitsPerEntry;
        return (int) ((data[longIndex] >>> offset) & mask);
    }

    public long[] data() {
        return data;
    }

    public int size() {
        return size;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*BitStorageTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add compacted bit storage primitive" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Palette — paletted container 인코딩

바닐라 paletted container: `[Byte bitsPerEntry][Palette][VarInt 데이터 long 수][long[]]`. 분포로 단일/간접/직접 자동 선택. 블록·바이옴 공용, 파라미터만 다름.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/Palette.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/PaletteTest.java`

규칙:
- 고유값 1개 → **단일**: `bits=0`, `[VarInt 값]`, `[VarInt 0]`(long 없음).
- 그 외 `neededBits=ceil(log2(고유수))`가 `maxIndirectBits` 이하 → **간접**: `bits=max(neededBits,minIndirectBits)`, `[VarInt 팰릿크기][VarInt 팰릿값들]`, `[VarInt long수][long[]]`(로컬 인덱스).
- 초과 → **직접**: `bits=directBits`, 팰릿 없음, `[VarInt long수][long[]]`(전역 값).

블록: directBits=15, minIndirect=4, maxIndirect=8. 바이옴: directBits=ceil(log2(biome수)), minIndirect=1, maxIndirect=3.

> 주의: 바닐라 클라는 블록 간접 최소 4비트를 기대(고유 2~3개여도 4비트). minIndirectBits로 강제.

- [ ] **Step 1: 실패하는 테스트 작성**

`PaletteTest.java`:
```java
// paletted container 인코딩: 단일/간접/직접 케이스 와이어 바이트 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class PaletteTest {

    @Test
    void single_writesBitsZero_valueAndZeroLongs() {
        int[] all = new int[4096]; // 전부 0
        ByteBuf buf = Unpooled.buffer();
        Palette.write(buf, all, 15, 4, 8);
        assertEquals(0, buf.readByte());          // bitsPerEntry = 0
        assertEquals(0, Types.readVarInt(buf));   // single value
        assertEquals(0, Types.readVarInt(buf));   // long count
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void indirect_fourDistinct_usesMinFourBits() {
        int[] vals = new int[4096];
        for (int i = 0; i < 4096; i++) vals[i] = i % 4 == 0 ? 79 : (i % 4 == 1 ? 10 : (i % 4 == 2 ? 9 : 0));
        ByteBuf buf = Unpooled.buffer();
        Palette.write(buf, vals, 15, 4, 8);
        assertEquals(4, buf.readByte());          // 고유 4개 → 4비트(최소)
        assertEquals(4, Types.readVarInt(buf));   // 팰릿 크기 4
        for (int i = 0; i < 4; i++) Types.readVarInt(buf); // 팰릿 값들
        int longCount = Types.readVarInt(buf);
        assertEquals(256, longCount);             // 4비트 4096칸 = 256 long
        buf.release();
    }

    @Test
    void direct_whenTooManyDistinct() {
        int[] vals = new int[4096];
        for (int i = 0; i < 4096; i++) vals[i] = i; // 4096 고유 → 직접
        ByteBuf buf = Unpooled.buffer();
        Palette.write(buf, vals, 15, 4, 8);
        assertEquals(15, buf.readByte());         // direct bits
        int longCount = Types.readVarInt(buf);    // 팰릿 없이 바로 long 수
        // 15비트 valuesPerLong=4 → ceil(4096/4)=1024
        assertEquals(1024, longCount);
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*PaletteTest"`
Expected: 컴파일 실패 — `Palette` 없음.

- [ ] **Step 3: `Palette.java` 작성**

```java
// paletted container(단일/간접/직접) 와이어 인코딩. 블록·바이옴 공용.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.io.Types;

public final class Palette {

    private Palette() {}

    /**
     * values를 paletted container로 buf에 기록.
     * @param directBits      직접 팰릿 비트수
     * @param minIndirectBits 간접 최소 비트수(블록 4, 바이옴 1)
     * @param maxIndirectBits 간접 최대 비트수(블록 8, 바이옴 3)
     */
    public static void write(ByteBuf buf, int[] values, int directBits,
                             int minIndirectBits, int maxIndirectBits) {
        // 고유값 수집(삽입 순서 = 팰릿 인덱스)
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : values) {
            if (!indexOf.containsKey(v)) {
                indexOf.put(v, palette.size());
                palette.add(v);
            }
        }

        if (palette.size() == 1) { // 단일값
            buf.writeByte(0);
            Types.writeVarInt(buf, palette.get(0));
            Types.writeVarInt(buf, 0); // long 수 0
            return;
        }

        int neededBits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
        if (neededBits <= maxIndirectBits) { // 간접
            int bits = Math.max(neededBits, minIndirectBits);
            buf.writeByte(bits);
            Types.writeVarInt(buf, palette.size());
            for (int entry : palette) Types.writeVarInt(buf, entry);
            BitStorage store = new BitStorage(bits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, indexOf.get(values[i]));
            writeLongs(buf, store.data());
        } else { // 직접
            buf.writeByte(directBits);
            BitStorage store = new BitStorage(directBits, values.length);
            for (int i = 0; i < values.length; i++) store.set(i, values[i]);
            writeLongs(buf, store.data());
        }
    }

    private static void writeLongs(ByteBuf buf, long[] data) {
        Types.writeVarInt(buf, data.length);
        for (long l : data) buf.writeLong(l);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*PaletteTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add paletted container encoding (single/indirect/direct)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: ChunkSection + ChunkColumn + heightmap

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkSection.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkColumnTest.java`

좌표 규약: 섹션은 블록 16³, 인덱스 `y*256 + z*16 + x`(YZX 순, 바닐라). 바이옴 4³, 인덱스 `by*16 + bz*4 + bx`. ChunkColumn 24섹션(섹션0 = y −64..−49). heightmap 9비트, 값 = `surfaceWorldY - minY + 1`(minY=−64).

- [ ] **Step 1: 실패하는 테스트 작성**

`ChunkColumnTest.java`:
```java
// 섹션 블록 set/get, 비공기 카운트, heightmap 패킹 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkColumnTest {

    @Test
    void section_setBlock_countsNonAir() {
        ChunkSection s = new ChunkSection();
        assertEquals(0, s.nonAirCount());
        s.setBlock(0, 0, 0, 79); // bedrock
        s.setBlock(1, 0, 0, 79);
        assertEquals(2, s.nonAirCount());
        s.setBlock(0, 0, 0, 0);  // 다시 air → 카운트 감소
        assertEquals(1, s.nonAirCount());
        assertEquals(79, s.getBlock(1, 0, 0));
    }

    @Test
    void column_has24Sections_andBiomeFill() {
        ChunkColumn c = new ChunkColumn(6); // plains 인덱스 6
        assertEquals(24, c.sections().length);
        // 모든 섹션 바이옴이 단일 plains
        assertEquals(6, c.sections()[0].getBiome(0, 0, 0));
    }

    @Test
    void heightmap_packs9Bit() {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4); // (x=0,z=0) 표면 높이값 4
        long[] hm = c.motionBlockingHeightmap();
        // 9비트 256칸 valuesPerLong=7 → ceil(256/7)=37 long
        assertEquals(37, hm.length);
        // 첫 칸 값 4 복원
        BitStorage bs = BitStorage.wrap(9, 256, hm);
        assertEquals(4, bs.get(0));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkColumnTest"`
Expected: 컴파일 실패 — 클래스/`BitStorage.wrap` 없음.

- [ ] **Step 3: `BitStorage`에 `wrap` 추가**

`BitStorage.java`에 기존 배열을 감싸는 정적 팩토리 추가(테스트 복원용). 필드 `data`를 받는 private 생성자 + `wrap`:
```java
    private BitStorage(int bitsPerEntry, int size, long[] data) {
        this.bitsPerEntry = bitsPerEntry;
        this.valuesPerLong = 64 / bitsPerEntry;
        this.mask = (1L << bitsPerEntry) - 1;
        this.size = size;
        this.data = data;
    }

    /** 기존 long[]를 감싼다(읽기용). */
    public static BitStorage wrap(int bitsPerEntry, int size, long[] data) {
        return new BitStorage(bitsPerEntry, size, data);
    }
```

- [ ] **Step 4: `ChunkSection.java` 작성**

```java
// 청크 섹션: 블록 16³ + 바이옴 4³ + 비공기 수. 인덱스는 바닐라 YZX 순.
package org.domi.furnace.server.world;

public final class ChunkSection {

    public static final int AIR = 0;

    private final int[] blocks = new int[4096]; // 전부 0(air)
    private final int[] biomes = new int[64];
    private short nonAirCount = 0;

    private static int blockIndex(int x, int y, int z) {
        return y * 256 + z * 16 + x;
    }

    private static int biomeIndex(int x, int y, int z) {
        return y * 16 + z * 4 + x;
    }

    public void setBlock(int x, int y, int z, int stateId) {
        int idx = blockIndex(x, y, z);
        int old = blocks[idx];
        if (old == AIR && stateId != AIR) nonAirCount++;
        else if (old != AIR && stateId == AIR) nonAirCount--;
        blocks[idx] = stateId;
    }

    public int getBlock(int x, int y, int z) {
        return blocks[blockIndex(x, y, z)];
    }

    public void fillBiome(int biomeId) {
        java.util.Arrays.fill(biomes, biomeId);
    }

    public int getBiome(int x, int y, int z) {
        return biomes[biomeIndex(x, y, z)];
    }

    public short nonAirCount() {
        return nonAirCount;
    }

    public int[] blocks() {
        return blocks;
    }

    public int[] biomes() {
        return biomes;
    }
}
```

- [ ] **Step 5: `ChunkColumn.java` 작성**

```java
// 청크 컬럼: 24섹션(y −64..319) + MOTION_BLOCKING heightmap. minY=−64.
package org.domi.furnace.server.world;

public final class ChunkColumn {

    public static final int SECTION_COUNT = 24;
    public static final int MIN_Y = -64;
    public static final int HEIGHTMAP_BITS = 9; // worldHeight 384 → 0..384

    private final ChunkSection[] sections = new ChunkSection[SECTION_COUNT];
    private final int[] surface = new int[256]; // heightmap 값(0 = 바닥 아래 없음)

    public ChunkColumn(int biomeId) {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new ChunkSection();
            sections[i].fillBiome(biomeId);
        }
    }

    public ChunkSection[] sections() {
        return sections;
    }

    /** worldY 절대좌표에 블록 설정. */
    public void setBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
    }

    /** (x,z) heightmap 값(= surfaceWorldY − minY + 1). */
    public void setSurface(int x, int z, int value) {
        surface[z * 16 + x] = value;
    }

    public long[] motionBlockingHeightmap() {
        BitStorage bs = new BitStorage(HEIGHTMAP_BITS, 256);
        for (int i = 0; i < 256; i++) bs.set(i, surface[i]);
        return bs.data();
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkColumnTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add chunk section and column with heightmap" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: WorldSource + SuperflatWorldSource

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldSource.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/SuperflatWorldSource.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/SuperflatWorldSourceTest.java`

superflat 레이어: y=−64 bedrock, −63·−62 dirt, −61 grass_block, 위 전부 air. heightmap 표면값 = (−61 − (−64) + 1) = 4.

- [ ] **Step 1: 실패하는 테스트 작성**

`SuperflatWorldSourceTest.java`:
```java
// superflat 생성: 레이어 블록 배치 + 비공기 수 + heightmap 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class SuperflatWorldSourceTest {

    private static SuperflatWorldSource source() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new SuperflatWorldSource(reg, 6); // plains 인덱스 6
    }

    @Test
    void generates_layers() {
        ChunkColumn c = source().generate(0, 0);
        // 섹션0(y −64..−49): bedrock/dirt/grass/air
        assertEquals(79, sectionBlock(c, 0));  // y=−64 bedrock
        assertEquals(10, sectionBlock(c, 1));  // y=−63 dirt
        assertEquals(10, sectionBlock(c, 2));  // y=−62 dirt
        assertEquals(9, sectionBlock(c, 3));   // y=−61 grass
        assertEquals(0, sectionBlock(c, 4));   // y=−60 air
    }

    @Test
    void nonAirCount_section0_is1024() {
        ChunkColumn c = source().generate(0, 0);
        // (1 bedrock + 2 dirt + 1 grass) * 256 = 1024
        assertEquals(1024, c.sections()[0].nonAirCount());
    }

    @Test
    void heightmap_surfaceIs4() {
        ChunkColumn c = source().generate(0, 0);
        org.domi.furnace.server.world.BitStorage bs =
            org.domi.furnace.server.world.BitStorage.wrap(9, 256, c.motionBlockingHeightmap());
        assertEquals(4, bs.get(0));
    }

    private static int sectionBlock(ChunkColumn c, int localY) {
        return c.sections()[0].getBlock(0, localY, 0);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*SuperflatWorldSourceTest"`
Expected: 컴파일 실패 — 클래스 없음.

- [ ] **Step 3: `WorldSource.java` 작성**

```java
// 청크 컬럼을 생성하는 월드 소스. superflat이 첫 구현, Anvil이 M4서 두 번째 구현.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.ChunkColumn;

public interface WorldSource {

    ChunkColumn generate(int chunkX, int chunkZ);
}
```

- [ ] **Step 4: `SuperflatWorldSource.java` 작성**

```java
// 클래식 superflat 생성: bedrock/dirt×2/grass 레이어. 좌표 무관 동일 컬럼.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

public final class SuperflatWorldSource implements WorldSource {

    private final int bedrock;
    private final int dirt;
    private final int grass;
    private final int biomeId;

    public SuperflatWorldSource(BlockRegistry reg, int biomeId) {
        this.bedrock = reg.defaultStateId("minecraft:bedrock");
        this.dirt = reg.defaultStateId("minecraft:dirt");
        this.grass = reg.defaultStateId("minecraft:grass_block");
        this.biomeId = biomeId;
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        ChunkColumn c = new ChunkColumn(biomeId);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                c.setBlock(x, -64, z, bedrock);
                c.setBlock(x, -63, z, dirt);
                c.setBlock(x, -62, z, dirt);
                c.setBlock(x, -61, z, grass);
                c.setSurface(x, z, 4); // (−61 − (−64)) + 1
            }
        }
        return c;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*SuperflatWorldSourceTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): add superflat world source" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: NbtWriter — heightmap용 네트워크 NBT write

1.20.2+ 네트워크 NBT는 루트 compound에 이름이 없다. heightmap 전송에 필요한 태그만: Compound, Long_Array, End.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/nbt/NbtWriter.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/nbt/NbtWriterTest.java`

NBT 바이트: TAG_Compound=0x0a, TAG_Long_Array=0x0c, TAG_End=0x00. 이름 있는 태그 = `[타입바이트][UShort 이름길이][이름 UTF-8]`. Long_Array payload = `[Int 길이][long[]]`.

- [ ] **Step 1: 실패하는 테스트 작성**

`NbtWriterTest.java`:
```java
// 루트 무명 compound + long_array + end 바이트 시퀀스 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class NbtWriterTest {

    @Test
    void writesRootCompound_withLongArray() {
        ByteBuf buf = Unpooled.buffer();
        NbtWriter w = new NbtWriter(buf);
        w.beginRootCompound();
        w.writeLongArray("MOTION_BLOCKING", new long[]{1L, 2L});
        w.endCompound();

        assertEquals(0x0a, buf.readByte());       // 루트 compound 타입
        // 루트는 이름 없음 → 바로 첫 태그
        assertEquals(0x0c, buf.readByte());       // long_array 타입
        assertEquals(15, buf.readUnsignedShort());// 이름 길이 "MOTION_BLOCKING"=15
        byte[] name = new byte[15];
        buf.readBytes(name);
        assertEquals("MOTION_BLOCKING", new String(name, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, buf.readInt());           // 배열 길이
        assertEquals(1L, buf.readLong());
        assertEquals(2L, buf.readLong());
        assertEquals(0x00, buf.readByte());       // end
        assertFalse(buf.isReadable());
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*NbtWriterTest"`
Expected: 컴파일 실패 — `NbtWriter` 없음.

- [ ] **Step 3: `NbtWriter.java` 작성**

```java
// 네트워크 NBT(1.20.2+, 루트 무명) write. heightmap 전송용 최소 태그만.
package org.domi.furnace.server.net.io.nbt;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

public final class NbtWriter {

    private static final byte TAG_END = 0x00;
    private static final byte TAG_COMPOUND = 0x0a;
    private static final byte TAG_LONG_ARRAY = 0x0c;

    private final ByteBuf buf;

    public NbtWriter(ByteBuf buf) {
        this.buf = buf;
    }

    /** 루트 compound 시작(이름 없음). */
    public void beginRootCompound() {
        buf.writeByte(TAG_COMPOUND);
    }

    public void writeLongArray(String name, long[] values) {
        buf.writeByte(TAG_LONG_ARRAY);
        writeName(name);
        buf.writeInt(values.length);
        for (long v : values) buf.writeLong(v);
    }

    public void endCompound() {
        buf.writeByte(TAG_END);
    }

    private void writeName(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*NbtWriterTest"`
Expected: PASS (1 test).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add minimal network NBT writer for heightmaps" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: ChunkSerializer + LevelChunkWithLight + SetCenterChunk

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/SetCenterChunk.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/ChunkSerializer.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/protocol/play/LevelChunkWithLight.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/ChunkSerializerTest.java`

`level_chunk_with_light`(0x27) body:
```
Int chunkX, Int chunkZ
NBT heightmaps  { MOTION_BLOCKING: long[] }
VarInt dataSize + data(24섹션: [Short nonAir][block container][biome container])
VarInt blockEntityCount = 0
--- light ---
BitSet skyLightMask        (VarInt longCount + long[])   ← 26섹션 전부 set
BitSet blockLightMask      (VarInt 0)
BitSet emptySkyLightMask   (VarInt 0)
BitSet emptyBlockLightMask (VarInt longCount + long[])   ← 26섹션 전부 set
VarInt skyLightArrayCount = 26, 각 (VarInt 2048 + 2048바이트 0xFF)
VarInt blockLightArrayCount = 0
```
라이트 섹션 = 24 + 2(아래/위) = 26. superflat 어둠 방지로 전 섹션 풀 skylight. 바이옴 direct 비트 = ceil(log2(biome수)). M2는 단일 바이옴이라 항상 단일 팰릿(bits 0)이지만 인자로 넘긴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`ChunkSerializerTest.java`:
```java
// 청크 직렬화 헤더(좌표) + 섹션/라이트 카운트 스모크 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSerializerTest {

    @Test
    void serialize_writesCoordsAndIsNonEmpty() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 6).generate(3, -5);
        ByteBuf buf = Unpooled.buffer();
        ChunkSerializer.serialize(buf, 3, -5, col, reg.directBits(), 7); // biomeDirectBits=7(128 biome 가정)

        assertEquals(3, buf.readInt());   // chunkX
        assertEquals(-5, buf.readInt());  // chunkZ
        assertEquals(0x0a, buf.readByte());// heightmap NBT 루트 compound
        assertTrue(buf.readableBytes() > 100, "section/light 데이터 있어야");
        buf.release();
    }

    @Test
    void setCenterChunk_writesVarIntCoords() {
        SetCenterChunk p = new SetCenterChunk(3, -5);
        assertEquals(0x54, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(3, Types.readVarInt(buf));
        assertEquals(-5, Types.readVarInt(buf));
        buf.release();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSerializerTest"`
Expected: 컴파일 실패 — 클래스 없음.

- [ ] **Step 3: `SetCenterChunk.java` 작성**

```java
// S→C 센터 청크 설정. 이후 청크 패킷들의 기준 좌표.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetCenterChunk(int chunkX, int chunkZ) implements Packet {

    public static final int ID = 0x54;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, chunkX);
        Types.writeVarInt(buf, chunkZ);
    }
}
```

- [ ] **Step 4: `ChunkSerializer.java` 작성**

```java
// ChunkColumn을 level_chunk_with_light body로 직렬화(섹션 팰릿 + heightmap NBT + 풀 skylight)
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.Palette;

public final class ChunkSerializer {

    private static final int LIGHT_SECTIONS = ChunkColumn.SECTION_COUNT + 2; // 26

    private ChunkSerializer() {}

    public static void serialize(ByteBuf buf, int chunkX, int chunkZ, ChunkColumn col,
                                 int blockDirectBits, int biomeDirectBits) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);

        // heightmap NBT
        NbtWriter nbt = new NbtWriter(buf);
        nbt.beginRootCompound();
        nbt.writeLongArray("MOTION_BLOCKING", col.motionBlockingHeightmap());
        nbt.endCompound();

        // 섹션 데이터(별도 버퍼 후 길이 prefix)
        ByteBuf data = Unpooled.buffer();
        for (ChunkSection s : col.sections()) {
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

    private static void writeBitSet(ByteBuf buf, long[] longs) {
        Types.writeVarInt(buf, longs.length);
        for (long l : longs) buf.writeLong(l);
    }
}
```

- [ ] **Step 5: `LevelChunkWithLight.java` 작성**

```java
// S→C 청크 데이터+라이트. body는 ChunkSerializer가 채운 ByteBuf를 그대로 싣는다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record LevelChunkWithLight(ByteBuf body) implements Packet {

    public static final int ID = 0x27;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(body);
        body.release();
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSerializerTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): serialize chunk column to level_chunk_with_light packet" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: ChunkWorkerPool — 전용 병렬 직렬화 풀

청크 생성+직렬화를 IO 스레드 밖 고정 스레드풀서 병렬 수행. 결과 ByteBuf를 `CompletableFuture`로 반환한다.

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/ChunkWorkerPool.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/ChunkWorkerPoolTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`ChunkWorkerPoolTest.java`:
```java
// 워커풀이 작업을 풀 스레드(호출 스레드 아님)서 실행하고 결과를 반환하는지 검증
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkWorkerPoolTest {

    @Test
    void submit_runsOnPoolThread_returnsResult() throws Exception {
        ChunkWorkerPool pool = new ChunkWorkerPool(2);
        try {
            String caller = Thread.currentThread().getName();
            String runner = pool.submit(() -> Thread.currentThread().getName()).get();
            assertNotEquals(caller, runner);
            assertTrue(runner.contains("chunk-worker"), runner);
        } finally {
            pool.shutdown();
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkWorkerPoolTest"`
Expected: 컴파일 실패 — `ChunkWorkerPool` 없음.

- [ ] **Step 3: `ChunkWorkerPool.java` 작성**

```java
// 청크 생성+직렬화를 IO 스레드 밖에서 병렬 수행하는 전용 고정 스레드풀
package org.domi.furnace.server.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ChunkWorkerPool {

    private final ExecutorService executor;

    public ChunkWorkerPool(int threads) {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "chunk-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(threads, factory);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkWorkerPoolTest"`
Expected: PASS (1 test).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): add dedicated chunk worker pool" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: 배선 — Config view-distance + onEnterPlay 청크 스트림

월드/풀을 NetworkServer 생애주기로 만들고 ConnectionHandler로 주입, `onEnterPlay`서 청크를 스트림한다.

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/Config.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/ConfigLoader.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/config/ConfigLoaderTest.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/NetworkServer.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/net/NetworkServerTest.java`
- Modify: `furnace-server/src/test/java/org/domi/furnace/server/net/StatusPingTest.java`

- [ ] **Step 1: `Config`에 viewDistance 추가**

`Config.java` 교체:
```java
// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance) {

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5);
}
```

- [ ] **Step 2: `ConfigLoader`에 view-distance 로드 추가**

`return new Config(...)` 부분 교체:
```java
        int viewDistance = Integer.parseInt(
                props.getProperty("view-distance",
                        Integer.toString(Config.DEFAULT.viewDistance())));
        return new Config(name, tick, port, compression, viewDistance);
```

- [ ] **Step 3: `ConfigLoaderTest` 갱신**

기본값 테스트에 단언 추가:
```java
        assertEquals(5, c.viewDistance());
```
파일 오버라이드 테스트의 `Files.writeString` 줄과 단언 교체:
```java
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\nport=25500\ncompression-threshold=-1\nview-distance=8\n");
        Config c = ConfigLoader.load(f);
        assertEquals("My Server", c.serverName());
        assertEquals(25_000_000L, c.tickNanos());
        assertEquals(25500, c.port());
        assertEquals(-1, c.compressionThreshold());
        assertEquals(8, c.viewDistance());
```

- [ ] **Step 4: 테스트 실패 확인(컴파일 깨짐)**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: 컴파일 실패 — 다른 `new Config(...)` 호출부(FurnaceMain 등)와 시그니처 불일치.

- [ ] **Step 5: `NetworkServer`에 WorldSource·풀·뷰거리·바이옴비트 주입**

import 추가:
```java
import org.domi.furnace.server.world.gen.WorldSource;
```
필드/생성자 교체(기존 4-인자 → 7-인자):
```java
    private final int port;
    private final int compressionThreshold;
    private final String serverName;
    private final FurnaceScheduler scheduler;
    private final WorldSource worldSource;
    private final int viewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;
    private final ChunkWorkerPool chunkPool;

    public NetworkServer(int port, int compressionThreshold, String serverName,
                         FurnaceScheduler scheduler, WorldSource worldSource,
                         int viewDistance, int blockDirectBits, int biomeDirectBits) {
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
        this.scheduler = scheduler;
        this.worldSource = worldSource;
        this.viewDistance = viewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
        this.chunkPool = new ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
    }
```
`initChannel`의 handler 생성 교체:
```java
                                .addLast("handler",
                                        new ConnectionHandler(serverName, compressionThreshold,
                                                scheduler, worldSource, chunkPool,
                                                viewDistance, blockDirectBits, biomeDirectBits));
```
`stop()`에 풀 종료 추가(맨 위):
```java
        chunkPool.shutdown();
```

- [ ] **Step 6: `ConnectionHandler`에 청크 스트림 배선**

import 추가:
```java
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.WorldSource;
```
필드 추가(`private final FurnaceScheduler scheduler;` 아래):
```java
    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final int viewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;
```
생성자 교체:
```java
    public ConnectionHandler(String serverName, int compressionThreshold, FurnaceScheduler scheduler,
                             WorldSource worldSource, ChunkWorkerPool chunkPool, int viewDistance,
                             int blockDirectBits, int biomeDirectBits) {
        this.serverName = serverName;
        this.compressionThreshold = compressionThreshold;
        this.scheduler = scheduler;
        this.worldSource = worldSource;
        this.chunkPool = chunkPool;
        this.viewDistance = viewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
    }
```
`onEnterPlay` 끝(`scheduleKeepAlive();` 위)에 청크 스트림 추가:
```java
        sendChunks(ctx);
```
메서드 추가:
```java
    private void sendChunks(ChannelHandlerContext ctx) {
        send(ctx, new SetCenterChunk(0, 0));
        for (int cx = -viewDistance; cx <= viewDistance; cx++) {
            for (int cz = -viewDistance; cz <= viewDistance; cz++) {
                final int fx = cx;
                final int fz = cz;
                chunkPool.submit(() -> {
                    ChunkColumn col = worldSource.generate(fx, fz);
                    io.netty.buffer.ByteBuf body = Unpooled.buffer();
                    ChunkSerializer.serialize(body, fx, fz, col, blockDirectBits, biomeDirectBits);
                    return body;
                }).thenAccept(body ->
                        ctx.channel().eventLoop().execute(() ->
                                send(ctx, new LevelChunkWithLight(body))));
            }
        }
    }
```

- [ ] **Step 7: `FurnaceMain` 배선**

import 추가:
```java
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.domi.furnace.server.net.registry.RegistrySet;
```
`NetworkServer network = ...` 부분 교체:
```java
        BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        WorldSource worldSource = new SuperflatWorldSource(blockRegistry, plainsBiome);
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName(), scheduler,
                worldSource, config.viewDistance(), blockRegistry.directBits(), biomeDirectBits);
```

- [ ] **Step 8: 기존 테스트 2개 생성자 갱신**

`NetworkServerTest`와 `StatusPingTest`의 `new NetworkServer(0, -1, "Furnace Server", sched)` 호출을 교체. import 추가:
```java
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
```
생성 부분 교체(두 테스트 동일):
```java
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched,
                new SuperflatWorldSource(reg, 0), 2, reg.directBits(), 7);
```

- [ ] **Step 9: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 전체 테스트 통과(M0+M1+M2: Config, Types, Framing, NetworkServer, StatusPing, Login, RegistrySet, RegistryData, PlayPackets, Compression, BlockRegistry, BitStorage, Palette, ChunkColumn, SuperflatWorldSource, NbtWriter, ChunkSerializer, ChunkWorkerPool).

- [ ] **Step 10: 커밋**

```bash
git add furnace-server
git commit -m "feat(net): stream superflat chunks on play join via worker pool" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: 수동 검증 — 바닐라 1.21.1 클라 접속 (M2 최종 verify)

- [ ] **Step 1: 서버 기동**

Run: `./gradlew :furnace-server:run`
Expected: "네트워크 서버 listen" 로그.

- [ ] **Step 2: 바닐라 1.21.1 클라 접속**

멀티플레이어 → `localhost` → 접속. 기대:
- 스폰 후 **grass 바닥이 렌더되고 그 위에 선다(void로 안 빠짐).**
- 사방 청크가 보인다(뷰거리만큼).
- 끊기지 않고 유지(KeepAlive).

- [ ] **Step 3: 워커 스레드 확인**

서버 로그/디버거에서 청크 직렬화가 `chunk-worker-*` 스레드서 도는지 확인(IO 스레드 `nioEventLoopGroup` 아님). 필요시 `ChunkSerializer.serialize` 진입에 임시 로그 `log.debug("serialize on {}", Thread.currentThread().getName())` 추가해 관찰 후 제거.

- [ ] **문제 해결 가이드**
  - 클라가 **`level_chunk_with_light ... larger/smaller than expected`**로 끊김 → paletted container의 "Data Array Length VarInt" 유무가 1.21.1과 어긋났을 가능성. 본 plan은 present로 작성. minecraft.wiki 1.21.1 "Chunk Data" 포맷과 대조해 `Palette.writeLongs`/single 케이스의 VarInt long-count를 조정. (single은 `[VarInt 0]` 유지/제거가 핵심 후보.)
  - **`Missing registry`** → biome/dimension 인덱스 문제. M1 `registries/1.21.1.txt`와 `plainsBiome` 인덱스 확인.
  - **바닥 뚫림(void)** → heightmap/섹션0 블록 미배치. `SuperflatWorldSourceTest` 재확인.
  - **패킷 ID 의심** → `build/generated/reports/packets.json`의 `play.clientbound`와 대조(level_chunk_with_light=0x27, set_chunk_cache_center=0x54).

- [ ] **Step 4: M2 완료 커밋(필요 시 수정 반영)**

수동 검증서 포맷 수정이 있었다면 커밋:
```bash
git add furnace-server
git commit -m "fix(net): correct chunk wire format per vanilla 1.21.1 client" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모(plan 작성자 → 실행자)

- **spec 커버리지:** §5.1 BlockRegistry=T1, §5.2 BitStorage=T2, §5.3 Palette=T3, §5.4 Chunk=T4, §2 WorldSource=T5, §5.5 NBT=T6, §5.6 Serializer/§5.7 SetCenterChunk=T7, ChunkWorkerPool=T8, §6 config+배선=T9, §7 verify=T10. 전 항목 태스크 존재.
- **위험 1 (와이어 포맷):** paletted container의 Data Array Length VarInt를 present로 가정. 틀리면 T10 문제해결 가이드대로 조정. 단위 테스트는 자체 인코딩 라운드트립이라 이 가정 검증 못 함 — 실 클라가 최종 판정.
- **위험 2 (라이트 포맷):** 26섹션 풀 skylight. emptyBlockLightMask=전섹션 set. 클라가 라이트 거부 시 T10서 조정.
- **타입 일관성:** `BitStorage.wrap`(T4 Step3 추가, T4 테스트 사용), `Palette.write(buf,int[],directBits,minIndirect,maxIndirect)`(T3 정의, T7 사용), `ChunkSerializer.serialize(buf,cx,cz,col,blockDirectBits,biomeDirectBits)`(T7 정의, T9 사용), `ConnectionHandler` 8-인자 생성자(T9 정의, NetworkServer/테스트서 사용) — 일치 확인.
