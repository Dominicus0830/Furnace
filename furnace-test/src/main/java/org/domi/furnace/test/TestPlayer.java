// 시험 서버의 가짜 플레이어: 루프백 소켓으로 실제 접속 흐름(핸드셰이크 → 오프라인 로그인 → 구성 → 플레이)을 밟는 최소 클라이언트.
// 읽기 스레드가 클라이언트가 꼭 해야 하는 답(keep-alive 되돌리기, 텔레포트 확인, 청크 묶음 받음)을 대신 한다.
// 받은 시스템 메시지·액션바는 서버 쪽 패킷 구독으로 Text 그대로 모은다(직렬화 전 값 — NBT 를 다시 풀지 않는다).
// 받은 패킷: 읽기 스레드가 내용을 읽을 수 있는 종류(타입 레코드·GenericPacket)를 버전 중립 값으로 풀어 모은다 — received(type)·awaitPacket.
// 다른 플러그인 리스너가 바꾸거나 취소한 뒤, 실제로 소켓으로 온 그대로다.
// command()/chat() 은 패킷을 보낸 뒤 동기화 장벽(같은 연결의 다음 패킷 → 전역 스레드 → 플레이어 region)을 거쳐, 서버가 그 패킷을
// 처리한 뒤에 돌아온다. 그래서 바로 다음 줄에서 결과(메시지·블록·플러그인 상태)를 확인해도 된다. 틱은 진행하지 않는다.
package org.domi.furnace.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;

public final class TestPlayer implements AutoCloseable {

    /** 1.21.1 프로토콜 번호. */
    static final int PROTOCOL = 767;
    /** 동기화 장벽에 쓰는 플러그인 채널(서버 쪽 구독이 가로채 버린다 — 플러그인에는 가지 않는다). */
    static final String SYNC_CHANNEL = "furnace-test:sync";

    private final FurnaceTestServer server;
    private final String name;
    private final Socket socket;
    private final OutputStream out;
    private final DataInputStream in;
    private UUID uuid;
    private final List<Text> messages = new CopyOnWriteArrayList<>();
    private final List<Text> actionBars = new CopyOnWriteArrayList<>();
    private final List<org.domi.furnace.api.packet.clientbound.ClientboundCustomPayloadPacket> pluginMessages = new CopyOnWriteArrayList<>();
    /** 구성 단계에서 minecraft:register 로 알릴 채널(모드 클라이언트처럼). */
    private final String[] configChannels;
    private final AtomicLong syncSent = new AtomicLong();
    private final AtomicLong syncSeen = new AtomicLong();
    private final List<EventSubscription> subs = new CopyOnWriteArrayList<>();
    /** 받은 패킷(읽을 수 있는 종류만, 받은 순서). 오래된 것부터 버린다. */
    private final java.util.ArrayDeque<org.domi.furnace.api.packet.Packet> received = new java.util.ArrayDeque<>();
    /** received 에 남기는 최대 수(오래 도는 시험의 메모리 상한). */
    static final int RECEIVED_MAX = 20_000;
    private volatile org.domi.furnace.server.plugin.packet.ProtocolCodec codec;
    private volatile int pingId = -1;
    /** 우리 ping 의 기준값 — 플러그인이 보낸 ping(안티치트 등)과 섞이지 않게 기준값 + 번호만 인정한다. */
    private final int pingBase = java.util.concurrent.ThreadLocalRandom.current().nextInt();
    private final AtomicLong pingSent = new AtomicLong();
    private final AtomicLong pingSeen = new AtomicLong();
    /** 읽을 수 있는 종류인데 풀지 못한 패킷 수(실패 메시지에 보인다). */
    private final AtomicLong decodeFailures = new AtomicLong();
    private volatile boolean connected;
    private volatile String disconnectReason;
    private Thread reader;

    private TestPlayer(FurnaceTestServer server, String name, Socket socket, String[] configChannels) throws IOException {
        this.server = server;
        this.name = name;
        this.configChannels = configChannels.clone();
        this.socket = socket;
        this.out = socket.getOutputStream();
        this.in = new DataInputStream(socket.getInputStream());
    }

