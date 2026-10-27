// 단일 Anvil 영역 파일(.mca). 32x32 청크, 4KB 섹터, 8KB 헤더(위치+타임스탬프).
package org.domi.furnace.server.world.anvil;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RegionFile implements Closeable {

    private static final int SECTOR = 4096;
    /** 헤더의 섹터 수는 1바이트 — 이 이상이면 바닐라처럼 외부 .mcc 파일로 뺀다(RegionFile.isOversized). */
    static final int MAX_SECTORS = 255;
    private static final int EXTERNAL_FLAG = 0x80;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RegionFile.class);
    private final Path path;
    private final RandomAccessFile raf;
    private final int[] locations = new int[1024]; // (sectorOffset<<8)|sectorCount

    /** 헤더 8KB(위치 1024 + 타임스탬프 1024) 메모리 사본. 쓸 때마다 통째로 기록한다(바닐라 RegionFile.writeHeader). */
    private final byte[] header = new byte[2 * SECTOR];
    /** 사용 중 섹터 비트맵. 바닐라 RegionBitmap 과 같은 첫 적합 알고리즘(F-05). */
    private final SectorBitmap used = new SectorBitmap();
    /** 로드 시 헤더 항목끼리 섹터가 겹쳤는가. 겹치면 해제 때 남은 항목의 섹터를 다시 표시한다(바닐라에 없는 방어). */
    private boolean overlapping;
    /** 시험용 크래시 모의: 남은 기록 바이트 예산(음수면 끔). 다 쓰면 그 지점에서 SimulatedCrash. */
    long crashBudget = -1;
    /** 시험용: true 면 바닐라처럼 매번 헤더 8KB 를 통째로 쓴다(바이트 동일성 대조용). */
    boolean fullHeaderWrites;
    /** 시험용 시계(초). */
    java.util.function.IntSupplier clock = () -> (int) (System.currentTimeMillis() / 1000);
    /** 로드 때 무효 항목을 0 으로 지웠다 — 다음 기록 때 헤더를 통째로 써서 디스크에도 반영한다(바닐라와 같은 시점). */
    private boolean headerDirty;
    /** sync-chunk-writes: 바닐라처럼 기록마다 데이터 동기화(O_DSYNC, RandomAccessFile "rwd"). */
    final boolean sync;
    /** 시험용: close 때 force 호출 횟수. */
    int forceCount;

    static final class SimulatedCrash extends IOException {
        SimulatedCrash() { super("simulated crash"); }
    }

    public RegionFile(Path path) throws IOException { this(path, false); }

    /** sync 면 바닐라 RegionFile 의 DSYNC 열기와 같다(server.properties sync-chunk-writes). */
    public RegionFile(Path path, boolean sync) throws IOException {
        this.path = path;
        this.sync = sync;
        Files.createDirectories(path.getParent());
        this.raf = new RandomAccessFile(path.toFile(), sync ? "rwd" : "rw");
        long size = raf.length();
        if (size < 2L * SECTOR) {
            raf.setLength(2L * SECTOR); // 빈 헤더 2섹터
            if (size > 0) LOG.warn("[REGION] {} 헤더가 잘려 있음({}B)", path, size);
            size = 2L * SECTOR;
        }
        raf.seek(0);
        raf.readFully(header); // 1024회 readInt(각 4syscall) 대신 1회 벌크 read
        used.set(0, 2);
        // 바닐라 RegionFile 생성자와 같은 검사: 헤더와 겹침·0섹터·시작이 파일 밖이면 메모리에서 지운다
        for (int i = 0; i < 1024; i++) {
            int loc = readIntAt(header, i * 4);
            if (loc == 0) continue;
            int off = loc >>> 8, cnt = loc & 0xFF;
            if (off < 2) {
                LOG.warn("[REGION] {} 항목 {} 섹터 {} 가 헤더와 겹침", path, i, off);
                loc = 0;
            } else if (cnt == 0) {
                LOG.warn("[REGION] {} 항목 {} 섹터 수 0", path, i);
                loc = 0;
            } else if ((long) off * SECTOR > size) {
                LOG.warn("[REGION] {} 항목 {} 섹터 {} 가 파일 밖", path, i, off);
                loc = 0;
            } else {
                if (used.anySet(off, cnt)) {
                    overlapping = true;
                    LOG.warn("[REGION] {} 항목 {} 섹터 {}+{} 가 다른 청크와 겹침", path, i, off, cnt);
                }
                used.set(off, cnt);
            }
            locations[i] = loc;
            if (loc == 0 && readIntAt(header, i * 4) != 0) { writeIntAt(header, i * 4, 0); headerDirty = true; }
        }
    }

    private static void writeIntAt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24); b[off + 1] = (byte) (v >>> 16); b[off + 2] = (byte) (v >>> 8); b[off + 3] = (byte) v;
    }

    /** 크래시 모의를 거치는 기록. */
    private void pwrite(long pos, byte[] b, int off, int len) throws IOException {
        if (crashBudget >= 0 && crashBudget < len) {
            int part = (int) crashBudget;
            raf.seek(pos);
            raf.write(b, off, part);
            crashBudget = 0;
            throw new SimulatedCrash();
        }
        if (crashBudget >= 0) crashBudget -= len;
        raf.seek(pos);
        raf.write(b, off, len);
    }

    /** 파일 연산(이동·삭제) 직전 크래시 지점. 1바이트 예산을 쓴다. */
    private void step() throws IOException {
        if (crashBudget == 0) throw new SimulatedCrash();
        if (crashBudget > 0) crashBudget--;
    }

    /** 시험용: 항목 원값. */
    synchronized int location(int idx) { return locations[idx]; }

    /** 시험용: 사용 중 섹터 사본. */
    synchronized java.util.BitSet usedSectors() { return used.copy(); }

    private static int readIntAt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static int index(int chunkX, int chunkZ) {
        return (chunkX & 31) + (chunkZ & 31) * 32;
    }

    /** 청크 압축해제 전 raw payload bytes 반환(없으면 null). payload = NBT 직렬 바이트(압축해제됨). */
    /** 헤더에 그 청크 자리가 있는가. */
    public synchronized boolean hasChunk(int chunkX, int chunkZ) { return locations[index(chunkX, chunkZ)] != 0; }

    public synchronized byte[] readChunk(int chunkX, int chunkZ) throws IOException {
        int loc = locations[index(chunkX, chunkZ)];
        if (loc == 0) return null;
        int offset = loc >>> 8, count = loc & 0xFF;
        long fileSectors = raf.length() / SECTOR;
        // 바닐라 RegionFile.getChunkDataInputStream 처럼 깨진 헤더는 로그 후 없는 청크로 본다
        if (offset < 2 || count == 0 || (long) offset + count > fileSectors) {
            LOG.error("[REGION] {} 청크 {},{} 헤더 범위 이상(offset={}, count={}, 파일 {}섹터)", path, chunkX, chunkZ, offset, count, fileSectors);
            return null;
        }
        raf.seek((long) offset * SECTOR);
        byte[] head = new byte[5];
        raf.readFully(head); // len(4)+type(1) 단건 readInt/readByte(5syscall) 대신 1회 벌크 read
        int len = readIntAt(head, 0);
        int compType = head[4] & 0xFF;
        if (len <= 0 || (long) len + 4 > (long) count * SECTOR) {
            LOG.error("[REGION] {} 청크 {},{} 길이 이상(len={}, {}섹터)", path, chunkX, chunkZ, len, count);
            return null;
        }
        if ((compType & EXTERNAL_FLAG) != 0) {
            if (len != 1) LOG.warn("[REGION] {} 청크 {},{} 외부 청크인데 본문 {}B 무시", path, chunkX, chunkZ, len - 1);
            Path ext = externalPath(chunkX, chunkZ);
            if (!Files.isRegularFile(ext)) {
                LOG.error("[REGION] {} 외부 청크 파일 없음: {}", path, ext);
                return null;
            }
            return Compression.decompress(compType & ~EXTERNAL_FLAG, Files.readAllBytes(ext));
        }
        byte[] comp = new byte[len - 1];
        raf.readFully(comp);
        return Compression.decompress(compType, comp);
    }

    /** 바닐라 RegionFile.getExternalChunkPath: 같은 디렉토리의 c.<x>.<z>.mcc (절대 청크 좌표). */
    Path externalPath(int chunkX, int chunkZ) {
        return path.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
    }

    /** NBT 직렬 바이트를 zlib 압축해 기록. 항상 새 자리에 쓰고 헤더를 바꾼 뒤 옛 섹터를 놓는다(바닐라 RegionFile.write). */
    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        if (nbtBytes == null) throw new IllegalArgumentException("nbtBytes null");
        writeCompressed(chunkX, chunkZ, Compression.deflate(nbtBytes));
    }

    /**
     * 이미 zlib(type 2)로 압축한 본문을 기록. RegionFileStore 가 파일 잠금 밖에서 압축한 뒤 부른다(F-01).
     * 순서(크래시 일관성): [외부면 .mcc 임시→원자 교체] → 새 섹터에 데이터 → 헤더 8KB → [내부면 옛 .mcc 삭제] → 옛 섹터 해제.
     * 헤더가 바뀌기 전까지 옛 섹터는 비트맵에 남아 있어 새 데이터가 옛 데이터를 덮지 않는다. 섹터 할당은 바닐라와 같은 첫 적합.
     */
    synchronized void writeCompressed(int chunkX, int chunkZ, byte[] comp) throws IOException {
        long total0 = comp.length + 5L;              // len(4)+type(1)+data
        long needed0 = (total0 + SECTOR - 1) / SECTOR;
        boolean external = needed0 > MAX_SECTORS;     // 바닐라: sectors >= 256 이면 외부
        Path ext = externalPath(chunkX, chunkZ);
        if (external) {
            // 압축본은 .mcc 로, 본 파일엔 1섹터 스텁(len=1, type|0x80). 임시 파일 후 원자적 교체.
            // 바닐라는 헤더 뒤에 교체하지만, 먼저 교체해야 어느 지점의 크래시에서도 옛 값/새 값 중 하나가 읽힌다.
            Path tmp = ext.resolveSibling(ext.getFileName() + ".tmp");
            step();
            Files.write(tmp, comp);
            step();
            Files.move(tmp, ext, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
        int total = external ? 5 : (int) total0;
        int needed = external ? 1 : (int) needed0;   // 섹터 수(1..255)
        int idx = index(chunkX, chunkZ);
        int oldLoc = locations[idx];
        int offset = used.allocate(needed);          // 옛 섹터를 쥔 채 새 자리(바닐라와 같다 — in-place 덮어쓰기 없음)
        if ((long) offset + needed > 0xFFFFFF) {
            used.clear(offset, needed);
            throw new IOException("영역 파일 섹터 오프셋 한도(24비트) 초과: " + path);
        }
        // 실패하면 새 섹터는 표시된 채 둔다(누수일 뿐 손상은 아니다 — 헤더가 이미 새 자리를 가리켰을 수도 있다)
        byte[] buf = new byte[needed * SECTOR];      // 섹터 패딩 포함 — 파일 길이가 항상 섹터 배수
        if (external) {
            writeIntAt(buf, 0, 1);
            buf[4] = (byte) (2 | EXTERNAL_FLAG);      // zlib + 외부
        } else {
            writeIntAt(buf, 0, comp.length + 1);
            buf[4] = 2;                               // zlib
            System.arraycopy(comp, 0, buf, 5, comp.length);
        }
        pwrite((long) offset * SECTOR, buf, 0, buf.length);
        int newLoc = (offset << 8) | needed;
        locations[idx] = newLoc;
        writeIntAt(header, idx * 4, newLoc);
        writeIntAt(header, SECTOR + idx * 4, clock.getAsInt());
        // 바닐라는 8KB 를 통째로 쓰지만 디스크와 메모리 사본이 같으면 바뀐 두 항목만 쓴 결과와 바이트가 같다.
        // 각 항목은 4바이트 정렬이라 섹터 경계에서 찢어지지 않는다. 위치 항목 → 타임스탬프 순.
        // sync 면 기록마다 동기화가 붙어 두 번 쓰기가 한 번(8KB)보다 느리다(측정: f05 설계서 6장) → 통째로 한 번.
        if (fullHeaderWrites || headerDirty || sync) {
            pwrite(0, header, 0, header.length);
            headerDirty = false;
        } else {
            pwrite((long) idx * 4, header, idx * 4, 4);
            pwrite(SECTOR + (long) idx * 4, header, SECTOR + idx * 4, 4);
        }
        if (!external) {
            step();
            Files.deleteIfExists(ext);               // 예전에 외부로 뺐던 청크가 다시 작아졌다
        }
        if (oldLoc != 0) release(oldLoc);
    }

    /** 옛 섹터 해제. 로드 때 겹침이 있었으면 아직 살아 있는 다른 항목의 섹터를 다시 표시한다. */
    private void release(int loc) {
        used.clear(loc >>> 8, loc & 0xFF);
        if (!overlapping) return;
        used.set(0, 2);
        for (int l : locations) if (l != 0) used.set(l >>> 8, l & 0xFF);
    }

    @Override
    public synchronized void close() throws IOException {
        // 바닐라 RegionFile.close: sync 여부와 상관없이 force(true) 후 닫는다
        try {
            raf.getChannel().force(true);
            forceCount++;
        } finally {
            raf.close();
        }
    }
}
