// 블록 충돌 모양이 바뀌면 근처에서 길을 가는 몹이 길을 다시 찾는다(바닐라 ServerLevel.sendBlockUpdated → navigatingMobs.shouldRecomputePath →
// recomputePath). 20틱 안에 또 바뀌면 다시 찾기를 미뤘다가 navigation.tick 에서 21틱째에 한다.
package org.domi.furnace.server.entity.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NavigationBlockUpdateTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    private static final class Clocked extends BlockPlacementTest.TestLevel {
        long time = 100;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long gameTime() { return time; }
            };
        }
    }

    static final class Walker extends Mob {
        Walker(Clocked level, int id) { super(EntityType.VILLAGER, level, id); }
        @Override protected void createAttributes(org.domi.furnace.server.attribute.AttributeMap map) {
            super.createAttributes(map);
            map.add(org.domi.furnace.server.attribute.Attribute.FOLLOW_RANGE, 35.0);
        }
    }

    private static Walker walking(Clocked level) {
        var w = new Walker(level, level.nextEntityId());
        w.moveTo(0.5, -60, 0.5, 0, 0);
        w.setOnGround(true);
        assertTrue(level.addFreshEntity(w));
        assertTrue(w.getNavigation().moveTo(10.5, -60, 0.5, 1.0), "평지 직선 길");
        return w;
    }

    private static int stone() { return B.defaultState("minecraft:stone"); }

    private static boolean detours(Path p) {
        for (int i = 0; i < p.getNodeCount(); i++) if (Math.abs(p.getNode(i).z) >= 4) return true;
        return false;
    }

    @Test
    void wallOnThePathRecomputesAndDelaysTheRest() {
        var level = new Clocked();
        var w = walking(level);
        var nav = w.getNavigation();
        Path first = nav.getPath();
        assertFalse(detours(first));

        // 첫 블록: 마지막 다시 찾기(0)에서 20틱이 넘었으니 바로 다시 찾는다.
        level.setBlock(new Pos(4, -60, 0), stone(), BlockLevel.UPDATE_ALL);
        Path second = nav.getPath();
        assertNotSame(first, second, "충돌 모양이 바뀐 칸이 길 근처면 다시 찾는다");

        // 같은 틱의 나머지 벽: 20틱 안이라 미루기만 한다(길은 그대로).
        for (int z = -3; z <= 3; z++) for (int y = -60; y <= -59; y++) level.setBlock(new Pos(4, y, z), stone(), BlockLevel.UPDATE_ALL);
        assertSame(second, nav.getPath(), "20틱 안의 다시 찾기는 미룬다");

        level.time += 20;   // 120 − 100 = 20 은 아직 아니다
        nav.tick();
        assertSame(second, nav.getPath());
        level.time += 1;    // 121 − 100 > 20
        nav.tick();
        Path third = nav.getPath();
        assertNotSame(second, third, "미룬 다시 찾기는 navigation.tick 이 한다");
        assertTrue(detours(third), "다 지은 벽을 돌아간다");
    }

    @Test
    void doorClosingRecomputes() {
        var level = new Clocked();
        Pos lower = new Pos(4, -60, 2), upper = lower.above();
        level.setBlock(lower, door("lower", true), BlockLevel.UPDATE_ALL);
        level.setBlock(upper, door("upper", true), BlockLevel.UPDATE_ALL);
        var w = walking(level);
        Path before = w.getNavigation().getPath();
        level.setBlock(lower, door("lower", false), BlockLevel.UPDATE_CLIENTS | 16);
        assertNotSame(before, w.getNavigation().getPath(), "문이 닫히면(충돌 모양 바뀜) 다시 찾는다");
    }

    @Test
    void sameShapeOrNoClientUpdateOrFarDoesNothing() {
        var level = new Clocked();
        var w = walking(level);
        var nav = w.getNavigation();
        Path p = nav.getPath();
        int grass = B.defaultState("minecraft:short_grass");
        // 빈 모양 → 빈 모양
        level.setBlock(new Pos(4, -60, 1), grass, BlockLevel.UPDATE_ALL);
        assertSame(p, nav.getPath(), "충돌 모양이 그대로면(공기 → 풀) 그대로");
        // 꽉 찬 블록 → 꽉 찬 블록(땅)
        level.setBlock(new Pos(4, -61, 0), B.defaultState("minecraft:cobblestone"), BlockLevel.UPDATE_ALL);
        assertSame(p, nav.getPath(), "충돌 모양이 그대로면(땅 → 조약돌) 그대로");
        // UPDATE_CLIENTS 없이(바닐라는 sendBlockUpdated 를 부르지 않는다)
        level.setBlock(new Pos(4, -60, 0), stone(), BlockLevel.UPDATE_NEIGHBORS);
        assertSame(p, nav.getPath(), "UPDATE_CLIENTS 가 없으면 그대로");
        // 길 중점(몹·끝 노드 가운데)에서 남은 노드 수보다 먼 칸
        level.setBlock(new Pos(5, -60, 20), stone(), BlockLevel.UPDATE_ALL);
        assertSame(p, nav.getPath(), "먼 칸은 그대로");
    }

    @Test
    void mobWithoutPathOrRemovedIsIgnored() {
        var level = new Clocked();
        var w = walking(level);
        w.getNavigation().stop();
        level.setBlock(new Pos(4, -60, 0), stone(), BlockLevel.UPDATE_ALL);
        assertNull(w.getNavigation().getPath(), "길이 없으면 아무것도");

        var v = walking(level);
        Path p = v.getNavigation().getPath();
        level.entities().remove(v);
        level.setBlock(new Pos(4, -59, 0), stone(), BlockLevel.UPDATE_ALL);
        assertSame(p, v.getNavigation().getPath(), "레벨에서 빠진 몹은 훑지 않는다");
        assertFalse(level.entities().mobs().contains(v));
    }

    @Test
    void shapeDiffersIsVolumeNotBoxList() {
        Shape full = Shape.BLOCK;
        Shape split = new Shape(new double[] {0, 0, 0, 1, 0.5, 1, 0, 0.5, 0, 1, 1, 1});
        Shape slab = new Shape(new double[] {0, 0, 0, 1, 0.5, 1});
        assertFalse(Shape.differs(full, split), "상자 쪼개기만 다르면 같다");
        assertFalse(Shape.differs(Shape.EMPTY, Shape.EMPTY));
        assertTrue(Shape.differs(full, Shape.EMPTY));
        assertTrue(Shape.differs(Shape.EMPTY, slab));
        assertTrue(Shape.differs(full, slab));
        assertTrue(Shape.differs(slab, full));
    }

    private static int door(String half, boolean open) {
        return REG.stateIdOrAir("minecraft:oak_door", java.util.Map.of("facing", "west", "half", half, "hinge", "left",
                "open", open ? "true" : "false", "powered", "false"));
    }
}
