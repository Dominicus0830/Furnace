// 타입 있는 블록 속성(BlockProperty·BlockData 접근자)·Direction·BlockSnapshot 값 시험.
package org.domi.furnace.api.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class BlockTypedPropertyTest {

    @Test
    void booleanIntegerAndEnumRoundTrip() {
        BlockData d = BlockData.parse("minecraft:oak_stairs[facing=north,half=top,waterlogged=true]");
        assertEquals(Direction.NORTH, d.get(BlockProperties.FACING));
        assertEquals("top", d.get(BlockProperties.HALF));
        assertTrue(d.get(BlockProperties.WATERLOGGED));
        BlockData e = d.with(BlockProperties.FACING, Direction.SOUTH).with(BlockProperties.WATERLOGGED, false);
        assertEquals("minecraft:oak_stairs[facing=south,half=top,waterlogged=false]", e.toString());
        assertEquals(d.type(), e.type());
        assertEquals(3, BlockData.of("wheat").with(BlockProperties.AGE, 3).get(BlockProperties.AGE));
        assertEquals(Axis.Y, BlockData.parse("oak_log[axis=y]").get(BlockProperties.AXIS));
    }

    @Test
    void missingPropertyAndWrongFormat() {
        BlockData d = BlockData.of("stone");
        assertFalse(d.has(BlockProperties.FACING));
        assertTrue(d.property(BlockProperties.FACING).isEmpty());
        assertThrows(NoSuchElementException.class, () -> d.get(BlockProperties.FACING));
        // 값 형식이 속성 종류와 안 맞으면 읽을 때 거부
        BlockData bad = new BlockData("minecraft:wheat", java.util.Map.of("age", "many"));
        assertThrows(IllegalArgumentException.class, () -> bad.get(BlockProperties.AGE));
        assertThrows(IllegalArgumentException.class, () -> BlockProperties.WATERLOGGED.parse("yes"));
        assertThrows(IllegalArgumentException.class, () -> BlockProperties.FACING.parse("sideways"));
    }

    @Test
    void withoutRemovesProperty() {
        BlockData d = BlockData.parse("lever[powered=true,face=floor]");
        BlockData e = d.without(BlockProperties.POWERED);
        assertFalse(e.has(BlockProperties.POWERED));
        assertTrue(e.has(BlockProperties.FACE));
        assertSame(e, e.without(BlockProperties.POWERED), "없는 속성을 지우면 그대로");
    }

    @Test
    void propertyKeysCompareByNameAndType() {
        assertEquals(BlockProperty.bool("lit"), BlockProperties.LIT);
        assertNotEquals(BlockProperty.integer("lit"), BlockProperties.LIT);
        assertEquals("facing:Direction", BlockProperties.FACING.toString());
        assertEquals("south", BlockProperties.FACING.format(Direction.SOUTH));
    }

    @Test
    void directionHelpers() {
        assertEquals(Direction.WEST, Direction.EAST.opposite());
        assertEquals(Direction.DOWN, Direction.UP.opposite());
        assertTrue(Direction.NORTH.isHorizontal());
        assertFalse(Direction.UP.isHorizontal());
        assertEquals(-1, Direction.NORTH.dz());
        assertEquals(Direction.EAST, Direction.byId("EAST"));
        assertEquals("east", Direction.EAST.id());
    }

    @Test
    void blockTypePropertyInfoMatchesTypedKeys() {
        var facing = new BlockType.PropertyInfo("facing", BlockType.PropertyInfo.Kind.ENUM, java.util.List.of("north", "south"));
        assertTrue(facing.matches(BlockProperties.FACING));
        assertTrue(facing.accepts("north"));
        assertFalse(facing.accepts("up"));
        assertFalse(facing.matches(BlockProperties.WATERLOGGED));
        var age = new BlockType.PropertyInfo("age", BlockType.PropertyInfo.Kind.INTEGER, java.util.List.of("0", "1"));
        assertTrue(age.matches(BlockProperties.AGE));
        assertFalse(age.matches(BlockProperty.bool("age")));
    }

    @Test
    void blockSnapshotValidatesAndExposesType() {
        var s = new BlockSnapshot("minecraft:overworld", new BlockPos(1, 2, 3), BlockData.of("stone"));
        assertEquals("minecraft:stone", s.type());
        assertThrows(NullPointerException.class, () -> new BlockSnapshot(null, new BlockPos(0, 0, 0), BlockData.AIR));
    }
}
