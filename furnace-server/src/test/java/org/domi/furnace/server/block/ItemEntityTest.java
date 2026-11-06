package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 엔티티 틀과 아이템 엔티티(바닐라 Entity.move·ItemEntity.tick·ServerEntity·EntityStorage)를 손 계산·바닐라 규칙으로 확인. 바닥은 y=-60. */
class ItemEntityTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks BL = Blocks.get();

    private static ItemStack stack(String item, int n) { return B.stack(item, n); }

    private static ItemEntity item(BlockPlacementTest.TestLevel level, double x, double y, double z, ItemStack s) {
        ItemEntity e = new ItemEntity(level, level.nextEntityId(), x, y, z, s, 0, 0, 0);
        assertTrue(level.addFreshEntity(e));
        return e;
    }

    /** 서버 틱의 블록·엔티티 부분: 예약 틱 → 엔티티. */
    private static void tick(ChunkLevel level, int n) {
        for (int i = 0; i < n; i++) {
            level.runScheduledTicks();
            level.tickEntities(k -> true);
        }
    }

    @Test
    void item_fallsWithVanillaGravityAndDrag_thenRestsOnGround() {
        var level = new BlockPlacementTest.TestLevel();
        ItemEntity e = item(level, 0.5, -55, 0.5, stack("minecraft:dirt", 1));
        tick(level, 1);
        assertEquals(-55.04, e.y(), 1e-12, "중력 0.04 를 먼저 더해 움직인다");
        assertEquals(-0.04 * 0.98, e.vy(), 1e-12, "그다음 공기 저항 0.98");
        tick(level, 80);
        assertEquals(-60.0, e.y(), 0.0, "풀 블록 위에 정확히 선다");
        assertTrue(e.onGround());
        // 땅에서는 4틱마다만 움직이므로 그사이 중력이 쌓였다가 부딪혀 0 이 된다(바닐라와 같다)
        assertTrue(e.vy() <= 0.0 && e.vy() >= -0.16, "vy " + e.vy());
        tick(level, 8);
        assertEquals(-60.0, e.y(), 0.0);
        assertFalse(e.isRemoved());
    }

    @Test
    void items_mergeAfter40Ticks_intoTheLargerOrLaterOne() {
        var level = new BlockPlacementTest.TestLevel();
        ItemEntity a = item(level, 0.5, -60, 0.5, stack("minecraft:dirt", 1));
        ItemEntity b = item(level, 0.6, -60, 0.5, stack("minecraft:dirt", 1));
        tick(level, 39);
        assertFalse(a.isRemoved());
        assertFalse(b.isRemoved());
        tick(level, 1);   // tickCount 40: 움직이지 않았으면 40틱마다 합친다
        assertTrue(a.isRemoved(), "개수가 같으면 먼저 돈 쪽이 상대에게 합쳐진다");
        assertEquals(2, b.getItem().count());
        assertEquals(1, level.entities().size());
    }

    @Test
    void items_ofDifferentKinds_doNotMerge() {
        var level = new BlockPlacementTest.TestLevel();
        item(level, 0.5, -60, 0.5, stack("minecraft:dirt", 1));
        item(level, 0.6, -60, 0.5, stack("minecraft:stone", 1));
        tick(level, 80);
        assertEquals(2, level.entities().size());
    }

    @Test
    void item_despawnsAfter6000Ticks() {
        var level = new BlockPlacementTest.TestLevel();
        ItemEntity e = item(level, 0.5, -60, 0.5, stack("minecraft:dirt", 1));
        tick(level, 5999);
        assertFalse(e.isRemoved());
        tick(level, 1);
        assertTrue(e.isRemoved());
        assertTrue(level.entities().isEmpty());
    }

    @Test
    void item_burnsUpInFire() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:fire"), 3);
        ItemEntity e = item(level, 0.5, -60, 0.5, stack("minecraft:oak_planks", 1));
        tick(level, 1);
        assertFalse(e.isRemoved(), "체력 5 — 불 속 피해 1(움직이는 틱마다)·불붙음 피해 1(20틱마다)");
        tick(level, 40);
        assertTrue(e.isRemoved());
    }

    @Test
    void fireResistantItem_survivesFire() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:fire"), 3);
        ItemEntity e = item(level, 0.5, -60, 0.5, stack("minecraft:netherite_ingot", 1));
        tick(level, 40);
        assertFalse(e.isRemoved());
    }

    @Test
    void item_isPushedAlongWaterFlow() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:water"), 2);
        level.setBlock(new Pos(1, -60, 0), BL.prop("level").withInt(BL.defaultState("minecraft:water"), 1), 2);
        level.setBlock(new Pos(2, -60, 0), BL.prop("level").withInt(BL.defaultState("minecraft:water"), 2), 2);
        ItemEntity e = item(level, 1.5, -59.9, 0.5, stack("minecraft:dirt", 1));
        tick(level, 10);
        assertTrue(e.x() > 1.5, "높은 쪽(원천)에서 낮은 쪽(+x)으로 흐른다: " + e.x());
        assertTrue(e.isInWater());
    }

    @Test
    void item_pressesWoodenPlate_butNotStonePlate() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:oak_pressure_plate"), 3);
        level.setBlock(new Pos(3, -60, 0), BL.defaultState("minecraft:stone_pressure_plate"), 3);
        item(level, 0.5, -59.5, 0.5, stack("minecraft:dirt", 1));
        item(level, 3.5, -59.5, 0.5, stack("minecraft:dirt", 1));
        tick(level, 20);
        assertEquals("minecraft:oak_pressure_plate[powered=true]", BL.describe(level.getBlockState(new Pos(0, -60, 0))));
        assertEquals("minecraft:stone_pressure_plate[powered=false]", BL.describe(level.getBlockState(new Pos(3, -60, 0))), "돌 압력판은 생물만");
    }

    @Test
    void weightedPlate_countsItemEntities() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:light_weighted_pressure_plate"), 3);
        item(level, 0.3, -59.5, 0.3, stack("minecraft:dirt", 1));
        item(level, 0.7, -59.5, 0.7, stack("minecraft:stone", 1));
        item(level, 0.5, -59.5, 0.5, stack("minecraft:sand", 1));
        tick(level, 20);
        assertEquals("minecraft:light_weighted_pressure_plate[power=3]", BL.describe(level.getBlockState(new Pos(0, -60, 0))),
                "가벼운 판: ceil(3/15 × 15) = 3");
    }

    @Test
    void item_bouncesOnSlime() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:slime_block"), 3);
        ItemEntity e = item(level, 0.5, -55, 0.5, stack("minecraft:dirt", 1));
        double maxVyAfterLanding = 0;
        for (int i = 0; i < 60; i++) {
            tick(level, 1);
            maxVyAfterLanding = Math.max(maxVyAfterLanding, e.vy());
        }
        assertTrue(maxVyAfterLanding > 0.1, "생물이 아니면 0.8 배로 튕긴다: " + maxVyAfterLanding);
    }

    @Test
    void stuckItem_isPushedOutOfBlock() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), BL.defaultState("minecraft:stone"), 3);
        ItemEntity e = item(level, 0.5, -59.8, 0.5, stack("minecraft:dirt", 1));
        tick(level, 40);
        assertTrue(e.y() >= -59.0 || Math.abs(e.x() - 0.5) > 0.5 || Math.abs(e.z() - 0.5) > 0.5, "블록에서 밀려나야 한다: " + e);
    }

    @Test
    void popResource_spawnsItemWithDefaultPickupDelay() {
        var level = new BlockPlacementTest.TestLevel();
        level.popResource(new Pos(0, -59, 0), stack("minecraft:dirt", 3));
        assertEquals(1, level.entities().size());
        ItemEntity e = (ItemEntity) level.entities().all().iterator().next();
        assertEquals(10, e.pickupDelay());
        assertTrue(e.x() >= 0.25 && e.x() <= 0.75 && e.z() >= 0.25 && e.z() <= 0.75);
        assertTrue(e.y() >= -59 + 0.25 - 0.125 && e.y() <= -59 + 0.75 - 0.125);
        assertEquals(0.2, e.vy(), 0.0);
        assertEquals(3, e.getItem().count());
    }

    @Test
    void doTileDropsOff_spawnsNothing() {
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            protected LevelServices services() {
                return new LevelServices.Delegating(LevelServices.DEFAULT) {
                    @Override
                    public boolean gameRule(String name) { return !name.equals("doTileDrops") && super.gameRule(name); }
                };
            }
        };
        level.popResource(new Pos(0, -59, 0), stack("minecraft:dirt", 3));
        assertTrue(level.entities().isEmpty());
    }

    @Test
    void brokenChest_spillsContents_inStacksOf10To30() {
        var level = new BlockPlacementTest.TestLevel();
        Pos p = new Pos(0, -60, 0);
        level.setBlock(p, BL.defaultState("minecraft:chest"), 3);
        var col = level.chunk(0, 0);
        var chest = org.domi.furnace.server.world.ChestBlockEntity.empty(0, -60, 0);
        chest.items[0] = stack("minecraft:cobblestone", 64);
        chest.items[5] = stack("minecraft:diamond", 3);
        col.putChest(chest);
        level.setBlock(p, BL.defaultState("minecraft:air"), 3);
        int total = 0;
        for (Entity e : level.entities().all()) {
            ItemStack s = ((ItemEntity) e).getItem();
            total += s.count();
            assertTrue(s.count() >= 1 && s.count() <= 30, "한 번에 10~30 개(남은 것은 그 이하)");
            assertTrue(e.x() >= 0.125 && e.x() <= 0.875 && e.y() >= -60 && e.y() <= -59.25);
        }
        assertEquals(67, total);
        assertTrue(level.entities().size() >= 4, "64 개는 최소 세 덩이 + 다이아");
        assertTrue(chest.items[0].isEmpty());
    }

    // ------------------------------------------------------------------ 줍기·추적

    private static Player player(double x, double y, double z) {
        Player p = new Player(900_000 + (int) (Math.random() * 1000), UUID.randomUUID(), "P", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(x, y, z, 0, 0, true);
        return p;
    }

    @Test
    void playerTouch_picksUpWhenDelayIsOver() {
        var level = new BlockPlacementTest.TestLevel();
        ItemEntity e = item(level, 0.5, -60, 0.5, stack("minecraft:dirt", 5));
        e.setPickUpDelay(2);
        Player p = player(0.5, -60, 0.5);
        e.playerTouch(p);
        assertFalse(e.isRemoved(), "줍기 지연 중에는 못 줍는다");
        tick(level, 2);
        e.playerTouch(p);
        assertTrue(e.isRemoved());
        assertEquals(5, p.inventory().get(36).count(), "든 칸(핫바 0)부터 넣는다");
    }

    @Test
    void playerTouch_partialPickup_shrinksStackWithoutTaking() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = player(0.5, -60, 0.5);
        for (int ws = 9; ws <= 44; ws++) p.inventory().set(ws, stack("minecraft:stone", 64));
        p.inventory().set(36, stack("minecraft:dirt", 60));
        p.inventory().set(45, stack("minecraft:stone", 64));
        ItemEntity e = item(level, 0.5, -60, 0.5, stack("minecraft:dirt", 10));
        e.setNoPickUpDelay();
        e.playerTouch(p);
        assertFalse(e.isRemoved(), "바닐라 Inventory.add 는 마지막 시도가 다 넣지 못하면 거짓 — 줍기 처리를 하지 않는다");
        assertEquals(6, e.getItem().count());
        assertEquals(64, p.inventory().get(36).count());
    }

    @Test
    void tracker_spawnsForNearbyPlayer_inABundle_andSendsMotionThenRemove() {
        Player p = player(3, -60, 3);
        EmbeddedChannel ch = (EmbeddedChannel) channelOf(p);
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            protected Iterable<Player> trackingPlayers() { return List.of(p); }
        };
        ItemEntity e = new ItemEntity(level, level.nextEntityId(), 0.5, -59, 0.5, stack("minecraft:dirt", 1));
        level.addFreshEntity(e);
        List<Integer> ids = drainIds(ch);
        assertEquals(List.of(0x00, 0x01, 0x58, 0x5A, 0x00), ids, "묶음: add_entity → 데이터(아이템) → 속도");
        assertTrue(e.tracker.isSeenBy(p));
        e.discard();
        assertEquals(List.of(0x42), drainIds(ch), "remove_entities");
    }

    @Test
    void tracker_farPlayer_doesNotSee() {
        Player p = player(200, -60, 0);
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            protected Iterable<Player> trackingPlayers() { return List.of(p); }
        };
        ItemEntity e = item(level, 0.5, -59, 0.5, stack("minecraft:dirt", 1));
        assertFalse(e.tracker.isSeenBy(p), "아이템 추적 거리 6 청크(96 블록)");
    }

    private static io.netty.channel.Channel channelOf(Player p) {
        try {
            var f = Player.class.getDeclaredField("channel");
            f.setAccessible(true);
            return (io.netty.channel.Channel) f.get(p);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static List<Integer> drainIds(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Integer> ids = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            ids.add(Types.readVarInt(b));
            b.release();
        }
        return ids;
    }

    // ------------------------------------------------------------------ 저장

    @Test
    void chunkEntities_roundTripThroughNbt() {
        var level = new BlockPlacementTest.TestLevel();
        ItemEntity e = item(level, 0.5, -59, 0.5, stack("minecraft:diamond", 7));
        e.setPickUpDelay(33);
        var col = level.chunk(0, 0);
        col.entitiesLoaded(true);
        var root = level.chunkEntitiesForSave(col, 0, 0, true);
        assertNotNull(root);
        assertTrue(e.isRemoved(), "내릴 때는 저장한 뒤 뺀다");
        assertTrue(level.entities().isEmpty());

        var level2 = new BlockPlacementTest.TestLevel();
        var col2 = level2.chunk(0, 0);
        level2.loadChunkEntities(col2, root);
        assertEquals(1, level2.entities().size());
        ItemEntity back = (ItemEntity) level2.entities().all().iterator().next();
        assertEquals(e.uuid(), back.uuid());
        assertEquals(7, back.getItem().count());
        assertEquals(33, back.pickupDelay());
        assertEquals(-59.0, back.y(), 0.0);
    }

    @Test
    void unknownEntityTypes_arePreservedVerbatim() {
        var level = new BlockPlacementTest.TestLevel();
        var col = level.chunk(0, 0);
        var root = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        var pig = new org.domi.furnace.server.net.io.nbt.NbtCompound();   // 아직 없는 종류(구현되면 다른 종류로 바꾼다)
        pig.putString("id", "minecraft:test_unknown_mob");
        pig.putFloat("Health", 20.0F);
        list.add(pig);
        root.putList("Entities", list);
        level.loadChunkEntities(col, root);
        assertTrue(level.entities().isEmpty());
        var saved = level.chunkEntitiesForSave(col, 0, 0, false);
        assertEquals(1, saved.getList("Entities").size());
        assertSame(pig, saved.getList("Entities").get(0));
    }

    // ------------------------------------------------------------------ 충돌 격자

    @Test
    void voxelCollide_usesShapeGrid_likeVanilla() {
        // 바닥 반 블록(y 0..0.5) 위로 떨어지는 상자는 0.5 에서 멈춘다
        Shape slab = new Shape(new double[] {0, 0, 0, 1, 0.5, 1}).move(0, -60, 0);
        AABB box = new AABB(0.2, -58.0, 0.2, 0.4, -57.8, 0.4);
        assertEquals(-1.5, slab.collide(1, box, -3.0), 1e-12);
        // 계단 아래 상자(x 0..1, y 0..0.5) 안에서 +x 로 움직이면 x 격자선(0.5)에서 막힌다 — 바닐라 격자 규칙
        Shape stairs = new Shape(new double[] {0, 0, 0, 1, 0.5, 1, 0.5, 0.5, 0, 1, 1, 1});
        AABB inside = new AABB(0.0, 0.1, 0.2, 0.2, 0.4, 0.4);
        assertEquals(0.3, stairs.collide(0, inside, 1.0), 1e-12);
        // 꽉 찬 블록 안에서는 막히지 않는다(격자 칸이 하나)
        assertEquals(1.0, Shape.BLOCK.collide(0, inside, 1.0), 1e-12);
    }

    @Test
    void entityQuery_returnsOnlyIntersecting() {
        var level = new BlockPlacementTest.TestLevel();
        item(level, 0.5, -60, 0.5, stack("minecraft:dirt", 1));
        item(level, 10.5, -60, 0.5, stack("minecraft:dirt", 1));
        List<Entity> near = level.getEntities(null, new AABB(0, -61, 0, 1, -59, 1), null);
        assertEquals(1, near.size());
    }
}
