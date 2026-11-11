// 네트워크 NBT 할당 한도(바닐라 NbtAccounter 2 MiB)가 길이가 음수인 리스트·배열로 풀리지 않는지(A02-01 회귀 시험).
// 바닐라 ListTag.load 는 Lists.newArrayListWithCapacity(음수), 배열은 new T[음수] 에서 할당 없이 실패한다 — 같은 바이트에서 디코드 실패여야 한다.
package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NbtNetworkQuotaTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    private static void utf(ByteBuf b, String s) {
        byte[] x = s.getBytes(StandardCharsets.UTF_8);
        b.writeShort(x.length);
        b.writeBytes(x);
    }

    /** 루트 compound { a: [](길이 Integer.MIN_VALUE), b: 배열(claim) } — 본문 없이 30바이트 남짓. */
    private static ByteBuf payload(boolean negativeList, byte arrayType, int claim) {
        ByteBuf b = Unpooled.buffer();
        b.writeByte(NbtIo.TAG_COMPOUND);
        if (negativeList) {
            b.writeByte(NbtIo.TAG_LIST); utf(b, "a");
            b.writeByte(NbtIo.TAG_END);
            b.writeInt(Integer.MIN_VALUE);   // 예전: account(q, 4L*n) = -8 GiB → 한도 +8 GiB
        }
        b.writeByte(arrayType); utf(b, "b");
        b.writeInt(claim);                    // 주장하는 원소 수 — 본문은 보내지 않는다
        b.writeByte(NbtIo.TAG_END);
        return b;
    }

    private static void assertRejectedWithoutAllocation(ByteBuf p) {
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        long a0 = mx.getThreadAllocatedBytes(tid);
        CodecException e = assertThrows(CodecException.class, () -> NbtVals.readNetwork(p));
        long mib = (mx.getThreadAllocatedBytes(tid) - a0) >> 20;
        assertTrue(mib < 4, "거절 전에 " + mib + " MiB 를 할당했다: " + e.getMessage());
    }

    @Test
    void quotaRejectsHugeArrayBeforeAllocation() {
        CodecException e = assertThrows(CodecException.class, () -> NbtVals.readNetwork(payload(false, NbtIo.TAG_INT_ARRAY, 1_000_000)));
        assertTrue(e.getMessage().contains("too big"), e.getMessage());
    }

    @Test
    void negativeListLengthIsRejected() {
        CodecException e = assertThrows(CodecException.class, () -> NbtVals.readNetwork(payload(true, NbtIo.TAG_INT_ARRAY, 1_000_000)));
        assertTrue(e.getMessage().contains("negative length"), e.getMessage());
    }

    @Test
    void negativeListThenGigabyteLongArrayDoesNotAllocate() {
        assertRejectedWithoutAllocation(payload(true, NbtIo.TAG_LONG_ARRAY, 134_217_728));   // long[134M] = 1 GiB 주장
    }

    @Test
    void negativeListWithElementTypeIsRejected() {
        ByteBuf b = Unpooled.buffer();
        b.writeByte(NbtIo.TAG_LIST);
        b.writeByte(NbtIo.TAG_INT);
        b.writeInt(-1);
        assertThrows(CodecException.class, () -> NbtVals.readNetwork(b));
    }

    @Test
    void negativeArrayLengthsAreRejected() {
        for (byte t : new byte[] {NbtIo.TAG_BYTE_ARRAY, NbtIo.TAG_INT_ARRAY, NbtIo.TAG_LONG_ARRAY}) {
            ByteBuf b = Unpooled.buffer();
            b.writeByte(t);
            b.writeInt(-5);
            CodecException e = assertThrows(CodecException.class, () -> NbtVals.readNetwork(b), "type " + t);
            assertTrue(e.getMessage().contains("negative length"), e.getMessage());
        }
    }

    @Test
    void emptyListsAndArraysStillDecode() {
        NbtCompound c = new NbtCompound();
        c.tags().put("l", new NbtList(NbtIo.TAG_END));
        c.tags().put("i", new int[0]);
        c.tags().put("x", new long[] {1, 2});
        ByteBuf b = Unpooled.buffer();
        NbtVals.writeNetwork(b, c);
        NbtCompound back = (NbtCompound) NbtVals.readNetwork(b);
        assertEquals(0, ((NbtList) back.tags().get("l")).size());
        assertEquals(0, ((int[]) back.tags().get("i")).length);
        assertArrayEquals(new long[] {1, 2}, (long[]) back.tags().get("x"));
    }

    @Test
    void rejectedFromClientItemSlot() {
        // 컨테이너 클릭(0x0E)·창작 칸(0x32)의 readSlot → ItemStackCodecs.readStack → custom_data(PCodec.COMPOUND) → NbtVals.readNetwork
        int stone = ItemContext.id("minecraft:item", "minecraft:stone");
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, 1);
        Types.writeVarInt(b, stone);
        Types.writeVarInt(b, 1);
        Types.writeVarInt(b, 0);
        Types.writeVarInt(b, DataComponents.networkId(DataComponents.CUSTOM_DATA));
        b.writeBytes(payload(true, NbtIo.TAG_INT_ARRAY, 1_000_000));
        Throwable t = assertThrows(Throwable.class, () -> ItemStackCodecs.readStack(b));
        assertInstanceOf(CodecException.class, t, "아이템 칸 디코드에서 한도가 풀렸다: " + t);
    }

    @Test
    void concurrentClaimsDoNotExhaustHeap() throws Exception {
        int threads = 4;
        java.util.concurrent.CyclicBarrier go = new java.util.concurrent.CyclicBarrier(threads);
        java.util.List<String> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.List<Thread> ts = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    NbtVals.readNetwork(payload(true, NbtIo.TAG_LONG_ARRAY, 134_217_728));
                    results.add("ok");
                } catch (Throwable e) {
                    results.add(e.getClass().getSimpleName());
                }
            });
            ts.add(t);
            t.start();
        }
        for (Thread t : ts) t.join();
        assertEquals(java.util.List.of("CodecException", "CodecException", "CodecException", "CodecException"), results);
    }
}
