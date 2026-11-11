package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

/**
 * N0 2부: 아이템 레지스트리·Items 상수·레지스트리 키·SectionPos·InteractionHand. SectionPos 기대값은 바닐라 1.21.1 {@code kf} 바이트코드
 * (x 22비트 &lt;&lt;42, y 20비트, z 22비트 &lt;&lt;20, 상대 short = x&lt;&lt;8 | z&lt;&lt;4 | y)에서 손으로 셈했다.
 */
class NmsItemsSectionTest {

    @Test
    void itemRegistryAndConstantsFollowVanillaOrder() throws IllegalAccessException {
        try (var server = FurnaceTestServer.builder().start()) {
            var reg = BuiltInRegistries.ITEM;
            assertEquals(1333, reg.size());
            int i = 0;
            for (Field f : Items.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Item.class) continue;
                Item it = (Item) f.get(null);
                assertEquals(i, reg.getId(it), f.getName());
                assertSame(it, Item.byId(i));
                assertEquals(f.getName().toLowerCase(java.util.Locale.ROOT), it.furnaceKey().substring("minecraft:".length()));
                i++;
            }
            assertEquals(1333, i);
            assertSame(Item.byKey("minecraft:stone"), Items.STONE, "ItemStack.getItem() 과 같은 객체");
            assertEquals(0, Item.getId(null));
            assertEquals(0, Item.getId(Items.AIR));
            assertEquals(1, Item.getId(Items.STONE));
            assertSame(Items.AIR, Item.byId(-1), "모르는 번호는 공기");
            assertSame(Items.AIR, Item.byId(1333));
            assertSame(Items.TORCH, Item.byBlock(Blocks.WALL_TORCH));
            assertSame(Items.AIR, Item.byBlock(Blocks.WATER));
            assertSame(Items.AIR, reg.get(ResourceLocation.withDefaultNamespace("no_such_item")));
            assertEquals("Registry[ResourceKey[minecraft:root / minecraft:item] (Stable)]", reg.toString());
            assertEquals("Reference{ResourceKey[minecraft:item / minecraft:stone]=minecraft:stone}", Items.STONE.builtInRegistryHolder().toString());
            assertSame(ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("stone")), Items.STONE.builtInRegistryHolder().key());
            assertEquals(server.api().gameRegistries().builtIn("minecraft:item").get(500), reg.byId(500).furnaceKey());
        }
    }

    @Test
    void registryKeysAreInterned() {
        assertEquals("ResourceKey[minecraft:root / minecraft:worldgen/biome]", Registries.BIOME.toString());
        assertSame(Registries.DIMENSION, Registries.LEVEL_STEM, "바닐라도 둘 다 minecraft:dimension 이라 같은 객체");
        assertSame(Registries.ENTITY_TYPE, ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("entity_type")));
    }

    @Test
    void sectionPosPacksLikeVanilla() {
        assertEquals((1L << 42) | 2L | (3L << 20), SectionPos.asLong(1, 2, 3));
        for (int[] c : new int[][] {{0, 0, 0}, {-1, -4, -1}, {1875000, 524287, -1875000}, {-2097152, -524288, 2097151}}) {
            long p = SectionPos.asLong(c[0], c[1], c[2]);
            assertEquals(c[0], SectionPos.x(p));
            assertEquals(c[1], SectionPos.y(p));
            assertEquals(c[2], SectionPos.z(p));
            assertEquals(SectionPos.of(c[0], c[1], c[2]), SectionPos.of(p));
        }
        SectionPos s = SectionPos.of(new BlockPos(-1, -64, 17));
        assertEquals(SectionPos.of(-1, -4, 1), s);
        assertEquals("SectionPos{x=-1, y=-4, z=1}", s.toString());
        assertEquals(-16, s.minBlockX());
        assertEquals(-1, s.maxBlockX());
        assertEquals(-49, s.maxBlockY());
        assertEquals(new BlockPos(-16, -64, 16), s.origin());
        assertEquals(new BlockPos(-8, -56, 24), s.center());
        assertEquals(new ChunkPos(-1, 1), s.chunk());
        assertEquals(SectionPos.asLong(-1, -4, 1), SectionPos.blockToSection(BlockPos.asLong(-1, -64, 17)));
        assertEquals(SectionPos.asLong(-1, -4, 1), SectionPos.asLong(new BlockPos(-1, -64, 17)));
        assertEquals(SectionPos.asLong(-1, 0, 1), SectionPos.getZeroNode(SectionPos.asLong(-1, -4, 1)));
        assertEquals(SectionPos.asLong(-1, 0, 1), SectionPos.getZeroNode(-1, 1));
        assertEquals(SectionPos.asLong(0, -4, 1), SectionPos.offset(s.asLong(), Direction.EAST));

        short rel = SectionPos.sectionRelativePos(new BlockPos(-1, -64, 17));
        assertEquals((15 << 8) | (1 << 4), rel);
        assertEquals(15, SectionPos.sectionRelativeX(rel));
        assertEquals(0, SectionPos.sectionRelativeY(rel));
        assertEquals(1, SectionPos.sectionRelativeZ(rel));
        assertEquals(new BlockPos(-1, -64, 17), s.relativeToBlockPos(rel));
        assertEquals(-1, SectionPos.blockToSectionCoord(-0.5));
        assertEquals(-1, SectionPos.posToSectionCoord(-0.5));
        assertEquals(2, SectionPos.sectionToBlockCoord(0, 2));
        assertTrue(s.offset(0, 0, 0) == s);
        assertEquals(SectionPos.of(0, -4, 1), s.offset(1, 0, 0));
        assertEquals(0, InteractionHand.MAIN_HAND.ordinal());
        assertEquals(1, InteractionHand.OFF_HAND.ordinal());
    }
}
