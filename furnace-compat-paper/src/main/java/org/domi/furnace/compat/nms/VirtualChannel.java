package org.domi.furnace.compat.nms;

import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
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
 *   <li>활성·원격 주소는 접속 상태를 따라간다(접속이 끊기면 비활성) — 라이브러리의 "연결되어 있는가" 판단과 같다.</li>
 *   <li>이벤트 루프는 호출 스레드에서 바로 실행한다({@code execute} = 그 자리에서 run, {@code inEventLoop} = true). 바닐라는 보낸 스레드에서
 *       패킷 라이브러리가 먼저 가로채고 나머지를 연결 스레드에 올리지만, Furnace 의 패킷 전달은 이미 스레드 안전·순서 보장이므로 줄 세우지 않는다.</li>
 *   <li>파이프라인 끝으로 흘러나온 쓰기·읽기는 아무 데도 가지 않는다 — 바이트 와이어 패킷({@code sendWirePacket})은 아직 지원하지 않는다(버리고 한 번 경고).</li>
 * </ul>
 * 플러그인용이 아니다.
 */
public final class VirtualChannel extends EmbeddedChannel {

    private final BooleanSupplier online;
    private final Supplier<SocketAddress> remote;
    private final EventLoop inline;
    private final Runnable onDropped;

    /**
     * @param online 접속이 아직 붙어 있는가
     * @param remote 원격 주소(모르면 null)
     * @param onDropped 파이프라인 끝으로 흘러나온 메시지를 버릴 때(한 번만 부르는 것은 호출한 쪽 몫)
     */
    public VirtualChannel(BooleanSupplier online, Supplier<SocketAddress> remote, Runnable onDropped) {
        super();
        this.online = online;
        this.remote = remote;
        this.onDropped = onDropped;
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

    @Override
    protected void handleOutboundMessage(Object msg) {
        ReferenceCountUtil.release(msg);
        onDropped.run();
    }

    @Override
    protected void handleInboundMessage(Object msg) {
        ReferenceCountUtil.release(msg);
    }

    /** 이 접속의 파이프라인을 닫는다(접속이 끊겼을 때). 라이브러리는 닫힘 알림으로 자기 처리기를 걷는다. */
    public void disconnected() {
        if (super.isOpen()) close();
    }

    /** 호출 스레드에서 바로 실행하는 이벤트 루프. 그 밖의 메서드는 내장 루프에 맡긴다. */
    private static final class Inline implements InvocationHandler {
        private final EventLoop delegate;

        Inline(EventLoop delegate) { this.delegate = delegate; }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) throws Throwable {
            switch (m.getName()) {
                case "execute":
                    if (args != null && args.length == 1) {
                        ((Runnable) args[0]).run();
                        return null;
                    }
                    break;
                case "inEventLoop":
                    return Boolean.TRUE;
                case "equals":
                    if (args != null && args.length == 1) return proxy == args[0];
                    break;
                case "hashCode":
                    if (args == null || args.length == 0) return System.identityHashCode(proxy);
                    break;
                case "toString":
                    if (args == null || args.length == 0) return "VirtualChannel.InlineEventLoop";
                    break;
                default:
                    break;
            }
            try {
                return m.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
