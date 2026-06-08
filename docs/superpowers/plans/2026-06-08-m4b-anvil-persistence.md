# M4b — Anvil 영속성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 청크 편집과 플레이어 위치를 표준 Anvil(.mca)/playerdata로 디스크에 영속화 — 재시작 후 편집·위치 유지, 기존 바닐라 월드 로드(속성 근사).

**Architecture:** 풀 NBT 코덱 → RegionFile(.mca 섹터) → AnvilChunkCodec(ChunkColumn↔NBT) → AnvilWorldSource. 청크 로드는 IO 풀서 비동기(region 틱 비차단), owner 스레드서 보관. dirty 청크는 퇴출·주기·종료 시 IO 풀서 저장. .mca는 RegionFileStore가 파일별 직렬화.

**Tech Stack:** Java 21, JUnit 5, java.util.zip(Deflater/GZIP), DataInput/Output. M4a(청크 소유·구독·snapshot) 위. 브랜치 `m4b-persistence`.

**spec:** `docs/superpowers/specs/2026-06-08-m4b-anvil-persistence-design.md`

**검증 게이트:** T1~T6 = 직렬화 단위(코덱). T7~T11 = 통합(로드/저장/수명주기/플레이어/종료). **T12 A(우리 라운드트립) 먼저, B(바닐라 로드) 나중.**

---

## File Structure

| 파일 | 책임 |
|---|---|
| `net/io/nbt/NbtCompound.java` | (create) 명명 태그 맵 + 타입 게터 |
| `net/io/nbt/NbtList.java` | (create) 타입드 리스트 |
| `net/io/nbt/NbtIo.java` | (create) DataInput/Output NBT read/write + gzip 헬퍼 |
| `world/anvil/RegionFile.java` | (create) 단일 .mca 섹터 read/write |
| `world/anvil/RegionFileStore.java` | (create) RegionFile 캐시 + 파일별 직렬화 |
| `world/anvil/AnvilChunkCodec.java` | (create) ChunkColumn ↔ NbtCompound |
| `world/gen/AnvilWorldSource.java` | (create) WorldSource(디스크+generate 폴백) |
| `world/ChunkColumn.java` | (modify) dirty 플래그 |
| `config/Config.java`·`ConfigLoader.java` | (modify) level-name/io-threads/autosave-ticks/eviction-grace |
| `world/World.java` | (modify) IO 풀·RegionFileStore 보유, snapshotChunk→loadChunk, autosave/flush |
| `world/Region.java` | (modify) loadChunk async, 퇴출/저장, 플레이어 저장 |
| `entity/Player.java` | (modify) 위치 NBT 직렬화 헬퍼 |
| `net/ConnectionHandler.java` | (modify) 접속 시 playerdata 로드→스폰 |
| `FurnaceMain.java` | (modify) 배선 + 종료 시퀀스 |

**상수:** DataVersion=3955(1.21.1). 섹션 Y = 인덱스(0..23) − 4. 블록 index = y*256+z*16+x, 바이옴 index = y*16+z*4+x. 압축타입 2(zlib).

---

## Task 1: NBT 태그 모델 (NbtCompound / NbtList)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/nbt/NbtCompound.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/net/io/nbt/NbtList.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/nbt/NbtCompoundTest.java`

- [ ] **Step 1: 실패 테스트**

`NbtCompoundTest.java`:
```java
// NbtCompound 저장/타입게터 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NbtCompoundTest {

    @Test
    void putAndGet() {
        NbtCompound c = new NbtCompound();
        c.putInt("x", 5);
        c.putString("name", "stone");
        c.putLongArray("data", new long[]{1, 2});
        assertEquals(5, c.getInt("x"));
        assertEquals("stone", c.getString("name"));
        assertArrayEquals(new long[]{1, 2}, c.getLongArray("data"));
        assertTrue(c.contains("x"));
        assertFalse(c.contains("absent"));
    }

    @Test
    void list_holdsTypedItems() {
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        l.add("a");
        l.add("b");
        assertEquals(2, l.size());
        assertEquals("a", l.get(0));
        assertEquals(NbtIo.TAG_STRING, l.elementType());
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*NbtCompoundTest"`
Expected: 컴파일 실패 — 클래스 없음.

- [ ] **Step 3: NbtList.java**

```java
// 타입드 NBT 리스트(모든 요소 동일 태그 타입).
package org.domi.furnace.server.net.io.nbt;

import java.util.ArrayList;
import java.util.List;

public final class NbtList {

    private final byte elementType;
    private final List<Object> items = new ArrayList<>();

    public NbtList(byte elementType) {
        this.elementType = elementType;
    }

    public void add(Object v) { items.add(v); }
    public Object get(int i) { return items.get(i); }
    public int size() { return items.size(); }
    public byte elementType() { return elementType; }
    public List<Object> items() { return items; }
}
```

- [ ] **Step 4: NbtCompound.java**

```java
// 명명 NBT 태그 맵 + 타입 게터. 값은 Byte/Short/Integer/Long/Float/Double/String/byte[]/int[]/long[]/NbtCompound/NbtList.
package org.domi.furnace.server.net.io.nbt;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NbtCompound {

    private final Map<String, Object> tags = new LinkedHashMap<>();

    public Map<String, Object> tags() { return tags; }
    public boolean contains(String k) { return tags.containsKey(k); }

    public void putByte(String k, byte v) { tags.put(k, v); }
    public void putInt(String k, int v) { tags.put(k, v); }
    public void putLong(String k, long v) { tags.put(k, v); }
    public void putString(String k, String v) { tags.put(k, v); }
    public void putLongArray(String k, long[] v) { tags.put(k, v); }
    public void putList(String k, NbtList v) { tags.put(k, v); }
    public void putCompound(String k, NbtCompound v) { tags.put(k, v); }
    public void put(String k, Object v) { tags.put(k, v); }

    public byte getByte(String k) { return (Byte) tags.get(k); }
    public int getInt(String k) { return (Integer) tags.get(k); }
    public long getLong(String k) { return (Long) tags.get(k); }
    public String getString(String k) { return (String) tags.get(k); }
    public long[] getLongArray(String k) { return (long[]) tags.get(k); }
    public NbtList getList(String k) { return (NbtList) tags.get(k); }
    public NbtCompound getCompound(String k) { return (NbtCompound) tags.get(k); }
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*NbtCompoundTest"`
Expected: 컴파일 실패 — `NbtIo.TAG_STRING` 없음(T2서 정의). **이 Task에서는 NbtIo 상수만 임시로 필요** → 다음 Step.

- [ ] **Step 6: NbtIo 상수 stub 추가(T2서 본체)**

`furnace-server/src/main/java/org/domi/furnace/server/net/io/nbt/NbtIo.java`:
```java
// NBT 파일 read/write(DataInput/Output) + gzip. 표준 named-root 포맷.
package org.domi.furnace.server.net.io.nbt;

public final class NbtIo {

    public static final byte TAG_END = 0;
    public static final byte TAG_BYTE = 1;
    public static final byte TAG_SHORT = 2;
    public static final byte TAG_INT = 3;
    public static final byte TAG_LONG = 4;
    public static final byte TAG_FLOAT = 5;
    public static final byte TAG_DOUBLE = 6;
    public static final byte TAG_BYTE_ARRAY = 7;
    public static final byte TAG_STRING = 8;
    public static final byte TAG_LIST = 9;
    public static final byte TAG_COMPOUND = 10;
    public static final byte TAG_INT_ARRAY = 11;
    public static final byte TAG_LONG_ARRAY = 12;

    private NbtIo() {}
}
```

- [ ] **Step 7: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*NbtCompoundTest"`
Expected: PASS (2 tests).
```bash
git add furnace-server
git commit -m "feat(nbt): add NBT tag model (NbtCompound/NbtList)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: NBT read/write 코덱 (NbtIo)

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/io/nbt/NbtIo.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/io/nbt/NbtIoTest.java`

- [ ] **Step 1: 실패 테스트**

