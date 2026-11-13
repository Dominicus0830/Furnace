// 호환 기준 장면 compat-block-reads — 모든 블록 상태를 한 번씩 깔고 Bukkit 블록 읽기(getType·getBlockData·getAsString·isEmpty 등)를 해시한다.
// T-072(상태 id → Material·BlockData 표)가 "전 상태에서 결과 같음"을 증명하는 바탕이다. 그 TASK 의 스레드 축(소유 region·다른 region·전역 빌림·비동기)은
// 장면 SceneWorldHandles 가 맡는다. 블록을 FAST(이웃 갱신 없음)로 깔고 틱을 내지 않으므로 읽는 값이 상태 그대로다.
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
                String[] cur = s.onRegion(corner, () -> sweep(bw, total, size));
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
    private static String[] sweep(World bw, int total, Size size) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Set<String> materials = new java.util.TreeSet<>();
        int errors = 0;
        IdentityHash h = hash();
        int start = 0;
        for (int i = 0; i < total; i++) {
            Block b = bw.getBlockAt(x(i), y(i), z(i));
            String type = safe(() -> b.getType().name());
            String data = safe(() -> b.getBlockData().getAsString());
            String dm = safe(() -> b.getBlockData().getMaterial().name());
            h.add(i).add(type).add(data).add(dm);
            h.add(safe(() -> b.isEmpty())).add(safe(() -> b.isLiquid()));
            h.add(safe(() -> b.getType().isAir())).add(safe(() -> b.getType().isBlock())).add(safe(() -> b.getType().getKey()));
            h.add(safe(() -> b.getBlockData().getAsString(true)));
            h.add(safe(() -> b.getState().getClass().getSimpleName()));
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
        out.add("unreadable " + errors);
        out.add("materials " + materials.size());
        return out.toArray(String[]::new);
    }
}
