// 패킷 API 구현. 등록은 잠금 아래 목록에 쌓고, 바뀔 때마다 방향별 "id → 리스너 배열" 표를 새로 구워 volatile 로 바꿔 끼운다.
// 패킷 경로(이벤트 루프)는 표 한 칸만 읽는다 — 구독자 없는 id 는 null 이라 디코드·할당이 없다.
package org.domi.furnace.server.plugin.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.OpaquePacket;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketEvent;
import org.domi.furnace.api.packet.PacketListener;
import org.domi.furnace.api.packet.PacketService;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.net.ConnectionHandler;
import org.domi.furnace.server.net.PacketTap;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.RawPacket;
import org.domi.furnace.server.plugin.PlayerHandle;
import org.domi.furnace.server.plugin.PluginHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PacketServiceImpl implements PacketService {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    private record Reg(FurnacePlugin plugin, PacketType<?> type, EventPriority priority, PacketListener<Packet> fn, long order, Object token) {}

    private final PluginHost host;
    private final ProtocolCodec codec;
    private final Object lock = new Object();
    private final List<Reg> regs = new ArrayList<>();   // lock 아래
    private final AtomicLong order = new AtomicLong();
    private volatile Reg[][] outbound = new Reg[0][];
    private volatile Reg[][] inbound = new Reg[0][];

    public PacketServiceImpl(PluginHost host, ProtocolCodec codec) {
        this.host = host;
        this.codec = codec;
    }

    public ProtocolCodec codec() { return codec; }

    // ------------------------------------------------------------------ 등록

    @Override
    @SuppressWarnings("unchecked")
    public <P extends Packet> EventSubscription listen(FurnacePlugin owner, PacketType<P> type, EventPriority priority, PacketListener<P> listener) {
        Objects.requireNonNull(type, "type");
        return add(owner, priority, (PacketListener<Packet>) (PacketListener<?>) listener, List.of(type));
    }

    @Override
    public EventSubscription listen(FurnacePlugin owner, EventPriority priority, PacketListener<Packet> listener, PacketType<?>... types) {
        if (types.length == 0) throw new IllegalArgumentException("종류가 하나는 있어야 한다");
        for (PacketType<?> t : types) Objects.requireNonNull(t, "type");
        return add(owner, priority, listener, List.of(types));
    }

    private EventSubscription add(FurnacePlugin owner, EventPriority priority, PacketListener<Packet> fn, List<PacketType<?>> types) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(fn, "listener");
        Object token = new Object();
        synchronized (lock) {
            for (PacketType<?> t : types) {
                if (!isSupported(t)) log.debug("[{}] 이 서버 버전에 없는 패킷을 구독: {}", name(owner), t);
                regs.add(new Reg(owner, t, priority, fn, order.getAndIncrement(), token));
            }
            rebuild();
        }
        return () -> {
            synchronized (lock) {
                if (regs.removeIf(r -> r.token == token)) rebuild();
            }
        };
    }

    /** 플러그인이 꺼질 때 그 플러그인의 리스너를 모두 뗀다. */
    public void unregisterAll(FurnacePlugin plugin) {
        synchronized (lock) {
            if (regs.removeIf(r -> r.plugin == plugin)) rebuild();
        }
    }

    private void rebuild() {
        List<Reg> sorted = new ArrayList<>(regs);
        sorted.sort(Comparator.comparing(Reg::priority).thenComparingLong(Reg::order));
        List<List<Reg>> out = new ArrayList<>(), in = new ArrayList<>();
        for (Reg r : sorted) {
            List<List<Reg>> table = r.type.direction() == PacketDirection.CLIENTBOUND ? out : in;
            for (int id : codec.ids(r.type)) {
                while (table.size() <= id) table.add(null);
                if (table.get(id) == null) table.set(id, new ArrayList<>());
                table.get(id).add(r);
            }
        }
        outbound = bake(out);
        inbound = bake(in);
    }

    private static Reg[][] bake(List<List<Reg>> table) {
        Reg[][] a = new Reg[table.size()][];
        for (int i = 0; i < a.length; i++) a[i] = table.get(i) == null ? null : table.get(i).toArray(new Reg[0]);
        return a;
    }

    // ------------------------------------------------------------------ 보내기·넣기

    @Override
    public void send(Player player, ClientboundPacket packet) { serverPlayer(player).send(encodeOutbound(packet, false)); }

    @Override
    public void sendSilently(Player player, ClientboundPacket packet) { serverPlayer(player).send(encodeOutbound(packet, true)); }

    @Override
    public void broadcast(ClientboundPacket packet) {
        RawPacket raw = encodeOutbound(packet, false);
        for (org.domi.furnace.server.entity.Player p : host.core().players()) p.send(raw);
    }

    @Override
    public void receive(Player player, ServerboundPacket packet) { inject(player, packet, false); }

    @Override
    public void receiveSilently(Player player, ServerboundPacket packet) { inject(player, packet, true); }

    private RawPacket encodeOutbound(ClientboundPacket packet, boolean silent) {
        Objects.requireNonNull(packet, "packet");
        if (packet.type().direction() != PacketDirection.CLIENTBOUND) throw new IllegalArgumentException("서버→클라이언트 패킷이 아니다: " + packet.type());
        ByteBuf b = Unpooled.buffer();
        try {
            int id = codec.encode(packet, b);
            return new RawPacket(id, ByteBufUtil.getBytes(b), packet, silent);
        } finally {
            b.release();
        }
    }

    private void inject(Player player, ServerboundPacket packet, boolean silent) {
        Objects.requireNonNull(packet, "packet");
        if (packet.type().direction() != PacketDirection.SERVERBOUND) throw new IllegalArgumentException("클라이언트→서버 패킷이 아니다: " + packet.type());
        org.domi.furnace.server.entity.Player p = serverPlayer(player);
        ByteBuf body = Unpooled.buffer();
        ByteBuf frame;
        try {
            int id = codec.encode(packet, body);
            frame = Unpooled.buffer(body.readableBytes() + 5);
            Types.writeVarInt(frame, id);
            frame.writeBytes(body);
        } finally {
            body.release();
        }
        ConnectionHandler h = p.channel().pipeline().get(ConnectionHandler.class);
        if (h == null) {
            frame.release();
            return;
        }
        h.injectPlay(frame, silent);
    }

    private static org.domi.furnace.server.entity.Player serverPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        if (player instanceof PlayerHandle ph) return ph.handle();
        throw new IllegalArgumentException("이 서버의 플레이어가 아니다: " + player);
    }

    @Override
    public boolean isSupported(PacketType<?> type) { return codec.ids(type).length > 0; }

    @Override
    public Collection<PacketType<?>> supportedTypes(PacketDirection direction) {
        List<PacketType<?>> out = new ArrayList<>();
        for (PacketType<?> t : PacketTypes.all(direction)) if (isSupported(t)) out.add(t);
        return List.copyOf(out);
    }

    @Override
    public int nextEntityId() { return host.core().world().entityIds().next(); }

    @Override
    public int protocolVersion() { return codec.protocolVersion(); }

    // ------------------------------------------------------------------ 연결마다

    /** play 진입 때: 이 플레이어 연결에 가로채기 지점을 붙인다. */
    public void attach(org.domi.furnace.server.entity.Player p) {
        p.channel().attr(PacketTap.KEY).set(new Tap(p));
    }

    private final class Tap implements PacketTap {

        private final org.domi.furnace.server.entity.Player player;
        private PlayerHandle api;   // 이벤트 루프에서만

        Tap(org.domi.furnace.server.entity.Player player) { this.player = player; }

        private PlayerHandle api() {
            PlayerHandle a = api;
            if (a == null) api = a = host.player(player);
            return a;
        }

        @Override
        public org.domi.furnace.server.net.protocol.Packet outbound(org.domi.furnace.server.net.protocol.Packet packet) {
            RawPacket raw = packet instanceof RawPacket r ? r : null;
            if (raw != null && raw.silent()) return packet;
            int id = packet.id();
            Reg[][] table = outbound;
            Reg[] rs = id >= 0 && id < table.length ? table[id] : null;
            if (rs == null) return packet;
            PacketType<?> type = codec.clientboundType(id);
            if (type == null) return packet;
            Packet value;
            byte[] bytes = null;
            try {
                if (raw != null && raw.source() instanceof Packet src) {
                    value = src;
                } else if (type.typed()) {
                    ByteBuf b = Unpooled.buffer();
                    try {
                        packet.write(b);
                        bytes = ByteBufUtil.getBytes(b);
                    } finally {
                        b.release();
                    }
                    value = codec.decode(type, id, Unpooled.wrappedBuffer(bytes));
                } else {
                    value = opaque(type);
                }
            } catch (RuntimeException e) {
                log.warn("패킷 디코드 실패({}), 리스너를 건너뛴다: {}", type, e.toString());
                return bytes != null ? new RawPacket(id, bytes, null, true) : packet;
            }
            EventImpl ev = fire(rs, type, value, raw != null);
            if (ev.cancelled) return null;
            if (!ev.modified) return bytes != null ? new RawPacket(id, bytes, null, true) : packet;
            try {
                return encodeOutbound((ClientboundPacket) ev.value, true);
            } catch (RuntimeException e) {
                log.warn("리스너가 바꾼 패킷을 인코딩하지 못했다({}), 원래 패킷을 보낸다: {}", type, e.toString());
                return bytes != null ? new RawPacket(id, bytes, null, true) : packet;
            }
        }

        @Override
        public ByteBuf inbound(int packetId, ByteBuf body, boolean pluginPacket) {
            Reg[][] table = inbound;
            Reg[] rs = packetId >= 0 && packetId < table.length ? table[packetId] : null;
            if (rs == null) return body;
            PacketType<?> type = codec.serverboundType(packetId);
            if (type == null) return body;
            Packet value;
            try {
                value = type.typed() ? codec.decode(type, packetId, body.duplicate()) : opaque(type);
            } catch (RuntimeException e) {
                return body;   // 잘못된 패킷 — 서버 본래 처리(바닐라처럼 끊기 등)에 맡긴다
            }
            EventImpl ev = fire(rs, type, value, pluginPacket);
            if (ev.cancelled) return null;
            if (!ev.modified) return body;
            ByteBuf b = Unpooled.buffer();
            try {
                int id = codec.encode(ev.value, b);
                ByteBuf frame = Unpooled.buffer(b.readableBytes() + 5);
                Types.writeVarInt(frame, id);
                frame.writeBytes(b);
                return frame;
            } catch (RuntimeException e) {
                log.warn("리스너가 바꾼 패킷을 인코딩하지 못했다({}), 원래 패킷을 처리한다: {}", type, e.toString());
                return body;
            } finally {
                b.release();
            }
        }

        private EventImpl fire(Reg[] rs, PacketType<?> type, Packet value, boolean plugin) {
            EventImpl ev = new EventImpl(api(), type, value, plugin);
            for (Reg r : rs) {
                ev.monitor = r.priority == EventPriority.MONITOR;
                try {
                    r.fn.onPacket(ev);
                } catch (Throwable t) {
                    log.error("[{}] 패킷 리스너 오류({})", name(r.plugin), type, t);
                }
            }
            return ev;
        }
    }

    private static String name(FurnacePlugin p) {
        try {
            return p.name();
        } catch (RuntimeException e) {   // 로더 밖에서 만든 플러그인(시험)
            return p.getClass().getSimpleName();
        }
    }

    @SuppressWarnings("unchecked")
    private static OpaquePacket opaque(PacketType<?> type) { return new OpaquePacket((PacketType<OpaquePacket>) type); }

    @SuppressWarnings("rawtypes")
    private static final class EventImpl implements PacketEvent {

        private final Player player;
        private final PacketType<?> type;
        private final boolean plugin;
        Packet value;
        boolean cancelled, modified, monitor;

        EventImpl(Player player, PacketType<?> type, Packet value, boolean plugin) {
            this.player = player;
            this.type = type;
            this.value = value;
            this.plugin = plugin;
        }

        @Override
        public Player player() { return player; }

        @Override
        public PacketType type() { return type; }

        @Override
        public Packet packet() { return value; }

        @Override
        public void setPacket(Packet packet) {
            if (monitor) throw new IllegalStateException("MONITOR 리스너는 패킷을 바꿀 수 없다");
            Objects.requireNonNull(packet, "packet");
            if (!type.typed()) throw new UnsupportedOperationException("타입 래퍼가 없는 패킷은 바꿀 수 없다: " + type);
            if (packet.type() != type) throw new IllegalArgumentException("같은 종류여야 한다: " + type + " ← " + packet.type());
            value = packet;
            modified = true;
        }

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public void setCancelled(boolean c) {
            if (monitor) throw new IllegalStateException("MONITOR 리스너는 취소할 수 없다");
            cancelled = c;
        }

        @Override
        public boolean isPluginPacket() { return plugin; }

        @Override
        public String toString() { return "PacketEvent[" + type + ", " + value + (cancelled ? ", cancelled" : "") + "]"; }
    }
}
