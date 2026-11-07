// Netty ServerBootstrap으로 프로토콜 파이프라인을 세워 접속을 수락하는 네트워크 서버
package org.domi.furnace.server.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
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

    private volatile int readTimeoutSeconds = -1;   // 기본은 ConnectionLimits(바닐라 ReadTimeoutHandler(30)와 같다). 테스트가 덮어쓴다
    private final io.netty.channel.group.ChannelGroup allChannels =
            new io.netty.channel.group.DefaultChannelGroup(io.netty.util.concurrent.GlobalEventExecutor.INSTANCE);

    private volatile boolean useNativeTransport = true;   // server.properties use-native-transport(바닐라 기본 true)

    /** start() 전에: epoll 을 쓸 수 있으면 쓸지(바닐라 ServerConnectionListener.startTcpServerListener 와 같은 선택). */
    public void useNativeTransport(boolean v) { this.useNativeTransport = v; }

    private volatile boolean useIoUring = false;   // furnace.properties io-uring (기본 꺼짐)

    /** start() 전에: io_uring 을 쓸 수 있으면 쓸지(기본 꺼짐). 쓸 수 없으면 경고하고 epoll/NIO 로 되돌아간다. */
    public void useIoUring(boolean v) { this.useIoUring = v; }

    /** 마지막 start() 가 고른 전송 방식("io_uring"·"epoll"·"nio"). */
    public String transportName() { return transportName; }

    private volatile String transportName = "nio";

    /** 테스트용: start() 전에 읽기 타임아웃(초)을 바꾼다. */
    void readTimeoutSeconds(int seconds) { this.readTimeoutSeconds = seconds; }

    private final java.util.concurrent.atomic.AtomicBoolean proxyWarned = new java.util.concurrent.atomic.AtomicBoolean();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final ServerLoginContext loginContext;
    private Channel channel;

    public NetworkServer(int port, int compressionThreshold, String serverName,
                         FurnaceScheduler scheduler, World world, EntityIdAllocator idAllocator) {
        this(port, compressionThreshold, ServerLoginContext.offline(serverName), scheduler, world, idAllocator);
    }

    public NetworkServer(int port, int compressionThreshold, ServerLoginContext loginContext,
                         FurnaceScheduler scheduler, World world, EntityIdAllocator idAllocator) {
        this.loginContext = loginContext;
        String serverName = loginContext.motd;
        this.port = port;
        this.compressionThreshold = compressionThreshold;
        this.serverName = serverName;
        this.scheduler = scheduler;
        this.world = world;
        this.idAllocator = idAllocator;
    }

    /** 이 주소에만 묶는다(null = 모든 주소, 기본). 시험 서버는 127.0.0.1 — start 전에 부른다. */
    private String bindHost;

    public NetworkServer bindHost(String host) {
        this.bindHost = host;
        return this;
    }

    /** 이 환경에서 io_uring 을 쓸 수 있는지(네이티브 로드 실패·커널 미지원이면 false). */
    public static boolean ioUringAvailable() {
        try {
            return io.netty.incubator.channel.uring.IOUring.isAvailable();
        } catch (Throwable t) {   // 네이티브 클래스가 아예 없을 때(NoClassDefFoundError 등)
            return false;
        }
    }

    static String ioUringUnavailableReason() {
        try {
            Throwable c = io.netty.incubator.channel.uring.IOUring.unavailabilityCause();
            return c == null ? "알 수 없음" : String.valueOf(c.getMessage() != null ? c.getMessage() : c);
        } catch (Throwable t) {
            return t.toString();
        }
    }

    /** 서버 기동. 실제 바인딩된 포트 반환(port=0이면 OS 배정 포트). */
    public int start() {
        // io-uring=true 면 io_uring 이 우선. 못 쓰면 바닐라와 같은 선택: Epoll.isAvailable() && use-native-transport 면 epoll, 아니면 NIO
        boolean uring = false;
        if (useIoUring) {
            uring = ioUringAvailable();
            if (!uring) log.warn("io-uring=true 이지만 이 환경에서 io_uring 을 쓸 수 없습니다({}) — epoll/NIO 로 되돌아갑니다", ioUringUnavailableReason());
        }
        boolean epoll = !uring && useNativeTransport && io.netty.channel.epoll.Epoll.isAvailable();
        Class<? extends io.netty.channel.ServerChannel> channelType;
        if (uring) {
            bossGroup = new io.netty.incubator.channel.uring.IOUringEventLoopGroup(1);
            workerGroup = new io.netty.incubator.channel.uring.IOUringEventLoopGroup();
            channelType = io.netty.incubator.channel.uring.IOUringServerSocketChannel.class;
            transportName = "io_uring";
            log.info("Using io_uring channel type");
        } else if (epoll) {
            bossGroup = new io.netty.channel.epoll.EpollEventLoopGroup(1);
            workerGroup = new io.netty.channel.epoll.EpollEventLoopGroup();
            channelType = io.netty.channel.epoll.EpollServerSocketChannel.class;
            transportName = "epoll";
            log.info("Using epoll channel type");
        } else {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            channelType = NioServerSocketChannel.class;
            transportName = "nio";
            log.info("Using default channel type");
        }
        if (loginContext.limits().proxyProtocol()) {
            log.warn("PROXY 프로토콜이 켜져 있습니다(proxy-protocol=true): 모든 접속이 PROXY 헤더를 보내야 하며, 앞단 밖에서 직접 접속할 수 있으면 주소를 속일 수 있습니다.{}",
                    loginContext.limits().proxyTrusted().isEmpty() ? " 'proxy-protocol.trusted' 로 앞단 주소를 제한하세요." : "");
        }
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(channelType)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new io.netty.channel.WriteBufferWaterMark(512 * 1024, 2 * 1024 * 1024))
                .childHandler(new ChannelInitializer<io.netty.channel.Channel>() {
                    @Override
                    protected void initChannel(io.netty.channel.Channel ch) {
                        allChannels.add(ch);
                        ConnectionLimits limits = loginContext.limits();
                        if (limits.proxyProtocol()) {
                            // PROXY 헤더는 연결의 첫 바이트들 — 다른 어떤 핸들러보다 앞에서 읽는다.
                            ch.pipeline().addLast("haproxySniffer", new ProxyProtocolHandler.Sniffer(proxyWarned))
                                    .addLast("haproxyDecoder", new io.netty.handler.codec.haproxy.HAProxyMessageDecoder())
                                    .addLast("haproxyHandler", new ProxyProtocolHandler(limits, proxyWarned));
                        }
                        ch.pipeline()
                                .addLast("readTimeout", new io.netty.handler.timeout.ReadTimeoutHandler(
                                        readTimeoutSeconds > 0 ? readTimeoutSeconds : limits.readTimeoutSeconds()))
                                .addLast("writabilityGuard", new WritabilityGuard())
                                .addLast("frameDecoder", new FrameDecoder())
                                .addLast("frameEncoder", new FrameEncoder())
                                .addLast("handler",
                                        new ConnectionHandler(loginContext, compressionThreshold,
                                                scheduler, world, idAllocator));
                    }
                });
        try {
            channel = (bindHost != null ? b.bind(bindHost, port) : b.bind(port)).syncUninterruptibly().channel();
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
