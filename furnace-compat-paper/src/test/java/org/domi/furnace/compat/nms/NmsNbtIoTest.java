package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * NMS shim 5e-3: 이진 NBT({@code NbtIo}·{@code Tag.write}·{@code TagType.load}·{@code NbtAccounter}). 기대값은 Paper 1.21.1 jar 의 진짜 {@code NbtIo} 를
 * 같은 입력으로 돌린 결과다(바이트·한도 사용량·오류 문구). 바닐라 묶음 키 차례(fastutil 지도)는 Furnace 와 달라, 바닐라가 쓴 바이트는 읽어서 견준다.
 */
class NmsNbtIoTest {

    /** Paper 가 쓴 {in:{d:1.5d,sh:7s,f:2.5f,lo:9L,b:8b},e:[],ba:[B;1B,2B],a:1,l:[5,6],ia:[I;3],la:[L;4L],s:"héllo"} (142 바이트). */
    private static final byte[] PAPER = HexFormat.of().parseHex("0a00000a0002696e060001643ff80000000000000200027368000705000166402000000400026c6f00000000000000"
            + "09010001620800090001650000000000070002626100000002010203000161000000010900016c030000000200000005000000060b0002696100000001000000030c00026c61"
            + "00000001000000000000000408000173000668c3a96c6c6f00");

    private static CompoundTag sample() {
        CompoundTag c = new CompoundTag();
        c.putInt("a", 1);
        c.putString("s", "héllo");
        ListTag l = new ListTag();
        l.add(IntTag.valueOf(5));
        l.add(IntTag.valueOf(6));
        c.put("l", l);
        c.put("e", new ListTag());
        c.putByteArray("ba", new byte[] {1, 2});
        c.putIntArray("ia", new int[] {3});
        c.putLongArray("la", new long[] {4});
        CompoundTag in = new CompoundTag();
        in.putDouble("d", 1.5);
        in.putFloat("f", 2.5f);
        in.putShort("sh", (short) 7);
        in.putByte("b", (byte) 8);
        in.putLong("lo", 9L);
        c.put("in", in);
        return c;
    }