`NbtIoTest.java`:
```java
// 전 태그종 NBT write→read 라운드트립 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class NbtIoTest {

    @Test
    void roundTrip_allTagTypes() throws IOException {
        NbtCompound root = new NbtCompound();
        root.putByte("b", (byte) 7);
        root.put("s", (short) 300);
        root.putInt("i", -123456);
        root.putLong("l", 9_000_000_000L);
        root.put("f", 1.5f);
        root.put("d", 2.5d);
        root.putString("str", "hello world");
        root.put("ba", new byte[]{1, 2, 3});
        root.put("ia", new int[]{4, 5});
        root.putLongArray("la", new long[]{6, 7});
        NbtList list = new NbtList(NbtIo.TAG_STRING);
        list.add("x"); list.add("y");
        root.putList("list", list);
        NbtCompound nested = new NbtCompound();
        nested.putInt("n", 42);
        root.putCompound("nested", nested);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), root, "Level");
        NbtCompound back = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertEquals((byte) 7, back.getByte("b"));
        assertEquals(-123456, back.getInt("i"));
        assertEquals(9_000_000_000L, back.getLong("l"));
        assertEquals("hello world", back.getString("str"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) back.tags().get("ba"));
        assertArrayEquals(new long[]{6, 7}, back.getLongArray("la"));
        assertEquals(2, back.getList("list").size());
        assertEquals("y", back.getList("list").get(1));
        assertEquals(42, back.getCompound("nested").getInt("n"));
    }

    @Test
    void gzip_roundTrip() throws IOException {
        NbtCompound root = new NbtCompound();
        root.putInt("x", 99);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.writeGzip(bos, root, "");
        NbtCompound back = NbtIo.readGzip(new ByteArrayInputStream(bos.toByteArray()));
        assertEquals(99, back.getInt("x"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*NbtIoTest"`
Expected: 컴파일 실패 — `write`/`read` 없음.

- [ ] **Step 3: NbtIo에 read/write 추가**

`private NbtIo() {}` 위에 메서드 추가:
```java
    // ---- write ----

    /** 명명 루트 compound write(표준 NBT: [type][name][payload]). */
    public static void write(DataOutput out, NbtCompound root, String rootName) throws java.io.IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF(rootName);
        writeCompoundPayload(out, root);
    }

    public static void writeGzip(java.io.OutputStream os, NbtCompound root, String rootName) throws java.io.IOException {
        try (DataOutputStream d = new DataOutputStream(new java.util.zip.GZIPOutputStream(os))) {
            write(d, root, rootName);
        }
    }

    private static void writeCompoundPayload(DataOutput out, NbtCompound c) throws java.io.IOException {
        for (var e : c.tags().entrySet()) {
            byte type = typeOf(e.getValue());
            out.writeByte(type);
            out.writeUTF(e.getKey());
            writePayload(out, type, e.getValue());
        }
        out.writeByte(TAG_END);
    }

    private static void writePayload(DataOutput out, byte type, Object v) throws java.io.IOException {
        switch (type) {
            case TAG_BYTE -> out.writeByte((Byte) v);
            case TAG_SHORT -> out.writeShort((Short) v);
            case TAG_INT -> out.writeInt((Integer) v);
            case TAG_LONG -> out.writeLong((Long) v);
            case TAG_FLOAT -> out.writeFloat((Float) v);
            case TAG_DOUBLE -> out.writeDouble((Double) v);
            case TAG_BYTE_ARRAY -> { byte[] a = (byte[]) v; out.writeInt(a.length); out.write(a); }
            case TAG_STRING -> out.writeUTF((String) v);
            case TAG_INT_ARRAY -> { int[] a = (int[]) v; out.writeInt(a.length); for (int x : a) out.writeInt(x); }
            case TAG_LONG_ARRAY -> { long[] a = (long[]) v; out.writeInt(a.length); for (long x : a) out.writeLong(x); }
            case TAG_LIST -> {
                NbtList l = (NbtList) v;
                out.writeByte(l.size() == 0 ? TAG_END : l.elementType());
                out.writeInt(l.size());
                for (Object item : l.items()) writePayload(out, l.elementType(), item);
            }
            case TAG_COMPOUND -> writeCompoundPayload(out, (NbtCompound) v);
            default -> throw new IllegalArgumentException("미지원 태그: " + type);
        }
    }

    private static byte typeOf(Object v) {
        if (v instanceof Byte) return TAG_BYTE;
        if (v instanceof Short) return TAG_SHORT;
        if (v instanceof Integer) return TAG_INT;
        if (v instanceof Long) return TAG_LONG;
        if (v instanceof Float) return TAG_FLOAT;
        if (v instanceof Double) return TAG_DOUBLE;
        if (v instanceof byte[]) return TAG_BYTE_ARRAY;
        if (v instanceof String) return TAG_STRING;
        if (v instanceof int[]) return TAG_INT_ARRAY;
        if (v instanceof long[]) return TAG_LONG_ARRAY;
        if (v instanceof NbtList) return TAG_LIST;
        if (v instanceof NbtCompound) return TAG_COMPOUND;
        throw new IllegalArgumentException("미지원 값: " + v.getClass());
    }

    // ---- read ----

    /** 명명 루트 compound read. 루트 이름은 버린다. */
    public static NbtCompound read(DataInput in) throws java.io.IOException {
        byte type = in.readByte();
        if (type != TAG_COMPOUND) throw new java.io.IOException("루트가 compound 아님: " + type);
        in.readUTF(); // 루트 이름 버림
        return readCompoundPayload(in);
    }

    public static NbtCompound readGzip(java.io.InputStream is) throws java.io.IOException {
        try (DataInputStream d = new DataInputStream(new java.util.zip.GZIPInputStream(is))) {
            return read(d);
        }
    }

    private static NbtCompound readCompoundPayload(DataInput in) throws java.io.IOException {
        NbtCompound c = new NbtCompound();
        while (true) {
            byte type = in.readByte();
            if (type == TAG_END) break;
            String name = in.readUTF();
            c.put(name, readPayload(in, type));
        }
        return c;
    }

    private static Object readPayload(DataInput in, byte type) throws java.io.IOException {
        return switch (type) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> { int n = in.readInt(); byte[] a = new byte[n]; in.readFully(a); yield a; }
            case TAG_STRING -> in.readUTF();
            case TAG_INT_ARRAY -> { int n = in.readInt(); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); yield a; }
            case TAG_LONG_ARRAY -> { int n = in.readInt(); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); yield a; }
            case TAG_LIST -> {
                byte et = in.readByte();
                int n = in.readInt();
                NbtList l = new NbtList(et);
                for (int i = 0; i < n; i++) l.add(readPayload(in, et));
                yield l;
            }
            case TAG_COMPOUND -> readCompoundPayload(in);
            default -> throw new java.io.IOException("미지원 태그: " + type);
        };
    }
```

- [ ] **Step 4: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*NbtIoTest"`
Expected: PASS (2 tests).
```bash
git add furnace-server
git commit -m "feat(nbt): add file NBT read/write codec with gzip" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: RegionFile (.mca 섹터 read/write)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/anvil/RegionFile.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/anvil/RegionFileTest.java`

- [ ] **Step 1: 실패 테스트**

`RegionFileTest.java`:
```java
// .mca 섹터 청크 바이트 write→read 라운드트립 + 음수좌표 + 재기록 검증
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileTest {

    @Test
    void writeRead_roundTrip(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        byte[] payload = "hello chunk".getBytes();
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(3, 5, payload);
            assertArrayEquals(payload, rf.readChunk(3, 5));
            assertNull(rf.readChunk(0, 0)); // 미기록
        }
        // 재오픈 후에도 유지
        try (RegionFile rf = new RegionFile(f)) {
            assertArrayEquals(payload, rf.readChunk(3, 5));
        }
    }

    @Test
    void rewrite_largerThenSmaller(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(1, 1, new byte[5000]); // 2섹터
            rf.writeChunk(1, 1, new byte[100]);  // 1섹터(in-place 축소)
            assertEquals(100, rf.readChunk(1, 1).length);
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionFileTest"`
Expected: 컴파일 실패 — `RegionFile` 없음.

- [ ] **Step 3: RegionFile.java**

