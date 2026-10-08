// Netty ServerBootstrap으로 프로토콜 파이프라인을 세워 접속을 수락하는 네트워크 서버
package org.domi.furnace.server.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.server.net.io.FrameDecoder;
import org.domi.furnace.server.net.io.FrameEncoder;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkServer {

    private static final Logger log = LoggerFactory.getLogger(NetworkServer.class);

    private final int port;
    private final int compressionThreshold;
    private final String serverName;
    private final FurnaceScheduler scheduler;
    private final World world;
    private final EntityIdAllocator idAllocator;

    private volatile int readTimeoutSeconds = 30;   // 바닐라 ReadTimeoutHandler(30)와 같다. 핸드셰이크 후 침묵하는 소켓을 정리
    private final io.netty.channel.group.ChannelGroup allChannels =
            new io.netty.channel.group.DefaultChannelGroup(io.netty.util.concurrent.GlobalEventExecutor.INSTANCE);

    /** 테스트용: start() 전에 읽기 타임아웃(초)을 바꾼다. */
    void readTimeoutSeconds(int seconds) { this.readTimeoutSeconds = seconds; }

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public NetworkServer(int port, int compressionThreshold, String serverName,
                         FurnaceScheduler scheduler, World world, EntityIdAllocator idAllocator) {
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
        this.scheduler = scheduler;
        this.world = world;
        this.idAllocator = idAllocator;
    }

    /** 서버 기동. 실제 바인딩된 포트 반환(port=0이면 OS 배정 포트). */
    public int start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        allChannels.add(ch);
                        ch.pipeline()
                                .addLast("readTimeout", new io.netty.handler.timeout.ReadTimeoutHandler(readTimeoutSeconds))
                                .addLast("frameDecoder", new FrameDecoder())
                                .addLast("frameEncoder", new FrameEncoder())
                                .addLast("handler",
                                        new ConnectionHandler(serverName, compressionThreshold,
                                                scheduler, world, idAllocator));
                    }
                });
        try {
            channel = b.bind(port).syncUninterruptibly().channel();
        } catch (Throwable e) {   // 체크 예외(BindException)도 sneaky throw로 오므로 RuntimeException만 잡으면 안 된다
            // bind 실패(포트 점유 등) 시 non-daemon 이벤트루프 스레드가 JVM을 붙잡지 않게 정리.
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            throw e;
        }
        int boundPort = ((InetSocketAddress) channel.localAddress()).getPort();
        log.info("네트워크 서버 listen: 포트 {} (압축 임계값 {})", boundPort, compressionThreshold);
        return boundPort;
    }

    public void stop() {
        if (channel != null) channel.close().syncUninterruptibly();
        // 접속 중인 연결에는 사유를 알리고 닫는다(예전엔 사유 없이 끊겨 클라가 "Connection lost"만 봤다).
        for (Channel c : allChannels) {
            ConnectionHandler h = c.pipeline().get(ConnectionHandler.class);
            if (h != null) h.kickForShutdown(); else c.close();
        }
        allChannels.newCloseFuture().awaitUninterruptibly(3000);
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        log.info("네트워크 서버 종료");
    }
}
