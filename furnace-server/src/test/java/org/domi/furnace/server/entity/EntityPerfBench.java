// 엔티티 틱 성능 측정(기본 꺼짐: -Dfurnace.bench=true). 많은 몹을 올려 놓고 틱당 평균 시간을 잰다.
package org.domi.furnace.server.entity;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityPerfBench {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Arena extends BlockPlacementTest.TestLevel {
        long time;
        Player player;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long gameTime() { return time; }
                @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
                @Override public String biomeAt(int x, int y, int z) { return "minecraft:plains"; }
                @Override public int difficulty() { return 2; }
            };
        }
    }

    @Test
    void walkingMobPackets() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        var level = new Arena();
        var ch = new EmbeddedChannel();
        var p = new Player(5, UUID.randomUUID(), "Bench", ch);
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        level.player = p;
        Entity e = Entities.create(level, "minecraft:pig");
        e.moveTo(8.5, -60, 8.5, 0, 0);
        level.addFreshEntity(e);
        StringBuilder sb = new StringBuilder();
        level.playerMoved(p);
        int prev = ch.outboundMessages().size();
        for (int t = 0; t < 40; t++) {
            if (t % 15 == 3) ((LivingEntity) e).knockback(0.4, -1, -0.3);
            level.time++;
            level.tickEntities(k -> true);
            level.trackEntities(k -> true, pl -> true);
            ch.runPendingTasks();
            int n = ch.outboundMessages().size();
            sb.append(String.format("t%d x=%.3f gnd=%b out=%d vx=%.3f%n", t, e.x(), e.onGround(), n - prev, e.vx()));
            prev = n;
        }
        System.out.println("WALK\n" + sb);
    }

    @Test
    void manyMobsTickCost() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        int count = Integer.getInteger("furnace.bench.count", 300);
        var level = new Arena();
        var p = new Player(5, UUID.randomUUID(), "Bench", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        level.player = p;
        String[] types = {"minecraft:cow", "minecraft:sheep", "minecraft:zombie", "minecraft:skeleton", "minecraft:pig", "minecraft:chicken", "minecraft:spider", "minecraft:creeper"};
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < count; i++) {
            Entity e = Entities.create(level, types[i % types.length]);
            e.moveTo(r.nextInt(60) - 30 + 0.5, -60, r.nextInt(60) - 30 + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(e);
        }
        long[] samples = new long[400];
        for (int t = 0; t < samples.length; t++) {
            long t0 = System.nanoTime();
            level.time++;
            level.tickEntities(k -> true);
            samples[t] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(samples, 100, samples.length);
        long sum = 0;
        for (int i = 100; i < samples.length; i++) sum += samples[i];
        System.out.printf("BENCH count=%d avg=%.2fms p50=%.2fms p95=%.2fms max=%.2fms%n", count, sum / 300.0 / 1e6, samples[250] / 1e6, samples[385] / 1e6, samples[399] / 1e6);
    }
}
