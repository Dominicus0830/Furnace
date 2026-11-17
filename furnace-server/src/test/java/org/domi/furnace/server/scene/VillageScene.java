// 마을 장면(O06-15 `village`): 침대·직업 블록(11종)·종이 있는 마을에 주민이 흩어져 있고, 하루 시간표(일 시작 직전 → 일 → 모임)를 따라 움직인다.
// 직업 얻기·침대 찾기·일터로 걷기(길찾기)·POI 점유가 모두 돈다. O06-01·02(주민 산책 창)·05(길찾기 표)·08(두뇌 고정 비용)·12 의 결과 동일 증명 기준.
// 엔티티가 있으므로 새 JVM 모드. 한계: 한 region 단일 스레드, 자연 스폰·철 골렘 소환 외 월드 이벤트 없음.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class VillageScene extends TickScene {
    private static final int FLOOR = SceneLevel.FLOOR_Y;
    private static final String[] JOBS = {"minecraft:composter", "minecraft:barrel", "minecraft:lectern", "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:cartography_table", "minecraft:fletching_table", "minecraft:grindstone", "minecraft:loom", "minecraft:smithing_table", "minecraft:stonecutter"};

    private SceneLevel level;

    @Override public String name() { return "village"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "O06-15 마을, O06-01·02(주민 산책 창)·05(길찾기 표)·08·12, POI 점유"; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("village", 3);
        level.dayTime = 1900;   // 일 시작(2000틱) 직전: 직업 얻기 → 일터로 걷기를 곧바로 본다
        level.gameTime = 1900;
        int cells = size.pick(4, 8);   // 집 cells×cells 칸(8 블록 간격)
        int house = 0;
        for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
            int hx = (i - cells / 2) * 8 + 2, hz = (j - cells / 2) * 8 + 2;
            level.setBlock(new Pos(hx, FLOOR, hz + 1), B.defaultState("minecraft:red_bed"), 3);
            level.setBlock(new Pos(hx, FLOOR, hz), B.prop("part").with(B.defaultState("minecraft:red_bed"), "head"), 3);
            level.setBlock(new Pos(hx + 3, FLOOR, hz), Blocks.get().defaultState(JOBS[house % JOBS.length]), 3);
            house++;
        }
        level.setBlock(new Pos(0, FLOOR, 0), Blocks.get().defaultState("minecraft:bell"), 3);
        java.util.Random r = new java.util.Random(3);
        int n = size.pick(16, 100);
        for (int i = 0; i < n; i++) {
            var v = new Villager(level, level.nextEntityId());
            v.moveTo(r.nextInt(8 * cells) - 4 * cells + 0.5, FLOOR, r.nextInt(8 * cells) - 4 * cells + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(v);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(300, 1200); }

    @Override protected int checkEvery(Size size) { return 100; }

    @Override protected void tick(int t) { level.tick(); }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        rec.line("village " + label + " entities", SceneKit.entities(level));
        rec.line("village " + label + " random", new IdentityHash().add(level.random().nextLong()));
        int employed = 0, total = 0;
        for (var e : level.entities().tickSnapshot()) if (e instanceof Villager v) { total++; if (!"none".equals(v.profession())) employed++; }
        rec.text("village " + label + " villagers=" + total + " employed=" + employed);
    }
}
