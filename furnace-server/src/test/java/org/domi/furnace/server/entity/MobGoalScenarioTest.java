// 새로 붙인 몹 목표의 대표 동작: 좀비가 철 골렘을 노리고, 거북 알을 밟아 깨고, 늑대가 라마를 피하고, 철 골렘이 평판 나쁜 플레이어를 노린다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.WrappedGoal;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobGoalScenarioTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 밤(햇빛 화상 난수 없음)이고 플레이어 하나를 둘 수 있는 평지. */
    static final class NightLevel extends BlockPlacementTest.TestLevel {
        Player player;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long dayTime() { return 18000; }
                @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
            };
        }
    }

    private static void tick(Entity e) {
        e.setOldPosAndRot();
        e.tickCount++;
        e.tick();
    }

    private static <T extends Entity> T put(BlockPlacementTest.TestLevel level, T e, double x, double z) {
        e.moveTo(x, -60, z, 0, 0);
        e.setOnGround(true);
        level.addFreshEntity(e);
        return e;
    }

    private static boolean running(Mob m, Class<?> goal) {
        for (WrappedGoal w : m.goalSelector.getAvailableGoals()) if (w.isRunning() && goal.isInstance(w.getGoal())) return true;
        return false;
    }

    @Test
    void zombieTargetsIronGolem() {
        var level = new NightLevel();
        var zombie = put(level, new Zombie(level, level.nextEntityId()), 0.5, 0.5);
        var golem = put(level, new IronGolem(level, level.nextEntityId()), 6.5, 0.5);
        for (int t = 0; t < 200 && zombie.getTarget() == null; t++) tick(zombie);
        assertSame(golem, zombie.getTarget(), "좀비가 철 골렘을 대상으로 삼는다");
    }

    @Test
    void zombieTramplesTurtleEgg() {
        var level = new NightLevel();
        Pos egg = new Pos(3, -60, 0);
        level.setBlock(egg, Blocks.get().defaultState("minecraft:turtle_egg"), 3);
        // 알 위(높이 7/16)에 세운다. 옆에서 다가와 올라서는 경로는 이 시험의 대상이 아니다.
        var zombie = new Zombie(level, level.nextEntityId());
        zombie.moveTo(3.5, -60 + 7.0 / 16.0, 0.5, 0, 0);
        zombie.setOnGround(true);
        level.addFreshEntity(zombie);
        boolean ran = false;
        for (int t = 0; t < 1200 && B.is(level.getBlockState(egg), "minecraft:turtle_egg"); t++) {
            tick(zombie);
            ran |= running(zombie, Zombie.ZombieAttackTurtleEggGoal.class);
        }
        assertTrue(ran, "거북 알 목표가 돌았다");
        assertFalse(B.is(level.getBlockState(egg), "minecraft:turtle_egg"), "알이 없어졌다: " + zombie.x() + "," + zombie.y() + "," + zombie.z());
    }

    @Test
    void wolfAvoidsLlama() {
        var level = new NightLevel();
        var wolf = put(level, new Wolf(level, level.nextEntityId()), 0.5, 0.5);
        var llama = put(level, new Llama(level, level.nextEntityId()), 4.5, 0.5);
        llama.entityData.set(Llama.DATA_STRENGTH_ID, 5);   // 힘 5 ≥ nextInt(5): 늘 피한다
        boolean ran = false;
        for (int t = 0; t < 200 && !ran; t++) {
            tick(wolf);
            ran = running(wolf, Wolf.WolfAvoidEntityGoal.class);
        }
        assertTrue(ran, "늑대가 라마를 피한다");
        for (int t = 0; t < 60; t++) tick(wolf);
        assertTrue(wolf.x() < 0.0 || Math.abs(wolf.z() - 0.5) > 1.0, "라마에게서 멀어졌다: " + wolf.x() + "," + wolf.z());
    }

    @Test
    void ironGolemDefendsVillageFromBadReputationPlayer() {
        var level = new NightLevel();
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(5.5, -60, 0.5, 90, 0, true);
        level.player = p;
        var golem = put(level, new IronGolem(level, level.nextEntityId()), 0.5, 0.5);
        var villager = put(level, new Villager(level, level.nextEntityId()), 2.5, 2.5);
        assertNull(golem.getTarget());
        for (int t = 0; t < 5; t++) tick(golem);
        assertNull(golem.getTarget(), "평판이 괜찮으면 노리지 않는다");
        villager.getGossips().add(p.uuid(), org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.MAJOR_NEGATIVE, 25);   // 25 × -5 = -125
        assertTrue(villager.getPlayerReputation(p) <= -100);
        for (int t = 0; t < 5 && golem.getTarget() == null; t++) tick(golem);
        assertSame(p, golem.getTarget(), "평판 -100 이하 플레이어를 노린다");
    }
}
