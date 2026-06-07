// NetworkServer가 설정 포트를 열어 TCP 연결을 수락하는지 검증
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class NetworkServerTest {

    @Test
    void server_acceptsTcpConnection() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched,
                new SuperflatWorldSource(reg, 0), 2, reg.directBits(), 7); // port 0 = OS가 빈 포트 배정
        int port = server.start();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            assertTrue(s.isConnected());
        } finally {
            server.stop();
            sched.close();
        }
    }
}
