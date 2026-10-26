// 말 탑승: 빈손으로 올라타기, 길들이기(RunAroundLikeCrazyGoal 템퍼 판정의 난수 순서), 안장 얹기와 조종, 탄 동안 서버 이동(회전만·속도 0),
// 내릴 자리(DismountHelper), 점프 명령.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.goal.RunAroundLikeCrazyGoal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HorseRidingTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Horse horse(BlockPlacementTest.TestLevel level) {
        Horse h = new Horse(level, level.nextEntityId());
        h.moveTo(0.5, -60, 0.5, 0, 0);
        h.setOnGround(true);
        return h;
    }

    private static Player player() {
        Player p = new Player(950, UUID.randomUUID(), "rider", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 30.0F, 20.0F, true);
        return p;
    }

    /** 엔티티 난수를 씨앗 있는 것으로 바꾼다(시험에서 바닐라 순서를 그대로 따라 보기 위해). */
    private static void seed(Entity e, long seed) throws Exception {
        var f = Entity.class.getDeclaredField("random");
        f.setAccessible(true);
        f.set(e, new LegacyRandomSource(seed));
    }

    private static ItemStack item(String name, int count) {
        return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", name), count);
    }

    @Test
    void emptyHandMountsUntamedHorseWithoutControl() {
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        Player p = player();
        assertTrue(h.interact(p, 0, ItemStack.EMPTY, null));
        assertSame(h, p.vehicle());
        assertTrue(h.hasPlayerPassenger());
        assertEquals(h.yRot(), p.yaw(), "타면서 말 방향을 본다");
        assertNull(h.getControllingPassenger(), "안장이 없으면 조종하지 못한다");
        assertFalse(h.isImmobile());
    }

    @Test
    void tamingRollsFollowVanillaOrder() throws Exception {
        for (int temper : new int[] {0, 100}) {
            var level = new BlockPlacementTest.TestLevel();
            Horse h = horse(level);
            seed(h, 1234L);
            Player p = player();
            h.setTemper(temper);
            p.startRiding(h, true);
            var goal = new RunAroundLikeCrazyGoal(h, 1.2);
            RandomSource mirror = new LegacyRandomSource(1234L);
            int expectTick = -1;
            for (int t = 0; t < 1000 && expectTick < 0; t++) if (mirror.nextInt(25) == 0) expectTick = t;
            int tick = -1;
            for (int t = 0; t < 1000; t++) {
                goal.tick();
                if (h.isTamed() || !h.isVehicle()) {
                    tick = t;
                    break;
                }
            }
            assertEquals(expectTick, tick, "nextInt(reducedTickDelay(50)) == 0 인 틱");
            boolean tamed = mirror.nextInt(100) < temper;
            assertEquals(tamed, h.isTamed());
            if (tamed) {
                assertSame(h, p.vehicle(), "길들여지면 그대로 탄다");
                assertEquals(p.uuid(), h.getOwnerUUID());
            } else {
                assertEquals(5, h.getTemper(), "실패하면 템퍼 +5");
                assertNull(p.vehicle(), "떨어뜨린다");
                assertTrue(h.isStanding(), "화나서 선다");
                mirror.nextFloat();   // makeMad 소리의 getVoicePitch
                mirror.nextFloat();
            }
            assertEquals(mirror.nextInt(), h.random().nextInt(), "그 뒤 난수 위치가 같다");
        }
    }

    @Test
    void saddleThenRiderControlsAndServerOnlyRotates() {
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        h.setTamed(true);
        Player p = player();
        int slot = p.inventory().heldWindowSlot();
        p.inventory().set(slot, item("saddle", 2));
        assertTrue(h.interact(p, 0, p.inventory().get(slot), null));
        assertTrue(h.isSaddled());
        assertEquals(1, p.inventory().get(slot).count(), "안장 하나를 쓴다");
        assertNull(p.vehicle(), "안장을 얹을 때는 타지 않는다");
        p.inventory().set(slot, ItemStack.EMPTY);
        assertTrue(h.interact(p, 0, ItemStack.EMPTY, null));
        assertSame(p, h.getControllingPassenger());
        assertTrue(h.canJump());
        p.applyRotation(90.0F, 40.0F, true);
        h.setDeltaMovement(0.3, 0.0, 0.3);
        double x = h.x();
        h.setOldPosAndRot();
        h.tickCount++;
        h.tick();
        assertEquals(90.0F, h.yRot(), "몸 방향 = 플레이어 시선");
        assertEquals(20.0F, h.xRot(), "세로는 절반");
        assertEquals(90.0F, h.yHeadRot());
        assertEquals(x, h.x(), 1e-9, "서버는 스스로 움직이지 않는다(move_vehicle 이 정한다)");
        h.handleStartJump(50);
        assertTrue(h.isStanding(), "점프 시작에 뒷발로 선다");
    }

    @Test
    void dismountGoesToSideOfHorse() {
        var level = new BlockPlacementTest.TestLevel();
        Horse h = horse(level);
        Player p = player();
        p.startRiding(h, true);
        Vec3 at = h.getDismountLocationForPassenger(p);
        // 오른손잡이: yRot + 90 쪽 — 방향 (-sin, cos)(90°) = (-1, 0), 거리 (말 폭 + 0.6 + 1e-5)/2
        double d = (h.bbWidth() + 0.6F + 1.0E-5F) / 2.0;
        assertEquals(0.5 - d, at.x(), 1e-5);
        assertEquals(-60.0, at.y(), 1e-9, "바닥 위");
        assertEquals(0.5, at.z(), 1e-5);
    }
}
