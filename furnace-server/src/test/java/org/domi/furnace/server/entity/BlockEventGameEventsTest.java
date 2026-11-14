// T-040b 후속(검토 R4): 개구리 알 놓기는 block_place 게임 이벤트 + BLOCKS 소리, 토끼 당근 갉기(나이>0)는 block_change 게임 이벤트(원인 토끼).
// 바닐라 1.21.1: TryLaySpawnOnWaterNearLand = setBlock → gameEvent(BLOCK_PLACE, Context.of(mob, state)) → playSound(FROG_LAY_SPAWN, BLOCKS),
// RaidGardenGoal(age>0) = setBlock → gameEvent(BLOCK_CHANGE, Context.of(rabbit)) → levelEvent(2001).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.TryLaySpawnOnWaterNearLand;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.junit.jupiter.api.Test;

class BlockEventGameEventsTest {

    static final class Level extends BlockPlacementTest.TestLevel {
        final List<String> log = new ArrayList<>();
        boolean cancelChange;

        @Override
        public org.domi.furnace.server.plugin.GameEvents gameEvents() {
            if (!cancelChange) return super.gameEvents();
            return new org.domi.furnace.server.plugin.GameEvents() {
                @Override public boolean wantsEntityChangeBlock() { return true; }
                @Override public boolean entityChangeBlock(String dimension, org.domi.furnace.server.entity.Rider who, Pos pos, int oldState, int newState, boolean cancelled) { return false; }
            };
        }

        @Override
        public void playSeededSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
            log.add("sound:" + sound + ":" + source);
            super.playSeededSound(except, x, y, z, sound, source, volume, pitch, seed);
        }

        @Override
        public void playSeededSound(BlockActor except, int entityId, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
            log.add("sound:" + sound + ":" + source);
            super.playSeededSound(except, entityId, x, y, z, sound, source, volume, pitch, seed);
        }

