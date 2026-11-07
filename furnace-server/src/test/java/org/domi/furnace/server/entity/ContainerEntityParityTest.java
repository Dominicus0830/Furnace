// M3 컨테이너·수레 보강: 스포너 수레 틱·저장, 라마 카펫(몸통 칸), 열지 않은 전리품 표(부술 때·창 칸 접근), 창 제목(이름표·팀 색).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.EntityContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerEntityParityTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static ItemStack item(String name, int count) {
        return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", name), count);
    }

    /** 근처 플레이어가 있고, 전리품 표를 풀면 첫 칸에 다이아몬드 1개를 넣는 시험 레벨. */
    static final class Lvl extends BlockPlacementTest.TestLevel {
        boolean playerNear = true;
        final List<Object> unpacked = new ArrayList<>();
        final RandomSource rnd = RandomSource.create(5);

        @Override public RandomSource random() { return rnd; }

        @Override public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) { return playerNear; }

        @Override
        public void unpackContainerLoot(Object c) {
            unpacked.add(c);
            if (c instanceof ContainerEntity ce && ce.lootTable() != null) {
                ce.setLootTable(null);
                ce.chestVehicleItems()[0] = item("minecraft:diamond", 3);
            }
        }
    }

    private static void tick(Entity e) {
        e.setOldPosAndRot();
        e.tickCount++;
        e.tick();
    }

    // ------------------------------------------------------------------ 스포너 수레

    @Test
    void spawnerMinecartTicksItsSpawner() {
        var level = new Lvl();
        var cart = (MinecartSpawner) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.SPAWNER);
        NbtCompound t = cart.save();
        t.putShort("Delay", (short) 5);
        var loaded = (MinecartSpawner) Entities.load(level, t);
        level.addFreshEntity(loaded);
        tick(loaded);
        assertEquals(4, loaded.spawner().spawnDelay(), "근처에 플레이어가 있으면 매 틱 지연이 줄어든다");
        assertEquals((short) 4, loaded.save().getShort("Delay"));

        level.playerNear = false;
        tick(loaded);
        assertEquals(4, loaded.spawner().spawnDelay(), "플레이어가 범위 밖이면 멈춘다");
    }

    @Test
    void spawnerMinecartDelaysWhenEntityIdUnknownAndKeepsData() {
        var level = new Lvl();
        var cart = (MinecartSpawner) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.SPAWNER);
        NbtCompound t = cart.save();
        t.putShort("Delay", (short) 0);
        NbtCompound entity = new NbtCompound();
        entity.putString("id", "minecraft:no_such_mob");
        NbtCompound sd = new NbtCompound();
        sd.putCompound("entity", entity);
        t.putCompound("SpawnData", sd);
        t.putShort("SpawnCount", (short) 2);
        var loaded = (MinecartSpawner) Entities.load(level, t);
        level.addFreshEntity(loaded);
        tick(loaded);
        int d = loaded.spawner().spawnDelay();
        assertTrue(d >= 200 && d < 800, "알 수 없는 엔티티면 지연을 다시 고른다: " + d);
        NbtCompound out = loaded.save();
        assertEquals((short) 2, out.getShort("SpawnCount"));
        assertEquals("minecraft:no_such_mob", out.getCompound("SpawnData").getCompound("entity").getString("id"));
    }

    // ------------------------------------------------------------------ 라마 카펫

    @Test
    void llamaWearsOnlyWoolCarpetsInBodySlot() {
        var level = new Lvl();
        var llama = new Llama(level, level.nextEntityId());
        assertTrue(llama.canUseBodySlot());
        assertTrue(llama.isBodyArmorItem(item("minecraft:white_carpet", 1)));
        assertTrue(llama.isBodyArmorItem(item("minecraft:red_carpet", 1)));
        assertFalse(llama.isBodyArmorItem(item("minecraft:moss_carpet", 1)), "이끼 카펫은 양털 카펫 태그가 아니다");
        assertFalse(llama.isBodyArmorItem(item("minecraft:iron_horse_armor", 1)));
        assertFalse(llama.isBodyArmorItem(ItemStack.EMPTY));
        assertFalse(llama.isWearingBodyArmor());
        llama.setBodyArmorItem(item("minecraft:red_carpet", 1));
        assertTrue(llama.isWearingBodyArmor());

        var back = (Llama) Entities.load(level, llama.save());
        assertEquals("minecraft:red_carpet", org.domi.furnace.server.item.ItemTools.itemName(back.getItemBySlot(EquipmentSlot.BODY)));
    }

    @Test
    void traderLlamaAlsoWearsCarpet() {
        var level = new Lvl();
        var llama = new TraderLlama(level, level.nextEntityId());
        assertTrue(llama.canUseBodySlot());
        assertTrue(llama.isBodyArmorItem(item("minecraft:blue_carpet", 1)));
    }

    @Test
    void donkeyAndMuleCannotWearBodyArmor() {
        var level = new Lvl();
        assertFalse(new Donkey(level, level.nextEntityId()).canUseBodySlot());
        assertFalse(new Mule(level, level.nextEntityId()).canUseBodySlot());
    }

    // ------------------------------------------------------------------ 열지 않은 전리품 표

    @Test
    void destroyingLootTableChestMinecartDropsGeneratedLoot() {
        var level = new Lvl();
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(cart);
        cart.setLootTable("minecraft:chests/abandoned_mineshaft", 123L);
        cart.remove(Entity.RemovalReason.KILLED);
        assertEquals(1, level.unpacked.size(), "내용물을 쏟기 전에 표를 푼다");
        int diamonds = 0;
        for (Entity e : level.entities().all()) {
            if (e instanceof ItemEntity ie && org.domi.furnace.server.item.ItemTools.itemName(ie.getItem()).equals("minecraft:diamond")) diamonds += ie.getItem().count();
        }
        assertEquals(3, diamonds);
    }

    @Test
    void windowSlotAccessUnpacksLootTableOnce() {
        var level = new Lvl();
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(cart);
        cart.setLootTable("minecraft:chests/abandoned_mineshaft", 1L);
        var c = new EntityContainer(cart);
        assertEquals(3, c.get(0).count());
        assertEquals(3, c.get(0).count());
        assertEquals(1, level.unpacked.size(), "한 번 풀면 표가 사라져 다시 풀지 않는다");
        assertNull(cart.lootTable());
    }

    @Test
    void chestBoatWithLootTableDropsLootToo() {
        var level = new Lvl();
        var boat = new ChestBoat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        boat.setLootTable("minecraft:chests/shipwreck_supply", 9L);
        boat.remove(Entity.RemovalReason.KILLED);
        assertEquals(1, level.unpacked.size());
    }

    // ------------------------------------------------------------------ 창 제목

    @Test
    void containerTitleUsesCustomNameOrTypeName() {
        var level = new Lvl();
        var cart = (MinecartChest) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.CHEST);
        assertEquals("entity.minecraft.chest_minecart", cart.containerTitle().translationKey());
        cart.setCustomName(org.domi.furnace.api.text.Text.text("보물"));
        assertEquals("보물", cart.containerTitle().getString());
        var hopper = (MinecartHopper) AbstractMinecart.create(level, 0.5, -60, 0.5, AbstractMinecart.Type.HOPPER);
        assertEquals("entity.minecraft.hopper_minecart", hopper.containerTitle().translationKey());
    }
}
