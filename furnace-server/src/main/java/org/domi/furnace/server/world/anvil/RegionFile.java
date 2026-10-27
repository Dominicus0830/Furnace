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

    public RegionFile(Path path) throws IOException {
        this.path = path;
        Files.createDirectories(path.getParent());
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        if (raf.length() < 2L * SECTOR) {
            raf.setLength(2L * SECTOR); // 빈 헤더 2섹터
        }
        raf.seek(0);
        byte[] header = new byte[1024 * 4];
        raf.readFully(header); // 1024회 readInt(각 4syscall) 대신 1회 벌크 read
        for (int i = 0; i < 1024; i++) locations[i] = readIntAt(header, i * 4);
    }

    private static int readIntAt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static int index(int chunkX, int chunkZ) {
        return (chunkX & 31) + (chunkZ & 31) * 32;
    }

    /** 청크 압축해제 전 raw payload bytes 반환(없으면 null). payload = NBT 직렬 바이트(압축해제됨). */
    /** 헤더에 그 청크 자리가 있는가. */
    public boolean hasChunk(int chunkX, int chunkZ) { return locations[index(chunkX, chunkZ)] != 0; }

    public byte[] readChunk(int chunkX, int chunkZ) throws IOException {
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

    /** NBT 직렬 바이트를 zlib 압축해 섹터에 기록(in-place 가능하면 재사용, 아니면 append). */
    public void writeChunk(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        if (nbtBytes == null) throw new IllegalArgumentException("nbtBytes null");
        byte[] comp = Compression.deflate(nbtBytes);
        long total0 = comp.length + 5L;              // len(4)+type(1)+data
        long needed0 = (total0 + SECTOR - 1) / SECTOR;
        boolean external = needed0 > MAX_SECTORS;     // 1바이트 섹터 수에 안 들어간다 — 잘라 쓰면 헤더가 깨진다
        Path ext = externalPath(chunkX, chunkZ);
        if (external) {
            // 바닐라처럼 압축본은 .mcc 로, 본 파일엔 1섹터 스텁(len=1, type|0x80). 임시 파일 후 원자적 교체.
            Path tmp = ext.resolveSibling(ext.getFileName() + ".tmp");
            Files.write(tmp, comp);
            Files.move(tmp, ext, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
        int total = external ? 5 : (int) total0;
        int needed = external ? 1 : (int) needed0;   // 섹터 수(1..255)
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
        if (external) {
            raf.writeInt(1);
            raf.writeByte(2 | EXTERNAL_FLAG);         // zlib + 외부
        } else {
            raf.writeInt(comp.length + 1);
            raf.writeByte(2);                         // zlib
            raf.write(comp);
        }
        // 섹터 패딩
        int pad = needed * SECTOR - total;
        if (pad > 0) raf.write(new byte[pad]);
        locations[idx] = (offset << 8) | needed;
        raf.seek((long) idx * 4);
        raf.writeInt(locations[idx]);
        // 타임스탬프(헤더 두 번째 4KB)
        raf.seek(SECTOR + (long) idx * 4);
        raf.writeInt((int) (System.currentTimeMillis() / 1000));
        if (!external) Files.deleteIfExists(ext);   // 예전에 외부로 뺐던 청크가 다시 작아졌다
    }

    private int allocSectors(int count) throws IOException {
        int max = 2; // 헤더 2섹터 뒤부터
        for (int l : locations) {
            int off = l >>> 8, cnt = l & 0xFF;
            if (off + cnt > max) max = off + cnt;
        }
        if ((long) max + count > 0xFFFFFF) throw new IOException("영역 파일 섹터 오프셋 한도(24비트) 초과: " + path);
        raf.setLength((long) (max + count) * SECTOR);
        return max;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
