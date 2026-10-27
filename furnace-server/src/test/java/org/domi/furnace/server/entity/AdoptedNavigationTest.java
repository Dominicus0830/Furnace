// 다른 region 레벨로 넘어간(adoptEntity) 몹의 길찾기는 새 레벨을 본다 — 생성 레벨에 묶여 있으면 옛 region 청크(여기선 빈 레벨)를 읽어 문 너머 침대에 못 닿았다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class AdoptedNavigationTest {

    /** 청크가 하나도 없는 레벨(넘겨 준 뒤의 옛 region 처럼 이 칸들을 소유하지 않는다). */
    static final class Empty extends BlockPlacementTest.TestLevel {
        @Override protected ChunkColumn chunk(int x, int z) { return null; }
    }

    @Test
    void adoptedVillagerPathsThroughDoorToBedInNewLevel() {
        var old = new Empty();
        var level = new BlockPlacementTest.TestLevel();
        int stone = B.defaultState("stone");
        // 5x5 방(벽 높이 2), 북쪽 벽 가운데 참나무 문
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x != 0 && x != 4 && z != 0 && z != 4) continue;
                for (int y = -60; y <= -59; y++) level.setBlock(new Pos(x, y, z), stone, 18);
            }
        }
        int door = B.defaultState("oak_door");
        var half = B.prop("half");
        level.setBlock(new Pos(2, -60, 0), half.with(door, "lower"), 18);
        level.setBlock(new Pos(2, -59, 0), half.with(door, "upper"), 18);
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) level.setBlock(new Pos(x, -58, z), stone, 18);   // 지붕
        Pos bed = new Pos(2, -60, 2);
        level.setBlock(bed, B.defaultState("red_bed"), 18);

        var v = new Villager(old, old.nextEntityId());
        v.moveTo(2.5, -60, -4.5, 0, 0);
        v.setOnGround(true);
        level.adoptEntity(v);
        assertSame(level, v.level());
        // AcquirePoi(HOME, 유효 범위 1) 와 같은 Set 오버로드 — 단일 Pos 오버로드는 단단한 블록(침대) 위로 목표를 올린다(GroundPathNavigation)
        Path p = v.getNavigation().createPath(java.util.Set.of(bed), 1);
        assertNotNull(p, "새 레벨에서 길을 찾는다");
        assertTrue(p.canReach(), "문을 지나 침대에 닿는다");
    }
}
