// API NBT 트리와 SNBT: 바닐라 TagParser·Tag.toString 규칙, CompoundTag 게터·merge 규칙, ListTag 종류 규칙.
package org.domi.furnace.api.nbt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NbtTest {

    @Test
    void snbtInfersTypesLikeVanilla() {
        NbtCompound c = NbtCompound.parse("{a:1b,b:2s,c:3,d:4L,e:1.5f,f:2.0,g:3d,h:true,i:false,j:hello,k:'q\"s',l:1e3,m:99999999999}");
        assertEquals((byte) 1, c.get("a"));
        assertEquals((short) 2, c.get("b"));
        assertEquals(3, c.get("c"));
        assertEquals(4L, c.get("d"));
        assertEquals(1.5f, c.get("e"));
        assertEquals(2.0, c.get("f"));
        assertEquals(3.0, c.get("g"));
        assertEquals((byte) 1, c.get("h"));
        assertEquals((byte) 0, c.get("i"));
        assertEquals("hello", c.get("j"));
        assertEquals("q\"s", c.get("k"));
        assertEquals("1e3", c.get("l"), "지수만 있는 수는 소수점이 없으면 문자열(바닐라 TagParser)");
        assertEquals("99999999999", c.get("m"), "int 범위를 넘는 접미사 없는 수는 문자열");
    }

    @Test
    void arraysListsAndNesting() {
        NbtCompound c = NbtCompound.parse("{ba:[B;1b,2b],ia:[I;1,2,3],la:[L;5L],list:[{x:1},{x:2}],empty:[],nested:{deep:{v:\"s\"}}}");
        assertArrayEquals(new byte[]{1, 2}, c.getByteArray("ba"));
        assertArrayEquals(new int[]{1, 2, 3}, c.getIntArray("ia"));
        assertArrayEquals(new long[]{5L}, c.getLongArray("la"));
        assertEquals(NbtType.COMPOUND, c.getList("list").elementType());
        assertEquals(2, c.getList("list").getCompound(1).getInt("x"));
        assertEquals(NbtType.END, c.getList("empty").elementType());
        assertEquals("s", c.getCompound("nested").getCompound("deep").getString("v"));
        var ex = assertThrows(SnbtParseException.class, () -> NbtCompound.parse("{l:[1,2b]}"));
        assertTrue(ex.position() > 0);
        assertThrows(SnbtParseException.class, () -> NbtCompound.parse("{a:1"));
        assertThrows(SnbtParseException.class, () -> NbtCompound.parse("[1,2]"), "묶음이 아니다");
        assertThrows(SnbtParseException.class, () -> Snbt.parse("{a:1} x"));
    }

    @Test
    void writeIsVanillaToStringAndRoundTrips() {
        NbtCompound c = new NbtCompound()
                .putByte("b", (byte) 1).putShort("s", (short) 2).putInt("i", 3).putLong("l", 4).putFloat("f", 1.5f).putDouble("d", 2.5)
                .putString("q", "it's \"x\"").putString("plain", "hi").putIntArray("ia", new int[]{1, 2}).putLongArray("la", new long[]{7})
                .putByteArray("ba", new byte[]{9}).putList("tags", NbtList.of("a", "b")).putString("weird key", "v");
        String s = c.toString();
        assertEquals("{b:1b,ba:[B;9B],d:2.5d,f:1.5f,i:3,ia:[I;1,2],l:4L,la:[L;7L],plain:\"hi\",q:\"it's \\\"x\\\"\",s:2s,tags:[\"a\",\"b\"],\"weird key\":\"v\"}", s);
        assertEquals(c, NbtCompound.parse(s));
        assertEquals("'say \"hi\"'", Snbt.write("say \"hi\""), "큰따옴표만 있으면 작은따옴표로 감싼다");
    }

    @Test
    void gettersFollowCompoundTagDefaultsAndConversions() {
        NbtCompound c = NbtCompound.parse("{f:2.7f,d:-1.5d,i:300,s:\"x\"}");
        assertEquals(2, c.getInt("f"));
        assertEquals(-2, c.getInt("d"), "floor");
        assertEquals((byte) 44, c.getByte("i"), "좁히기");
        assertEquals(0, c.getInt("s"), "형이 다르면 기본값");
        assertEquals(0, c.getInt("none"));
        assertEquals("", c.getString("none"));
        assertFalse(c.getBoolean("none"));
        assertTrue(c.contains("i", NbtType.DOUBLE), "숫자 종류는 서로 맞는다");
        assertFalse(c.contains("s", NbtType.INT));
        assertEquals(NbtType.STRING, c.type("s"));
        assertNull(c.type("none"));

        // getCompound 는 붙지 않은 빈 묶음, compound 는 만들어 붙인다
        c.getCompound("a").putInt("x", 1);
        assertFalse(c.contains("a"));
        c.compound("a").compound("b").putInt("x", 1);
        assertEquals(1, c.getCompound("a").getCompound("b").getInt("x"));
        c.list("tags").add("boss");
        assertEquals("boss", c.getList("tags").getString(0));
    }

    @Test
    void mergeIsDataMerge() {
        NbtCompound base = NbtCompound.parse("{a:1,inner:{x:1,y:2},list:[1,2,3]}");
        NbtCompound patch = NbtCompound.parse("{b:2,inner:{y:20,z:30},list:[9]}");
        base.merge(patch);
        assertEquals(NbtCompound.parse("{a:1,b:2,inner:{x:1,y:20,z:30},list:[9]}"), base);
        patch.getCompound("inner").putInt("y", 99);
        assertEquals(20, base.getCompound("inner").getInt("y"), "합친 값은 복사본이다");
    }

    @Test
    void valueValidationAndListTyping() {
        NbtCompound c = new NbtCompound();
        assertThrows(IllegalArgumentException.class, () -> c.put("x", new Object()));
        assertThrows(IllegalArgumentException.class, () -> c.put("x", null));
        assertThrows(IllegalArgumentException.class, () -> c.put("x", c), "자기 자신");
        assertThrows(IllegalArgumentException.class, () -> c.put("x", true), "참거짓은 putBoolean 으로");
        NbtList l = NbtList.of(1, 2);
        assertThrows(IllegalArgumentException.class, () -> l.add("s"));
        assertEquals(NbtType.INT, l.elementType());
        l.clear();
        l.add("s");
        assertEquals(NbtType.STRING, l.elementType(), "비면 종류가 다시 정해진다");
        assertEquals(List.of("s"), l.values());
        assertEquals("1", NbtList.of(1).getString(0), "문자열 리스트가 아니면 SNBT");
    }

    @Test
    void copyEqualsAndUuid() {
        NbtCompound c = NbtCompound.parse("{arr:[I;1,2],n:{v:1}}");
        NbtCompound d = c.copy();
        assertEquals(c, d);
        assertEquals(c.hashCode(), d.hashCode());
        d.getIntArray("arr")[0] = 5;
        d.getCompound("n").putInt("v", 2);
        assertEquals(1, c.getIntArray("arr")[0], "깊은 복사");
        assertEquals(1, c.getCompound("n").getInt("v"));
        assertNotEquals(c, d);

        UUID id = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
        NbtCompound u = new NbtCompound().putUuid("UUID", id);
        assertEquals(4, u.getIntArray("UUID").length);
        assertEquals(id, u.getUuid("UUID"));
        assertNull(u.getUuid("none"));
    }
}
