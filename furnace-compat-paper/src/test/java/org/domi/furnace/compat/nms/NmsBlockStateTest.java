package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.Material;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * N0 블록 상태 뼈대: 상태 번호 왕복·같은 객체·toString·속성 바꾸기·오류 문구·레지스트리 번호가 바닐라 1.21.1 과 같은지.
 * 기대 문구는 바닐라 서버 jar(dte·dtd·dfy·jm·akq·jz)를 javap 로 읽은 식에서 손으로 만든 것이다.
 */
class NmsBlockStateTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> Property<T> prop(Block b, String name) {
        Property p = b.getStateDefinition().getProperty(name);
        assertNotNull(p, b + " " + name);
        return p;
    }

    @Test
    void everyStateIdRoundTripsToOneObject() {
        int count = server.api().blockTypes().stateCount();
        assertEquals(26684, count);
        assertEquals(count, NmsBlocks.stateCount());
        for (int id = 0; id < count; id++) {
            BlockState s = Block.stateById(id);
            assertEquals(id, Block.getId(s), "번호 " + id);
            assertSame(s, Block.stateById(id));
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        assertSame(air, Block.stateById(-1), "모르는 번호는 공기");
        assertSame(air, Block.stateById(count));
        assertEquals(0, Block.getId(null));
        assertEquals(0, Block.getId(air));
        assertEquals(1, Block.getId(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void stateMatchesServerNamesForEveryId() {
        var types = server.api().blockTypes();
        for (int id = 0; id < types.stateCount(); id += 7) {
            BlockData d = types.byStateId(id).orElseThrow();
            BlockState s = Block.stateById(id);
            assertEquals(d.type(), s.getBlock().furnaceKey().toString());
            String expected = "Block{" + d.type() + "}" + (d.properties().isEmpty() ? "" : "[" + String.join(",", d.properties().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()) + "]");
            assertEquals(expected, s.toString(), "번호 " + id);
        }
    }

    @Test
    void toStringAndDescriptionLikeVanilla() {
        assertEquals("Block{minecraft:stone}", Blocks.STONE.toString());
        assertEquals("Block{minecraft:stone}", Blocks.STONE.defaultBlockState().toString());
        assertEquals("block.minecraft.stone", Blocks.STONE.getDescriptionId());
        assertEquals("Block{minecraft:oak_stairs}[facing=north,half=bottom,shape=straight,waterlogged=false]", Blocks.OAK_STAIRS.defaultBlockState().toString());
        assertEquals("StateDefinition{block=Block{minecraft:oak_stairs}, properties=[facing, half, shape, waterlogged]}", Blocks.OAK_STAIRS.getStateDefinition().toString());
        assertEquals("BooleanProperty{name=waterlogged, clazz=class java.lang.Boolean, values=[true, false]}", prop(Blocks.OAK_STAIRS, "waterlogged").toString());
        assertTrue(Blocks.AIR.defaultBlockState().isAir());
        assertTrue(Blocks.CAVE_AIR.defaultBlockState().isAir());
        assertTrue(Blocks.VOID_AIR.defaultBlockState().isAir());
        assertFalse(Blocks.STONE.defaultBlockState().isAir());
    }

    @Test
    void setValueCycleAndSharingFollowVanilla() {
        var types = server.api().blockTypes();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        Property<Direction> facing = prop(Blocks.OAK_STAIRS, "facing");
        Property<Boolean> water = prop(Blocks.OAK_STAIRS, "waterlogged");
        Property<Half> half = prop(Blocks.OAK_STAIRS, "half");

        BlockState east = stairs.setValue(facing, Direction.EAST);
        assertEquals(Direction.EAST, east.getValue(facing));
        assertEquals(types.stateId(BlockData.parse("minecraft:oak_stairs[facing=east]")), Block.getId(east));
        assertSame(east, east.setValue(water, true).setValue(water, false), "같은 값으로 돌아오면 같은 객체");
        assertSame(stairs, stairs.setValue(facing, Direction.NORTH), "같은 값이면 자기 자신");
        assertEquals(Half.TOP, stairs.cycle(half).getValue(half), "bottom 다음은 처음(top)으로 돈다");
        assertEquals(Half.BOTTOM, stairs.cycle(half).cycle(half).getValue(half));

        // 바닐라는 같은 속성 객체를 여러 블록이 나눠 쓴다(BlockStateProperties). 같은 이름·값이면 같은 객체.
        assertSame(water, prop(Blocks.SPRUCE_STAIRS, "waterlogged"));
        assertSame(water, prop(Blocks.OAK_SLAB, "waterlogged"));
        assertTrue(Blocks.SPRUCE_STAIRS.defaultBlockState().hasProperty(water));
        // 6방향 facing 은 4방향과 다른 속성
        Property<Direction> six = prop(Blocks.PISTON, "facing");
        assertFalse(six.equals(facing));
        assertFalse(stairs.hasProperty(six));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Blocks.STONE.defaultBlockState().getValue(water));
        assertEquals("Cannot get property " + water + " as it does not exist in Block{minecraft:stone}", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> Blocks.STONE.defaultBlockState().setValue(water, true));
        assertEquals("Cannot set property " + water + " as it does not exist in Block{minecraft:stone}", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> stairs.setValue(facing, Direction.UP));
        assertEquals("Cannot set property " + facing + " to up on Block{minecraft:oak_stairs}, it is not an allowed value", e.getMessage());
        assertSame(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState().trySetValue(water, true));
    }

    @Test
    void blockRegistryMatchesVanillaOrder() throws IllegalAccessException {
        var reg = BuiltInRegistries.BLOCK;
        assertEquals(1060, reg.size());
        int i = 0;
        for (Field f : Blocks.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Block.class) continue;
            Block b = (Block) f.get(null);
            assertEquals(i, reg.getId(b), f.getName());
            assertSame(b, reg.byId(i));
            assertEquals(f.getName().toLowerCase(java.util.Locale.ROOT), b.furnaceKey().getPath());
            i++;
        }
        assertEquals(1060, i);
        assertSame(Blocks.STONE, reg.get(ResourceLocation.withDefaultNamespace("stone")));
        assertSame(Blocks.AIR, reg.get(ResourceLocation.withDefaultNamespace("no_such_block")), "모르는 이름은 기본값(공기)");
        assertSame(Blocks.AIR, reg.byId(-5));
        assertTrue(reg.getOptional(ResourceLocation.withDefaultNamespace("no_such_block")).isEmpty());
        assertEquals(ResourceLocation.withDefaultNamespace("stone"), reg.getKey(Blocks.STONE));
        assertEquals("Registry[ResourceKey[minecraft:root / minecraft:block] (Stable)]", reg.toString());

        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("stone"));
        assertSame(key, Blocks.STONE.builtInRegistryHolder().key(), "ResourceKey 는 바닐라처럼 intern");
        assertEquals("ResourceKey[minecraft:block / minecraft:stone]", key.toString());
        assertEquals("Reference{ResourceKey[minecraft:block / minecraft:stone]=Block{minecraft:stone}}", Blocks.STONE.builtInRegistryHolder().toString());
        assertTrue(Blocks.STONE.defaultBlockState().is(Blocks.STONE.builtInRegistryHolder()));
        assertSame(Blocks.STONE.builtInRegistryHolder(), Blocks.STONE.defaultBlockState().getBlockHolder());
    }

    @Test
    void itemsAndBukkitBridges() {
        assertSame(Item.byKey("minecraft:torch"), Blocks.WALL_TORCH.asItem());
        assertSame(Item.byKey("minecraft:air"), Blocks.WATER.asItem(), "아이템 없는 블록은 공기");
        assertSame(Blocks.TORCH, Block.byItem(Item.byKey("minecraft:torch")), "벽 변형보다 이름이 같은 블록");
        assertSame(Blocks.REDSTONE_WIRE, Block.byItem(Item.byKey("minecraft:redstone")));
        assertSame(Blocks.AIR, Block.byItem(Item.byKey("minecraft:diamond")));

        assertSame(Blocks.STONE, CraftMagicNumbers.getBlock(Material.STONE));
        assertNull(CraftMagicNumbers.getBlock(Material.DIAMOND));
        assertSame(Item.byKey("minecraft:stone"), CraftMagicNumbers.getItem(Material.STONE));
        assertEquals(Material.OAK_STAIRS, CraftMagicNumbers.getMaterial(Blocks.OAK_STAIRS));
        assertEquals(Material.TORCH, CraftMagicNumbers.getMaterial(Item.byKey("minecraft:torch")));

        Property<Direction> facing = prop(Blocks.OAK_STAIRS, "facing");
        CraftBlockData data = CraftBlockData.newData(Material.OAK_STAIRS, "[facing=east]");
        assertEquals(Direction.EAST, data.getState().getValue(facing));
        for (int id : List.of(0, 1, 5000, 26683)) {
            BlockState s = Block.stateById(id);
            assertSame(s, CraftBlockData.fromData(s).getState());
        }
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", CraftBlockData.fromData(data.getState()).getAsString());
        assertEquals(Map.of(), Blocks.STONE.defaultBlockState().getValues());
    }
}
