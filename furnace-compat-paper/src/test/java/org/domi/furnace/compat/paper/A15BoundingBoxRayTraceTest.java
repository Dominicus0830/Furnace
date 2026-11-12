package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A15 후속: Entity#getBoundingBox, World#getNearbyEntities(BoundingBox), World#rayTrace*. 기대값은 Paper 1.21.1 CraftWorld·CraftEntity 본문과
 * 바닐라 충돌 상자 크기(좀비 0.6×1.95, 돼지 0.9×0.9, 플레이어 0.6×1.8)에서 옮겼다. 하늘 높이(공기)에 만들어 지형과 겹치지 않는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A15BoundingBoxRayTraceTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    World world;
    BlockPos origin;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Rhea");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 40, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        world = Compat.world(server.overworld());
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    Location at(double dx, double dy, double dz) { return new Location(world, origin.x() + 0.5 + dx, origin.y() + dy, origin.z() + 0.5 + dz); }

    void setBlock(int dx, int dy, int dz, Material m) { world.getBlockAt(origin.x() + dx, origin.y() + dy, origin.z() + dz).setType(m); }

    /**
     * 틱을 돌리지 않고 만든다 — 만든 자리에서 그대로 서 있게 중력도 끈다(몹 AI 는 틱을 돌려야 움직인다).
     * 좀비는 바닐라처럼 5% 로 아기(0.3×0.975)로 태어나 충돌 상자 기대값이 어긋나므로 어른(0.6 너비)이 나올 때까지 다시 만든다(흔들림의 원인이었다).
     */
    Entity spawn(Location at, EntityType type) {
        for (int attempt = 0; ; attempt++) {
            Entity e = owner(() -> {
                Entity made = world.spawnEntity(at, type);
                made.setGravity(false);
                return made;
            });
            if (type != EntityType.ZOMBIE || owner(() -> e.getBoundingBox().getWidthX()) > 0.5) return e;
            owner(() -> { e.remove(); return null; });
            if (attempt >= 200) throw new AssertionError("어른 좀비가 나오지 않는다");
        }
    }

    /** 앞 시험이 실패해도 엔티티가 남아 다음 시험을 흔들지 않게. */
    @AfterEach
    void removeLeftovers() {
        owner(() -> {
            for (Entity e : world.getNearbyEntities(new BoundingBox(origin.x() - 30, origin.y() - 5, origin.z() - 30, origin.x() + 30, origin.y() + 10, origin.z() + 30))) {
                if (!(e instanceof Player)) e.remove();
            }
            return null;
        });
    }

    static void assertBox(BoundingBox expected, BoundingBox actual) {
        assertEquals(expected.getMinX(), actual.getMinX(), 1.0E-6);
        assertEquals(expected.getMinY(), actual.getMinY(), 1.0E-6);
        assertEquals(expected.getMinZ(), actual.getMinZ(), 1.0E-6);
        assertEquals(expected.getMaxX(), actual.getMaxX(), 1.0E-6);
        assertEquals(expected.getMaxY(), actual.getMaxY(), 1.0E-6);
        assertEquals(expected.getMaxZ(), actual.getMaxZ(), 1.0E-6);
    }

    void assertSameEntity(Entity expected, Entity actual) {
        assertNotNull(actual);
        assertEquals(expected.getUniqueId(), actual.getUniqueId());
    }

    Material typeOf(org.bukkit.block.Block b) { return owner(b::getType); }

    void clearBlocks() {
        for (int dx = -2; dx <= 12; dx++) for (int dy = -2; dy <= 3; dy++) setBlock(dx, dy, 0, Material.AIR);
    }

    // ------------------------------------------------------------------ Entity#getBoundingBox

    @Test
    void boundingBoxMatchesVanillaDimensions() {
        Location l = at(0, 0, 0);
        Entity zombie = spawn(l, EntityType.ZOMBIE);
        Entity pig = spawn(at(5, 0, 0), EntityType.PIG);
        BoundingBox z = owner(zombie::getBoundingBox);
        assertBox(new BoundingBox(l.getX() - 0.3, l.getY(), l.getZ() - 0.3, l.getX() + 0.3, l.getY() + 1.95, l.getZ() + 0.3), z);
        BoundingBox p = owner(pig::getBoundingBox);
        assertEquals(0.9, p.getWidthX(), 1.0E-6);
        assertEquals(0.9, p.getHeight(), 1.0E-6);
        // 사본이다 — 고쳐도 엔티티는 그대로
        z.expand(10);
        assertEquals(0.6, owner(zombie::getBoundingBox).getWidthX(), 1.0E-6);
        // 소유 스레드가 아니어도 읽힌다(핸들을 만든 시점 값)
        assertNotNull(zombie.getBoundingBox());
        owner(() -> { zombie.remove(); pig.remove(); return null; });
    }

    @Test
    void playerBoundingBoxIsReadableFromAnyThread() {
        BoundingBox b = player.getBoundingBox();
        var loc = tp.player().location();
        assertEquals(0.6, b.getWidthX(), 1.0E-6);
        assertEquals(1.8, b.getHeight(), 1.0E-6);
        assertEquals(loc.x(), b.getCenterX(), 1.0E-6);
        assertEquals(loc.y(), b.getMinY(), 1.0E-6);
    }

    // ------------------------------------------------------------------ getNearbyEntities(BoundingBox)

    @Test
    void nearbyEntitiesByBoundingBoxUsesStrictOverlap() {
        Entity pig = spawn(at(3, 0, 0), EntityType.PIG);   // 충돌 상자 x: 2.55..3.45 (+0.5 칸 가운데)
        double px = pig.getLocation().getX();
        BoundingBox touching = new BoundingBox(px - 0.45 - 2, origin.y() - 1, origin.z() - 1, px - 0.45, origin.y() + 2, origin.z() + 2);
        assertFalse(owner(() -> world.getNearbyEntities(touching)).contains(pig), "경계만 닿으면 겹침이 아니다(바닐라 AABB.intersects)");
        BoundingBox overlapping = touching.clone().expand(0.01, 0, 0, 0.01, 0, 0);
        assertTrue(owner(() -> world.getNearbyEntities(overlapping)).contains(pig));
        // 위치(발밑 가운데)는 상자 밖이지만 충돌 상자가 걸치면 잡힌다
        BoundingBox clipsOnly = new BoundingBox(px - 3, origin.y(), origin.z() - 1, px - 0.3, origin.y() + 1, origin.z() + 1);
        assertTrue(owner(() -> world.getNearbyEntities(clipsOnly)).contains(pig));
        // 조건
        assertTrue(owner(() -> world.getNearbyEntities(overlapping, e -> false)).isEmpty());
        assertTrue(owner(() -> world.getNearbyEntities(overlapping, e -> e.getType() == EntityType.PIG)).contains(pig));
        // Location 판도 상자로 본다(Paper: BoundingBox.of(loc, x, y, z))
        assertTrue(owner(() -> world.getNearbyEntities(at(0, 0, 0), 2.7, 2, 2, e -> true)).contains(pig));
        assertFalse(owner(() -> world.getNearbyEntities(at(0, 0, 0), 2.5, 2, 2, e -> true)).contains(pig));
        assertThrows(ThreadOwnershipException.class, () -> world.getNearbyEntities(overlapping));
        assertThrows(IllegalArgumentException.class, () -> world.getNearbyEntities((BoundingBox) null));
        assertThrows(IllegalArgumentException.class, () -> world.getNearbyEntities((Location) null, 1, 1, 1, null));
        owner(() -> { pig.remove(); return null; });
    }

    @Test
    void entityNearbyUsesItsBoundingBoxAndSkipsItself() {
        Entity zombie = spawn(at(0, 0, 0), EntityType.ZOMBIE);
        Entity pig = spawn(at(3, 0, 0), EntityType.PIG);
        // 좀비 상자 x 끝 0.3 + 2.3 = 2.6 > 돼지 상자 시작 2.55 → 위치 거리 3 인데도 잡힌다
        List<Entity> around = owner(() -> zombie.getNearbyEntities(2.3, 2, 2));
        assertTrue(around.contains(pig));
        assertFalse(around.contains(zombie));
        // 2.25 면 닿지 않는다
        assertFalse(owner(() -> zombie.getNearbyEntities(2.25, 2, 2)).contains(pig));
        assertThrows(ThreadOwnershipException.class, () -> zombie.getNearbyEntities(2, 2, 2));
        owner(() -> { zombie.remove(); pig.remove(); return null; });
    }

    // ------------------------------------------------------------------ rayTraceEntities

    @Test
    void rayTraceEntitiesHitsNearestBoundingBox() {
        Entity zombie = spawn(at(4, 0, 0), EntityType.ZOMBIE);
        Entity pig = spawn(at(8, 0, 0), EntityType.PIG);
        Location start = at(0, 0.5, 0);
        Vector east = new Vector(1, 0, 0);
        RayTraceResult hit = owner(() -> world.rayTraceEntities(start, east, 20));
        assertNotNull(hit);
        assertSameEntity(zombie, hit.getHitEntity());
        assertEquals(zombie.getLocation().getX() - 0.3, hit.getHitPosition().getX(), 1.0E-6);
        assertEquals(BlockFace.WEST, hit.getHitBlockFace());
        assertNull(hit.getHitBlock());
        // 방향은 정규화된다 · 거리가 모자라면 빗나간다
        assertSameEntity(zombie, owner(() -> world.rayTraceEntities(start, new Vector(7, 0, 0), 20)).getHitEntity());
        assertNull(owner(() -> world.rayTraceEntities(start, east, 3)));
        assertNull(owner(() -> world.rayTraceEntities(start, east, -1)), "음수 거리는 null");
        // 조건으로 좀비를 걸러 내면 돼지
        assertSameEntity(pig, owner(() -> world.rayTraceEntities(start, east, 20, e -> e.getType() != EntityType.ZOMBIE)).getHitEntity());
        // raySize: 상자를 늘려 비껴간 광선도 맞춘다
        Location off = at(0, 0.5, 0.8);   // 좀비 상자 z 끝 0.3 보다 0.5 비껴감
        assertNull(owner(() -> world.rayTraceEntities(off, east, 20, 0.0)));
        assertSameEntity(zombie, owner(() -> world.rayTraceEntities(off, east, 20, 0.6)).getHitEntity());
        // Position 판
        assertSameEntity(zombie, owner(() -> world.rayTraceEntities((io.papermc.paper.math.Position) start, east, 20, 0.0, null)).getHitEntity());
        owner(() -> { zombie.remove(); pig.remove(); return null; });
    }

    @Test
    void rayTraceArgumentChecksUsePaperMessages() {
        Location start = at(0, 0.5, 0);
        assertEquals("Location start cannot be null", assertThrows(IllegalArgumentException.class, () -> world.rayTraceEntities(null, new Vector(1, 0, 0), 5)).getMessage());
        assertEquals("Vector direction cannot be null", assertThrows(IllegalArgumentException.class, () -> world.rayTraceEntities(start, null, 5)).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> world.rayTraceBlocks(start, new Vector(0, 0, 0), 5)).getMessage()
                .startsWith("Direction's magnitude (0.0) need to be greater than 0"));
        assertEquals("FluidCollisionMode cannot be null",
                assertThrows(IllegalArgumentException.class, () -> world.rayTraceBlocks(start, new Vector(1, 0, 0), 5, null)).getMessage());
        assertEquals("Location start is not finite", assertThrows(IllegalArgumentException.class,
                () -> world.rayTraceBlocks(new Location(world, Double.NaN, 1, 1), new Vector(1, 0, 0), 5)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> world.rayTraceBlocks(start, new Vector(Double.NaN, 0, 0), 5));
    }

    // ------------------------------------------------------------------ rayTraceBlocks

    @Test
    void rayTraceBlocksFindsFirstSolidBlockAndFace() {
        clearBlocks();
        setBlock(3, 0, 0, Material.STONE);
        setBlock(6, 0, 0, Material.STONE);
        Location start = at(0, 0.5, 0);
        RayTraceResult hit = owner(() -> world.rayTraceBlocks(start, new Vector(1, 0, 0), 20));
        assertNotNull(hit);
        assertEquals(origin.x() + 3, hit.getHitBlock().getX());
        assertEquals(Material.STONE, typeOf(hit.getHitBlock()));
        assertEquals(BlockFace.WEST, hit.getHitBlockFace());
        assertEquals(origin.x() + 3, hit.getHitPosition().getX(), 1.0E-9);
        assertEquals(origin.y() + 0.5, hit.getHitPosition().getY(), 1.0E-9);
        assertNull(hit.getHitEntity());
        assertNull(owner(() -> world.rayTraceBlocks(start, new Vector(1, 0, 0), 2)), "닿지 않으면 null");
        assertNull(owner(() -> world.rayTraceBlocks(start, new Vector(0, 1, 0), 20)));
        // 아래로 쏘면 위(UP)면, 반대로 서쪽에서 쏘면 동(EAST)면
        setBlock(0, -2, 0, Material.STONE);
        RayTraceResult down = owner(() -> world.rayTraceBlocks(start, new Vector(0, -1, 0), 10));
        assertEquals(BlockFace.UP, down.getHitBlockFace());
        RayTraceResult back = owner(() -> world.rayTraceBlocks(at(10, 0.5, 0), new Vector(-1, 0, 0), 20));
        assertEquals(BlockFace.EAST, back.getHitBlockFace());
        assertEquals(origin.x() + 6, back.getHitBlock().getX());
        assertThrows(ThreadOwnershipException.class, () -> world.rayTraceBlocks(start, new Vector(1, 0, 0), 20));
        clearBlocks();
    }

    @Test
    void ignorePassableSkipsBlocksWithoutCollision() {
        clearBlocks();
        setBlock(2, 0, 0, Material.SHORT_GRASS);
        setBlock(5, 0, 0, Material.STONE);
        Location start = at(0, 0.3, 0);
        Vector east = new Vector(1, 0, 0);
        RayTraceResult outline = owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.NEVER, false));
        assertEquals(Material.SHORT_GRASS, typeOf(outline.getHitBlock()), "윤곽 모양은 풀도 맞는다");
        RayTraceResult collider = owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.NEVER, true));
        assertEquals(Material.STONE, typeOf(collider.getHitBlock()), "ignorePassableBlocks 면 충돌 모양이라 풀을 지나간다");
        clearBlocks();
    }

    @Test
    void fluidCollisionModes() {
        clearBlocks();
        setBlock(3, 0, 0, Material.WATER);          // 원천 블록
        setBlock(6, 0, 0, Material.STONE);
        Location start = at(0, 0.5, 0);
        Vector east = new Vector(1, 0, 0);
        assertEquals(Material.STONE, typeOf(owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.NEVER)).getHitBlock()));
        RayTraceResult src = owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.SOURCE_ONLY));
        assertEquals(Material.WATER, typeOf(src.getHitBlock()));
        assertEquals(BlockFace.WEST, src.getHitBlockFace());
        assertEquals(Material.WATER, typeOf(owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.ALWAYS)).getHitBlock()));
        // 충돌 모양(ignorePassable)이어도 유체 모드는 따로 본다(바닐라 ClipContext)
        assertEquals(Material.WATER, typeOf(owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.ALWAYS, true)).getHitBlock()));
        assertEquals(Material.STONE, typeOf(owner(() -> world.rayTraceBlocks(start, east, 20, FluidCollisionMode.NEVER, true)).getHitBlock()));
        clearBlocks();
    }

    @Test
    void canCollideFiltersBlocks() {
        clearBlocks();
        setBlock(3, 0, 0, Material.STONE);
        setBlock(6, 0, 0, Material.DIRT);
        Location start = at(0, 0.5, 0);
        List<Material> asked = new ArrayList<>();
        RayTraceResult hit = owner(() -> world.rayTraceBlocks((io.papermc.paper.math.Position) start, new Vector(1, 0, 0), 20, FluidCollisionMode.NEVER, false, b -> {
            asked.add(b.getType());
            return b.getType() != Material.STONE;
        }));
        assertEquals(Material.DIRT, typeOf(hit.getHitBlock()));
        assertEquals(List.of(Material.STONE, Material.DIRT), asked, "공기 칸은 묻지 않는다(Paper 도 isAir 면 건너뛴다)");
        clearBlocks();
    }

    // ------------------------------------------------------------------ rayTrace(블록+엔티티)

    @Test
    void rayTraceReturnsWhicheverIsNearer() {
        clearBlocks();
        Entity zombie = spawn(at(4, 0, 0), EntityType.ZOMBIE);
        setBlock(8, 0, 0, Material.STONE);
        Location start = at(0, 0.5, 0);
        Vector east = new Vector(1, 0, 0);
        RayTraceResult first = owner(() -> world.rayTrace(start, east, 20, FluidCollisionMode.NEVER, false, 0.0, null));
        assertSameEntity(zombie, first.getHitEntity());
        // 엔티티를 걸러 내면 블록
        RayTraceResult block = owner(() -> world.rayTrace(start, east, 20, FluidCollisionMode.NEVER, false, 0.0, e -> false));
        assertEquals(Material.STONE, typeOf(block.getHitBlock()));
        // 블록이 엔티티 앞이면 블록
        setBlock(2, 0, 0, Material.STONE);
        RayTraceResult front = owner(() -> world.rayTrace(start, east, 20, FluidCollisionMode.NEVER, false, 0.0, null));
        assertNotNull(front.getHitBlock());
        assertEquals(origin.x() + 2, front.getHitBlock().getX());
        assertNull(owner(() -> world.rayTrace(start, new Vector(0, 1, 0), 20, FluidCollisionMode.NEVER, false, 0.0, null)));
        owner(() -> { zombie.remove(); return null; });
        clearBlocks();
    }
}