        @Override
        public void gameEventFrom(String event, Pos pos, Object entity, int state) {
            log.add("event:" + event + ":" + (entity == null ? "null" : entity.getClass().getSimpleName()) + ":" + state);
            super.gameEventFrom(event, pos, entity, state);
        }
    }

    private static int state(String name) { return B.defaultState(B.id("minecraft:" + name)); }

    @Test
    void frogSpawnPlacementFiresBlockPlaceBeforeBlocksSound() {
        var level = new Level();
        // 땅(0,-61,0) 옆 (1,-61,0) 이 물, 그 위가 공기
        level.setBlock(new Pos(1, -61, 0), state("water"), 2);
        level.setBlock(new Pos(1, -60, 0), state("air"), 2);
        var frog = new Frog(level, level.nextEntityId());
        frog.moveTo(0.5, -60, 0.5, 0, 0);
        frog.setOnGround(true);
        frog.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new Pos(1, -60, 0), 1.0F, 1));
        frog.getBrain().setMemory(MemoryModuleType.IS_PREGNANT, org.domi.furnace.server.entity.ai.brain.Unit.INSTANCE);
        level.addFreshEntity(frog);
        level.log.clear();

        assertTrue(TryLaySpawnOnWaterNearLand.create("minecraft:frogspawn").tryStart(level, frog, 0L));

        int egg = state("frogspawn");
        assertEquals(egg, level.getBlockState(new Pos(1, -60, 0)), "알이 놓인다");
        assertEquals(List.of("event:minecraft:block_place:Frog:" + egg, "sound:entity.frog.lay_spawn:blocks"), level.log,
                "바닐라 순서: 게임 이벤트(원인 개구리·새 상태) 다음 BLOCKS 소리");
        assertFalse(frog.getBrain().hasMemoryValue(MemoryModuleType.IS_PREGNANT));
    }

    @Test
    void rabbitNibbleOnGrowingCarrotFiresBlockChange() throws Exception {
        var level = new Level();
        Pos farm = new Pos(0, -61, 0), above = farm.above();
        level.setBlock(farm, state("farmland"), 2);
        int carrots = B.prop("age").withInt(state("carrots"), 7);
        level.setBlock(above, carrots, 2);
        var rabbit = new Rabbit(level, level.nextEntityId());
        rabbit.moveTo(0.5, -60, 0.5, 0, 0);
        rabbit.setOnGround(true);
        level.addFreshEntity(rabbit);
        level.log.clear();

        var goal = new Rabbit.RaidGardenGoal(rabbit);
        var blockPos = org.domi.furnace.server.entity.ai.goal.MoveToBlockGoal.class.getDeclaredField("blockPos");
        blockPos.setAccessible(true);
        blockPos.set(goal, farm);
        var canRaid = Rabbit.RaidGardenGoal.class.getDeclaredField("canRaid");
        canRaid.setAccessible(true);
        canRaid.setBoolean(goal, true);
        goal.tick();

        assertEquals(B.prop("age").withInt(carrots, 6), level.getBlockState(above), "한 단계 줄어든다");
        assertEquals(List.of("event:minecraft:block_change:Rabbit:-1"),
                level.log.stream().filter(s -> s.startsWith("event:")).toList(), "바닐라: Context.of(rabbit) — 새 상태 없음");
    }

    /** 검토 R3(케이크 먹기): 막히면 클라이언트가 미리 올린 체력·허기·포화를 서버 값 그대로 다시 보낸다(Paper sendHealthUpdate). */
    @Test
    void resyncVitalsSendsCurrentHealthFoodSaturation() {
        var ch = new io.netty.channel.embedded.EmbeddedChannel();
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", ch);
        p.vitals().env(new org.domi.furnace.server.entity.PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) ((io.netty.buffer.ByteBuf) o).release();   // 앞선 패킷 비우기
        new org.domi.furnace.server.block.PlayerActor(p).resyncVitals();
        ch.runPendingTasks();
        float health = -1;
        int food = -1;
        boolean found = false;
        while ((o = ch.readOutbound()) != null) {
            var bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == org.domi.furnace.server.net.protocol.play.SetHealth.ID) {
                found = true;
                health = bb.readFloat();
                food = org.domi.furnace.server.net.io.Types.readVarInt(bb);
            }
            bb.release();
        }
        assertTrue(found, "SetHealth 가 간다");
        assertEquals(p.vitals().health(), health);
        assertEquals(20, food);
    }

    private static Player creativePlayer(io.netty.channel.embedded.EmbeddedChannel ch) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", ch);
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.CREATIVE, false);   // 크리에이티브는 배고프지 않아도 먹는다(canEat)
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    /** 검토 R9: 실제 CakeBlock 경로 — ECB 를 막으면 케이크는 그대로, PASS, SetHealth 가 간다. */
    @Test
    void cancelledCakeEatPassesAndResendsVitals() {
        var level = new Level();
        level.cancelChange = true;
        Pos pos = new Pos(0, -60, 0);
        int cake = state("cake");
        level.setBlock(pos, cake, 2);
        var ch = new io.netty.channel.embedded.EmbeddedChannel();
        var p = creativePlayer(ch);
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) ((io.netty.buffer.ByteBuf) o).release();
        var r = B.behavior(cake).useWithoutItem(cake, level, pos, new org.domi.furnace.server.block.PlayerActor(p), null);
        ch.runPendingTasks();
        assertEquals(org.domi.furnace.server.block.InteractionResult.PASS, r);
        assertEquals(cake, level.getBlockState(pos), "막으면 케이크 그대로");
        boolean sent = false;
        while ((o = ch.readOutbound()) != null) {
            var bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == org.domi.furnace.server.net.protocol.play.SetHealth.ID) sent = true;
            bb.release();
        }
        assertTrue(sent, "체력·허기 SetHealth 재전송");
    }

    /** 검토 R5: 바닐라 getFluidState(adj).is(Fluids.WATER) — 원천 물만, 흐르는 물 옆에는 알을 낳지 않는다. */
    @Test
    void frogDoesNotLayOnFlowingWater() {
        var level = new Level();
        level.setBlock(new Pos(1, -61, 0), B.prop("level").withInt(state("water"), 1), 2);
        level.setBlock(new Pos(1, -60, 0), state("air"), 2);
        var frog = new Frog(level, level.nextEntityId());
        frog.moveTo(0.5, -60, 0.5, 0, 0);
        frog.setOnGround(true);
        frog.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new Pos(1, -60, 0), 1.0F, 1));
        frog.getBrain().setMemory(MemoryModuleType.IS_PREGNANT, org.domi.furnace.server.entity.ai.brain.Unit.INSTANCE);
        level.addFreshEntity(frog);
        TryLaySpawnOnWaterNearLand.create("minecraft:frogspawn").tryStart(level, frog, 0L);
        assertTrue(B.isAir(level.getBlockState(new Pos(1, -60, 0))));
        assertTrue(frog.getBrain().hasMemoryValue(MemoryModuleType.IS_PREGNANT), "알을 못 낳았으니 임신은 그대로");
    }
}
