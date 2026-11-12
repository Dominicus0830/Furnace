package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 엔티티 id 역조회(ServerLevel#getEntity(int))·머리 yaw — Paper 1.21.1 기대값(PR #249 독립 검토 재현을 시험으로 굳힘). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityIdLookupTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Rev");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(p, task); }

    org.domi.furnace.server.entity.Entity serverEntity(int cx, int cz, java.util.UUID id) {
        return server.internals().world().regionizer().ownerOf(cx, cz).level().entities().get(id);
    }

    /** Paper teleport 는 setYHeadRot(yaw)(SPIGOT-619) — 텔레포트 직후 다시 찾은 엔티티의 getLocation yaw = 목적지 yaw. */
    @Test
    void teleportThenFreshLookupYaw() {
        World world = player.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        Location spot = new Location(world, at.x() + 4.5, at.y() + 1, at.z() + 0.5);
        Entity pig = on(at, () -> world.spawnEntity(spot, EntityType.PIG));
        int cx = Math.floorDiv(at.x() + 4, 16), cz = Math.floorDiv(at.z(), 16);
        try {
            float[] r = on(at, () -> {
                var e = (org.domi.furnace.server.entity.LivingEntity) serverEntity(cx, cz, pig.getUniqueId());
                e.setYRot(10.0F);
                e.setYHeadRot(80.0F);
                Location to = spot.clone();
                to.setYaw(120.0F);
                pig.teleport(to);
                level.getEntity(pig.getEntityId());   // 새 스냅샷으로 같은 CraftEntity 를 갱신
                return new float[] { e.yRot(), e.yHeadRot(), pig.getLocation().getYaw() };
            });
            assertEquals(120.0F, r[2], "Paper: teleport 뒤 getLocation().getYaw() = 120(머리도 옮김)");
        } finally {
            on(at, () -> { pig.remove(); return null; });
        }
    }

    /** Paper spawn(살아 있는 것) = absMoveTo + setYHeadRot(yaw)(SPIGOT-3587) — 갓 스폰한 갑옷 거치대·돼지의 yaw = 스폰 yaw. */
    @Test
    void spawnedLivingYaw() {
        World world = player.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        List<String> out = new ArrayList<>();
        for (EntityType t : new EntityType[] {EntityType.ARMOR_STAND, EntityType.PIG, EntityType.ZOMBIE}) {
            Location spot = new Location(world, at.x() + 6.5, at.y() + 1, at.z() + 0.5, 120.0F, 0.0F);
            Entity e = on(at, () -> world.spawnEntity(spot, t));
            try {
                float y = on(at, () -> {
                    level.getEntity(e.getEntityId());
                    return e.getLocation().getYaw();
                });
                out.add(t + "=" + y);
            } finally {
                on(at, () -> { e.remove(); return null; });
            }
        }
        assertEquals(List.of("ARMOR_STAND=120.0", "PIG=120.0", "ZOMBIE=120.0"), out);
    }

    /** 먼 청크 = 다른 region. */
    BlockPos far() {
        int fx = at.x() + 16 * 40, fz = at.z();
        server.loadChunk(Math.floorDiv(fx, 16), Math.floorDiv(fz, 16));
        return new BlockPos(fx, at.y(), fz);
    }

    /** region 스레드가 남의 region 엔티티를 id 로 찾으면 — Paper/Folia 는 그냥 돌려준다(EntityLookup.get 은 스레드 검사 없음). */
    @Test
    void otherRegionTickThreadLookup() {
        World world = player.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        BlockPos f = far();
        var ra = server.internals().world().regionizer().ownerOf(Math.floorDiv(at.x(), 16), Math.floorDiv(at.z(), 16));
        var rb = server.internals().world().regionizer().ownerOf(Math.floorDiv(f.x(), 16), Math.floorDiv(f.z(), 16));
        assertNotSame(ra, rb, "먼 청크는 다른 region");
        Location spot = new Location(world, f.x() + 0.5, f.y() + 1, f.z() + 0.5);
        Entity pig = on(f, () -> world.spawnEntity(spot, EntityType.PIG));
        try {
            int id = pig.getEntityId();
            String r = on(at, () -> {
                try {
                    var h = level.getEntity(id);
                    return "found " + h;
                } catch (RuntimeException ex) {
                    return ex.getClass().getSimpleName();
                }
            });
            String g = server.onGlobal(() -> {
                try {
                    var h = level.getEntity(id);
                    return "found " + h;
                } catch (RuntimeException ex) {
                    return ex.getClass().getSimpleName();
                }
            });
            assertEquals(true, r.startsWith("found") && g.startsWith("found"), "Paper/Folia: 어느 스레드든 찾는다 — region=" + r + " global=" + g);
        } finally {
            on(f, () -> { pig.remove(); return null; });
        }
    }

    /**
     * 엔티티가 남의 region 청크로 들어갔지만 그 틱의 handOffEntities 전(옛 region 저장소에 있음) — 색인에는 있고 살아 있는데
     * entityById 는 새 주인 region 에서만 3번 바로 다시 찾아 empty. Paper 는 월드 전체 조회라 늘 찾는다.
     */
    @Test
    void lookupDuringHandoffWindow() {
        World world = player.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        BlockPos f = far();
        Location spot = new Location(world, f.x() + 0.5, f.y() + 1, f.z() + 0.5);
        Entity pig = on(f, () -> world.spawnEntity(spot, EntityType.PIG));
        int id = pig.getEntityId();
        int fcx = Math.floorDiv(f.x(), 16), fcz = Math.floorDiv(f.z(), 16);
        var e = on(f, () -> serverEntity(fcx, fcz, pig.getUniqueId()));
        assertNotNull(e);
        try {
            // region B 스레드에서: 엔티티를 A 의 청크로 옮김(틱 안 이동과 같은 상태 — handOff 는 다음 틱 끝, 시험은 틱을 돌리지 않는다)
            on(f, () -> { e.setPos(at.x() + 3.5, at.y() + 1, at.z() + 0.5); return null; });
            String r;
            try {
                var api = org.domi.furnace.api.Furnace.server().world(world.getKey().toString());
                var got = api.entityById(id).get(3, java.util.concurrent.TimeUnit.SECONDS);
                var shim = level.getEntity(id);
                r = "api=" + got.isPresent() + " shim=" + (shim != null) + " removed=" + e.isRemoved()
                        + " indexed=" + (server.internals().world().entityIndex().get(id) == e);
            } catch (Exception ex) {
                r = "api ex " + ex;
            } finally {
                on(f, () -> { e.setPos(f.x() + 0.5, f.y() + 1, f.z() + 0.5); return null; });   // 되돌린다
            }
            assertEquals(true, r.startsWith("api=true shim=true"), "Paper: 살아 있는 엔티티는 찾는다 — " + r);
        } finally {
            on(f, () -> { pig.remove(); return null; });
        }
    }
}