```java
// 단일 Anvil 영역 파일(.mca). 32x32 청크, 4KB 섹터, 8KB 헤더(위치+타임스탬프).
package org.domi.furnace.server.world.anvil;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RegionFile implements Closeable {

    private static final int SECTOR = 4096;
    private final RandomAccessFile raf;
    private final int[] locations = new int[1024]; // (sectorOffset<<8)|sectorCount

    public RegionFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        if (raf.length() < 2L * SECTOR) {
            raf.setLength(2L * SECTOR); // 빈 헤더 2섹터
        }
        raf.seek(0);
        for (int i = 0; i < 1024; i++) locations[i] = raf.readInt();
    }

    private static int index(int chunkX, int chunkZ) {
        return (chunkX & 31) + (chunkZ & 31) * 32;
    }

    /** 청크 압축해제 전 raw payload bytes 반환(없으면 null). payload = NBT 직렬 바이트(압축해제됨). */
    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        int loc = locations[index(chunkX, chunkZ)];
        if (loc == 0) return null;
        int offset = loc >>> 8;
        raf.seek((long) offset * SECTOR);
        int len = raf.readInt();
        if (len <= 0) return null;
        int compType = raf.readByte();
        byte[] comp = new byte[len - 1];
        raf.readFully(comp);
        return Compression.decompress(compType, comp);
    }

    /** NBT 직렬 바이트를 zlib 압축해 섹터에 기록(in-place 가능하면 재사용, 아니면 append). */
    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        byte[] comp = Compression.deflate(nbtBytes);
        int total = comp.length + 5;                 // len(4)+type(1)+data
        int needed = (total + SECTOR - 1) / SECTOR;   // 섹터 수
        int idx = index(chunkX, chunkZ);
        int loc = locations[idx];
        int oldCount = loc & 0xFF;
        int offset;
        if (loc != 0 && needed <= oldCount) {
            offset = loc >>> 8;                       // in-place
        } else {
            offset = allocSectors(needed);            // 끝에 할당(이전 섹터는 누수, 압축 정리는 향후)
        }
        raf.seek((long) offset * SECTOR);
        raf.writeInt(comp.length + 1);
        raf.writeByte(2);                             // zlib
        raf.write(comp);
        // 섹터 패딩
        int pad = needed * SECTOR - total;
        if (pad > 0) raf.write(new byte[pad]);
        locations[idx] = (offset << 8) | (needed & 0xFF);
        raf.seek((long) idx * 4);
        raf.writeInt(locations[idx]);
        // 타임스탬프(헤더 두 번째 4KB)
        raf.seek(SECTOR + (long) idx * 4);
        raf.writeInt((int) (System.currentTimeMillis() / 1000));
    }

    private int allocSectors(int count) throws IOException {
        int max = 2; // 헤더 2섹터 뒤부터
        for (int l : locations) {
            int off = l >>> 8, cnt = l & 0xFF;
            if (off + cnt > max) max = off + cnt;
        }
        raf.setLength((long) (max + count) * SECTOR);
        return max;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
```

- [ ] **Step 4: Compression 헬퍼**

`furnace-server/src/main/java/org/domi/furnace/server/world/anvil/Compression.java`:
```java
// Anvil 청크 압축(zlib=deflate, type 2 / gzip=type 1) 헬퍼.
package org.domi.furnace.server.world.anvil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class Compression {

    private Compression() {}

    public static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DeflaterOutputStream d = new DeflaterOutputStream(bos)) {
            d.write(data);
        }
        return bos.toByteArray();
    }

    public static byte[] decompress(int type, byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.InputStream dec = switch (type) {
            case 1 -> new GZIPInputStream(in);
            case 2 -> new InflaterInputStream(in);
            default -> throw new IOException("미지원 압축타입: " + type);
        };
        dec.transferTo(out);
        dec.close();
        return out.toByteArray();
    }
}
```

- [ ] **Step 5: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*RegionFileTest"`
Expected: PASS (2 tests).
```bash
git add furnace-server
git commit -m "feat(anvil): add RegionFile (.mca sector read/write)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: RegionFileStore (파일별 직렬화)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/anvil/RegionFileStore.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/anvil/RegionFileStoreTest.java`

- [ ] **Step 1: 실패 테스트**

