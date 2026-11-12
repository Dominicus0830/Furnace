// ProtocolLib 다리 2b-2: Furnace 서버가 스스로 보내는 패킷(채팅·엔티티·블록 등)을 ProtocolLib 의 나가는 리스너에 보여 준다.
//
// 바닐라·Paper 에서는 서버가 만든 패킷이 모두 Connection#send → channel.eventLoop().execute(작업) 를 지나고, ProtocolLib 이 그 자리(채널 프록시)에서
// 리스너를 부른다. Furnace 는 자기 레코드·코덱으로 바이트를 만들므로, Furnace 패킷 리스너(EventPriority.HIGHEST — 바이트에 가장 가깝게)에서
//   Furnace 패킷 → shim(PacketDelivery.fromFurnace) → 연결의 같은 자리(Connection#compatIntercept) → ProtocolLib 리스너 → shim' → Furnace 패킷
// 으로 한 바퀴 돌려 바꾸기·취소를 Furnace 이벤트에 되돌린다.
// 비용: ProtocolLib 리스너가 듣는 종류만 구독한다(ProtocolManager#getPacketListeners 의 보내기 허용 목록 합). 리스너가 없으면 구독도 변환도 없다.
// 다시 계산하는 때: 플러그인이 켜지고 꺼진 뒤 1틱, 그리고 20틱마다(런타임에 붙인 리스너 — 그 사이 패킷은 못 본다).
// 스레드: Furnace 패킷 리스너는 그 플레이어의 네트워크 스레드에서 패킷 순서대로 불린다. ProtocolLib 은 "주 스레드" 리스너(ASYNC 옵션이 없는 것 — 기본)가 있고
// 주 스레드가 아니면 패킷을 버리고 주 스레드에서 다시 보내는데, 그러면 Furnace 가 보낸 패킷이 1틱 늦게·다른 패킷 뒤에 도착한다(엔티티 생성이 메타데이터 뒤로 가는 등).
// 바닐라·Paper 에서 서버 패킷은 거의 모두 주 스레드에서 보내져 리스너가 그 자리에서 불리므로, 같은 결과를 위해 가로채는 동안 이 스레드를 주 스레드로 표시한다
// (PrimaryThread — region 빌림은 열지 않는다). 결정 기록: docs/user-todo/entries/2026-11-12-protocollib-2b2.md.
package org.domi.furnace.compat.paper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketEvent;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.compat.nms.DeliveredPackets;
import org.domi.furnace.compat.nms.PacketDelivery;
import org.domi.furnace.compat.thread.PrimaryThread;

public final class ProtocolLibBridge {

    /** ProtocolLib 플러그인의 주 클래스. */
    static final String MAIN_CLASS = "com.comphenix.protocol.ProtocolLib";

    private static final Set<String> PLAY_STATES = Set.of("game", "common", "cookie", "ping");

    /** 표지를 버리는 나이(리스너에 닿지 못한 것 — 앞선 Furnace 리스너가 취소·교체한 패킷). */
    private static final long MARK_TTL_NANOS = 10_000_000_000L;

    private static volatile ProtocolLibBridge active;

    private final BukkitPluginAdapter owner;
    /** ProtocolLib 나가는 리스너가 듣는 패킷 클래스(shim 클래스). 전역 스레드에서 부른다. */
    private final Supplier<Collection<Class<?>>> listened;
    private final Object lock = new Object();
    private Set<PacketType<?>> types = Set.of();   // lock 아래
    private EventSubscription subscription;          // lock 아래
    private ScheduledTask periodic;
    private volatile boolean stopped;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private ProtocolLibBridge(BukkitPluginAdapter owner, Supplier<Collection<Class<?>>> listened) {
        this.owner = owner;
        this.listened = listened;
    }

    // ------------------------------------------------------------------ 수명

    /** 버킷 플러그인이 켜진 뒤(BukkitPluginAdapter#onEnable): ProtocolLib 이면 다리를 켜고, 아니면 다음 틱에 구독을 다시 맞춘다. */
    static void pluginEnabled(BukkitPluginAdapter a) {
        try {
            if (a.javaPlugin() != null && a.javaPlugin().getClass().getName().equals(MAIN_CLASS)) {
                start(a, reflectiveSource(a.javaPlugin().getClass().getClassLoader()));
                return;
            }
            ProtocolLibBridge b = active;
            if (b != null) b.resyncSoon();
        } catch (RuntimeException | LinkageError e) {
            a.logger().log(Level.WARNING, "ProtocolLib 다리(서버가 보내는 패킷 보이기)를 켜지 못했다", e);
        }
    }

    /** 버킷 플러그인이 꺼진 뒤: ProtocolLib 이면 다리를 끄고(구독·예약은 플러그인과 함께 풀린다), 아니면 다음 틱에 다시 맞춘다. */
    static void pluginDisabled(BukkitPluginAdapter a) {
        ProtocolLibBridge b = active;
        if (b == null) return;
        if (b.owner == a) b.stop();
        else b.resyncSoon();
    }

