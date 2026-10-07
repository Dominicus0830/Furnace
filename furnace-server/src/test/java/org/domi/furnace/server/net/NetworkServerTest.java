// NetworkServer가 설정 포트를 열어 TCP 연결을 수락하는지 검증
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class NetworkServerTest {

    @Test
    void server_acceptsTcpConnection() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, 2, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched,
                world, new EntityIdAllocator()); // port 0 = OS가 빈 포트 배정
        int port = server.start();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            assertTrue(s.isConnected());
        } finally {
            server.stop();
            sched.close();
        }
    }

    /** 포트가 점유되어 bind가 실패해도 Netty 이벤트 루프 스레드가 남아 JVM을 붙잡지 않아야 한다(체크 예외 BindException도 잡혀야). */
    @Test
    void bindFailure_shutsDownEventLoops() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, 2, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        try (java.net.ServerSocket occupied = new java.net.ServerSocket(0)) {
            NetworkServer server = new NetworkServer(occupied.getLocalPort(), -1, "Furnace Server", sched,
                    world, new EntityIdAllocator());
            Throwable thrown = assertThrows(Throwable.class, server::start);
            assertTrue(thrown instanceof java.net.BindException, "BindException이 그대로 전달돼야: " + thrown);
            long deadline = System.nanoTime() + 5_000_000_000L;
            long alive;
            do {
                alive = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getName().startsWith("nioEventLoopGroup") && t.isAlive()).count();
                if (alive == 0) break;
                Thread.sleep(50);
            } while (System.nanoTime() < deadline);
            assertEquals(0, alive, "bind 실패 후 이벤트 루프 스레드가 남아 있다");
        } finally {
            sched.close();
        }
    }
}