`RegionFileStoreTest.java`:
```java
// 여러 청크(다른 .mca 매핑)를 store로 read/write, 닫기 검증
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileStoreTest {

    @Test
    void writeRead_acrossRegionFiles(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, "a".getBytes());
        store.writeChunk(40, 0, "b".getBytes());   // 다른 .mca(r.1.0)
        assertArrayEquals("a".getBytes(), store.readChunk(0, 0));
        assertArrayEquals("b".getBytes(), store.readChunk(40, 0));
        assertNull(store.readChunk(5, 5));
        store.closeAll();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionFileStoreTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: RegionFileStore.java**

```java
// region 디렉토리의 .mca 핸들 캐시 + 파일별 락 직렬화. 여러 region 스레드가 공유 안전.
package org.domi.furnace.server.world.anvil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class RegionFileStore {

    private final Path regionDir;
    private final Map<Long, RegionFile> open = new HashMap<>();

    public RegionFileStore(Path worldDir) {
        this.regionDir = worldDir.resolve("region");
    }

    private static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    private synchronized RegionFile file(int chunkX, int chunkZ) throws IOException {
        int rx = chunkX >> 5, rz = chunkZ >> 5;
        long k = key(rx, rz);
        RegionFile rf = open.get(k);
        if (rf == null) {
            rf = new RegionFile(regionDir.resolve("r." + rx + "." + rz + ".mca"));
            open.put(k, rf);
        }
        return rf;
    }

    /** 압축해제된 NBT 바이트(없으면 null). 파일별 락 직렬화. */
    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        RegionFile rf = file(chunkX, chunkZ);
        synchronized (rf) {
            return rf.readChunk(chunkX, chunkZ);
        }
    }

    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        RegionFile rf = file(chunkX, chunkZ);
        synchronized (rf) {
            rf.writeChunk(chunkX, chunkZ, nbtBytes);
        }
    }

    public synchronized void closeAll() throws IOException {
        for (RegionFile rf : open.values()) rf.close();
        open.clear();
    }
}
```

- [ ] **Step 4: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*RegionFileStoreTest"`
Expected: PASS.
```bash
git add furnace-server
git commit -m "feat(anvil): add RegionFileStore with per-file serialization" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: AnvilChunkCodec (ChunkColumn ↔ NBT)

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/BlockRegistry.java` (state id→name 역맵)
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/anvil/AnvilChunkCodec.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/anvil/AnvilChunkCodecTest.java`

- [ ] **Step 1: 실패 테스트**

`AnvilChunkCodecTest.java`:
```java
// ChunkColumn→NBT→ChunkColumn 블록 보존 + 편집 보존 검증
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class AnvilChunkCodecTest {

    @Test
    void roundTrip_preservesBlocks() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(2, -3);
        int grass = reg.defaultStateId("minecraft:grass_block");
        col.setBlock(0, -61, 0, ChunkSection.AIR); // 편집

        NbtCompound nbt = codec.toNbt(col, 2, -3);
        ChunkColumn back = codec.fromNbt(nbt);

        assertEquals(ChunkSection.AIR, back.getBlock(0, -61, 0));      // 편집 보존
        assertEquals(grass, back.getBlock(1, -61, 0));                 // 미편집 grass
        assertEquals(reg.defaultStateId("minecraft:bedrock"), back.getBlock(0, -64, 0));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*AnvilChunkCodecTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: BlockRegistry에 역맵 추가**

import에 `java.util.HashMap` 이미 있음. 필드 추가:
```java
    private final Map<Integer, String> nameByDefaultId;
```
생성자 시그니처/본문 수정:
```java
    private BlockRegistry(Map<String, Integer> ids, int totalStateCount) {
        this.defaultStateIds = ids;
        this.totalStateCount = totalStateCount;
        this.nameByDefaultId = new HashMap<>();
        for (var e : ids.entrySet()) nameByDefaultId.put(e.getValue(), e.getKey());
    }
```
메서드 추가:
```java
    /** state id → 블록명. default state만 매핑(우리 블록셋 한정); 미지원 id는 air. */
    public String nameOf(int stateId) {
        return nameByDefaultId.getOrDefault(stateId, "minecraft:air");
    }

    /** 블록명 → state id. 미지원이면 air(0). 바닐라 read 폴백용. */
    public int stateIdOrAir(String blockName) {
        Integer id = defaultStateIds.get(blockName);
        return id == null ? 0 : id;
    }
```

- [ ] **Step 4: AnvilChunkCodec.java**

```java
// ChunkColumn ↔ 표준 Anvil 청크 NBT. 팰릿 속성 무시(Name→default state id 근사).
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BitStorage;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnvilChunkCodec {

    private static final int DATA_VERSION = 3955; // 1.21.1
    private final BlockRegistry registry;
    private final int biomeId; // 단일 바이옴(superflat/근사). 바닐라 read 시 바이옴은 무시하고 이 값 사용.

    public AnvilChunkCodec(BlockRegistry registry, int biomeId) {
        this.registry = registry;
        this.biomeId = biomeId;
    }

    public NbtCompound toNbt(ChunkColumn col, int chunkX, int chunkZ) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("xPos", chunkX);
        root.putInt("zPos", chunkZ);
        root.putInt("yPos", -4);
        root.putString("Status", "minecraft:full");

        NbtList sections = new NbtList(NbtIo.TAG_COMPOUND);
        ChunkSection[] secs = col.sections();
        for (int i = 0; i < secs.length; i++) {
            NbtCompound s = new NbtCompound();
            s.putByte("Y", (byte) (i - 4));
            s.putCompound("block_states", blockStatesToNbt(secs[i]));
            sections.add(s);
        }
        root.putList("sections", sections);

        NbtCompound heightmaps = new NbtCompound();
        heightmaps.putLongArray("MOTION_BLOCKING", col.motionBlockingHeightmap());
        root.putCompound("Heightmaps", heightmaps);
        return root;
    }

    private NbtCompound blockStatesToNbt(ChunkSection sec) {
        int[] blocks = sec.blocks();
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : blocks) {
            if (!indexOf.containsKey(v)) { indexOf.put(v, palette.size()); palette.add(v); }
        }
        NbtList paletteList = new NbtList(NbtIo.TAG_COMPOUND);
        for (int stateId : palette) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Name", registry.nameOf(stateId));
            paletteList.add(entry);
        }
        NbtCompound bs = new NbtCompound();
        bs.putList("palette", paletteList);
        if (palette.size() > 1) {
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            BitStorage store = new BitStorage(bits, 4096);
            for (int i = 0; i < 4096; i++) store.set(i, indexOf.get(blocks[i]));
            bs.putLongArray("data", store.data());
        }
        return bs;
    }

    public ChunkColumn fromNbt(NbtCompound root) {
        ChunkColumn col = new ChunkColumn(biomeId);
        NbtList sections = root.getList("sections");
        for (Object o : sections.items()) {
            NbtCompound s = (NbtCompound) o;
            int y = s.getByte("Y");
            int sectionIdx = y + 4;
            if (sectionIdx < 0 || sectionIdx >= ChunkColumn.SECTION_COUNT) continue;
            if (!s.contains("block_states")) continue;
            applyBlockStates(col, sectionIdx, s.getCompound("block_states"));
        }
        return col;
    }

    private void applyBlockStates(ChunkColumn col, int sectionIdx, NbtCompound bs) {
        NbtList paletteList = bs.getList("palette");
        int[] paletteIds = new int[paletteList.size()];
        for (int i = 0; i < paletteIds.length; i++) {
            NbtCompound entry = (NbtCompound) paletteList.get(i);
            paletteIds[i] = registry.stateIdOrAir(entry.getString("Name"));
        }
        int baseY = ChunkColumn.MIN_Y + sectionIdx * 16;
        if (!bs.contains("data")) {
            if (paletteIds.length == 0 || paletteIds[0] == ChunkSection.AIR) return; // 전부 air
            for (int idx = 0; idx < 4096; idx++) setFromIndex(col, baseY, idx, paletteIds[0]);
            return;
        }
        long[] data = bs.getLongArray("data");
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteIds.length - 1));
        BitStorage store = BitStorage.wrap(bits, 4096, data);
        for (int idx = 0; idx < 4096; idx++) {
            int stateId = paletteIds[store.get(idx)];
            if (stateId != ChunkSection.AIR) setFromIndex(col, baseY, idx, stateId);
        }
    }

    // 섹션 로컬 index(y*256+z*16+x) → ChunkColumn.setBlock(x, worldY, z)
    private void setFromIndex(ChunkColumn col, int baseY, int idx, int stateId) {
        int x = idx & 15;
        int z = (idx >> 4) & 15;
        int yLocal = idx >> 8;
        col.setBlock(x, baseY + yLocal, z, stateId);
    }
}
```

- [ ] **Step 5: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*AnvilChunkCodecTest" --tests "*BlockRegistry*"`
Expected: PASS.
```bash
git add furnace-server
git commit -m "feat(anvil): add chunk NBT codec (ChunkColumn round-trip)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: ChunkColumn.dirty + AnvilWorldSource

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/AnvilWorldSource.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/AnvilWorldSourceTest.java`

- [ ] **Step 1: 실패 테스트**

`AnvilWorldSourceTest.java`:
```java
// AnvilWorldSource: 디스크 없으면 base generate, 있으면 .mca 로드 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

class AnvilWorldSourceTest {

    @Test
    void absent_fallsBackToGenerate(@TempDir Path dir) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilWorldSource src = new AnvilWorldSource(dir, new RegionFileStore(dir),
                new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        ChunkColumn col = src.generate(0, 0);
        assertEquals(reg.defaultStateId("minecraft:grass_block"), col.getBlock(0, -61, 0));
    }

    @Test
    void present_loadsFromDisk(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        RegionFileStore store = new RegionFileStore(dir);
        // 편집된 청크를 디스크에 둔다
        ChunkColumn edited = new SuperflatWorldSource(reg, 0).generate(0, 0);
        edited.setBlock(0, -61, 0, ChunkSection.AIR);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), codec.toNbt(edited, 0, 0), "");
        store.writeChunk(0, 0, bos.toByteArray());

        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        ChunkColumn col = src.generate(0, 0);
        assertEquals(ChunkSection.AIR, col.getBlock(0, -61, 0)); // 디스크 편집 반영
        store.closeAll();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*AnvilWorldSourceTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: ChunkColumn.dirty**

`surface` 필드 아래에 추가:
```java
    private boolean dirty;
```
`setBlock` 끝에 `dirty = true;` 추가:
```java
    public void setBlock(int x, int worldY, int z, int stateId) {
        int local = worldY - MIN_Y;
        sections[local >> 4].setBlock(x, local & 15, z, stateId);
        dirty = true;
    }
```
메서드 추가:
```java
    public boolean dirty() { return dirty; }
    public void clearDirty() { dirty = false; }
```
> 주의: generate/codec.fromNbt가 setBlock으로 채우면 dirty=true가 된다. 로드 직후 clean이어야 하므로 **로드/생성 경로에서 clearDirty() 호출**(T6 AnvilWorldSource·T8 loadChunk서 처리).

- [ ] **Step 4: AnvilWorldSource.java**

```java
// 디스크(.mca) 우선 로드, 없으면 base WorldSource로 생성하는 WorldSource. 로드 청크는 clean.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class AnvilWorldSource implements WorldSource {

    private final Path worldDir;
    private final RegionFileStore store;
    private final AnvilChunkCodec codec;
    private final WorldSource base; // 디스크 없을 때 폴백 생성

    public AnvilWorldSource(Path worldDir, RegionFileStore store, AnvilChunkCodec codec, WorldSource base) {
        this.worldDir = worldDir;
        this.store = store;
        this.codec = codec;
        this.base = base;
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        try {
            byte[] nbtBytes = store.readChunk(chunkX, chunkZ);
            if (nbtBytes != null) {
                NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbtBytes)));
                ChunkColumn col = codec.fromNbt(nbt);
                col.clearDirty();
                return col;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
        }
        ChunkColumn col = base.generate(chunkX, chunkZ);
        col.clearDirty();
        return col;
    }

    public RegionFileStore store() { return store; }
    public AnvilChunkCodec codec() { return codec; }
}
```

- [ ] **Step 5: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*AnvilWorldSourceTest" --tests "*ChunkColumn*"`
Expected: PASS.
```bash
git add furnace-server
git commit -m "feat(world): add ChunkColumn dirty flag and AnvilWorldSource" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Config — level-name / io-threads / autosave / eviction

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/Config.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/ConfigLoader.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/config/ConfigLoaderTest.java`

