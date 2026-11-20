package net.minecraft.world.level.chunk.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.world.level.ChunkPos;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 지역 파일 하나(.mca, Mojang 이름 shim, NMS shim 4번 묶음 — Orebfuscator 가 자기 캐시 폴더에 쓰는 파일). 서버의 anvil 지역 파일 구현을 그대로 쓴다:
 * 섹터 4096 바이트, 헤더 위치·시각 표, 255 섹터 넘는 청크는 같은 폴더의 {@code c.<x>.<z>.mcc}, 새 자리에 쓰고 헤더를 바꾼 뒤 옛 자리를 놓는 순서까지 바닐라와 같다.
 * 메서드는 스레드 안전하다(파일 하나에 잠금 하나 — 바닐라도 읽기·쓰기가 synchronized).
 * <ul>
 *   <li>외부 청크 폴더는 이 파일과 같은 폴더만 된다(바닐라 월드 저장·Orebfuscator 가 그렇게 쓴다). 다른 폴더면 {@link UnsupportedNmsCall}.</li>
 *   <li>압축 종류는 {@link RegionFileVersion} 의 gzip·deflate·none·lz4. 읽을 때는 청크마다 적힌 종류로 푼다.</li>
 *   <li>월드 저장 파일을 열지 않는다 — 서버 저장과 같은 파일을 따로 쓰게 된다(바닐라·Paper 도 같다).</li>
 * </ul>
 */
public class RegionFile implements AutoCloseable {

    private static final String WHAT = "RegionFile";

    final RegionStorageInfo info;
    private final Path path;
    private final Path externalFileDir;
    final RegionFileVersion version;
    private final ChunkInternals.RegionFileAccess file;

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, boolean dsync) throws IOException {
        this(info, path, externalFileDir, RegionFileVersion.getCompressionFormat(), dsync);
    }

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, RegionFileVersion version, boolean dsync) throws IOException {
        if (version == RegionFileVersion.VERSION_CUSTOM) {
            throw new UnsupportedNmsCall(WHAT, "compression " + version + " - use gzip, deflate, none or lz4");
        }
        if (!java.nio.file.Files.isDirectory(externalFileDir)) throw new IllegalArgumentException("Expected directory, got " + externalFileDir.toAbsolutePath());
        Path dir = path.toAbsolutePath().normalize().getParent();
        if (!externalFileDir.toAbsolutePath().normalize().equals(dir)) {
            throw new UnsupportedNmsCall(WHAT, "external chunk folder " + externalFileDir + " other than the region file's folder " + dir);
        }
        this.info = info;
        this.path = path;
        this.externalFileDir = externalFileDir;
        this.version = version;
        this.file = org.domi.furnace.api.Furnace.server().internals().chunks().openRegionFile(path, dsync);
    }

    public Path getPath() { return path; }

    /** 바닐라: 그 청크 본문을 푼 스트림. 없거나 깨졌으면 null. */
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException {
        byte[] data = file.read(pos.x, pos.z);
        return data == null ? null : new DataInputStream(new ByteArrayInputStream(data));
    }

    /** 바닐라: 쓰기 스트림 — 닫을 때 이 파일의 압축 종류로 그 청크 자리에 써 넣는다. */
    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) throws IOException {
        int x = pos.x, z = pos.z;
        return new DataOutputStream(new ByteArrayOutputStream(8096) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (closed) return;
                closed = true;
                file.write(x, z, toByteArray(), version.getId());
            }
        });
    }

    /** 바닐라: 헤더에 그 청크 자리가 있는가. */
    public boolean hasChunk(ChunkPos pos) { return file.hasChunk(pos.x, pos.z); }

    public void flush() throws IOException { file.flush(); }

    @Override
    public void close() throws IOException { file.close(); }
}
