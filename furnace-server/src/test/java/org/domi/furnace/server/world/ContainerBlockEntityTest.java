// 용기 블록 엔티티(통·셜커 상자·깔때기·발사기·공급기)의 바닐라 저장 꼴: Items[{Slot,id,count,components}], LootTable(있으면 아이템 대신), CustomName, TransferCooldown.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BarrelBlockEntity;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.block.entity.DispenserBlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerBlockEntityTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static int id(String name) { return ItemContext.id("minecraft:item", name); }

    static BlockEntity reload(BlockEntity be) {
        return BlockEntities.loadStatic(be.pos(), -1, be.saveWithFullMetadata());
    }

    @Test
    void barrelItemsRoundTripInVanillaFormat() {
        var barrel = new BarrelBlockEntity(new Pos(1, -60, 2), -1);
        barrel.items[0] = new ItemStack(id("stone"), 12);
        barrel.items[26] = new ItemStack(id("diamond_sword"), 1, ComponentPatch.of(Map.of("minecraft:custom_model_data", 4)));
        NbtCompound tag = barrel.saveWithFullMetadata();
        assertEquals("minecraft:barrel", tag.getString("id"));
        assertEquals(2, tag.getList("Items").size());
        var first = (NbtCompound) tag.getList("Items").items().get(0);
        assertEquals((byte) 0, first.getByte("Slot"));
        assertEquals("minecraft:stone", first.getString("id"));
        assertEquals(12, first.getInt("count"));
        var back = (BarrelBlockEntity) reload(barrel);
        assertEquals(barrel.items[0], back.items[0]);
        assertEquals(barrel.items[26], back.items[26]);
        assertTrue(back.items[1].isEmpty());
    }

    @Test
    void lootTableIsSavedInsteadOfItemsAndRestored() {
        var shulker = new ShulkerBoxBlockEntity(new Pos(0, 0, 0), -1);
        shulker.lootTable = "minecraft:chests/simple_dungeon";
        shulker.lootSeed = 99L;
        NbtCompound tag = shulker.saveWithFullMetadata();
        assertEquals("minecraft:chests/simple_dungeon", tag.getString("LootTable"));
        assertFalse(tag.contains("Items"), "표가 있으면 아이템은 쓰지 않는다");
        var back = (ShulkerBoxBlockEntity) reload(shulker);
        assertEquals("minecraft:chests/simple_dungeon", back.lootTable);
        assertEquals(99L, back.lootSeed);
        assertEquals(27, back.size());
    }

    @Test
    void customNameAndHopperCooldownSurvive() {
        var hopper = new HopperBlockEntity(new Pos(3, 4, 5), -1);
        hopper.customName = "{\"text\":\"Sorter\"}";
        hopper.cooldownTime = 7;
        hopper.items[2] = new ItemStack(id("iron_ingot"), 5);
        var back = (HopperBlockEntity) reload(hopper);
        assertEquals("{\"text\":\"Sorter\"}", back.customName);
        assertEquals(7, back.cooldownTime);
        assertEquals(5, back.size());
        assertEquals(5, back.items[2].count());
        assertEquals("container.hopper", back.defaultTitleKey());
    }

    @Test
    void dispenserHas9SlotsAndDropperIsSeparateType() {
        var d = new DispenserBlockEntity(new Pos(0, 0, 0), -1);
        assertEquals(9, d.size());
        assertEquals("minecraft:dispenser", d.type());
        var dropper = new org.domi.furnace.server.block.entity.DropperBlockEntity(new Pos(0, 0, 0), -1);
        assertEquals("minecraft:dropper", dropper.type());
        assertEquals(6, dropper.typeId());
    }

    @Test
    void chunkCodecKeepsNewContainersThroughSaveAndLoad() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        var barrel = new BarrelBlockEntity(new Pos(2, -60, 3), reg.defaultStateId("minecraft:barrel"));
        barrel.items[4] = new ItemStack(id("apple"), 9);
        col.setBlock(2, -60, 3, reg.defaultStateId("minecraft:barrel"));
        col.putBlockEntity(barrel);
        ChunkColumn back = codec.fromNbt(codec.toNbt(col, 0, 0));
        var restored = back.blockEntityAt(2, -60, 3);
        assertInstanceOf(BarrelBlockEntity.class, restored);
        assertEquals(9, ((ContainerBlockEntity) restored).items[4].count());
    }

    @Test
    void isValidMatchesBlock() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var barrel = new BarrelBlockEntity(new Pos(0, 0, 0), -1);
        assertTrue(barrel.isValid(reg.defaultStateId("minecraft:barrel")));
        assertFalse(barrel.isValid(reg.defaultStateId("minecraft:stone")));
        assertTrue(new ShulkerBoxBlockEntity(new Pos(0, 0, 0), -1).isValid(reg.defaultStateId("minecraft:lime_shulker_box")));
    }
}