- [ ] **Step 1: 실패 테스트**

`ConfigLoaderTest.java` `load_returnsDefaults_whenFileMissing`에 추가:
```java
        assertEquals("world", c.levelName());
        assertEquals(4, c.ioThreads());
        assertEquals(6000, c.autosaveTicks());
        assertEquals(100, c.chunkEvictionGraceTicks());
```
`load_overridesFromFile`의 props 문자열 끝에 추가(`region-threads=2\n` 뒤):
```java
                "level-name=myworld\nio-threads=2\nautosave-ticks=1200\nchunk-eviction-grace-ticks=40\n"
```
(문자열 연결: 기존 마지막 `\n"` 를 `\n" +` 로 바꾸고 위 줄 추가.) 단언 추가:
```java
        assertEquals("myworld", c.levelName());
        assertEquals(2, c.ioThreads());
        assertEquals(1200, c.autosaveTicks());
        assertEquals(40, c.chunkEvictionGraceTicks());
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: Config.java**

record 시그니처에 필드 추가(맨 끝) + DEFAULT:
```java
public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance, int entityViewDistance, int regionSize, int regionThreads,
                     String levelName, int ioThreads, int autosaveTicks, int chunkEvictionGraceTicks) {

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5, 8, 16,
                    Runtime.getRuntime().availableProcessors(),
                    "world", 4, 6000, 100);
}
```

- [ ] **Step 4: ConfigLoader.java**

`regionThreads` 파싱 아래에 추가:
```java
        String levelName = props.getProperty("level-name", Config.DEFAULT.levelName());
        int ioThreads = Integer.parseInt(
                props.getProperty("io-threads", Integer.toString(Config.DEFAULT.ioThreads())));
        int autosaveTicks = Integer.parseInt(
                props.getProperty("autosave-ticks", Integer.toString(Config.DEFAULT.autosaveTicks())));
        int evictionGrace = Integer.parseInt(
                props.getProperty("chunk-eviction-grace-ticks",
                        Integer.toString(Config.DEFAULT.chunkEvictionGraceTicks())));
```
`return new Config(...)`에 인자 추가:
```java
        return new Config(name, tick, port, compression, viewDistance, entityViewDistance,
                regionSize, regionThreads, levelName, ioThreads, autosaveTicks, evictionGrace);
```

- [ ] **Step 5: 통과 확인 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: PASS.
```bash
git add furnace-server
git commit -m "feat(config): add persistence settings" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: World IO 풀 + 비동기 loadChunk

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/AsyncLoadTest.java`

World 생성자에 IO 풀(ExecutorService)·AnvilWorldSource 보유. `Region.loadChunk(cp)` async. `snapshotChunk`이 loadChunk 경유.

- [ ] **Step 1: 실패 테스트**

`AsyncLoadTest.java`:
```java
// loadChunk async: 부재 시 generate, in-flight dedup, 재호출 동일 인스턴스
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsyncLoadTest {

    @Test
    void loadChunk_generatesAndRetains(@TempDir Path dir) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, 0),
                new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        ChunkColumn c1 = sched.region(rp).submit(() -> w.regionAt(rp).loadChunk(cp)).get().get();
        ChunkColumn c2 = sched.region(rp).submit(() -> w.regionAt(rp).loadChunk(cp)).get().get();
        assertNotNull(c1);
        assertSame(c1, c2);
        assertFalse(c1.dirty()); // 로드 직후 clean
        io.shutdown();
        store.closeAll();
        sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*AsyncLoadTest"`
Expected: 컴파일 실패 — World 생성자 인자/loadChunk 없음.

- [ ] **Step 3: World 생성자에 IO 풀 추가**

import 추가:
```java
import java.util.concurrent.ExecutorService;
```
필드 추가(`chunkPool` 아래):
```java
    private final ExecutorService ioPool;
```
생성자 시그니처에 `ExecutorService ioPool` 추가(맨 끝 인자) + 대입:
```java
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool) {
        // ... 기존 대입 ...
        this.ioPool = ioPool;
```
게터 추가:
```java
    public ExecutorService ioPool() { return ioPool; }
```

- [ ] **Step 4: snapshotChunk을 loadChunk 경유로**

기존 `snapshotChunk` 교체:
```java
    public CompletableFuture<ChunkSnapshot> snapshotChunk(ChunkPos cp) {
        RegionPos owner = RegionGrid.regionPosOfChunk(cp.x(), cp.z(), regionSize);
        return scheduler.region(owner)
                .submit(() -> regionAt(owner).loadChunk(cp))
                .thenCompose(f -> f)
                .thenApplyAsync(col -> ChunkSnapshot.of(cp.x(), cp.z(), col),
                        r -> scheduler.region(owner).execute(r));
    }
```
> 스냅샷은 owner 스레드서 떠야 함(`thenApplyAsync`의 executor = owner region.execute). loadChunk 완료(owner 스레드 저장) 후 스냅샷.

- [ ] **Step 5: Region.loadChunk + chunkAt 폴백**

import 추가:
```java
import java.util.concurrent.CompletableFuture;
```
필드 추가(`chunks` 아래):
```java
    private final Map<ChunkPos, CompletableFuture<ChunkColumn>> loading = new HashMap<>();
```
`chunkAt` 교체(로드됨 가정, 없으면 동기 generate 폴백 — breakBlock용):
```java
    /** 소유 스레드서 로드된 청크 반환. 없으면 동기 generate 폴백(편집은 로드된 청크 대상). */
    public ChunkColumn chunkAt(ChunkPos cp) {
        return chunks.computeIfAbsent(cp, p -> {
            ChunkColumn c = world.worldSource().generate(p.x(), p.z());
            c.clearDirty();
            return c;
        });
    }
```
`loadChunk` 추가:
```java
    /** 소유 스레드서 비동기 청크 로드(IO 풀 read+decode). in-flight dedup. */
    public CompletableFuture<ChunkColumn> loadChunk(ChunkPos cp) {
        world.assertOwns(pos);
        ChunkColumn present = chunks.get(cp);
        if (present != null) return CompletableFuture.completedFuture(present);
        CompletableFuture<ChunkColumn> inflight = loading.get(cp);
        if (inflight != null) return inflight;
        CompletableFuture<ChunkColumn> f = CompletableFuture
                .supplyAsync(() -> world.worldSource().generate(cp.x(), cp.z()), world.ioPool())
                .thenApplyAsync(col -> {
                    col.clearDirty();
                    chunks.put(cp, col);
                    loading.remove(cp);
                    return col;
                }, r -> world.scheduler().region(pos).execute(r));
        loading.put(cp, f);
        return f;
    }
```
> `world.worldSource()`는 AnvilWorldSource — IO 풀서 디스크 read/generate. 저장/dedup 갱신은 owner 스레드(region.execute executor).

- [ ] **Step 6: 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*AsyncLoadTest"`
Expected: PASS.

- [ ] **Step 7: 기존 호출부 컴파일 — World 생성자 +1 인자**

`NetworkServerTest`·`StatusPingTest`·`RegionTickTest`·`SnapshotRegistryTest`·`CrossRegionVisibilityTest`·`CrossRegionChunkEditTest`·`ChunkSubscriberTest`·`RegionChunkOwnershipTest`·`BlockBreakTest`·`HandoffVisibilityTest`의 `new World(...)`에 마지막 인자로 IO 풀 추가. 각 테스트 상단에 `var io = java.util.concurrent.Executors.newSingleThreadExecutor();` 두고 `..., 7, io)` 형태로, `sched.close()` 부근서 `io.shutdown()`. (테스트별로 직접 수정.)

> 대안(권장): 테스트 헬퍼 중복 줄이려 각 파일 내 생성 지점에만 인라인 추가. World 9→10 인자.

