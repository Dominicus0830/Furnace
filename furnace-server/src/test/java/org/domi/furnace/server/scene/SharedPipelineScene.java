// 공유 파이프라인 장면(O04-18 `SharedPipelineBench hash`): 서버가 실제로 쓰는 `generateShared`(7×7 원시 청크 → 단일 레인 장식·후처리·완성)로
// 완성 청크를 요청 하나씩(결정적 순서) 만들어 블록·생성 유체 틱 NBT 를 해시한다. `NoiseIdentityBench`(원시 청크, 장식 전)가 못 보는 장식 레인·청크 표현을
// 바꾸는 항목(O04-01~03·08·11)의 결과 동일 증명 기준이다. 옛 `.java.txt` 시제품의 PROTO_COMPACT_OFFLANE 두 줄은 시제품 전용이라 뺐다(O04 답변).
//
// 엔티티가 없고 정적 상태를 쓰지 않으므로 같은 JVM 모드. 요청이 하나씩이라 완성 청크 내용은 스레드 수와 무관하다.
// 부동소수점·문자열·NBT 바이트만 해시한다(정체 해시 없음).
package org.domi.furnace.server.scene;

import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;

public final class SharedPipelineScene implements LibraryScene {

    private record Case(String dim, long seed) {}

    @Override public String name() { return "shared-pipeline"; }
    @Override public boolean freshJvm() { return false; }
    @Override public String unit() { return "chunk"; }
    @Override public String covers() { return "O04-18 완성 청크(장식 포함), O04-01~03·08·11(장식 레인·청크 표현)"; }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws java.io.IOException {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        java.util.List<Case> cases = new java.util.ArrayList<>();
        if (size.full()) {   // 옛 벤치와 같은 범위: 세 차원 × 시드 셋 × 두 곳 × 6×6
            for (String dim : new String[]{"overworld", "nether", "end"}) for (long seed : new long[]{12345L, 0L, -7L}) cases.add(new Case(dim, seed));
        } else {
            cases.add(new Case("overworld", 12345L));
            cases.add(new Case("overworld", -7L));
            cases.add(new Case("nether", 12345L));
            cases.add(new Case("end", 12345L));
        }
        int side = size.pick(3, 6);
        int[][] origins = size.full() ? new int[][]{{0, 0}, {3000, -2000}} : new int[][]{{0, 0}};
        int step = 0;
        for (Case c : cases) {
            NoiseWorldSource src = switch (c.dim) {
                case "nether" -> NoiseWorldSource.nether(c.seed, blocks, registries);
                case "end" -> NoiseWorldSource.end(c.seed, blocks, registries);
                default -> NoiseWorldSource.overworld(c.seed, blocks, registries);
            };
            for (int[] o : origins) {
                IdentityHash all = new IdentityHash();
                for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) {
                    probe.before(step);
                    ChunkColumn col = src.pipeline().generateShared(o[0] + x, o[1] + z).join();
                    probe.after(step++);
                    IdentityHash h = new IdentityHash();
                    for (int y = col.minY(); y <= col.maxY(); y++)
                        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) h.add(col.getBlock(lx, y, lz));
                    // 생성 중 남은 NBT(예약 유체 틱 등)는 내용으로 섞는다. NbtCompound 에는 toString 이 없어 옛 시제품의 String.valueOf 는 정체 해시를 해시했다.
                    if (col.extraNbt() != null) h.add(NbtIo.toBytes(col.extraNbt(), "")); else h.add("no-extra-nbt");
                    all.add(h.value());
                    if (!size.full()) rec.line("chunk " + c.dim + " " + c.seed + " " + (o[0] + x) + " " + (o[1] + z), h);
                }
                if (size.full()) rec.line("area " + c.dim + " " + c.seed + " " + o[0] + " " + o[1] + " side " + side, all);
            }
        }
    }
}
