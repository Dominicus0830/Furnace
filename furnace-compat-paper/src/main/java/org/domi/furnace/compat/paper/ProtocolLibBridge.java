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
//
// 2b-3(T-180): 클라이언트가 보낸 패킷도 같은 방식으로 보여 준다(반치트·채팅 필터). Furnace 받는 패킷 리스너(EventPriority.LOWEST — 서버가 처리하기 전, 바이트에 가장 가깝게)에서
//   Furnace 패킷 → shim(ServerboundBridge.fromFurnace) → 연결 파이프라인 머리에서 읽기(Connection#compatReceive) → ProtocolLib 받는 처리기 → shim' → Furnace 패킷
// 으로 돌려 바꾸기·취소를 되돌린다. 받는 쪽은 주 스레드 표시를 하지 않는다 — ProtocolLib 받는 리스너는 기본이 Netty 스레드(isAsync)이고
// SYNC 리스너만 라이브러리가 스스로 주 스레드로 미룬다(Paper 와 같다, 결정 기록 docs/user-todo/entries/2026-11-15-protocollib-t180.md).
// 리스너가 다른 종류로 바꾼 패킷·receiveClientPacket 은 PacketService.receive 로 넣고, 리스너 안이라 서버가 그 자리에서 처리한다(뒤 패킷보다 먼저).
package org.domi.furnace.compat.paper;

import org.domi.furnace.compat.i18n.CompatLang;
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
import org.domi.furnace.compat.nms.ServerboundBridge;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.ServerboundPacket;
import org.domi.furnace.compat.thread.PrimaryThread;

public final class ProtocolLibBridge {

    /** ProtocolLib 플러그인의 주 클래스. */
    static final String MAIN_CLASS = "com.comphenix.protocol.ProtocolLib";

    private static final Set<String> PLAY_STATES = Set.of("game", "common", "cookie", "ping");

    /** 표지를 버리는 나이(리스너에 닿지 못한 것 — 앞선 Furnace 리스너가 취소·교체한 패킷). */
    private static final long MARK_TTL_NANOS = 10_000_000_000L;

    private static volatile ProtocolLibBridge active;

    private final BukkitPluginAdapter owner;
    /** ProtocolLib 리스너가 듣는 패킷 클래스(shim 클래스 — 보내는 쪽·받는 쪽이 클래스 방향으로 나뉜다). 전역 스레드에서 부른다. */
    private final Supplier<Collection<Class<?>>> listened;
    private final Object lock = new Object();
    private Set<PacketType<?>> types = Set.of();   // lock 아래
    private EventSubscription subscription;          // lock 아래
    private ScheduledTask periodic;
    private EventSubscription tracking;              // lock 아래 — 엔티티 번호를 담은 종류를 들을 때만
    private Set<PacketType<?>> inTypes = Set.of();   // lock 아래 — 받는 쪽(serverbound)
    private EventSubscription inSubscription;        // lock 아래
    private volatile boolean stopped;

    /**
     * 검토 R1·R1b: 플레이어가 엔티티를 보기 시작한 순간(소유 region 스레드) 만든 Bukkit 엔티티를 그 엔티티가 월드에 있는 동안 붙잡아 둔다. 그 엔티티의 패킷이
     * 네트워크 스레드에서 라이브러리를 지날 때 리스너의 {@code getEntityFromID}·{@code getEntityModifier} 가 찾게 — Compat 캐시는 약참조라 붙잡지 않으면
     * GC 뒤 사라진다. 엔티티가 없어지거나(제거·청크 내림·차원 이동) 번호가 다른 엔티티 것이 되면 주기 점검(resync)이 놓는다.
     */
    private final java.util.Map<Integer, Held> held = new ConcurrentHashMap<>();

    private record Held(Object entity, String world, java.util.UUID uuid) {}