    /** 다리를 켠다(시험은 listened 를 바꿔 끼운다). 이미 켜져 있으면 그것을 끄고 새로. */
    static ProtocolLibBridge start(BukkitPluginAdapter owner, Supplier<Collection<Class<?>>> listened) {
        ProtocolLibBridge old = active;
        if (old != null) old.stop();
        ProtocolLibBridge b = new ProtocolLibBridge(owner, listened);
        active = b;
        b.periodic = owner.tasks().runGlobalRepeating(b::resync, 1, 20);
        return b;
    }

    static ProtocolLibBridge active() { return active; }

    void stop() {
        stopped = true;
        if (active == this) active = null;
        ScheduledTask t = periodic;
        if (t != null) t.cancel();
        synchronized (lock) {
            if (subscription != null) subscription.unsubscribe();
            subscription = null;
            types = Set.of();
        }
        DeliveredPackets.watch(Set.of());
    }

    private void resyncSoon() {
        if (stopped) return;
        try {
            owner.tasks().runGlobal(this::resync, 1);
        } catch (RuntimeException e) {
            // 꺼지는 중 — 주기 점검이 맞춘다
        }
    }

    /** 지금 듣는 Furnace 종류(시험용). */
    Set<PacketType<?>> types() {
        synchronized (lock) {
            return types;
        }
    }

    /** ProtocolLib 이 듣는 종류에 맞춰 Furnace 패킷 구독을 다시 건다(바뀌었을 때만). 전역 스레드에서. */
    void resync() {
        if (stopped) return;
        DeliveredPackets.prune(MARK_TTL_NANOS);
        Collection<Class<?>> classes;
        try {
            classes = listened.get();
        } catch (RuntimeException | LinkageError e) {
            if (warned.add("listeners")) owner.logger().log(Level.WARNING, "ProtocolLib 리스너 목록을 읽지 못했다 — 서버가 보내는 패킷은 리스너에 보이지 않는다", e);
            return;
        }
        Set<PacketType<?>> want = new LinkedHashSet<>();
        Set<String> missing = new TreeSet<>();
        var packets = Furnace.server().packets();
        for (Class<?> c : classes) {
            PacketType<?> t = furnaceType(c);
            if (t == null) continue;   // play 단계 clientbound 가 아닌 것(로그인·상태·번들 등) — Furnace 패킷 API 범위 밖
            if (!packets.isSupported(t)) continue;
            String why = PacketDelivery.irreversibleReason(t);
            if (why == null && !t.typed() && !packets.isReadable(t)) why = "이 서버가 내용을 읽지 못하는 종류";
            if (why != null) {
                missing.add(t.name());
                continue;
            }
            want.add(t);
        }
        if (!missing.isEmpty() && warned.add("missing:" + missing)) {
            owner.logger().info("ProtocolLib 리스너가 듣는 종류 중 Furnace 가 스스로 보내는 패킷을 아직 보여 주지 못하는 것(플러그인이 보낸 것은 보인다): " + missing);
        }
        synchronized (lock) {
            if (stopped || want.equals(types)) return;
            if (subscription != null) subscription.unsubscribe();
            subscription = null;
            types = Set.copyOf(want);
            DeliveredPackets.watch(types);
            if (!want.isEmpty()) {
                subscription = packets.listen(owner, EventPriority.HIGHEST, this::onPacket, want.toArray(PacketType<?>[]::new));
            }
        }
    }

    /** shim 클래스 → Furnace play 단계 clientbound 종류(없으면 null). */
    static PacketType<?> furnaceType(Class<?> shim) {
        if (shim == null) return null;
        PacketDef d = PacketSchema.protocol767().packetByClass(shim.getName()).orElse(null);
        if (d == null || d.direction() != PacketDirection.CLIENTBOUND || !PLAY_STATES.contains(d.state())) return null;
        return PacketTypes.byName(PacketDirection.CLIENTBOUND, "minecraft:" + d.wireName());
    }

    // ------------------------------------------------------------------ 패킷 하나

