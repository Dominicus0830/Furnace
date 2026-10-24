// 길찾기: 평지 직선, 벽 우회, 한 칸 오르기, 못 가는 곳(부분 경로), 몹이 경로를 따라 걸어 도착한다.
package org.domi.furnace.server.entity.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NavigationTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Walker extends Mob {
        Walker(ChunkLevel level, int id) { super(EntityType.VILLAGER, level, id); }
        @Override protected void createAttributes(org.domi.furnace.server.attribute.AttributeMap map) {
            super.createAttributes(map);
            map.add(org.domi.furnace.server.attribute.Attribute.FOLLOW_RANGE, 35.0);   // 방문 노드 한도 = 따라가기 범위 × 16
        }
        @Override protected void addAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) { super.addAdditionalSaveData(tag); }
    }

    private static Walker walker(ChunkLevel level) {
        var w = new Walker(level, level.nextEntityId());
        w.moveTo(0.5, -60, 0.5, 0, 0);
        w.setOnGround(true);
        return w;
    }

    private static void wall(ChunkLevel level, int x, int z0, int z1, int y0, int y1) {
        int stone = B.defaultState("stone");
        for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) level.setBlock(new Pos(x, y, z), stone, 2);
    }

    private static void step(Walker w) {
        w.setOldPosAndRot();
        w.tickCount++;
        w.tick();
    }

    @Test
    void straightPathOnFlatGround() {
        var level = new BlockPlacementTest.TestLevel();
        var w = walker(level);
        var path = w.getNavigation().createPath(6.5, -60, 0.5, 1);
        assertNotNull(path);
        assertTrue(path.canReach());
        assertEquals(6, path.getEndNode().x, 1);
        assertEquals(-60, path.getEndNode().y);
    }

    @Test
    void goesAroundAWall() {
        var level = new BlockPlacementTest.TestLevel();
        wall(level, 3, -3, 3, -60, -59);   // 2칸 높이, 폭 7
        var w = walker(level);
        var path = w.getNavigation().createPath(6.5, -60, 0.5, 1);
        assertNotNull(path);
        assertTrue(path.canReach());
        boolean wentAside = false;
        for (int i = 0; i < path.getNodeCount(); i++) if (Math.abs(path.getNode(i).z) >= 4) wentAside = true;
        assertTrue(wentAside, "벽 끝(|z|>=4)을 돌아가야 한다");
    }

    @Test
    void stepsUpOneBlock() {
        var level = new BlockPlacementTest.TestLevel();
        wall(level, 3, -3, 3, -60, -60);   // 1칸 높이 계단
        var w = walker(level);
        var path = w.getNavigation().createPath(5.5, -59, 0.5, 1);
        assertNotNull(path);
        assertTrue(path.canReach(), "한 칸 오르기는 점프로 갈 수 있다");
    }

    @Test
    void unreachableTargetGivesPartialPath() {
        var level = new BlockPlacementTest.TestLevel();
        wall(level, 3, -40, 40, -60, -59);   // 청크 제한 안에서 사실상 막힌 벽
        var w = walker(level);
        var path = w.getNavigation().createPath(6.5, -60, 0.5, 1);
        assertNotNull(path);
        assertFalse(path.canReach());
    }

    @Test
    void mobWalksTheComputedPathAndArrives() {
        var level = new BlockPlacementTest.TestLevel();
        wall(level, 3, -3, 3, -60, -59);
        var w = walker(level);
        assertTrue(w.getNavigation().moveTo(6.5, -60, 0.5, 1.0));
        for (int t = 0; t < 400 && w.getNavigation().isInProgress(); t++) step(w);
        double dx = w.x() - 6.5, dz = w.z() - 0.5;
        assertTrue(Math.sqrt(dx * dx + dz * dz) < 1.5, "도착: " + w.x() + "," + w.z());
    }
}
