// A04 회귀 시험(2차 점검 A04 재현에서 옮김): 실제 루프백 접속(진짜 NIO 이벤트 루프·region 스레드)에서 use_item_on 과 Ctrl+Q 를
// 한 번에 이어 보냈을 때(한 TCP 쓰기) 돌이 늘어나는 비율. region 은 한가한 상태(수동 시계, 작업만 바로 돈다) — Furnace 에 가장 유리한 조건.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class A04RealSocketLineTest {

    @Test
    void placeAndDropAllInOneWrite() throws Exception { run(0); }

    /** region 이 틱 중(여기선 5ms 바쁜 작업)일 때 — 바쁜 서버에서 패킷이 region 틱과 겹치는 흔한 경우. */
    @Test
    void placeAndDropAllWhileRegionBusy() throws Exception { run(5); }

    @Test
    void placeOnlyControl() throws Exception { run(-1); }

    private void run(int busyMs) throws Exception {
        try (var server = FurnaceTestServer.builder().regionThreads(2).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(3);
            tp.sync();
            var p = server.internals().playerByName("Dana");
            for (int w = 0; w < 100 && p.y() == 0.0; w++) { server.tick(1); tp.sync(); }
            server.tick(5);
            tp.sync();
            Method send = TestPlayer.class.getDeclaredMethod("send", int.class, Consumer.class);
            send.setAccessible(true);
            var world = server.internals().world();
            int stone = world.itemRegistry().idOf("minecraft:stone");
            int stoneState = world.blockRegistry().defaultStateId("minecraft:stone");
            int air = world.blockRegistry().defaultStateId("minecraft:air");
            int px = (int) Math.floor(p.x()), py = (int) Math.floor(p.y()), pz = (int) Math.floor(p.z());
            int gx = px + 2, gy = py - 1, gz = pz;
            int placedN = 0;
            int dupes = 0, tries = busyMs < 0 ? 5 : 60;
            System.out.println("[A04 pos] p=" + p.x() + "," + p.y() + "," + p.z() + " ground=" + gx + "," + gy + "," + gz);
            for (int i = 0; i < tries; i++) {
                server.await(world.submitAtChunk(p.chunkPos(), r -> {
                    r.level().setBlock(new Pos(gx, gy + 1, gz), air, 3);
                    var box = new AABB(p.x() - 16, p.y() - 16, p.z() - 16, p.x() + 16, p.y() + 16, p.z() + 16);
                    for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) e.discard();
                    return null;
                }));
                p.inventory().set(36, new ItemStack(stone, 64));
                int seq = 100 + i;
                Consumer<ByteBuf> place = b -> {
                    Types.writeVarInt(b, 0);
                    Types.writeBlockPos(b, gx, gy, gz);
                    Types.writeVarInt(b, 1);
                    b.writeFloat(0.5f); b.writeFloat(1.0f); b.writeFloat(0.5f);
                    b.writeBoolean(false);
                    Types.writeVarInt(b, seq);
                };
                Consumer<ByteBuf> dropAll = b -> { Types.writeVarInt(b, 3); Types.writeBlockPos(b, 0, 0, 0); b.writeByte(0); Types.writeVarInt(b, 0); };
                if (busyMs > 0) {
                    var started = new java.util.concurrent.CountDownLatch(1);
                    world.submitAtChunk(p.chunkPos(), reg -> {
                        started.countDown();
                        long end = System.nanoTime() + busyMs * 1_000_000L;
                        while (System.nanoTime() < end) Thread.onSpinWait();
                        return null;
                    });
                    started.await();
                }
                send.invoke(tp, 0x38, place);
                if (busyMs >= 0) send.invoke(tp, 0x24, dropAll);
                tp.sync();
                server.tick(2);
                tp.sync();
                int[] r = server.await(world.submitAtChunk(p.chunkPos(), reg -> {
                    int placed = reg.level().getBlockState(new Pos(gx, gy + 1, gz)) == stoneState ? 1 : 0;
                    int floor = 0;
                    var box = new AABB(p.x() - 16, p.y() - 16, p.z() - 16, p.x() + 16, p.y() + 16, p.z() + 16);
                    for (var e : reg.level().getEntities(null, box, x -> x instanceof ItemEntity))
                        if (!e.isRemoved() && ((ItemEntity) e).getItem().itemId() == stone) floor += ((ItemEntity) e).getItem().count();
                    return new int[] {placed, floor};
                }));
                int inv = p.inventory().get(36).itemId() == stone ? p.inventory().get(36).count() : 0;
                if (r[0] + r[1] + inv > 64) dupes++;
                placedN += r[0];
                if (i < 3 || r[0] + r[1] + inv != 64) System.out.println("i=" + i + " " + "[A04-01 socket busy=" + busyMs + "] placed=" + r[0] + " floor=" + r[1] + " inv=" + inv);
            }
            System.out.println("[A04-01 socket busy=" + busyMs + "] dupes=" + dupes + "/" + tries + " placed=" + placedN);
            assertEquals(0, dupes, "한 번에 보낸 설치+Ctrl+Q 에서 돌이 늘었다");
        }
    }
}