    /** 패킷에 엔티티 번호가 들어 있는 종류(이 중 하나라도 들으면 보기 시작한 엔티티를 붙잡는다). */
    private static final Set<String> ENTITY_KINDS = Set.of("add_entity", "set_entity_motion", "rotate_head", "animate", "teleport_entity",
            "move_entity_pos", "move_entity_pos_rot", "move_entity_rot", "set_equipment", "damage_event", "hurt_animation", "entity_event",
            "take_item_entity", "set_passengers", "set_entity_link", "remove_entities", "update_mob_effect", "remove_mob_effect",
            "set_entity_data", "update_attributes");
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
            if (tracking != null) tracking.unsubscribe();
            tracking = null;
            types = Set.of();
            if (inSubscription != null) inSubscription.unsubscribe();
            inSubscription = null;
            inTypes = Set.of();
        }
        held.clear();
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

    /** 지금 듣는 받는 쪽 Furnace 종류(시험용). */
    Set<PacketType<?>> inboundTypes() {
        synchronized (lock) {
            return inTypes;
        }
    }

    /** ProtocolLib 이 듣는 종류에 맞춰 Furnace 패킷 구독을 다시 건다(바뀌었을 때만). 전역 스레드에서. */
    void resync() {
        if (stopped) return;
        DeliveredPackets.prune(MARK_TTL_NANOS);
        releaseGone();
        Collection<Class<?>> classes;
        try {
            classes = listened.get();
        } catch (RuntimeException | LinkageError e) {
            if (warned.add("listeners")) owner.logger().log(Level.WARNING, "ProtocolLib 리스너 목록을 읽지 못했다 — 서버가 보내는 패킷은 리스너에 보이지 않는다", e);
            return;
        }
        Set<PacketType<?>> want = new LinkedHashSet<>();
        Set<PacketType<?>> wantIn = new LinkedHashSet<>();
        Set<String> missing = new TreeSet<>();
        Set<String> missingIn = new TreeSet<>();
        Set<String> needsSetting = new TreeSet<>();
        var packets = Furnace.server().packets();
        for (Class<?> c : classes) {
            PacketType<?> in = ServerboundBridge.furnaceType(c);
            if (in != null) {   // 클라이언트가 보내는 패킷(2b-3)
                if (!packets.isSupported(in)) continue;
                String why = ServerboundBridge.refusal(in);
                if (why == null && !in.typed() && !packets.isReadable(in)) why = "이 서버가 내용을 읽지 못하는 종류";
                if (why != null) missingIn.add(in.name());
                else wantIn.add(in);
                continue;
            }
            PacketType<?> t = furnaceType(c);
            if (t == null) continue;   // play 단계 패킷이 아닌 것(로그인·상태·번들 등) — Furnace 패킷 API 범위 밖
            if (!packets.isSupported(t)) continue;
            String why = PacketDelivery.irreversibleReason(t);
            if (why == null && !t.typed() && !packets.isReadable(t)) why = "이 서버가 내용을 읽지 못하는 종류";
            if (why != null) {
                // 서버 설정으로 풀 수 있는 까닭(authlib 없음 등)은 따로 이름 — 까닭으로 적는다: 운영자가 무엇을 켜면 되는지 알게(PR #390 검토 R2)
                if (org.domi.furnace.compat.nms.PacketDelivery.fixableBySetting(why)) needsSetting.add(t.name() + " — " + why);
                else missing.add(t.name());
                continue;
            }
            want.add(t);
        }
        if (!missing.isEmpty() && warned.add("missing:" + missing)) {
            owner.logger().info(CompatLang.t("compat.pl-listen-missing-out", missing));
        }
        if (!needsSetting.isEmpty() && warned.add("needsSetting:" + needsSetting)) {
            owner.logger().info(CompatLang.t("compat.pl-listen-needs-setting", String.join("; ", needsSetting)));
        }
        if (!missingIn.isEmpty() && warned.add("missingIn:" + missingIn)) {
            owner.logger().info(CompatLang.t("compat.pl-listen-missing-in", missingIn));
        }
        resyncInbound(packets, wantIn);
        synchronized (lock) {
            if (stopped || want.equals(types)) return;
            if (subscription != null) subscription.unsubscribe();
            subscription = null;
            types = Set.copyOf(want);
            DeliveredPackets.watch(types);
            if (!want.isEmpty()) {
                subscription = packets.listen(owner, EventPriority.HIGHEST, this::onPacket, want.toArray(PacketType<?>[]::new));
            }
            boolean entities = want.stream().anyMatch(t -> ENTITY_KINDS.contains(t.name().substring(t.name().indexOf(':') + 1)));
            if (entities && tracking == null) {
                tracking = Furnace.server().events().subscribe(owner, org.domi.furnace.api.event.player.PlayerTrackEntityEvent.class,
                        EventPriority.MONITOR, false, this::onTrack);
            } else if (!entities && tracking != null) {
                tracking.unsubscribe();
                tracking = null;
                held.clear();
            }
        }
    }

    /** 받는 쪽 구독을 맞춘다(바뀌었을 때만). */
    private void resyncInbound(org.domi.furnace.api.packet.PacketService packets, Set<PacketType<?>> want) {
        synchronized (lock) {
            if (stopped || want.equals(inTypes)) return;
            if (inSubscription != null) inSubscription.unsubscribe();
            inSubscription = null;
            inTypes = Set.copyOf(want);
            if (!want.isEmpty()) {
                inSubscription = packets.listen(owner, EventPriority.LOWEST, this::onInbound, want.toArray(PacketType<?>[]::new));
            }
        }
    }

    /** 붙잡고 있는 엔티티 수(시험용). */
    int heldCount() { return held.size(); }

    /** 월드에서 없어진(또는 번호가 다른 엔티티 것이 된) 엔티티를 놓는다. 번호 → UUID 색인은 어느 스레드에서나 읽힌다. */
    private void releaseGone() {
        if (held.isEmpty()) return;
        var server = Furnace.server();
        held.entrySet().removeIf(en -> {
            var w = server.world(en.getValue().world());
            return w == null || !w.entityUuidById(en.getKey()).map(en.getValue().uuid()::equals).orElse(false);
        });
    }

    /** 엔티티 소유 region 스레드: Bukkit 엔티티를 만들어(Compat 캐시에 들어간다) 월드에 있는 동안 붙잡는다. */
    private void onTrack(org.domi.furnace.api.event.player.PlayerTrackEntityEvent e) {
        if (stopped) return;
        var entity = e.entity();
        if (entity instanceof org.domi.furnace.api.entity.Player) return;   // 플레이어는 번호 조회가 어느 스레드에서나 된다
        try {
            held.put(entity.entityId(), new Held(Compat.entity(entity), entity.location().world(), entity.uniqueId()));
        } catch (RuntimeException ex) {
            warnOnce("track", "compat.pl-track-failed", ex);
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
        String refusal = PacketDelivery.quickRefusal(value);
        if (refusal != null) {   // 예외 없이 건너뛴다(원래 패킷은 그대로 나간다). 경고는 값 종류마다 한 번(검토 R2·R3)
            warnOnce("from:" + value.type() + ":" + refusal, "compat.pl-out-unshowable", value.type().name(), refusal);
            return;
        }
        net.minecraft.network.protocol.Packet<?> shim;
        ClientboundPacket before;
        try {
            shim = PacketDelivery.fromFurnace(value);
            // 비교 기준(되돌리기가 값을 조금 바꿔도 리스너가 손대지 않았으면 원래 바이트를 보낸다). 값이 그대로 도는 종류는 원래 값 그대로
            before = PacketDelivery.losslessRoundTrip(value.type()) && value instanceof ClientboundPacket cp ? cp : PacketDelivery.convert(shim);
        } catch (RuntimeException ex) {
            warnOnce("from:" + value.type(), "compat.pl-out-show-failed", value.type().name(), ex.getMessage());
            return;
        }
        net.minecraft.network.protocol.Packet<?> after;
        try (PrimaryThread.Scope scope = PrimaryThread.enter()) {
            after = c.compatIntercept(shim);
        } catch (RuntimeException ex) {
            warnOnce("intercept:" + value.type(), "compat.pl-out-intercept-error", value.type().name(), ex);
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
            warnOnce("to:" + value.type(), "compat.pl-out-convert-back-failed", value.type().name(), ex.getMessage());
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
            warnOnce("set:" + value.type(), "compat.pl-out-write-failed", value.type().name(), ex.getMessage());
        }
    }

    /**
     * 클라이언트가 보낸 패킷 하나(그 플레이어의 네트워크 스레드, 서버가 처리하기 전). 플러그인이 넣은 것(PacketService.receive·라이브러리 receiveClientPacket 이
     * {@code Connection#read} 로 넣은 것)은 건너뛴다 — 라이브러리는 넣을 때 이미 리스너를 불렀다(receiveClientPacket 의 filters).
     */
    private void onInbound(PacketEvent<Packet> e) {
        if (e.isPluginPacket() || e.isCancelled() || stopped) return;
        Packet value = e.packet();
        if (value instanceof GenericPacket g && !g.readable()) return;   // 읽지 못한 것(잘못된 패킷) — 서버 본래 처리에 맡긴다
        net.minecraft.network.Connection c = connection(e.player());
        if (c == null) return;
        net.minecraft.network.protocol.Packet<?> shim;
        ServerboundPacket before;
        try {
            shim = ServerboundBridge.fromFurnace((ServerboundPacket) value);
            before = ServerboundBridge.toFurnace(shim);   // 비교 기준
        } catch (RuntimeException ex) {
            warnOnce("in-from:" + value.type(), "compat.pl-in-show-failed", value.type().name(), ex.getMessage());
            return;
        }
        net.minecraft.network.protocol.Packet<?> after;
        // 주 스레드 표시를 하지 않는다: ProtocolLib 받는 리스너는 기본이 Netty 스레드(isAsync)이고, SYNC 리스너만 라이브러리가 스스로 주 스레드로
        // 미룬다(그때 compatReceive 는 null — 나중에 Connection#read 가 넣는다). 보내는 쪽(기본이 동기)과 반대다(PR #389 검토 R1).
        try {
            after = c.compatReceive(shim);
        } catch (RuntimeException ex) {
            warnOnce("in-intercept:" + value.type(), "compat.pl-in-intercept-error", value.type().name(), ex);
            return;
        }
        if (after == null) {   // 리스너가 취소했다(또는 라이브러리가 나중으로 미뤘다 — 그때는 나중에 Connection#read 가 넣는다)
            e.setCancelled(true);
            return;
        }
        ServerboundPacket out;
        try {
            out = ServerboundBridge.toFurnace(after);
        } catch (RuntimeException ex) {
            warnOnce("in-to:" + value.type(), "compat.pl-in-convert-back-failed", value.type().name(), ex.getMessage());
            return;
        }
        if (sameInbound(out, before)) return;   // 리스너가 손대지 않았다
        if (out.type() != value.type()) {   // 다른 종류로 바꿨다 — 이 패킷 대신 그것을 넣는다
            e.setCancelled(true);
            try {
                Furnace.server().packets().receive(e.player(), out);
            } catch (RuntimeException ex) {
                warnOnce("in-instead:" + out.type(), "compat.pl-in-inject-failed", out.type().name(), ex.getMessage());
            }
            return;
        }
        try {
            e.setPacket(out);
        } catch (RuntimeException ex) {
            warnOnce("in-set:" + value.type(), "compat.pl-in-write-failed", value.type().name(), ex.getMessage());
        }
    }

    /** 손대지 않았나: 서명 있는 채팅은 레코드 equals 가 byte[] 를 객체로 비교하므로 내용으로 본다(PR #389 검토 S2). */
    static boolean sameInbound(ServerboundPacket a, ServerboundPacket b) {
        if (a instanceof org.domi.furnace.api.packet.serverbound.ServerboundChatPacket x && b instanceof org.domi.furnace.api.packet.serverbound.ServerboundChatPacket y) {
            return x.message().equals(y.message()) && x.timestamp().equals(y.timestamp()) && x.salt() == y.salt()
                    && java.util.Arrays.equals(x.signature(), y.signature()) && x.lastSeenOffset() == y.lastSeenOffset()
                    && x.lastSeenAcknowledged() == y.lastSeenAcknowledged();
        }
        return a.equals(b);
    }

    /** 리스너가 다른 종류·묶음으로 바꾼 패킷을 그 자리에 보낸다(다시 라이브러리를 지나지 않게 표지를 붙여). */
    private void sendInstead(PacketEvent<Packet> e, net.minecraft.network.protocol.Packet<?> p) {
        try {
            PacketDelivery.deliver(e.player(), p);
        } catch (RuntimeException ex) {
            warnOnce("instead:" + p.getClass().getName(), "compat.pl-send-failed", p.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private static net.minecraft.network.Connection connection(org.domi.furnace.api.entity.Player p) {
        org.bukkit.craftbukkit.entity.CraftPlayer cp = Compat.existingPlayer(p);
        if (cp == null) return null;   // 아직 감싸지 않은 플레이어 — ProtocolLib 도 주입하지 않았다
        return cp.peekHandle() instanceof net.minecraft.server.level.ServerPlayer sp ? sp.connection.compatConnection() : null;
    }

    private void warnOnce(String key, String i18nKey, Object... args) {
        if (warned.add(key)) owner.logger().warning(CompatLang.t(i18nKey, args));
    }

    // ------------------------------------------------------------------ 진짜 ProtocolLib

    /** ProtocolLib 공개 API 로 리스너의 패킷 클래스(보내는 쪽·받는 쪽)를 모은다(반사 — 호환 모듈은 ProtocolLib 없이 컴파일된다). */
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
            var getReceiving = listener.getMethod("getReceivingWhitelist");
            var getTypes = whitelist.getMethod("getTypes");
            var getPacketClass = type.getMethod("getPacketClass");
            var isServer = type.getMethod("isServer");
            var isClient = type.getMethod("isClient");
            return () -> {
                try {
                    Object pm = getManager.invoke(null);
                    if (pm == null) return List.of();
                    Set<Class<?>> out = new LinkedHashSet<>();
                    for (Object l : (Iterable<?>) getListeners.invoke(pm)) {
                        // 보내는 쪽(서버 패킷)과 받는 쪽(클라이언트 패킷, 2b-3) — 클래스 방향으로 나뉜다
                        Object[][] sides = {{getSending.invoke(l), isServer}, {getReceiving.invoke(l), isClient}};
                        for (Object[] side : sides) {
                            Object wl = side[0];
                            if (wl == null) continue;
                            var dir = (java.lang.reflect.Method) side[1];
                            for (Object t : (Collection<?>) getTypes.invoke(wl)) {
                                if (!Boolean.TRUE.equals(dir.invoke(t))) continue;
                                Object c = getPacketClass.invoke(t);
                                if (c instanceof Class<?> k) out.add(k);
                            }
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
