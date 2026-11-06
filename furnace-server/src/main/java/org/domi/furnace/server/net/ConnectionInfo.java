// 플러그인이 보는 연결(api PlayerConnection) — ConnectionHandler 하나에 하나. 값은 연결 스레드가 채우고 어느 스레드에서나 읽는다.
package org.domi.furnace.server.net;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.api.net.ConnectionPhase;
import org.domi.furnace.api.net.PlayerConnection;
import org.domi.furnace.api.net.QuitReason;
import org.domi.furnace.api.net.ResourcePack;
import org.domi.furnace.api.net.ResourcePackStatus;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.ConnectionState;

final class ConnectionInfo implements PlayerConnection {

    private static final AtomicLong IDS = new AtomicLong();

    private final long id = IDS.incrementAndGet();
    private final ConnectionHandler handler;
    volatile String hostname;
    volatile int hostPort;
    volatile int protocolVersion = -1;
    volatile boolean transferred;
    volatile String name;
    volatile UUID uniqueId;

    ConnectionInfo(ConnectionHandler handler) { this.handler = handler; }

    /** 핸드셰이크 주소 칸 → 가상 호스트: 첫 NUL 앞(FML 표식·BungeeCord 포워딩 칸 제거), 끝의 점 제거. */
    static String hostnameOf(String serverAddress) {
        if (serverAddress == null) return null;
        int nul = serverAddress.indexOf('\0');
        String h = nul >= 0 ? serverAddress.substring(0, nul) : serverAddress;
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    @Override
    public long id() { return id; }

    @Override
    public ConnectionPhase phase() {
        if (!handler.isChannelOpen()) return ConnectionPhase.CLOSED;
        return switch (handler.connectionState()) {
            case HANDSHAKE -> ConnectionPhase.HANDSHAKE;
            case STATUS -> ConnectionPhase.STATUS;
            case LOGIN -> ConnectionPhase.LOGIN;
            case CONFIGURATION -> ConnectionPhase.CONFIGURATION;
            case PLAY -> ConnectionPhase.PLAY;
        };
    }

    @Override
    public boolean isConnected() { return handler.isChannelOpen(); }

    @Override
    public InetSocketAddress address() { return handler.apiAddress(); }

    @Override
    public String hostname() { return hostname; }

    @Override
    public int hostPort() { return hostPort; }

    @Override
    public int protocolVersion() { return protocolVersion; }

    @Override
    public boolean isTransferred() { return transferred; }

    @Override
    public String name() { return name; }

    @Override
    public UUID uniqueId() { return uniqueId; }

    @Override
    public String clientBrand() { return handler.clientBrandValue(); }

    @Override
    public String locale() { return handler.clientLocaleValue(); }

    @Override
    public void disconnect(Text reason) { handler.disconnectFromApi(reason == null ? Text.empty() : reason); }

    @Override
    public QuitReason quitReason() { return handler.quitReasonOrNull(); }

    @Override
    public CompletableFuture<ResourcePackStatus> sendResourcePack(ResourcePack pack) { return handler.apiSendResourcePack(pack); }

    @Override
    public void removeResourcePack(UUID id) { handler.apiRemoveResourcePack(java.util.Objects.requireNonNull(id, "id")); }

    @Override
    public void removeAllResourcePacks() { handler.apiRemoveResourcePack(null); }

    @Override
    public CompletableFuture<byte[]> requestCookie(String key) { return handler.apiRequestCookie(key); }

    @Override
    public void storeCookie(String key, byte[] value) { handler.apiStoreCookie(key, value); }

    @Override
    public void transfer(String host, int port) { handler.apiTransfer(host, port); }

    @Override
    public String toString() { return "PlayerConnection#" + id + (name != null ? "(" + name + ")" : ""); }
}
