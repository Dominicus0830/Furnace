// 발사기·공급기: 신호 → 4틱 예약, 무작위 칸, 기본 던지기(1000·2000), 빈 발사기 1001, 공급기의 용기 넣기, 아이템별 동작(화살·양동이·TNT·부싯돌·뼛가루·배·생성 알·골렘 바탕).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.entity.BarrelBlockEntity;
import org.domi.furnace.server.block.entity.DispenserBlockEntity;
import org.domi.furnace.server.entity.AbstractArrow;
import org.domi.furnace.server.entity.Arrow;
import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PrimedTnt;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DispenserBlockTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<int[]> levelEvents = new ArrayList<>();

        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { levelEvents.add(new int[] {type, data}); }
    }

    static final Pos POS = new Pos(0, -50, 0);

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static int block(String name, String facing) { return B.prop("facing").with(B.defaultState("minecraft:" + name), facing); }

    static DispenserBlockEntity place(Lvl l, String name, String facing) {
        l.setBlock(POS, block(name, facing), 3);
        return (DispenserBlockEntity) l.getBlockEntity(POS);
    }

    static void fire(Lvl l) {
        int st = l.getBlockState(POS);
        B.behavior(st).tick(st, l, POS, l.random());
    }

    static <T extends Entity> List<T> entities(Lvl l, Class<T> c) {
        List<T> out = new ArrayList<>();
        for (Entity e : l.entities().all()) if (c.isInstance(e)) out.add(c.cast(e));
        return out;
    }

    @Test
    void signalSchedulesFourTicksAndSetsTriggered() {
        Lvl l = new Lvl();
        place(l, "dispenser", "east");
        l.setBlock(POS.relative(Dir.WEST), B.defaultState("minecraft:redstone_block"), 3);
        int st = l.getBlockState(POS);
        assertTrue(B.prop("triggered").is(st));
        assertTrue(l.hasScheduledTick(POS, B.block(st)));
    }

    @Test
    void emptyDispenserPlaysFailSound() {
        Lvl l = new Lvl();
        place(l, "dispenser", "east");
        fire(l);
        assertEquals(1, l.levelEvents.size());
        assertArrayEquals(new int[] {1001, 0}, l.levelEvents.get(0));
        assertTrue(entities(l, ItemEntity.class).isEmpty());
    }

    @Test
    void defaultBehaviorDropsOneItemForward() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[4] = item("stone", 3);
        fire(l);
        assertEquals(2, be.items[4].count());
        var items = entities(l, ItemEntity.class);
        assertEquals(1, items.size());
        ItemEntity ie = items.get(0);
        assertEquals(1, ie.getItem().count());
        assertEquals(POS.x() + 0.5 + 0.7, ie.x(), 1e-9, "가운데 + 0.7 앞");
        assertEquals(POS.y() + 0.5 - 0.15625, ie.y(), 1e-9, "옆으로 쏘면 0.15625 아래");
        assertTrue(ie.vx() > 0.0, "앞(동쪽)으로");
        assertArrayEquals(new int[] {1000, 0}, l.levelEvents.get(0));
        assertArrayEquals(new int[] {2000, Dir.EAST}, l.levelEvents.get(1));
    }

    @Test
    void randomSlotOnlyPicksFilledSlots() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "up");
        be.items[2] = item("stone", 64);
        be.items[7] = item("dirt", 64);
        for (int i = 0; i < 40; i++) fire(l);
        assertEquals(128 - 40, be.items[2].count() + be.items[7].count());
        assertTrue(be.items[2].count() < 64 && be.items[7].count() < 64, "두 칸 다 고른다");
        for (int i = 0; i < 9; i++) if (i != 2 && i != 7) assertTrue(be.items[i].isEmpty());
    }

    @Test
    void dropperInsertsIntoContainerSilently() {
        Lvl l = new Lvl();
        var be = place(l, "dropper", "east");
        l.setBlock(POS.relative(Dir.EAST), B.defaultState("minecraft:barrel"), 3);
        var barrel = (BarrelBlockEntity) l.getBlockEntity(POS.relative(Dir.EAST));
        be.items[0] = item("stone", 2);
        fire(l);
        assertEquals(1, be.items[0].count());
        assertEquals(1, barrel.items[0].count());
        assertTrue(l.levelEvents.isEmpty(), "용기에 넣을 때는 소리 없음");
        assertTrue(entities(l, ItemEntity.class).isEmpty());
    }

    @Test
    void dropperWithoutContainerDropsAndEmptyDropperFails() {
        Lvl l = new Lvl();
        var be = place(l, "dropper", "east");
        be.items[0] = item("arrow", 1);
        fire(l);
        assertTrue(be.items[0].isEmpty());
        assertEquals(1, entities(l, ItemEntity.class).size(), "공급기는 화살도 그냥 던진다");
        assertTrue(entities(l, Arrow.class).isEmpty());
        l.levelEvents.clear();
        fire(l);
        assertEquals(1, l.levelEvents.size());
        assertArrayEquals(new int[] {1001, 0}, l.levelEvents.get(0));
    }

    @Test
    void arrowIsShotWithPickupAllowed() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[0] = item("arrow", 5);
        fire(l);
        assertEquals(4, be.items[0].count());
        var arrows = entities(l, Arrow.class);
        assertEquals(1, arrows.size());
        Arrow a = arrows.get(0);
        assertEquals(AbstractArrow.Pickup.ALLOWED, a.pickup);
        assertEquals(POS.x() + 0.5 + 0.7, a.x(), 1e-9);
        assertEquals(POS.y() + 0.5 + 0.1, a.y(), 1e-9);
        assertTrue(a.vx() > 1.0, "힘 1.1");
        assertArrayEquals(new int[] {1002, 0}, l.levelEvents.get(0));
        assertArrayEquals(new int[] {2000, Dir.EAST}, l.levelEvents.get(1));
    }

    @Test
    void waterBucketPlacesWaterAndEmptyBucketPicksItUp() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        Pos front = POS.relative(Dir.EAST);
        be.items[0] = item("water_bucket", 1);
        fire(l);
        assertTrue(Fluids.isWater(l.getFluidState(front)));
        assertEquals("minecraft:bucket", ItemTools.itemName(be.items[0]));
        fire(l);
        assertEquals("minecraft:water_bucket", ItemTools.itemName(be.items[0]));
        assertFalse(Fluids.isWater(l.getFluidState(front)));
    }

    @Test
    void bucketStackKeepsRestAndStoresFilledBucket() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        l.setBlock(POS.relative(Dir.EAST), B.defaultState("minecraft:water"), 3);
        be.items[0] = item("bucket", 3);
        fire(l);
        assertEquals(2, be.items[0].count());
        assertEquals("minecraft:water_bucket", ItemTools.itemName(be.items[1]), "남은 것은 다음 빈 칸에");
    }

    @Test
    void tntIsPrimedInFront() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[0] = item("tnt", 1);
        fire(l);
        var tnt = entities(l, PrimedTnt.class);
        assertEquals(1, tnt.size());
        assertEquals(POS.x() + 1.5, tnt.get(0).x(), 1e-9);
        assertTrue(be.items[0].isEmpty());
    }

    @Test
    void flintAndSteelLightsFireAndTakesDurability() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "down");
        Pos front = POS.below();
        l.setBlock(front.below(), B.defaultState("minecraft:stone"), 3);
        be.items[0] = item("flint_and_steel", 1);
        fire(l);
        assertTrue(B.is(l.getBlockState(front), "minecraft:fire"));
        assertEquals(1, ItemTools.damage(be.items[0]));
        assertArrayEquals(new int[] {1000, 0}, l.levelEvents.get(0));
    }

    @Test
    void boneMealWithoutTargetFailsAndKeepsStack() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[0] = item("bone_meal", 2);
        fire(l);
        assertEquals(2, be.items[0].count());
        assertArrayEquals(new int[] {1001, 0}, l.levelEvents.get(0));
        assertTrue(entities(l, ItemEntity.class).isEmpty());
    }

    @Test
    void boatNeedsWater() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[0] = item("oak_boat", 1);
        fire(l);
        assertTrue(entities(l, Boat.class).isEmpty());
        assertEquals(1, entities(l, ItemEntity.class).size(), "물이 없으면 그냥 던진다");
        Lvl w = new Lvl();
        var be2 = place(w, "dispenser", "east");
        w.setBlock(POS.relative(Dir.EAST), B.defaultState("minecraft:water"), 3);
        be2.items[0] = item("oak_boat", 1);
        fire(w);
        var boats = entities(w, Boat.class);
        assertEquals(1, boats.size());
        assertEquals(POS.y() + 0.5 + 1.0, boats.get(0).y(), 1e-9);
        assertEquals(270.0F, boats.get(0).yRot(), "동쪽 = toYRot 270");
    }

    @Test
    void spawnEggSpawnsMob() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[0] = item("pig_spawn_egg", 1);
        fire(l);
        assertEquals(1, entities(l, org.domi.furnace.server.entity.Pig.class).size());
        assertTrue(be.items[0].isEmpty());
    }

    @Test
    void carvedPumpkinOnSnowBaseBuildsGolem() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        Pos front = POS.relative(Dir.EAST);
        l.setBlock(front.below(), B.defaultState("minecraft:snow_block"), 3);
        l.setBlock(front.below(2), B.defaultState("minecraft:snow_block"), 3);
        be.items[0] = item("carved_pumpkin", 1);
        fire(l);
        assertTrue(be.items[0].isEmpty());
        assertEquals(1, entities(l, org.domi.furnace.server.entity.SnowGolem.class).size());
        assertArrayEquals(new int[] {1000, 0}, l.levelEvents.get(l.levelEvents.size() - 2));
    }

    @Test
    void carvedPumpkinWithoutBaseOrMobFails() {
        Lvl l = new Lvl();
        var be = place(l, "dispenser", "east");
        be.items[0] = item("carved_pumpkin", 1);
        fire(l);
        assertEquals(1, be.items[0].count(), "골렘 바탕도 생물도 없으면 그대로");
        assertArrayEquals(new int[] {1001, 0}, l.levelEvents.get(0));
    }
}
