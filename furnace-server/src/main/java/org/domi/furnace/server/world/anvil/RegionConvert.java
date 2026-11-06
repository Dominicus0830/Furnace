// region 파일 압축 방식 변환 도구: 월드 폴더의 region·entities·poi 의 .mca 를 읽어 지정한 압축으로 다시 쓴다.
// 사용: java -cp furnace-server.jar org.domi.furnace.server.world.anvil.RegionConvert <월드 폴더> <deflate|gzip|lz4|none|zstd>
// zstd 로 쓴 월드는 바닐라·Paper 가 못 열므로, 되돌릴 때 deflate 로 변환한다. 서버를 끈 채 돌린다.
package org.domi.furnace.server.world.anvil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class RegionConvert {

    private RegionConvert() {}

    /** 변환한 청크 수. */
    public record Result(int files, int chunks) {}

    /** worldDir 아래 region/entities/poi 의 모든 .mca 청크를 method 로 다시 쓴다. */
    public static Result convert(Path worldDir, String method) throws IOException {
        int type = typeOf(method);
        int files = 0, chunks = 0;
        for (String sub : List.of("region", "entities", "poi")) {
            Path dir = worldDir.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            List<Path> mcas;
            try (Stream<Path> s = Files.list(dir)) {
                mcas = s.filter(p -> p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).sorted().toList();
            }
            for (Path mca : mcas) {
                String[] parts = mca.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
                try (RegionFile rf = new RegionFile(mca)) {
                    for (int i = 0; i < 1024; i++) {
                        int cx = rx * 32 + (i & 31), cz = rz * 32 + (i >> 5);
                        if (!rf.hasChunk(cx, cz)) continue;
                        RegionFile.Payload p = rf.readPayload(cx, cz);
                        if (p == null) continue;
                        if (p.type() == type) continue;
                        rf.writeCompressed(cx, cz, Compression.compress(type, p.decompress()), type);
                        chunks++;
                    }
                }
                files++;
            }
        }
        return new Result(files, chunks);
    }

    static int typeOf(String method) {
        return switch (method) {
            case "deflate" -> Compression.DEFLATE;
            case "gzip" -> Compression.GZIP;
            case "none" -> Compression.NONE;
            case "lz4" -> Compression.LZ4;
            case "zstd" -> Compression.ZSTD;
            default -> throw new IllegalArgumentException("모르는 압축 방식: " + method + " (deflate, gzip, lz4, none, zstd)");
        };
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("사용: RegionConvert <월드 폴더> <deflate|gzip|lz4|none|zstd>");
            System.exit(2);
        }
        Result r = convert(Path.of(args[0]), args[1]);
        System.out.println("region 파일 " + r.files() + "개, 청크 " + r.chunks() + "개를 " + args[1] + " 로 변환했다.");
    }
}
