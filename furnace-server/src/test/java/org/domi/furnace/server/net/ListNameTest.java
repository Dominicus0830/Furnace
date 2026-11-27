// 탭 목록 표시 이름(Paper setPlayerListName): 보고 있는 모두에게 UPDATE_DISPLAY_NAME 한 항목, 되돌리면 "없음" 칸, 나중에 들어온 사람의 탭 추가 항목에 이름이 실림.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListNameTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private NetworkServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, 2);
        core.world().startTicking();
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        org.domi.furnace.server.entity.view.EntityViewRules.clearForTest();
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player awaitListed(UUID id) throws InterruptedException {
        for (int i = 0; i < 1500; i++) {
            Player p = core.playerByUuid(id);
            if (p != null && !p.tabListingHeld() && p.ownerRegion != null) return p;
            Thread.sleep(10);
        }
        fail("접속 완료: " + id);
        return null;
    }

    /** 표시 이름 갱신(액션 0x20 하나뿐인 패킷)의 (UUID, 이름 바이트 — 없으면 null). */
    private record NameUpdate(UUID uuid, byte[] nbt) {}

    private static NameUpdate nameUpdate(ByteBuf f) {
        int id = Types.readVarInt(f);
        if (id != PlayerInfoUpdate.ID) return null;
        if (f.readUnsignedByte() != 0x20) return null;
        Types.readVarInt(f);
        UUID u = Types.readUuid(f);
        byte[] nbt = null;
        if (f.readBoolean()) { nbt = new byte[f.readableBytes()]; f.readBytes(nbt); }
        return new NameUpdate(u, nbt);
    }

    private static List<NameUpdate> readUpdates(TestClient c, Predicate<List<NameUpdate>> done, long millis) throws Exception {
        List<NameUpdate> out = new ArrayList<>();
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < deadline && !done.test(out)) {
            ByteBuf f = c.poll(50);
            if (f == null) continue;
            NameUpdate n = nameUpdate(f);
            if (n != null) out.add(n);
        }
        return out;
    }

    @Test
    void everyViewerGetsOneUpdateAndResetSendsNone() throws Exception {
        try (TestClient bob = TestClient.join(port, "Bob"); TestClient alex = TestClient.join(port, "Alex")) {
            awaitListed(bob.uuid);
            Player alexP = awaitListed(alex.uuid);
            readUpdates(bob, u -> false, 300);
            readUpdates(alex, u -> false, 300);
            Text name = Text.text("[VIP] Alex");
            core.plugins().player(alexP).listName(name);
            for (TestClient c : List.of(bob, alex)) {   // 본인 포함 모두
                List<NameUpdate> got = readUpdates(c, u -> !u.isEmpty(), 5_000);
                assertEquals(1, got.size());
                assertEquals(alex.uuid, got.get(0).uuid());
                assertArrayEquals(name.networkNbt(), got.get(0).nbt());
            }
            core.plugins().player(alexP).listName(name);   // 같은 값도 다시 보낸다(Paper 는 부를 때마다 브로드캐스트)
            assertEquals(1, readUpdates(bob, u -> !u.isEmpty(), 5_000).size());
            core.plugins().player(alexP).listName(null);   // 되돌림: "없음" 칸
            List<NameUpdate> back = readUpdates(bob, u -> !u.isEmpty(), 5_000);
            assertEquals(1, back.size());
            assertNull(back.get(0).nbt());
            assertNull(core.plugins().player(alexP).listName());
        }
    }

    @Test
    void aLateJoinerGetsTheNameInTheAddEntry() throws Exception {
        try (TestClient alex = TestClient.join(port, "Alex")) {
            Player alexP = awaitListed(alex.uuid);
            Text name = Text.text("Boss");
            core.plugins().player(alexP).listName(name);
            assertEquals(name, core.plugins().player(alexP).listName());
            try (TestClient bob = TestClient.join(port, "Bob")) {
                awaitListed(bob.uuid);
                // 접속 탭 묶음에 alex 항목의 이름 칸이 실려 있다(add_player 항목 끝의 update_display_name)
                boolean found = false;
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (!found && System.nanoTime() < deadline) {
                    ByteBuf f = bob.poll(50);
                    if (f == null) continue;
                    byte[] all = new byte[f.readableBytes()];
                    f.getBytes(f.readerIndex(), all);
                    if (Types.readVarInt(f) != PlayerInfoUpdate.ID) continue;
                    if (indexOf(all, name.networkNbt()) >= 0) found = true;
                }
                assertTrue(found, "접속 탭 항목에 표시 이름 NBT");
            }
        }
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