    private static CompoundTag read(byte[] data, NbtAccounter acc) throws IOException {
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(data)), acc);
    }

    private static byte[] write(CompoundTag c) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        NbtIo.write(c, new DataOutputStream(bo));
        return bo.toByteArray();
    }

    @Test
    void readsPaperBytesWithTheSameAccounting() throws IOException {
        assertEquals(142, PAPER.length);
        NbtAccounter acc = NbtAccounter.unlimitedHeap();
        CompoundTag back = read(PAPER, acc);
        assertEquals(sample(), back);
        assertEquals(1279, acc.getUsage(), "Paper 와 같은 사용량(= sizeInBytes)");
        assertEquals(1279, sample().sizeInBytes());
        assertEquals(0, acc.getDepth());
        assertEquals(sample(), read(write(sample()), NbtAccounter.unlimitedHeap()), "쓴 것을 다시 읽으면 같다");
        assertEquals(142, write(sample()).length, "키 차례만 다르고 길이는 같다");
    }

    @Test
    void singleKeyBytesAreVanilla() throws IOException {
        CompoundTag c = new CompoundTag();
        c.putInt("a", 1);
        assertEquals("0a000003000161000000010" + "0", HexFormat.of().formatHex(write(c)));
        ByteArrayOutputStream any = new ByteArrayOutputStream();
        NbtIo.writeAnyTag(IntTag.valueOf(3), new DataOutputStream(any));
        assertEquals("0300000003", HexFormat.of().formatHex(any.toByteArray()));
        ByteArrayOutputStream end = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(EndTag.INSTANCE, new DataOutputStream(end));
        assertEquals("00", HexFormat.of().formatHex(end.toByteArray()));
        Tag hi = NbtIo.readAnyTag(new DataInputStream(new ByteArrayInputStream(new byte[] {8, 0, 2, 'h', 'i'})), NbtAccounter.unlimitedHeap());
        assertEquals(StringTag.valueOf("hi"), hi);
    }

    @Test
    void accountingAndErrorsLikePaper() throws IOException {
        NbtAccounter quota = NbtAccounter.create(100);
        var e = assertThrows(NbtAccounterException.class, () -> read(PAPER, quota));
        assertEquals("Tried to read NBT tag that was too big; tried to allocate: 80 + 48 bytes where max allowed: 100", e.getMessage());
        assertEquals(0, quota.getDepth());

        NbtAccounter simple = NbtAccounter.unlimitedHeap();
        assertEquals(1, read(new byte[] {10, 0, 0, 3, 0, 1, 'a', 0, 0, 0, 1, 0}, simple).getInt("a"));
        assertEquals(126, simple.getUsage());
        NbtAccounter dup = NbtAccounter.unlimitedHeap();
        assertEquals(2, read(new byte[] {10, 0, 0, 3, 0, 1, 'a', 0, 0, 0, 1, 3, 0, 1, 'a', 0, 0, 0, 2, 0}, dup).getInt("a"), "같은 키는 뒤 값");
        assertEquals(168, dup.getUsage(), "같은 키의 둘째는 36 을 다시 세지 않는다");

        NbtAccounter rootInt = NbtAccounter.unlimitedHeap();
        assertEquals("Root tag must be a named compound tag", assertThrows(IOException.class, () -> read(new byte[] {3, 0, 0, 0, 0, 0, 1}, rootInt)).getMessage());
        assertEquals(12, rootInt.getUsage());
        assertEquals("Root tag must be a named compound tag", assertThrows(IOException.class, () -> read(new byte[] {0}, NbtAccounter.unlimitedHeap())).getMessage());

        NbtAccounter noType = NbtAccounter.unlimitedHeap();
        assertEquals("Missing type on ListTag", assertThrows(NbtFormatException.class,
                () -> read(new byte[] {10, 0, 0, 9, 0, 1, 'l', 0, 0, 0, 0, 1, 0}, noType)).getMessage());
        assertEquals(115, noType.getUsage());
        NbtAccounter neg = NbtAccounter.unlimitedHeap();
        assertEquals("initialArraySize cannot be negative but was: -1", assertThrows(IllegalArgumentException.class,
                () -> read(new byte[] {10, 0, 0, 9, 0, 1, 'l', 3, -1, -1, -1, -1, 0}, neg)).getMessage());
        assertEquals(111, neg.getUsage());
        NbtAccounter big = NbtAccounter.unlimitedHeap();
        assertNull(assertThrows(IllegalArgumentException.class, () -> read(new byte[] {10, 0, 0, 7, 0, 1, 'b', 1, 0, 0, 0, 0}, big)).getMessage());
        assertEquals(102, big.getUsage());

        ByteArrayOutputStream d = new ByteArrayOutputStream();
        DataOutputStream dd = new DataOutputStream(d);
        dd.writeByte(10);
        dd.writeUTF("");
        for (int i = 0; i < 600; i++) {
            dd.writeByte(10);
            dd.writeUTF("x");
        }
        for (int i = 0; i <= 600; i++) dd.writeByte(0);
        NbtAccounter deep = NbtAccounter.unlimitedHeap();
        assertEquals("Tried to read NBT tag with too high complexity, depth > 512", assertThrows(NbtAccounterException.class,
                () -> read(d.toByteArray(), deep)).getMessage());
        assertEquals(39936, deep.getUsage());
        assertEquals(0, deep.getDepth());
    }

    @Test
    void ioErrorsInsideACompoundAreReportedAsLoadingNbtData() {
        NbtAccounter badId = NbtAccounter.unlimitedHeap();
        var r = assertThrows(ReportedNbtException.class, () -> read(new byte[] {10, 0, 0, 99, 0, 1, 'x', 0}, badId));
        assertEquals("Loading NBT data", r.getMessage());
        assertEquals("Invalid tag id: 99", r.getCause().getMessage());
        assertEquals(78, badId.getUsage());
        var root = assertThrows(ReportedNbtException.class, () -> read(new byte[] {99, 0, 0}, NbtAccounter.unlimitedHeap()));
        assertEquals("Invalid tag id: 99", root.getCause().getMessage());
        NbtAccounter eof = NbtAccounter.unlimitedHeap();
        var cut = assertThrows(ReportedNbtException.class, () -> read(new byte[] {10, 0, 0, 3, 0, 1, 'a', 0, 0}, eof));
        assertInstanceOf(EOFException.class, cut.getCause());
        assertEquals(90, eof.getUsage());
    }

    @Test
    void gzipRoundTripAndTooLongStringFallsBackToEmpty() throws IOException {
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        NbtIo.writeCompressed(sample(), gz);
        assertEquals(sample(), NbtIo.readCompressed(new ByteArrayInputStream(gz.toByteArray()), NbtAccounter.unlimitedHeap()));
        CompoundTag h = new CompoundTag();
        h.putString("h", "x".repeat(70000));
        assertEquals("0a00000800016800000" + "0", HexFormat.of().formatHex(write(h)), "UTF 65535 바이트를 넘는 문자열은 빈 문자열로(바닐라 StringFallbackDataOutput)");
        assertSame(StringTag.class, read(write(h), NbtAccounter.unlimitedHeap()).get("h").getClass());
    }
}
