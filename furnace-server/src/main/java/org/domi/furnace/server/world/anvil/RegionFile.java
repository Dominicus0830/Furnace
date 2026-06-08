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
