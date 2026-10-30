// NbtCompound 저장/타입게터 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NbtCompoundTest {

    @Test
    void putAndGet() {
        NbtCompound c = new NbtCompound();
        c.putInt("x", 5);
        c.putString("name", "stone");
        c.putLongArray("data", new long[]{1, 2});
        assertEquals(5, c.getInt("x"));
        assertEquals("stone", c.getString("name"));
        assertArrayEquals(new long[]{1, 2}, c.getLongArray("data"));
        assertTrue(c.contains("x"));
        assertFalse(c.contains("absent"));
    }

    @Test
    void list_holdsTypedItems() {
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        l.add("a");
        l.add("b");
        assertEquals(2, l.size());
        assertEquals("a", l.get(0));
        assertEquals(NbtIo.TAG_STRING, l.elementType());
    }

    @Test
    void missingOrMismatchedKeysGiveVanillaDefaults() {
        NbtCompound c = new NbtCompound();
        c.putString("s", "text");
        c.putInt("i", 5);
        for (String k : new String[] {"absent", "s"}) {
            assertEquals(0, c.getInt(k));
            assertEquals(0L, c.getLong(k));
            assertEquals((short) 0, c.getShort(k));
            assertEquals((byte) 0, c.getByte(k));
            assertEquals(0f, c.getFloat(k));
            assertEquals(0.0, c.getDouble(k));
            assertFalse(c.getBoolean(k));
        }
        assertEquals("", c.getString("absent"));
        assertEquals("", c.getString("i"));
        assertEquals(0, c.getCompound("i").tags().size());
        assertEquals(0, c.getList("i").size());
        assertEquals(0, c.getList("s", NbtIo.TAG_COMPOUND).size());
        assertEquals(0, c.getByteArray("i").length);
        assertEquals(0, c.getIntArray("i").length);
        assertEquals(0, c.getLongArray("i").length);
    }

    @Test
    void numericConversionFollowsVanillaTags() {
        NbtCompound c = new NbtCompound();
        c.putFloat("f", -0.5f);
        c.putDouble("d", -0.5);
        c.putLong("l", 0x1_0000_0005L);
        assertEquals(0L, c.getLong("f"), "FloatTag.getAsLong 은 자르기");
        assertEquals(-1L, c.getLong("d"), "DoubleTag.getAsLong 은 floor");
        assertEquals(-1, c.getInt("f"), "FloatTag.getAsInt 는 floor");
        assertEquals(-1, c.getInt("d"));
        assertEquals((byte) 0xFF, c.getByte("f"));
        assertEquals((short) 0xFFFF, c.getShort("d"));
        assertEquals(5, c.getInt("l"), "LongTag.getAsInt 는 좁히기");
        assertTrue(c.getBoolean("l"), "바닐라 getBoolean 은 getByte != 0 (하위 8비트가 5)");
    }
}