    static TestPlayer connect(FurnaceTestServer server, int port, String name, String... configChannels) {
        TestPlayer p = null;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", port), 5000);
            s.setSoTimeout((int) Math.min(Integer.MAX_VALUE, server.timeoutMillis()));
            p = new TestPlayer(server, name, s, configChannels);
            p.subscribe();
            p.login();
            p.awaitOnline();
            return p;
        } catch (Exception e) {
            if (p != null) p.closeQuietly();
            try {
                s.close();
            } catch (IOException ignored) { }
            throw new IllegalStateException("가짜 플레이어 " + name + " 접속 실패", e);
        }
    }

    // ------------------------------------------------------------------ 접속

    private void subscribe() {
        var packets = server.internals().plugins().packetsImpl();
        subs.add(packets.listenInternal(PacketTypes.Clientbound.SYSTEM_CHAT, EventPriority.MONITOR, e -> {
            if (!isMe(e.player())) return;
            (e.packet().actionBar() ? actionBars : messages).add(e.packet().message());
        }));
        subs.add(packets.listenInternal(PacketTypes.Clientbound.SET_ACTION_BAR_TEXT, EventPriority.MONITOR, e -> {
            if (isMe(e.player())) actionBars.add(e.packet().text());
        }));
        subs.add(packets.listenInternal(PacketTypes.Clientbound.CUSTOM_PAYLOAD, EventPriority.MONITOR, e -> {
            if (isMe(e.player())) pluginMessages.add(e.packet());
        }));
        subs.add(packets.listenInternal(PacketTypes.Serverbound.CUSTOM_PAYLOAD, EventPriority.LOWEST, e -> {
            if (!SYNC_CHANNEL.equals(e.packet().channel()) || !isMe(e.player())) return;
            e.setCancelled(true);
            syncSeen.accumulateAndGet(Long.parseLong(new String(e.packet().data(), StandardCharsets.UTF_8)), Math::max);
        }));
    }

    private boolean isMe(Player p) { return p != null && p.name().equals(name); }

    private void login() throws Exception {
        write(0x00, b -> {   // handshake → 로그인
            Types.writeVarInt(b, PROTOCOL);
            Types.writeString(b, "localhost");
            b.writeShort(25565);
            Types.writeVarInt(b, 2);
        });
        write(0x00, b -> {   // login start
            Types.writeString(b, name);
            Types.writeUuid(b, new UUID(0, 0));
        });
        ByteBuf success = await(0x02, 0x00);   // login success (0x00 = 로그인 단계 끊기)
        uuid = Types.readUuid(success);
        write(0x03, null);                       // login acknowledged → 구성
        if (configChannels.length > 0) {         // custom_payload(구성): minecraft:register
            byte[] names = String.join("\0", configChannels).getBytes(StandardCharsets.UTF_8);
            write(0x02, b -> {
                Types.writeString(b, "minecraft:register");
                b.writeBytes(names);
            });
        }
        write(0x07, b -> Types.writeVarInt(b, 0));   // known packs: 없음
        await(0x03, 0x02);                       // finish configuration (0x02 = 구성 단계 끊기)
        write(0x03, null);                       // ack → 플레이
        await(0x2B, 0x1D);                       // login(play)
        codec = server.internals().plugins().packetsImpl().codec();
        int[] ping = codec.ids(PacketTypes.Clientbound.PING);
        pingId = ping.length > 0 ? ping[0] : -1;
        connected = true;
        reader = new Thread(this::readLoop, "furnace-test-player-" + name);
        reader.setDaemon(true);
        reader.start();
    }

    /** 서버 접속자 목록에 들 때까지(틱 없이). */
    private void awaitOnline() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.timeoutMillis());
        while (server.internals().playerByName(name) == null) {
            if (!connected) throw new IllegalStateException("접속이 끊겼다: " + disconnectReason);
            if (System.nanoTime() - deadline > 0) throw new IllegalStateException("접속자 목록에 들지 않았다");
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    // ------------------------------------------------------------------ 읽기(클라이언트가 꼭 하는 답)

    /** 시험 전용(같은 패키지): 읽기 스레드가 받은 play 프레임(id, 본문)을 그대로 넘긴다. 범용 코덱 왕복 시험이 쓴다. */
    static volatile java.util.function.BiConsumer<Integer, byte[]> frameTap;

    private void readLoop() {
        try {
            while (connected) {
                ByteBuf f = read();
                int id = Types.readVarInt(f);
                if (id == pingId) {   // 받은 패킷 장벽(flushReceived) — 서버가 그 앞에 쓴 것은 모두 읽었다
                    long d = (f.getInt(f.readerIndex()) - pingBase) & 0xFFFFFFFFL;   // 우리가 보낸 번호(1..보낸 수)만 인정
                    if (d >= 1 && d <= pingSent.get()) pingSeen.accumulateAndGet(d, Math::max);
                    continue;
                }
                var tap = frameTap;
                if (tap != null) {
                    try {
                        tap.accept(id, io.netty.buffer.ByteBufUtil.getBytes(f));
                    } catch (RuntimeException e) {   // 시험 코드 실수가 접속 끊김처럼 보이지 않게
                        e.printStackTrace();
                    }
                }
                record(id, f.duplicate());
                switch (id) {
                    case 0x26 -> {   // keep_alive → 그대로 되돌린다
                        long v = f.readLong();
                        write(0x18, b -> b.writeLong(v));
                    }
                    case 0x40 -> {   // player_position → accept_teleportation
                        f.skipBytes(8 * 3 + 4 * 2 + 1);
                        int tp = Types.readVarInt(f);
                        write(0x00, b -> Types.writeVarInt(b, tp));
                    }
                    case 0x0C -> write(0x08, b -> b.writeFloat(25.0f));   // chunk_batch_finished → chunk_batch_received
                    case 0x1D -> {   // disconnect
                        disconnectReason = "disconnect";
                        connected = false;
                    }
                    default -> { }
                }
            }
        } catch (Exception e) {
            if (connected && disconnectReason == null) disconnectReason = String.valueOf(e);
        } finally {
            connected = false;
        }
    }

    /** 읽을 수 있는 종류면 풀어서 모은다(못 풀면 건너뛴다). 읽기 스레드에서. */
    private void record(int id, ByteBuf body) {
        var c = codec;
        if (c == null) return;
        var t = c.clientboundType(id);
        if (t == null || !codec.readable(t)) return;
        org.domi.furnace.api.packet.Packet p;
        try {
            p = c.decode(t, id, body);
        } catch (RuntimeException e) {
            decodeFailures.incrementAndGet();
            return;
        }
        synchronized (received) {
            if (received.size() >= RECEIVED_MAX) received.pollFirst();
            received.addLast(p);
            received.notifyAll();
        }
    }

    // ------------------------------------------------------------------ 보내기

    /** 명령을 보낸다(앞의 '/' 는 떼어 보낸다). 서버가 처리한 뒤 돌아온다. */
    public TestPlayer command(String command) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        send(0x04, b -> Types.writeString(b, c));   // chat_command(서명 없음)
        sync();
        return this;
    }

    /** 채팅을 보낸다(서명 없음). 서버가 처리한 뒤 돌아온다. */
    public TestPlayer chat(String message) {
        send(0x06, b -> {
            Types.writeString(b, message);
            b.writeLong(System.currentTimeMillis());
            b.writeLong(0L);
            b.writeBoolean(false);           // 서명 없음
            Types.writeVarInt(b, 0);         // last seen: offset
            b.writeBytes(new byte[3]);       // last seen: 20비트 확인 표
        });
        sync();
        return this;
    }

    /** 위치를 보낸다(move_player_pos). 서버는 다음 틱에 이동을 검사·반영한다. */
    public TestPlayer moveTo(double x, double y, double z, boolean onGround) {
        send(0x1A, b -> {
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
            b.writeBoolean(onGround);
        });
        sync();
        return this;
    }

    /** 플러그인 채널로 보낸다(play custom_payload). 서버가 처리한 뒤 돌아온다(전역 스레드로 넘긴 일까지). */
    public TestPlayer sendPluginMessage(String channel, byte[] data) {
        send(0x12, b -> {
            Types.writeString(b, channel);
            b.writeBytes(data);
        });
        sync();
        return this;
    }

    /** minecraft:register 로 채널을 알린다(play 단계). */
    public TestPlayer registerChannels(String... channels) {
        return sendPluginMessage("minecraft:register", String.join("\0", channels).getBytes(StandardCharsets.UTF_8));
    }

    /** minecraft:unregister 로 채널을 거둔다. */
    public TestPlayer unregisterChannels(String... channels) {
        return sendPluginMessage("minecraft:unregister", String.join("\0", channels).getBytes(StandardCharsets.UTF_8));
    }

    /** 원시 serverbound play 패킷(id + 본문). 다른 방법이 없을 때만. */
    public TestPlayer sendPacket(int id, Consumer<ByteBuf> body) {
        send(id, body);
        sync();
        return this;
    }

    /**
     * 장벽: 지금까지 보낸 패킷을 서버가 처리했을 때 돌아온다. 같은 연결의 패킷은 차례로 처리되므로 장벽 패킷이 서버에 닿으면 앞 패킷은
     * 끝났고, 그 패킷이 전역 스레드 → 플레이어 region → 연결 이벤트 루프로 넘긴 일은 그 뒤에 넣은 빈 작업이 끝날 때 끝났다.
     * 그보다 여러 번 건너가는 일(다른 region 의 칸을 만지는 명령, region 이 다시 전역으로 넘기는 일)은 덮지 못한다 — awaitMessage 나 tick() 으로 본다.
     */
    public void sync() {
        long n = syncSent.incrementAndGet();
        send(0x12, b -> {   // custom_payload
            Types.writeString(b, SYNC_CHANNEL);
            b.writeBytes(Long.toString(n).getBytes(StandardCharsets.UTF_8));
        });
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.timeoutMillis());
        while (syncSeen.get() < n) {
            if (!connected) return;   // 끊겼다(명령이 쫓아냈을 수 있다) — 시험이 isConnected 로 본다
            if (System.nanoTime() - deadline > 0) throw new IllegalStateException("서버가 패킷을 처리하지 않았다(장벽 " + n + ")");
            java.util.concurrent.locks.LockSupport.parkNanos(200_000L);
        }
        server.onGlobal(() -> null);
        var p = server.internals().playerByName(name);
        if (p != null) {
            var cp = p.chunkPos();
            server.await(server.internals().world().submitAtChunk(cp, r -> null));
            // 보낸 패킷(메시지 등)은 연결의 이벤트 루프에서 인코딩될 때 구독자가 본다 — 그 앞에 쌓인 보내기까지 끝나게
            var ch = p.channel();
            if (ch != null && ch.isActive()) {
                try {
                    ch.eventLoop().submit(() -> { }).get(server.timeoutMillis(), TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.RejectedExecutionException | java.util.concurrent.ExecutionException e) {
                    // 끊기는 중
                } catch (java.util.concurrent.TimeoutException e) {
                    throw new IllegalStateException("이벤트 루프가 응답하지 않는다", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void send(int id, Consumer<ByteBuf> body) {
        if (!connected) throw new IllegalStateException(name + " 은 접속해 있지 않다(" + disconnectReason + ")");
        try {
            write(id, body);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ 받은 것

    public String name() { return name; }

    public UUID uuid() { return uuid; }

    /** 플러그인이 보는 플레이어 핸들. 나갔으면 null. */
    public Player player() { return server.api().player(uuid); }

    public boolean isConnected() { return connected; }

    /** 끊긴 까닭(서버가 끊었으면 "disconnect"). 접속 중이면 null. */
    public String disconnectReason() { return connected ? null : disconnectReason; }

    /** 받은 채팅 창 시스템 메시지(명령 출력·공지·플러그인 sendMessage), 받은 차례로. */
    public List<Text> messages() { return List.copyOf(messages); }

    /** messages() 를 글자만. */
    public List<String> messageLines() { return messages.stream().map(Text::plainText).toList(); }

    /** 받은 액션바 글. */
    public List<Text> actionBars() { return List.copyOf(actionBars); }

    /** 서버가 이 플레이어에게 보낸 플러그인 채널 메시지(minecraft:register 포함, 받은 순서). */
    public List<org.domi.furnace.api.packet.clientbound.ClientboundCustomPayloadPacket> pluginMessages() { return List.copyOf(pluginMessages); }

    public void clearPluginMessages() { pluginMessages.clear(); }

    /** 모은 메시지를 비운다. */
    public void clearMessages() {
        messages.clear();
        actionBars.clear();
    }

    /** 글자가 조건에 맞는 메시지가 올 때까지(틱 없이) 기다린다. 비동기로 보내는 플러그인 메시지용. */
    public Text awaitMessage(Predicate<String> match) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.timeoutMillis());
        while (true) {
            for (Text t : messages) if (match.test(t.plainText())) return t;
            if (System.nanoTime() - deadline > 0) throw new IllegalStateException(name + ": 맞는 메시지가 오지 않았다. 받은 것: " + messageLines());
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    // ------------------------------------------------------------------ 받은 패킷

    /**
     * 받은 패킷 중 그 종류(받은 순서). 읽을 수 있는 종류만 모은다(PacketService.isReadable). 서버가 지금까지 이 플레이어에게 보낸 것은
     * 모두 읽은 뒤에 돌려준다(flushReceived). 예) tp.received(PacketTypes.Clientbound.SET_ENTITY_DATA)
     */
    public <P extends org.domi.furnace.api.packet.Packet> List<P> received(org.domi.furnace.api.packet.PacketType<P> type) {
        java.util.Objects.requireNonNull(type, "type");
        if (!codec.readable(type)) throw new IllegalArgumentException(type + " 는 이 서버 버전에서 내용을 읽을 수 없어 모으지 않는다");
        flushReceived();
        List<P> out = new java.util.ArrayList<>();
        synchronized (received) {
            for (var p : received) if (p.type() == type) out.add(type.packetClass().cast(p));
        }
        return out;
    }

    /** 받은 패킷 전부(읽을 수 있는 종류, 받은 순서). */
    public List<org.domi.furnace.api.packet.Packet> received() {
        flushReceived();
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    /** 모은 패킷을 비운다. 그 전에 서버가 보낸 것까지 읽고 버린다 — 뒤에 받는 것만 남는다. */
    public void clearReceived() {
        flushReceived();
        synchronized (received) {
            received.clear();
        }
    }

    /**
     * 그 종류이고 조건에 맞는 패킷이 올 때까지(틱 없이) 기다린다 — 이미 받은 것도 본다. 다음 틱에 보내는 것이면 server.tick() 을 먼저.
     * 오지 않으면 받은 그 종류 목록을 담아 IllegalStateException. 예) tp.awaitPacket(PacketTypes.Clientbound.SET_ENTITY_DATA, d -> d.entityId() == id)
     */
    public <P extends org.domi.furnace.api.packet.Packet> P awaitPacket(org.domi.furnace.api.packet.PacketType<P> type, Predicate<? super P> match) {
        java.util.Objects.requireNonNull(match, "match");
        if (!codec.readable(type)) throw new IllegalArgumentException(type + " 는 이 서버 버전에서 내용을 읽을 수 없어 모으지 않는다");
        flushReceived();   // 서버가 이미 보낸 것은 먼저 다 읽는다
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.timeoutMillis());
        while (true) {
            List<org.domi.furnace.api.packet.Packet> snap;
            synchronized (received) {
                snap = List.copyOf(received);
            }
            // 조건은 잠금 밖에서 돌린다 — 조건 안에서 received() 를 불러도 읽기 스레드가 막히지 않게
            List<P> sameType = new java.util.ArrayList<>();
            for (var p : snap) {
                if (p.type() != type) continue;
                P cast = type.packetClass().cast(p);
                if (match.test(cast)) return cast;
                sameType.add(cast);
            }
            long left = deadline - System.nanoTime();
            if (left <= 0 || !connected) throw new IllegalStateException(name + ": 맞는 " + type + " 가 오지 않았다" + (connected ? "" : "(접속 끊김)")
                    + ". " + summary(sameType));
            synchronized (received) {
                if (received.size() == snap.size() && (snap.isEmpty() || received.peekLast() == snap.get(snap.size() - 1))) {
                    try {
                        received.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(Math.min(left, 50_000_000L))));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
    }

    /** 실패 메시지용: 그 종류 받은 수, 마지막 10개(각각 300자까지), 풀지 못한 패킷 수. */
    private String summary(List<? extends org.domi.furnace.api.packet.Packet> sameType) {
        StringBuilder b = new StringBuilder("받은 그 종류 ").append(sameType.size()).append("개");
        if (!sameType.isEmpty()) {
            b.append(sameType.size() > 10 ? ", 마지막 10개: [" : ": [");
            List<? extends org.domi.furnace.api.packet.Packet> tail = sameType.subList(Math.max(0, sameType.size() - 10), sameType.size());
            for (int i = 0; i < tail.size(); i++) {
                String one = String.valueOf(tail.get(i));
                b.append(i == 0 ? "" : ", ").append(one.length() > 300 ? one.substring(0, 300) + "…" : one);
            }
            b.append(']');
        }
        long bad = decodeFailures.get();
        if (bad > 0) b.append(". 풀지 못한 패킷 ").append(bad).append("개");
        return b.toString();
    }

    /** 읽을 수 있는 종류인데 풀지 못해 모으지 못한 패킷 수. */
    public long decodeFailures() { return decodeFailures.get(); }

    /**
     * 받은 패킷 장벽: 서버가 지금까지 이 플레이어 연결에 쓴 패킷을 읽기 스레드가 모두 읽을 때까지 기다린다(서버가 ping 을 보내고 그것이 오면 끝).
     * 서버 쪽 처리(명령·틱)를 기다리지는 않는다 — 그건 sync()·tick().
     */
    public void flushReceived() {
        if (pingId < 0) return;
        var p = connected ? server.internals().playerByName(name) : null;
        if (p == null) {   // 끊겼거나 서버 목록에서 빠졌다(킥 직후 등) — 장벽을 보낼 곳이 없으니 읽기 스레드가 연결 끝까지 다 읽기를 기다린다
            awaitReaderEnd();
            return;
        }
        long n = pingSent.incrementAndGet();
        byte[] body = java.nio.ByteBuffer.allocate(4).putInt(pingBase + (int) n).array();
        p.send(new org.domi.furnace.server.net.protocol.RawPacket(pingId, body, null, true));   // 리스너를 거치지 않는다
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.timeoutMillis());
        while (pingSeen.get() < n) {
            if (!connected) {   // 장벽을 보낸 뒤 끊겼다 — 남은 것을 다 읽을 때까지
                awaitReaderEnd();
                return;
            }
            if (System.nanoTime() - deadline > 0) throw new IllegalStateException(name + ": 받은 패킷 장벽이 돌아오지 않았다");
            java.util.concurrent.locks.LockSupport.parkNanos(200_000L);
        }
    }

    /** 읽기 스레드가 끝날 때까지(연결 끝 — EOF·disconnect) 기다린다. 시간 제한을 넘기면 그대로 돌아간다. */
    private void awaitReaderEnd() {
        Thread r = reader;
        if (r == null || r == Thread.currentThread()) return;
        try {
            r.join(server.timeoutMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ 나가기

    /** 연결을 닫고 서버가 접속자 목록에서 뺄 때까지(틱 없이) 기다린다. */
    @Override
    public void close() {
        closeQuietly();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(server.timeoutMillis());
        while (server.internals().playerByName(name) != null && System.nanoTime() - deadline < 0) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    void closeQuietly() {
        connected = false;
        if (disconnectReason == null) disconnectReason = "closed";
        try {
            socket.close();
        } catch (IOException ignored) { }
        for (EventSubscription s : subs) {
            try {
                s.unsubscribe();
            } catch (RuntimeException ignored) { }
        }
        subs.clear();
        server.playerLeft(this);
    }

    // ------------------------------------------------------------------ 틀(압축 없음 — 시험 서버는 압축을 끈다)

    private void write(int id, Consumer<ByteBuf> body) throws IOException {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        ByteBuf f = Unpooled.buffer();
        Types.writeVarInt(f, b.readableBytes());
        f.writeBytes(b);
        byte[] bytes = new byte[f.readableBytes()];
        f.readBytes(bytes);
        synchronized (out) {
            out.write(bytes);
            out.flush();
        }
    }

    private ByteBuf read() throws IOException {
        int len = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            len |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        byte[] data = new byte[len];
        in.readFully(data);
        return Unpooled.wrappedBuffer(data);
    }

    /** id 인 패킷을 받을 때까지 읽는다. disconnectId 가 오면 끊긴 것으로 보고 던진다. */
    private ByteBuf await(int id, int disconnectId) throws IOException {
        while (true) {
            ByteBuf f = read();
            int got = Types.readVarInt(f);
            if (got == id) return f.slice();
            if (got == disconnectId) throw new IOException("서버가 접속을 거절했다(패킷 0x" + Integer.toHexString(got) + ")");
        }
    }
}
