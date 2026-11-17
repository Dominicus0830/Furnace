package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.DeathProbe;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A16-01 검토(PR #230): setHealth(0) 은 Paper 처럼 die(generic) — 피해 이벤트가 겹치지 않고, 곧바로 isDead. */
class HeldEntityDeathRulesTest {

    private static final String YML = """
            name: A16Death
            version: '1'
            main: a16.A16Death
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package a16;
            import org.bukkit.entity.*;
            import org.bukkit.event.*;
            import org.bukkit.event.entity.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.DeathProbe;
            public final class A16Death extends JavaPlugin implements Listener {
                @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
                @EventHandler public void dmg(EntityDamageEvent e) {
                    if (!"dmg-kill".equals(DeathProbe.mode) || e.getEntityType() != EntityType.PIG) return;
                    DeathProbe.LOG.add("dmg " + e.getCause());
                    try {
                        if (DeathProbe.LOG.size() < 6) ((LivingEntity) e.getEntity()).setHealth(0);   // 시험이 무한 재귀로 죽지 않게 6번에서 멈춤
                    } catch (Throwable t) { DeathProbe.failure = t; }
                }
                @EventHandler public void death(EntityDeathEvent e) {
                    if ("dmg-kill".equals(DeathProbe.mode) && e.getEntityType() == EntityType.PIG) DeathProbe.LOG.add("death");
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { DeathProbe.reset(); }

    record Env(FurnaceTestServer server, TestPlayer tp, BlockPos at) {}

    private Env start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("a16.jar"), YML, Map.of("a16.A16Death", PLUGIN));
        FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Lia");
        server.tick(3);
        var loc = tp.player().location();
        return new Env(server, tp, new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z())));
    }

    private static org.domi.furnace.server.entity.Entity rawPig(Env env, java.util.UUID id) {
        return env.server().onRegion(env.at(), () -> {
            var w = env.server().internals().world();
            var r = w.regionizer().ownerOf(env.at().x() >> 4, env.at().z() >> 4);
            return r.level().entities().get(id);
        });
    }

    /** (1) EntityDamageEvent 안의 setHealth(0): Paper 는 setHealth+die 라 피해 이벤트가 다시 안 난다. 이 PR 은 곧바로 kill()=hurt(generic_kill) 이라 이벤트가 겹겹이 난다. */
    @Test
    void setHealthZeroInsideDamageEventReentersDamageEvent() throws Exception {
        DeathProbe.mode = "dmg-kill";
        Env env = start();
        try (var server = env.server()) {
            World world = Compat.player(env.tp().player()).getWorld();
            var pig = server.onRegion(env.at(), () -> world.spawnEntity(new Location(world, env.at().x() + 3.5, env.at().y() + 1, env.at().z() + 0.5), EntityType.PIG));
            var raw = rawPig(env, pig.getUniqueId());
            server.onRegion(env.at(), () -> ((org.domi.furnace.server.entity.LivingEntity) raw).hurt(org.domi.furnace.server.entity.Entity.Damage.CACTUS, 1f));
            server.tick(2);
            assertNull(DeathProbe.failure, () -> "리스너가 던졌다: " + DeathProbe.failure);
            assertEquals(List.of("dmg CONTACT", "death"), DeathProbe.LOG, "Paper: 피해 이벤트 한 번, 죽음 한 번");
        }
    }

    /** (2) setHealth(0) 직후 isDead: Paper 는 LivingEntity.isAlive()=체력>0 이라 곧바로 true. */
    @Test
    void setHealthZeroIsDeadRightAway() throws Exception {
        Env env = start();
        try (var server = env.server()) {
            World world = Compat.player(env.tp().player()).getWorld();
            var pig = server.onRegion(env.at(), () -> world.spawnEntity(new Location(world, env.at().x() + 3.5, env.at().y() + 1, env.at().z() + 0.5), EntityType.PIG));
            boolean[] seen = server.onRegion(env.at(), () -> {
                ((LivingEntity) pig).setHealth(0);
                return new boolean[]{pig.isDead(), pig.isValid()};
            });
            assertTrue(seen[0], "Paper: setHealth(0) 직후 isDead()==true");
            assertFalse(seen[1], "Paper: setHealth(0) 직후 isValid()==false");
        }
    }
}
