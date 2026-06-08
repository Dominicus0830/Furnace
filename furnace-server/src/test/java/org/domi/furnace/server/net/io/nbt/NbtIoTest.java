// 전 태그종 NBT write→read 라운드트립 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class NbtIoTest {

    @Test
    void roundTrip_allTagTypes() throws IOException {
        NbtCompound root = new NbtCompound();
        root.putByte("b", (byte) 7);
        root.put("s", (short) 300);
        root.putInt("i", -123456);
        root.putLong("l", 9_000_000_000L);
        root.put("f", 1.5f);
        root.put("d", 2.5d);
        root.putString("str", "hello world");
        root.put("ba", new byte[]{1, 2, 3});
        root.put("ia", new int[]{4, 5});
        root.putLongArray("la", new long[]{6, 7});
        NbtList list = new NbtList(NbtIo.TAG_STRING);
        list.add("x"); list.add("y");
        root.putList("list", list);
        NbtCompound nested = new NbtCompound();
        nested.putInt("n", 42);
        root.putCompound("nested", nested);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), root, "Level");
        NbtCompound back = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));

        assertEquals((byte) 7, back.getByte("b"));
        assertEquals(-123456, back.getInt("i"));
        assertEquals(9_000_000_000L, back.getLong("l"));
        assertEquals("hello world", back.getString("str"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) back.tags().get("ba"));
        assertArrayEquals(new long[]{6, 7}, back.getLongArray("la"));
        assertEquals(2, back.getList("list").size());
        assertEquals("y", back.getList("list").get(1));
        assertEquals(42, back.getCompound("nested").getInt("n"));
    }

    @Test
    void gzip_roundTrip() throws IOException {
        NbtCompound root = new NbtCompound();
        root.putInt("x", 99);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.writeGzip(bos, root, "");
        NbtCompound back = NbtIo.readGzip(new ByteArrayInputStream(bos.toByteArray()));
        assertEquals(99, back.getInt("x"));
    }
}
