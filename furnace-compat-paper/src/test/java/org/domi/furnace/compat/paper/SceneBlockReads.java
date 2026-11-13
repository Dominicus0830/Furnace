// 호환 기준 장면 compat-block-reads — 모든 블록 상태를 한 번씩 깔고 Bukkit 블록 읽기(getType·getBlockData·getAsString·isEmpty 등)를 해시한다.
// T-072(상태 id → Material·BlockData 표)가 "전 상태에서 결과 같음"을 증명하는 바탕이다. 그 TASK 의 스레드 축(소유 region·다른 region·전역 빌림·비동기)은
// 장면 SceneWorldHandles 가 맡는다. 블록을 FAST(이웃 갱신 없음)로 깔고 틱을 내지 않지만, 놓일 때 스스로 반응하는 블록(레드스톤 선·불·용암 등, 약 7.7%)은 다른 블록이 된다 —
// 그런 칸은 읽기 해시에서 빼고 개수만 placed-mismatch 줄에 적는다(읽기 경로 시험이 놓기 반응에 묶이지 않게).
package org.domi.furnace.compat.paper;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.api.world.BlockEditOptions;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.plugin.ApiConvert;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.test.FurnaceTestServer;

public final class SceneBlockReads extends CompatScene {

    private static final int Y0 = -60;
    private static final int PER_LINE = 2048;

    @Override public String name() { return "compat-block-reads"; }

    @Override public String unit() { return "sweep"; }

    @Override public String covers() { return "T-072 Block.getType·getBlockData 상태 id 표(전 상태), T-075"; }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int total = reg.totalStateCount();
        try (FurnaceTestServer s = server()) {
            s.loadChunk(0, 0);
            var furnaceWorld = s.overworld();
            BlockPos corner = new BlockPos(0, Y0, 0);
            s.onRegion(corner, () -> {
                furnaceWorld.editBlocksNow(BlockEditOptions.FAST, ed -> {
                    for (int i = 0; i < total; i++) ed.set(x(i), y(i), z(i), ApiConvert.block(i, reg));
                });
                return null;
            });
            World bw = Compat.world("minecraft:overworld");
            rec.text("states " + total);
            int rounds = size.pick(1, 25);
            String[] lines = null;
            for (int round = 0; round < rounds; round++) {
                probe.before(round);
                String[] cur = s.onRegion(corner, () -> sweep(bw, furnaceWorld, reg, total, size));
                probe.after(round);
                // 같은 장면 안에서도 읽기를 되풀이했을 때 값이 안 변해야 한다(캐시·표가 상태를 오염시키지 않는다)
                if (lines != null && !java.util.Arrays.equals(lines, cur)) throw new AssertionError("되풀이 읽기 결과가 달라졌다(" + round + "번째 훑기)");
                lines = cur;
            }
            for (String l : lines) rec.text(l);
        }
    }

    private static int x(int i) { return i & 15; }
    private static int z(int i) { return (i >> 4) & 15; }
    private static int y(int i) { return Y0 + (i >> 8); }

    /** 상태 id 순으로 읽어 PER_LINE 개마다 한 줄(구간 해시 + 못 읽은 수·서로 다른 Material 수). */
    private static String[] sweep(World bw, org.domi.furnace.api.world.World fw, BlockRegistry reg, int total, Size size) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Set<String> materials = new java.util.TreeSet<>();
        int errors = 0, mismatch = 0;
        java.util.Map<String, Integer> mismatchTypes = new java.util.TreeMap<>();
        java.util.Set<String> stateClasses = new java.util.TreeSet<>();
        IdentityHash h = hash();
        int start = 0;
        for (int i = 0; i < total; i++) {
            // 놓을 때 onPlace 가 반응해 다른 블록이 된 칸(레드스톤 선·불·용암 등)은 읽기 해시에서 뺀다 — 읽기 경로 시험이 놓기 반응에 묶이지 않게.
            // 몇 개가 빠졌는지는 따로 한 줄(placed-mismatch)에 적는다.
            var placed = fw.blockNow(new BlockPos(x(i), y(i), z(i))).map(org.domi.furnace.api.world.BlockSnapshot::data).orElse(null);
            if (placed == null || !placed.equals(ApiConvert.block(i, reg))) {
                mismatch++;
                mismatchTypes.merge(ApiConvert.block(i, reg).type(), 1, Integer::sum);
                if ((i + 1) % PER_LINE == 0 || i + 1 == total) {
                    out.add("states " + start + "-" + i + " " + IdentityHash.hex(h.value()));
                    start = i + 1;
                    h = hash();
                }
                continue;
            }
            Block b = bw.getBlockAt(x(i), y(i), z(i));
            String type = safe(() -> b.getType().name());
            String data = safe(() -> b.getBlockData().getAsString());
            String dm = safe(() -> b.getBlockData().getMaterial().name());
            h.add(i).add(type).add(data).add(dm);
            h.add(safe(() -> b.isEmpty())).add(safe(() -> b.isLiquid()));
            h.add(safe(() -> b.getType().isAir())).add(safe(() -> b.getType().isBlock())).add(safe(() -> b.getType().getKey()));
            h.add(safe(() -> b.getBlockData().getAsString(true)));
            stateClasses.add(safe(() -> b.getState().getClass().getSimpleName()));
            h.add(safe(() -> b.getLocation().toVector()));
            // 읽은 BlockData 가 다시 파싱돼 같은 상태가 되는가(문자열 왕복)
            h.add(safe(() -> org.bukkit.Bukkit.createBlockData(b.getBlockData().getAsString()).equals(b.getBlockData())));
            h.add(b.getX()).add(b.getY()).add(b.getZ()).add(b.getWorld().getName());
            if (type.startsWith("!")) errors++; else materials.add(type);
            if ((i + 1) % PER_LINE == 0 || i + 1 == total) {
                out.add("states " + start + "-" + i + " " + IdentityHash.hex(h.value()));
                start = i + 1;
                h = hash();
            }
        }
        out.add("read " + (total - mismatch) + " placed-mismatch " + mismatch + " " + IdentityHash.hex(hash().add(mismatchTypes.toString()).value()));
        // getState() 클래스 이름은 따로 한 줄 — 어댑터에 TileState(상자·화로 등)를 넣는 PR 은 이 줄만 바꾼다(읽기 경로 해시에는 안 섞는다)
        out.add("state-classes " + stateClasses);
        out.add("unreadable " + errors);
        out.add("materials " + materials.size());
        return out.toArray(String[]::new);
    }
}