- [ ] **Step 8: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): async chunk loading via IO pool" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: dirty 저장 + 청크 퇴출 + autosave

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkSaveTest.java`

- [ ] **Step 1: 실패 테스트**

`ChunkSaveTest.java`:
```java
// dirty 청크 저장 → 디스크서 재로드 시 편집 유지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkSaveTest {

    @Test
    void saveDirty_thenReload_keepsEdit(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        // 편집 후 저장
        sched.region(rp).execute(() -> {
            w.regionAt(rp).breakBlock(0, -61, 0);
            w.regionAt(rp).saveDirtyChunks(); // 동기 트리거(테스트)
        }).get();
        Thread.sleep(200); // IO flush 대기

        // 새 store로 디스크서 직접 로드 → air 유지
        RegionFileStore store2 = new RegionFileStore(dir);
        AnvilWorldSource src2 = new AnvilWorldSource(dir, store2, codec, new SuperflatWorldSource(reg, 0));
        assertEquals(ChunkSection.AIR, src2.generate(0, 0).getBlock(0, -61, 0));
        store2.closeAll();
        io.shutdown(); store.closeAll(); sched.close();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ChunkSaveTest"`
Expected: 컴파일 실패 — `saveDirtyChunks` 없음.

- [ ] **Step 3: World에 saveChunk 헬퍼**

import 추가:
```java
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
```
메서드 추가:
```java
    /** owner 스레드서 스냅샷 뜬 뒤 IO 풀서 .mca write. worldSource가 AnvilWorldSource일 때만. */
    public void saveChunkAsync(ChunkSnapshot snap) {
        if (!(worldSource instanceof AnvilWorldSource anvil)) return;
        ioPool.execute(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                NbtCompound nbt = anvil.codec().toNbt(snapToColumn(snap), snap.chunkX(), snap.chunkZ());
                org.domi.furnace.server.net.io.nbt.NbtIo.write(new DataOutputStream(bos), nbt, "");
                anvil.store().writeChunk(snap.chunkX(), snap.chunkZ(), bos.toByteArray());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(World.class)
                        .error("[SAVE] 청크 {},{} 저장 실패", snap.chunkX(), snap.chunkZ(), e);
            }
        });
    }
```
> **문제:** AnvilChunkCodec.toNbt는 ChunkColumn을 받는데 우리는 ChunkSnapshot을 가졌다. 스냅샷서 ChunkColumn 복원 헬퍼가 필요. 더 단순한 방법 = codec에 `toNbt(ChunkSnapshot, cx, cz)` 오버로드 추가(섹션 blocks 배열 직접 사용). **T5 코덱을 ChunkSnapshot 입력도 받게 보강**하거나, 여기서 snapToColumn 구현. 아래 Step 3b서 codec 보강.

- [ ] **Step 3b: AnvilChunkCodec에 ChunkSnapshot 오버로드**

`AnvilChunkCodec.java`에 추가(blocks 배열 직접 사용 — `blockStatesToNbt`를 int[] 받게 분리):
```java
    public NbtCompound toNbt(org.domi.furnace.server.world.ChunkSnapshot snap) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("xPos", snap.chunkX());
        root.putInt("zPos", snap.chunkZ());
        root.putInt("yPos", -4);
        root.putString("Status", "minecraft:full");
        NbtList sections = new NbtList(NbtIo.TAG_COMPOUND);
        org.domi.furnace.server.world.ChunkSnapshot.Section[] secs = snap.sections();
        for (int i = 0; i < secs.length; i++) {
            NbtCompound s = new NbtCompound();
            s.putByte("Y", (byte) (i - 4));
            s.putCompound("block_states", blockStatesFromArray(secs[i].blocks()));
            sections.add(s);
        }
        root.putList("sections", sections);
        NbtCompound heightmaps = new NbtCompound();
        heightmaps.putLongArray("MOTION_BLOCKING", snap.heightmap());
        root.putCompound("Heightmaps", heightmaps);
        return root;
    }
```
그리고 `blockStatesToNbt(ChunkSection sec)`의 본체를 `blockStatesFromArray(int[] blocks)`로 추출(`sec.blocks()` → 매개변수 `blocks`), `blockStatesToNbt`는 `return blockStatesFromArray(sec.blocks());`로. 그러면 toNbt(ChunkColumn)·toNbt(ChunkSnapshot) 공용.

World의 `saveChunkAsync`는 `anvil.codec().toNbt(snap)` 사용(snapToColumn 불필요):
```java
                NbtCompound nbt = anvil.codec().toNbt(snap);
```

- [ ] **Step 4: Region.saveDirtyChunks + 퇴출 + autosave 훅**

`grace` 추적 필드 추가:
```java
    private final Map<ChunkPos, Integer> evictGrace = new HashMap<>();
```
메서드 추가:
```java
    /** 로드된 dirty 청크 전부 저장(스냅샷→IO write), dirty clear. */
    public void saveDirtyChunks() {
        for (var e : chunks.entrySet()) {
            ChunkColumn col = e.getValue();
            if (col.dirty()) {
                world.saveChunkAsync(ChunkSnapshot.of(e.getKey().x(), e.getKey().z(), col));
                col.clearDirty();
            }
        }
    }

    /** 구독자 0인 청크를 grace 후 퇴출(dirty면 저장). 매 틱 호출. */
    private void evictIdleChunks() {
        var it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ChunkPos cp = e.getKey();
            if (!world.subscribers(cp).isEmpty()) { evictGrace.remove(cp); continue; }
            int g = evictGrace.merge(cp, 1, Integer::sum);
            if (g >= world.chunkEvictionGraceTicks()) {
                ChunkColumn col = e.getValue();
                if (col.dirty()) {
                    world.saveChunkAsync(ChunkSnapshot.of(cp.x(), cp.z(), col));
                    col.clearDirty();
                }
                it.remove();
                evictGrace.remove(cp);
            }
        }
    }
```
`tick()` 끝(handoff 루프 뒤)에 추가:
```java
        evictIdleChunks();
        if (world.autosaveDue()) saveDirtyChunks();
```

- [ ] **Step 5: World autosave 카운터 + 게터**

필드/메서드 추가:
```java
    private int tickCounter;

    public int chunkEvictionGraceTicks() { return chunkEvictionGraceTicks; }

    /** 호출당 1틱 누적, autosaveTicks 도달 시 true(리셋). region 0만 호출하도록 tickThread서 게이트. */
    public boolean autosaveDue() {
        // 단순화: 각 region 틱마다 호출되므로 regionThreads로 나눠 카운트 X.
        // tickThread(0)서만 카운트하도록 World에 별도 메서드 둠(아래 autosaveTick).
        return false;
    }
```
> 정확성 위해: autosave 카운트는 region별 중복 방지가 필요. **간단 처리** — `tickThread(int index)`서 index==0일 때만 카운터 증가, 임계 도달 시 플래그. Region.tick은 World 플래그를 읽어 자기 청크 flush. 아래 Step 5b.

- [ ] **Step 5b: autosave를 tickThread(0) 기준으로**

`World`에 추가:
```java
    private volatile boolean autosaveFlag;
    private int autosaveCounter;
    private final int autosaveTicks;
    private final int chunkEvictionGraceTicks;
```
(생성자서 `config` 값 대입 — Config에서 받도록 생성자 인자 추가 또는 FurnaceMain서 setter. **간단히 생성자 인자 2개 추가**: `autosaveTicks`, `chunkEvictionGraceTicks` — T8 생성자에 이미 IO 추가했으니 여기 2개 더. 모든 World 생성 테스트 호출부 갱신 필요 → 부담. **대안: World에 `configurePersistence(autosaveTicks, graceTicks)` setter** 두고 FurnaceMain서 호출. 테스트는 기본값(autosave 비활성=Integer.MAX, grace=기본).)

**결정: setter 방식**(생성자 인자 폭발 회피):
```java
    private int autosaveTicks = Integer.MAX_VALUE; // 기본 비활성(테스트)
    private int chunkEvictionGraceTicks = 100;

    public void configurePersistence(int autosaveTicks, int graceTicks) {
        this.autosaveTicks = autosaveTicks;
        this.chunkEvictionGraceTicks = graceTicks;
    }
    public int chunkEvictionGraceTicks() { return chunkEvictionGraceTicks; }
```
`tickThread(int index)` 시작에 추가:
```java
        if (index == 0 && ++autosaveCounter >= autosaveTicks) {
            autosaveCounter = 0;
            autosaveFlag = true;
        }
```
Region.tick의 autosave 호출 교체:
```java
        evictIdleChunks();
        if (world.consumeAutosaveFlag()) saveDirtyChunks();
```
World에:
```java
    /** autosave 플래그를 읽고 끈다(각 region이 한 번씩 자기 청크 flush). */
    public boolean consumeAutosaveFlag() { return autosaveFlag; }
    /** tickThread 마지막에 플래그 리셋(모든 region이 본 후). */
    public void clearAutosaveFlag() { autosaveFlag = false; }
```
`tickThread`의 region 루프 뒤에 `if (index == regionThreads - 1) clearAutosaveFlag();` 추가(마지막 스레드가 리셋 — 근사. 정밀 동기화는 향후).
그리고 `autosaveDue()`/이전 stub 제거.

> **단순화 주의:** autosave 플래그의 region 간 정확한 1회 보장은 느슨하다(틱 경합 시 일부 region이 1틱 늦게 flush 가능). dirty 기반이라 결과는 멱등(다음 주기 flush). 정밀 배리어는 향후 최적화.

- [ ] **Step 6: 통과 확인 + 전체 빌드**

Run: `./gradlew :furnace-server:test --tests "*ChunkSaveTest"`
Expected: PASS.
Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): chunk save, eviction, and autosave" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: 플레이어 위치 영속

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/entity/PlayerData.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java` (disconnect 저장)
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java` (playerdata 경로/IO)
- Test: `furnace-server/src/test/java/org/domi/furnace/server/entity/PlayerDataTest.java`

