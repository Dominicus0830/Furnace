package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class TransportOptionsTest {

    @Test
    void defaultOffAndParse() {
        assertFalse(TransportOptions.from(new Properties()).ioUring());
        Properties p = new Properties();
        p.setProperty("io-uring", "true");
        assertTrue(TransportOptions.from(p).ioUring());
        p.setProperty("io-uring", "maybe");
        assertThrows(IllegalArgumentException.class, () -> TransportOptions.from(p));
    }

    /** io-uring=true 를 요청해도 서버는 뜬다: 쓸 수 있으면 io_uring, 아니면 epoll/NIO 로 되돌아가고 접속을 받는다. */
    @Test
    void ioUringRequestedStartsAndAccepts() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, 2, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        NetworkServer server = new NetworkServer(0, -1, "Furnace Server", sched, world, new EntityIdAllocator());
        server.useIoUring(true);
        int port = server.start();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            assertTrue(s.isConnected());
            assertEquals(NetworkServer.ioUringAvailable() ? "io_uring" : (io.netty.channel.epoll.Epoll.isAvailable() ? "epoll" : "nio"),
                    server.transportName());
        } finally {
            server.stop();
            sched.close();
        }
    }
}
