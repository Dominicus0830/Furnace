package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 피스톤·셜커 상자가 엔티티를 미는 것(바닐라 PistonMovingBlockEntity.moveCollidedEntities·moveStuckEntities·Entity.limitPistonMovement) — 손 계산. 바닥 y=-60. */
class PistonEntityPushTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks BL = Blocks.get();

    /** 게임 시각이 흐르는 시험 레벨(피스톤 이동량 누적은 게임 틱마다 초기화). */
    static class TimedLevel extends BlockPlacementTest.TestLevel {
        long time;
        Player player;
        final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public long gameTime() { return time; }
            @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
        };

        @Override protected LevelServices services() { return svc; }

        /** 서버 틱 한 번: (엔티티) → 예약 틱 → 블록 이벤트 → 블록 엔티티 → 시각 + 1. */
        void step(int n, boolean entities) {
            for (int i = 0; i < n; i++) {
                if (entities) tickEntities(k -> true);
                runScheduledTicks();
                runBlockEvents();
                tickBlockEntities(k -> true);
                time++;
            }
        }
    }

    private static int piston(String name, String facing) { return B.prop("facing").with(B.defaultState(name), facing); }

    private static ItemEntity item(TimedLevel l, double x, double y, double z) {
        ItemEntity e = new ItemEntity(l, l.nextEntityId(), x, y, z, B.stack("minecraft:dirt", 1), 0, 0, 0);
        assertTrue(l.addFreshEntity(e));
        return e;
    }

    /** 위로 미는 피스톤(0,-60,0) + 돌(0,-59,0). 빨간돌 블록으로 켠다. */
    private static TimedLevel upPiston(String pushed) {
        TimedLevel l = new TimedLevel();
        l.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "up"), 3);
        l.setBlock(new Pos(0, -59, 0), B.defaultState(pushed), 3);
        return l;
    }

    private static void power(TimedLevel l) { l.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3); }

    @Test
    void pistonUp_liftsItem_051_then_050() {
        TimedLevel l = upPiston("minecraft:stone");
        ItemEntity e = item(l, 0.5, -58.0, 0.5);
        power(l);
        l.step(1, false);
        // 진행 0 → 0.5: 앞면 띠 [-58, -57.5] 와 겹친 깊이 0.5, + 0.01 → 한 틱 한도 0.51
        assertEquals(-57.49, e.y(), 1e-9);
        l.step(1, false);
        // 진행 0.5 → 1: 띠 [-57.5, -57.0], 깊이 0.49 + 0.01
        assertEquals(-56.99, e.y(), 1e-9);
        l.step(1, false);
        assertEquals("minecraft:stone", BL.describe(l.getBlockState(new Pos(0, -58, 0))));
        assertEquals(-56.99, e.y(), 1e-9, "다 밀린 뒤엔 블록 윗면 0.01 위");
    }

    @Test
    void pistonEast_pushesCow() {
        TimedLevel l = new TimedLevel();
        l.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        l.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:stone"), 3);
        Cow cow = new Cow(l, l.nextEntityId());
        cow.moveTo(2.5, -60.0, 0.5, 0, 0);
        l.addFreshEntity(cow);
        power(l);
        l.step(1, false);
        assertEquals(2.5 + 0.46, cow.x(), 1e-6, "소 몸통(너비 0.9F) [2.05, 2.95], 띠 [2, 2.5] → 0.45 + 0.01");
        l.step(1, false);
        assertEquals(2.5 + 0.46 + 0.50, cow.x(), 1e-6, "띠 [2.5, 3.0] → 3.0 - 2.51 + 0.01");
        assertEquals(-60.0, cow.y(), 1e-9);
    }

    @Test
    void pistonEast_crushesCowAgainstWall() {
        TimedLevel l = new TimedLevel();
        l.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        l.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:stone"), 3);
        l.setBlock(new Pos(3, -60, 0), B.defaultState("minecraft:obsidian"), 3);
        Cow cow = new Cow(l, l.nextEntityId());
        cow.moveTo(2.5, -60.0, 0.5, 0, 0);
        l.addFreshEntity(cow);
        power(l);
        l.step(3, false);
        // 벽(x=3)에 막혀 0.05 만 가고 그 뒤로는 못 간다 — 블록이 놓이면 몸통이 돌 칸 [2,3] 안에 끼인다(질식은 엔티티 틱의 몫)
        assertEquals(3.0, cow.boundingBox().maxX(), 1e-9);
        assertEquals("minecraft:stone", BL.describe(l.getBlockState(new Pos(2, -60, 0))));
        assertTrue(cow.boundingBox().minX() < 3.0 && cow.boundingBox().minX() > 2.0);
    }

    @Test
    void slimeBlock_launchesItem_withVelocityOne() {
        TimedLevel l = upPiston("minecraft:slime_block");
        ItemEntity e = item(l, 0.5, -58.0, 0.5);
        power(l);
        l.step(1, false);
        assertEquals(1.0, e.vy(), 0.0, "슬라임: 미는 축 속도를 방향 단위값(1)으로");
        assertEquals(0.0, e.vx(), 0.0);
        assertEquals(-57.49, e.y(), 1e-9, "밀기도 같이");
    }

    @Test
    void honeyBlock_dragsItemStandingOnTop() {
        // 땅에 닿으면 꿀이 땅을 끌어 못 민다 — 공중(y=-57)에 둔다
        TimedLevel l = new TimedLevel();
        l.setBlock(new Pos(0, -57, 0), piston("minecraft:piston", "east"), 3);
        l.setBlock(new Pos(1, -57, 0), B.defaultState("minecraft:honey_block"), 3);
        ItemEntity e = item(l, 1.5, -56.0, 0.5);
        for (int i = 0; i < 20; i++) l.step(1, true);
        assertTrue(e.onGround());
        assertEquals(-56.0625, e.y(), 1e-9, "꿀 블록 윗면 0.9375");
        l.setBlock(new Pos(-1, -57, 0), B.defaultState("minecraft:redstone_block"), 3);
        l.step(1, true);
        assertEquals(2.0, e.x(), 1e-9, "꿀 위 엔티티는 + 0.01 없이 진행도만큼");
        l.step(1, true);
        assertEquals(2.5, e.x(), 1e-9);
    }

    @Test
    void pistonMovement_isClampedTo051PerAxisPerGameTick() {
        TimedLevel l = new TimedLevel();
        ItemEntity e = item(l, 0.5, -55.0, 0.5);
        e.move(Entity.MOVER_PISTON, 0.0, 0.3, 0.0);
        e.move(Entity.MOVER_PISTON, 0.0, 0.3, 0.0);
        assertEquals(-55.0 + 0.51, e.y(), 1e-9, "같은 틱 합 0.51");
        e.move(Entity.MOVER_PISTON, 0.2, 0.3, 0.0);
        assertEquals(0.7, e.x(), 1e-9, "x 가 있으면 x 축만 본다");
        assertEquals(-55.0 + 0.51, e.y(), 1e-9);
        l.time++;
        e.move(Entity.MOVER_PISTON, 0.0, 0.3, 0.0);
        assertEquals(-55.0 + 0.81, e.y(), 1e-9, "다음 게임 틱이면 다시 0.51 까지");
    }

    @Test
    void player_isNotMovedServerSide_andGetsNoCorrectionPacket() {
        TimedLevel l = upPiston("minecraft:stone");
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(900_123, UUID.randomUUID(), "P", ch);
        p.inventory(new PlayerInventory());
        p.applyMove(0.5, -58.0, 0.5, 0, 0, true);
        l.player = p;
        power(l);
        l.step(3, false);
        assertEquals(-58.0, p.y(), 0.0, "플레이어 위치는 클라이언트가 같은 블록 엔티티로 스스로 민다");
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) {   // 블록 갱신·소리는 오지만 위치 동기화(player_position 0x40)는 없다
            io.netty.buffer.ByteBuf b = (io.netty.buffer.ByteBuf) o;
            int id = org.domi.furnace.server.net.io.Types.readVarInt(b);
            b.release();
            assertNotEquals(0x40, id, "바닐라도 피스톤 밀기에 따로 위치 보정 패킷을 보내지 않는다");
        }
    }

    @Test
    void shulkerBoxLid_pushesItemUp() {
        TimedLevel l = new TimedLevel();
        l.setBlock(new Pos(0, -60, 0), B.prop("facing").with(B.defaultState("minecraft:shulker_box"), "up"), 3);
        var be = assertInstanceOf(ShulkerBoxBlockEntity.class, l.getBlockEntity(new Pos(0, -60, 0)));
        ItemEntity e = item(l, 0.5, -59.0, 0.5);
        be.triggerEvent(1, 1);
        l.step(1, false);
        // 뚜껑 띠 [-59, -59 + 0.1F], 두께 + 0.01 만큼 위로
        double thick = (-59.0 + (double) 0.1F) - (-59.0);
        assertEquals(-59.0 + thick + 0.01, e.y(), 1e-6);
    }
}
