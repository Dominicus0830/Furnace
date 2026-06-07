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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkServer {

    private static final Logger log = LoggerFactory.getLogger(NetworkServer.class);

    private final int port;
    private final int compressionThreshold;
    private final String serverName;
    private final FurnaceScheduler scheduler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public NetworkServer(int port, int compressionThreshold, String serverName, FurnaceScheduler scheduler) {
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
        this.scheduler = scheduler;
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
                        ch.pipeline()
                                .addLast("frameDecoder", new FrameDecoder())
                                .addLast("frameEncoder", new FrameEncoder())
                                .addLast("handler",
                                        new ConnectionHandler(serverName, compressionThreshold, scheduler));
                    }
                });
        try {
            channel = b.bind(port).syncUninterruptibly().channel();
        } catch (RuntimeException e) {
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
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        log.info("네트워크 서버 종료");
    }
}