- [ ] **Step 1: 실패 테스트**

`PlayerDataTest.java`:
```java
// 플레이어 위치 NBT write→read 라운드트립
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataTest {

    @Test
    void writeRead_position(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        PlayerData.save(dir, id, 12.5, -60, -7.25, 90f, 10f, true);
        double[] pos = PlayerData.load(dir, id);
        assertNotNull(pos);
        assertEquals(12.5, pos[0]);
        assertEquals(-60, pos[1]);
        assertEquals(-7.25, pos[2]);
        assertEquals(90f, (float) pos[3]);
        assertEquals(10f, (float) pos[4]);
        assertEquals(1.0, pos[5]); // onGround
        assertNull(PlayerData.load(dir, UUID.randomUUID())); // 없음
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*PlayerDataTest"`
Expected: 컴파일 실패.

- [ ] **Step 3: PlayerData.java**

```java
// playerdata/<uuid>.dat (gzip NBT) 위치 read/write. M4c가 Inventory/Health 확장.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class PlayerData {

    private PlayerData() {}

    private static Path file(Path worldDir, UUID id) {
        return worldDir.resolve("playerdata").resolve(id + ".dat");
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround) {
        NbtCompound root = new NbtCompound();
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE);
        pos.add(x); pos.add(y); pos.add(z);
        root.putList("Pos", pos);
        NbtList rot = new NbtList(NbtIo.TAG_FLOAT);
        rot.add(yaw); rot.add(pitch);
        root.putList("Rotation", rot);
        root.putByte("OnGround", (byte) (onGround ? 1 : 0));
        try {
            Path f = file(worldDir, id);
            Files.createDirectories(f.getParent());
            try (var os = Files.newOutputStream(f)) {
                NbtIo.writeGzip(os, root, "");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 저장 실패 " + id, e);
        }
    }

    /** {x,y,z,yaw,pitch,onGround(1/0)} 또는 null(없음). */
    public static double[] load(Path worldDir, UUID id) {
        Path f = file(worldDir, id);
        if (!Files.exists(f)) return null;
        try (var is = Files.newInputStream(f)) {
            NbtCompound root = NbtIo.readGzip(is);
            NbtList pos = root.getList("Pos");
            NbtList rot = root.getList("Rotation");
            return new double[]{
                    (Double) pos.get(0), (Double) pos.get(1), (Double) pos.get(2),
                    (Float) rot.get(0), (Float) rot.get(1),
                    root.getByte("OnGround") != 0 ? 1.0 : 0.0
            };
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 로드 실패 " + id, e);
        }
    }
}
```

- [ ] **Step 4: World 월드 디렉토리 노출 + Region disconnect 저장**

World에 worldDir 필드/게터 필요. **setter 방식**(생성자 폭발 회피):
```java
    private Path worldDir = Path.of("world");
    public void worldDir(Path dir) { this.worldDir = dir; }
    public Path worldDir() { return worldDir; }
```
import `java.nio.file.Path`.
Region.tick의 disconnected 분기에 위치 저장 추가:
```java
            if (p.isDisconnected()) {
                it.remove();
                for (ChunkPos cp : p.loadedChunks()) world.unsubscribeChunk(cp, p);
                final Player fp = p;
                world.ioPool().execute(() -> org.domi.furnace.server.entity.PlayerData.save(
                        world.worldDir(), fp.uuid(), fp.x(), fp.y(), fp.z(), fp.yaw(), fp.pitch(), fp.onGround()));
                broadcastRemove(p.entityId());
                continue;
            }
```

- [ ] **Step 5: 통과 확인 + 빌드 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*PlayerDataTest"`
Expected: PASS.
Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.
```bash
git add furnace-server
git commit -m "feat(entity): persist player position to playerdata" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: FurnaceMain 배선 + 접속 로드 + 종료 시퀀스

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/net/ConnectionHandler.java`

테스트 없음(통합 배선 — T12 수동 verify). 빌드만 확인.

- [ ] **Step 1: FurnaceMain — Anvil 배선**

`World world = new World(...)` 부분을 교체. 기존 superflat WorldSource를 AnvilWorldSource로 감싸고 IO 풀·worldDir·persistence 설정:
```java
        java.nio.file.Path worldDir = java.nio.file.Path.of(config.levelName());
        var regionStore = new org.domi.furnace.server.world.anvil.RegionFileStore(worldDir);
        var chunkCodec = new org.domi.furnace.server.world.anvil.AnvilChunkCodec(blockRegistry, plainsBiome);
        var superflat = new SuperflatWorldSource(blockRegistry, plainsBiome);
        var anvilSource = new org.domi.furnace.server.world.gen.AnvilWorldSource(
                worldDir, regionStore, chunkCodec, superflat);
        java.util.concurrent.ExecutorService ioPool =
                java.util.concurrent.Executors.newFixedThreadPool(config.ioThreads());
        World world = new World(anvilSource, chunkPool, scheduler, config.regionThreads(),
                config.regionSize(), config.viewDistance(), config.entityViewDistance(),
                blockRegistry.directBits(), biomeDirectBits, ioPool);
        world.worldDir(worldDir);
        world.configurePersistence(config.autosaveTicks(), config.chunkEvictionGraceTicks());
        scheduler.setRegionTick(world::tickThread);
```

- [ ] **Step 2: 종료 시퀀스(graceful stop)**

FurnaceMain 부팅 후, shutdown hook 추가(scheduler·ioPool·regionStore 정리). 부팅 코드 뒤에:
```java
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            org.slf4j.LoggerFactory.getLogger(FurnaceMain.class).info("[STOP] 종료 — dirty 청크/플레이어 flush");
            try {
                // 각 region 스레드서 dirty flush(await)
                java.util.List<java.util.concurrent.CompletableFuture<Void>> fs = new java.util.ArrayList<>();
                for (int i = 0; i < config.regionThreads(); i++) {
                    final int idx = i;
                    fs.add(scheduler.region(regionPosForThread(idx, config)).execute(() -> world.flushThread(idx)));
                }
                for (var f : fs) f.get();
                ioPool.shutdown();
                ioPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                regionStore.closeAll();
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(FurnaceMain.class).error("[STOP] flush 실패", e);
            }
        }));
```
> `flushThread(idx)` = World가 스레드 idx의 모든 region의 dirty 청크 + 접속 플레이어 저장. `regionPosForThread`는 idx 스레드가 소유하는 임의 RegionPos(스레드 타겟팅용). **단순화**: World에 `flushAll()` 두고 각 region 스레드서 자기 region flush. 아래 Step 2b.

