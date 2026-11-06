// 실제 소켓: op 가 보낸 entity_tag_query(F3+I)에 tag_query 로 답하는지. 플레이어 NBT 의 UUID 는 int 배열이라
// /data 경로 연산용 꼴(NbtArray)로 보내면 NbtWriter 가 예외를 내고 응답이 사라진다(2026-11-03 봇 시험에서 발견).
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TagQueryFlowTest {

    @TempDir Path tmp;

    @Test
    void opEntityTagQueryAnswersWithPlayerNbt() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        try {
            ServerCore core = TestServers.core(tmp, sched);
            core.world().startTicking();
            server = new NetworkServer(0, -1, ServerLoginContext.offline("t").core(core), sched, core.world(), new EntityIdAllocator());
            int port = server.start();
            try (TestClient c = TestClient.join(port, "Admin")) {
                for (int i = 0; i < 500 && core.playerByName("Admin") == null; i++) Thread.sleep(20);
                Player p = core.playerByName("Admin");
                assertNotNull(p, "접속 처리 완료");
                core.lists().op(GameProfile.offline("Admin"), 4);
                for (int i = 0; i < 500 && core.profilePermissions(ServerCore.profileOf(p)) < 2; i++) Thread.sleep(20);
                assertTrue(core.profilePermissions(ServerCore.profileOf(p)) >= 2, "op 적용");
                for (int i = 0; i < 20; i++) {   // 소유 region 에 자리 잡기 전 질의는 버려진다 — 몇 번 보낸다(응답은 처리 번호로 고른다)
                    c.write(0x15, b -> { Types.writeVarInt(b, 7); Types.writeVarInt(b, p.entityId()); });   // entity_tag_query
                    Thread.sleep(100);
                }
                ByteBuf r = c.await(0x6E, b -> Types.readVarInt(b) == 7);
                Types.readVarInt(r);
                assertEquals(0x0A, r.readByte(), "null 이 아닌 compound 로 답한다");
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }
}
