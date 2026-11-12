package org.domi.furnace.compat.nms;

import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 접속 하나의 가상 Netty 채널(ProtocolLib 다리 2b). Furnace 는 접속마다 Netty 파이프라인에 패킷 객체를 흘리지 않으므로(프레임 바이트 처리기뿐),
 * 패킷 라이브러리가 {@code Connection#channel} 을 보고 처리기·이벤트 루프 가로채기를 끼울 수 있도록 바닐라와 같은 모양의 채널을 따로 둔다.
 *
 * <ul>
 *   <li>활성·원격 주소는 접속 상태를 따라간다(접속이 끊기면 비활성). 채널 번호는 접속마다 다르다.</li>
 *   <li>이벤트 루프는 호출 스레드에서 바로 실행한다: {@code execute}·{@code submit}·{@code invokeAll} 은 그 자리에서 돌고, {@code schedule*} 는
 *       Netty 공용 예약기(데몬 스레드)가 시간이 되면 돌리며, {@code inEventLoop} 은 늘 true 다. 바닐라는 보낸 스레드에서 패킷 라이브러리가 먼저 가로채고
 *       나머지를 연결 스레드에 올리지만, Furnace 의 패킷 전달은 이미 스레드 안전·순서 보장이므로 줄 세우지 않는다.</li>
 *   <li><b>스레드 안전</b>: Netty 의 출력 버퍼(unsafe)는 이벤트 루프 한 스레드 전용인데 이 루프는 모든 스레드가 "이벤트 루프 안"이다. 그래서 쓰기는 파이프라인 맨 앞의
 *       끝 처리기({@link Connection} 이 단다)가 끝내고 출력 버퍼·unsafe 에는 닿지 않는다. 처리기들은 상태가 없거나 라이브러리가 스레드 안전을 맡는다.</li>
 * </ul>
 * 플러그인용이 아니다.
 */
public final class VirtualChannel extends EmbeddedChannel {

    private final BooleanSupplier online;
    private final Supplier<SocketAddress> remote;
    private final EventLoop inline;

    /**
     * @param online 접속이 아직 붙어 있는가
     * @param remote 원격 주소(모르면 null)
     */
    public VirtualChannel(BooleanSupplier online, Supplier<SocketAddress> remote) {
        super(DefaultChannelId.newInstance());
        this.online = online;
        this.remote = remote;
        EventLoop embedded = super.eventLoop();
        this.inline = (EventLoop) Proxy.newProxyInstance(EventLoop.class.getClassLoader(), new Class<?>[] {EventLoop.class}, new Inline(embedded));
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop e = inline;
        return e != null ? e : super.eventLoop();   // 상위 생성자가 등록하는 동안에는 아직 없다
    }

    @Override
    public boolean isActive() {
        if (!super.isActive()) return false;
        BooleanSupplier o = online;   // 상위 생성자가 등록하는 동안에는 아직 없다
        return o == null || o.getAsBoolean();
    }

    @Override
    protected SocketAddress remoteAddress0() { return isActive() ? remote.get() : null; }

    /** 끝 처리기를 지나쳐 출력 버퍼까지 온 것(있을 수 없다)은 버린다. */
    @Override
    protected void handleOutboundMessage(Object msg) { ReferenceCountUtil.release(msg); }

    @Override
    protected void handleInboundMessage(Object msg) { ReferenceCountUtil.release(msg); }

    /** 이 접속의 파이프라인을 닫는다(접속이 끊겼을 때). 라이브러리는 닫힘 알림으로 자기 처리기를 걷는다. */
    public void disconnected() {
        if (super.isOpen()) close();
    }

    /** 호출 스레드에서 바로 실행하는 이벤트 루프. 예약은 공용 예약기, 그 밖의 메서드는 내장 루프에 맡긴다. */
    private static final class Inline implements InvocationHandler {
        private final EventLoop delegate;

        Inline(EventLoop delegate) { this.delegate = delegate; }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) throws Throwable {
            String n = m.getName();
            Object target = delegate;
            switch (n) {
                case "execute":
                    if (args != null && args.length == 1) {
                        ((Runnable) args[0]).run();
                        return null;
                    }
                    break;
                case "inEventLoop":
                    return Boolean.TRUE;
                case "next":
                    return proxy;
                case "equals":
                    if (args != null && args.length == 1) return proxy == args[0];
                    break;
                case "hashCode":
                    if (args == null || args.length == 0) return System.identityHashCode(proxy);
                    break;
                case "toString":
                    if (args == null || args.length == 0) return "VirtualChannel.InlineEventLoop";
                    break;
                case "submit", "invokeAll", "invokeAny":
                    target = ImmediateEventExecutor.INSTANCE;   // 그 자리에서 돌고 끝난 future 를 준다
                    break;
                case "schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay":
                    target = GlobalEventExecutor.INSTANCE;      // 진짜 예약기
                    break;
                default:
                    break;
            }
            try {
                return m.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