- [ ] **Step 2b: World.flushThread + 플레이어 저장**

World에 추가:
```java
    /** 종료용: 스레드 idx의 모든 region의 dirty 청크 + 접속 플레이어 저장(owner 스레드서 호출). */
    public void flushThread(int index) {
        for (Region r : regionsByThread[index].values()) {
            r.saveDirtyChunks();
            r.savePlayers(worldDir, ioPool);
        }
    }
```
Region에 추가:
```java
    /** 접속 중 플레이어 위치 저장. */
    public void savePlayers(java.nio.file.Path worldDir, java.util.concurrent.ExecutorService io) {
        for (Player p : players.values()) {
            final Player fp = p;
            io.execute(() -> org.domi.furnace.server.entity.PlayerData.save(
                    worldDir, fp.uuid(), fp.x(), fp.y(), fp.z(), fp.yaw(), fp.pitch(), fp.onGround()));
        }
    }
```
FurnaceMain Step 2의 루프를 단순화 — 스레드 idx 타겟은 `scheduler.region(pos)`로 idx 스레드에 도달해야 함. RegionGrid.threadIndex로 idx에 매핑되는 RegionPos를 찾는 헬퍼:
```java
    private static org.domi.furnace.api.sched.RegionPos regionPosForThread(int idx, org.domi.furnace.server.config.Config cfg) {
        for (int x = 0; x < cfg.regionThreads() * 2 + 1; x++) {
            org.domi.furnace.api.sched.RegionPos rp = new org.domi.furnace.api.sched.RegionPos(x, 0);
            if (org.domi.furnace.server.world.RegionGrid.threadIndex(rp, cfg.regionThreads()) == idx) return rp;
        }
        return new org.domi.furnace.api.sched.RegionPos(idx, 0);
    }
```

- [ ] **Step 3: ConnectionHandler — 접속 시 playerdata 로드 후 스폰**

`onEnterPlay` 교체 — 위치를 playerdata서 읽어(IO 풀) 스폰:
```java
    private void onEnterPlay(ChannelHandlerContext ctx) {
        int eid = idAllocator.next();
        int dimIndex = Math.max(0,
                REGISTRIES.indexOf("minecraft:dimension_type", "minecraft:overworld"));
        send(ctx, new LoginPlay(eid, "minecraft:overworld", dimIndex, world.viewDistance()));
        double[] saved = org.domi.furnace.server.entity.PlayerData.load(world.worldDir(), playerUuid);
        double sx = saved != null ? saved[0] : 0;
        double sy = saved != null ? saved[1] : -60;
        double sz = saved != null ? saved[2] : 0;
        float syaw = saved != null ? (float) saved[3] : 0f;
        float spitch = saved != null ? (float) saved[4] : 0f;
        send(ctx, new SyncPlayerPosition(sx, sy, sz, 1));
        send(ctx, new SetDefaultSpawnPosition((int) sx, (int) sy, (int) sz));
        send(ctx, new GameEvent(GameEvent.START_WAITING_FOR_CHUNKS, 0f));
        Player p = new Player(eid, playerUuid, playerName, ctx.channel());
        p.publishPosition(sx, sy, sz, syaw, spitch, true);
        this.player = p;
        RegionPos spawn = RegionGrid.regionPos(sx, sz, world.regionSize());
        scheduler.region(spawn).execute(() -> world.regionAt(spawn).adopt(p));
        scheduleKeepAlive();
    }
```
> playerdata load는 작은 파일 동기 read(Netty 이벤트루프) — 경미. 엄밀히는 IO 풀로 빼야 하나 M4b 단순화(접속은 드묾). 향후 비동기화.

- [ ] **Step 4: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): wire Anvil persistence + load player on join + shutdown flush" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 12: 수동 검증

- [ ] **Step 1: 설정** — `furnace-server/furnace.properties`:
```
view-distance=8
entity-view-distance=8
region-size=4
region-threads=4
level-name=world
autosave-ticks=200
```

**A — 우리 월드 영속(먼저):**

- [ ] **Step 2:** `./gradlew :furnace-server:run` → 클라 접속(크리에이티브, superflat). 블록 몇 개 파괴. 위치 이동.
- [ ] **Step 3:** 서버 종료(Ctrl+C → shutdown hook flush). `world/region/*.mca` 생성 확인.
- [ ] **Step 4:** 재기동 + 재접속 → **파괴 블록 여전히 깸 + 마지막 위치 스폰**. ✅ A 통과.
- [ ] **Step 5:** autosave 확인 — 편집 후 종료 안 하고 ~10초(200틱) 대기 → `.mca` mtime 갱신.

**B — 바닐라 월드 로드(나중):**

- [ ] **Step 6:** 바닐라 1.21.1 월드 생성(build의 `minecraft_server.1.21.1.jar`로 또는 기존 월드) → `world/region/*.mca`를 Furnace `world/region/`에 복사.
- [ ] **Step 7:** 기동 + 접속 → **진짜 지형 렌더**(돌·흙·광물 제자리). 방향성 블록(통나무·계단)은 기본값(속성 근사 — M4d서 정확). ✅ B 통과.

- [ ] **문제 해결 가이드**
  - 재접속 시 블록 복구 → 저장 안 됨. shutdown hook flush·saveDirtyChunks·dirty 플래그 확인. `.mca` 생성 여부.
  - 클라 킥/청크 깨짐 → NBT/팰릿 인코딩 오류. AnvilChunkCodecTest 통과 재확인. bits 계산(max 4).
  - 바닐라 월드 공허/깨짐 → 섹션 Y 오프셋(−4) 또는 팰릿 data 길이/bits 불일치. 음수 청크좌표 섹터 인덱싱(`& 31`, `>> 5`).
  - 스폰 위치 이상 → playerdata Pos 순서(x,y,z) 또는 SyncPlayerPosition 좌표.

- [ ] **Step 8: 완료 커밋(수정 있었다면)**

```bash
git add furnace-server
git commit -m "fix(world): M4b persistence from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모

- **spec 커버리지:** §3.1 NBT=T1/T2, §3.2 RegionFile/Store=T3/T4, §3.3 청크코덱=T5, §3.4 async 로드=T8, §3.5 dirty/퇴출/저장=T6(dirty)+T9, §3.6 플레이어=T10, §3.7 종료=T11, §5 설정=T7. 전 항목 존재.
- **타입 일관성:** `NbtCompound`/`NbtList`/`NbtIo.write/read/writeGzip/readGzip`(T1/T2) · `RegionFile.readChunk/writeChunk`·`Compression`(T3) · `RegionFileStore.readChunk/writeChunk/closeAll`(T4) · `AnvilChunkCodec.toNbt(ChunkColumn,cx,cz)/toNbt(ChunkSnapshot)/fromNbt`·`BlockRegistry.nameOf/stateIdOrAir`(T5/T9) · `ChunkColumn.dirty/clearDirty`(T6) · `AnvilWorldSource.store()/codec()`(T6) · `World` 10-인자 생성자+`ioPool/worldDir/configurePersistence/saveChunkAsync/consumeAutosaveFlag/flushThread`(T8/T9/T10/T11) · `Region.loadChunk/saveDirtyChunks/savePlayers`(T8/T9/T11) · `PlayerData.save/load`(T10).
- **빌드 단위:** World 생성자 +1 인자(IO 풀, T8)는 모든 테스트 호출부를 T8 Step7서 원자 갱신. AnvilChunkCodec ChunkSnapshot 오버로드(T9 Step3b)는 T5 메서드 분리 의존 — T9서 함께.
- **위험:** autosave 플래그 region 간 1회 보장 느슨(dirty 멱등이라 안전, 정밀 배리어 향후). RegionFile 섹터 누수(rewrite-grow 시, 압축 정리 향후). playerdata 접속 동기 read(경미). 바닐라 바이옴 무시(단일 biomeId). 전부 spec 위험/향후에 정합.
- **회귀:** snapshotChunk async 체인 변경 — M4a CrossRegionChunkEdit/BlockBreak 테스트가 IO 풀 경유로도 통과해야(T8 호출부 갱신 시 IO 풀 주입). breakBlock은 chunkAt 동기 폴백 유지.