    private void onPacket(PacketEvent<Packet> e) {
        Packet value = e.packet();
        if (DeliveredPackets.take(value)) return;   // 플러그인이 NMS·ProtocolLib 로 보낸 것 — 리스너가 이미 봤다
        if (e.isCancelled() || stopped) return;    // 앞선 Furnace 리스너가 버린 패킷은 나가지 않는다(라이브러리도 못 본다)
        net.minecraft.network.Connection c = connection(e.player());
        if (c == null) return;
        net.minecraft.network.protocol.Packet<?> shim;
        ClientboundPacket before;
        try {
            shim = PacketDelivery.fromFurnace(value);
            before = PacketDelivery.convert(shim);   // 비교 기준(되돌리기가 값을 조금 바꿔도 리스너가 손대지 않았으면 원래 바이트를 보낸다)
        } catch (RuntimeException ex) {
            warnOnce("from:" + value.type(), "Furnace 가 보내는 " + value.type().name() + " 패킷 하나를 ProtocolLib 리스너에 보여 주지 못했다(첫 오류): " + ex.getMessage());
            return;
        }
        net.minecraft.network.protocol.Packet<?> after;
        try (PrimaryThread.Scope scope = PrimaryThread.enter()) {
            after = c.compatIntercept(shim);
        } catch (RuntimeException ex) {
            warnOnce("intercept:" + value.type(), "ProtocolLib 가로채기 중 오류(" + value.type().name() + ", 원래 패킷을 보낸다): " + ex);
            return;
        }
        if (after == null) {
            e.setCancelled(true);
            return;
        }
        if (after instanceof net.minecraft.network.protocol.BundlePacket<?>) {   // 다른 모양으로 바꿨다 — 이 패킷 대신 그것을 보낸다
            e.setCancelled(true);
            sendInstead(e, after);
            return;
        }
        ClientboundPacket out;
        try {
            out = PacketDelivery.convert(after);
        } catch (RuntimeException ex) {
            warnOnce("to:" + value.type(), "ProtocolLib 리스너가 바꾼 " + value.type().name() + " 패킷을 Furnace 패킷으로 되돌리지 못해 원래 패킷을 보낸다(첫 오류): " + ex.getMessage());
            return;
        }
        if (out.equals(before)) return;   // 리스너가 손대지 않았다
        if (out.type() != value.type()) {
            e.setCancelled(true);
            sendInstead(e, after);
            return;
        }
        try {
            e.setPacket(out);
        } catch (RuntimeException ex) {
            warnOnce("set:" + value.type(), "ProtocolLib 리스너가 바꾼 " + value.type().name() + " 패킷을 쓸 수 없어 원래 패킷을 보낸다(첫 오류): " + ex.getMessage());
        }
    }

    /** 리스너가 다른 종류·묶음으로 바꾼 패킷을 그 자리에 보낸다(다시 라이브러리를 지나지 않게 표지를 붙여). */
    private void sendInstead(PacketEvent<Packet> e, net.minecraft.network.protocol.Packet<?> p) {
        try {
            PacketDelivery.deliver(e.player(), p);
        } catch (RuntimeException ex) {
            warnOnce("instead:" + p.getClass().getName(), "ProtocolLib 리스너가 바꾼 패킷(" + p.getClass().getSimpleName() + ")을 보내지 못했다(첫 오류): " + ex.getMessage());
        }
    }

    private static net.minecraft.network.Connection connection(org.domi.furnace.api.entity.Player p) {
        org.bukkit.craftbukkit.entity.CraftPlayer cp = Compat.existingPlayer(p);
        if (cp == null) return null;   // 아직 감싸지 않은 플레이어 — ProtocolLib 도 주입하지 않았다
        return cp.peekHandle() instanceof net.minecraft.server.level.ServerPlayer sp ? sp.connection.compatConnection() : null;
    }

    private void warnOnce(String key, String msg) {
        if (warned.add(key)) owner.logger().warning(msg);
    }

    // ------------------------------------------------------------------ 진짜 ProtocolLib

    /** ProtocolLib 공개 API 로 나가는 리스너의 패킷 클래스를 모은다(반사 — 호환 모듈은 ProtocolLib 없이 컴파일된다). */
    private static Supplier<Collection<Class<?>>> reflectiveSource(ClassLoader cl) {
        try {
            Class<?> library = Class.forName("com.comphenix.protocol.ProtocolLibrary", true, cl);
            Class<?> manager = Class.forName("com.comphenix.protocol.ProtocolManager", true, cl);
            Class<?> listener = Class.forName("com.comphenix.protocol.events.PacketListener", true, cl);
            Class<?> whitelist = Class.forName("com.comphenix.protocol.events.ListeningWhitelist", true, cl);
            Class<?> type = Class.forName("com.comphenix.protocol.PacketType", true, cl);
            var getManager = library.getMethod("getProtocolManager");
            var getListeners = manager.getMethod("getPacketListeners");
            var getSending = listener.getMethod("getSendingWhitelist");
            var getTypes = whitelist.getMethod("getTypes");
            var getPacketClass = type.getMethod("getPacketClass");
            var isServer = type.getMethod("isServer");
            return () -> {
                try {
                    Object pm = getManager.invoke(null);
                    if (pm == null) return List.of();
                    Set<Class<?>> out = new LinkedHashSet<>();
                    for (Object l : (Iterable<?>) getListeners.invoke(pm)) {
                        Object wl = getSending.invoke(l);
                        if (wl == null) continue;
                        for (Object t : (Collection<?>) getTypes.invoke(wl)) {
                            if (!Boolean.TRUE.equals(isServer.invoke(t))) continue;
                            Object c = getPacketClass.invoke(t);
                            if (c instanceof Class<?> k) out.add(k);
                        }
                    }
                    return out;
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex.getCause() != null ? ex.getCause() : ex);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ProtocolLib 공개 API 를 찾지 못했다: " + e, e);
        }
    }
}
