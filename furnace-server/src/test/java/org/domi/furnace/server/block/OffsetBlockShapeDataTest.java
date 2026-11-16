// 위치 어긋남(offsetType) 블록의 바깥 모양 데이터: 바닐라 getShape 는 어긋남을 더하기 전의 SHAPE 를 주므로(어긋남은 getShape 가 호출하는 쪽이 더한다),
// 데이터의 모양은 어긋남을 뺀 원래 모양이어야 한다. 추출기가 원점의 어긋남을 뺀 값(+0.25)을 적던 오류로 키 큰 식물 14상태와 풀·해초·뿌리 26상태가 밀려 있었다
// (N2b-6 Paper 정답표·독립 검토가 찾음). 값은 바닐라 1.21.1 각 블록의 SHAPE 상수(Block.box 픽셀 ÷ 16).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OffsetBlockShapeDataTest {

    /** 블록 → 단일 상자 {minX,minY,minZ,maxX,maxY,maxZ}. 모든 상태가 같은 모양이다(모양을 가르는 속성이 없는 블록). */
    private static final Map<String, double[]> VANILLA = Map.ofEntries(
            Map.entry("minecraft:short_grass", new double[] {0.125, 0, 0.125, 0.875, 0.8125, 0.875}),   // box(2,0,2,14,13,14)
            Map.entry("minecraft:fern", new double[] {0.125, 0, 0.125, 0.875, 0.8125, 0.875}),
            Map.entry("minecraft:tall_seagrass", new double[] {0.125, 0, 0.125, 0.875, 1, 0.875}),   // box(2,0,2,14,16,14)
            Map.entry("minecraft:warped_roots", new double[] {0.125, 0, 0.125, 0.875, 0.8125, 0.875}),
            Map.entry("minecraft:crimson_roots", new double[] {0.125, 0, 0.125, 0.875, 0.8125, 0.875}),
            Map.entry("minecraft:nether_sprouts", new double[] {0.125, 0, 0.125, 0.875, 0.1875, 0.875}),   // box(2,0,2,14,3,14)
            Map.entry("minecraft:hanging_roots", new double[] {0.125, 0.625, 0.125, 0.875, 1, 0.875}));   // box(2,10,2,14,16,14)

    @Test
    void offsetBlocksCarryTheirVanillaShapeConstant() {
        var blocks = Blocks.get();
        var info = BlockInfo.get();
        for (var e : VANILLA.entrySet()) {
            Shape s = info.shape(blocks.defaultState(e.getKey()));
            assertEquals(1, s.boxCount(), e.getKey());
            assertArrayEquals(e.getValue(), s.box(0), 1e-9, e.getKey());
        }
    }

    @Test
    void smallDripleafAndTallPlantsAreFixedToo() {
        var blocks = Blocks.get();
        var info = BlockInfo.get();
        Shape dripleaf = info.shape(blocks.defaultState("minecraft:small_dripleaf"));
        assertArrayEquals(new double[] {0.125, 0, 0.125, 0.875, 0.8125, 0.875}, dripleaf.box(0), 1e-9);   // box(2,0,2,14,13,14)
        for (String tall : new String[] {"minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony", "minecraft:tall_grass", "minecraft:large_fern", "minecraft:pitcher_plant"}) {
            assertEquals(Shape.BLOCK, info.shape(blocks.defaultState(tall)), tall + " 은 Shapes.block()");
        }
    }
}
