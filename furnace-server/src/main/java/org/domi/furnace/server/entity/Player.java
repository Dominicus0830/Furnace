// 플레이어 엔티티 상태(region 스레드 소유). 채널로 패킷 전송(eventLoop 마샬).
package org.domi.furnace.server.entity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.RegionGrid;

public final class Player {

    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final Channel channel;

    private volatile double x, y, z; // volatile — region 외 스레드(청크 전송 완료 콜백)서 chunkPos 읽음
    private float yaw, pitch;
    private boolean onGround;

    private ChunkPos lastCenter;                 // 마지막으로 SetCenterChunk 보낸 청크(null=미전송)
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    private final Set<ChunkPos> pendingChunks = new LinkedHashSet<>(); // 시야 진입했으나 아직 전송 안 한 청크(순서 보존). region 스레드 전용.
    private final Map<Integer, double[]> tracked = new HashMap<>(); // 관찰중 엔티티 → 마지막 전송 {x,y,z}
    private final Map<Integer, Integer> missingTicks = new HashMap<>(); // 추적중인데 후보서 사라진 틱 수(grace despawn)
    // 이 플레이어의 탭 목록에 올려둔 엔티티 → uuid. 시야 밖으로 나가 엔티티가 디스폰돼도 접속 중이면 탭엔 남기고,
    // 후보에서 완전히 사라졌을 때(퇴장 등)만 PlayerInfoRemove로 뺀다. region 스레드 전용.
    private final Map<Integer, UUID> listed = new HashMap<>();

    private volatile double[] pending;            // {x,y,z,yaw,pitch,onGround} IO→region 발행
    private volatile boolean disconnected;
    private volatile RegionPos currentRegion;     // 현재 소유 region(adopt가 설정)
    // 유효 view distance = min(서버 view-distance, 클라 요청). ClientInformation(config 0x00/play 0x0A)서 갱신.
    // 게임중 클라가 렌더거리 바꾸면 0x0A로 다시 옴 → 갱신. onEnterPlay서 초기 설정.
    private volatile int viewDistance = 10;
    // 열어둔 체스트 세션(M4c-3 슬라이스 B) — netty 스레드가 쓰고 region 스레드(breakBlock 강제닫기)가 읽음.
    private volatile int openContainerId = -1;
    private volatile int openChestX, openChestY, openChestZ;

    public Player(int entityId, UUID uuid, String name, Channel channel) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.name = name;
        this.channel = channel;
    }

    public void applyMove(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public void applyRotation(float yaw, float pitch, boolean onGround) {
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public ChunkPos chunkPos() {
        return ChunkPos.ofBlock(x, z);
    }

    /** IO 스레드: 들어온 위치를 발행(단일 writer). */
    public void publishPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.pending = new double[]{x, y, z, yaw, pitch, onGround ? 1 : 0};
    }

    /** region 스레드: 발행된 위치를 권위 필드에 적용(단일 reader). 변경 있으면 true. */
    public boolean drainPending() {
        double[] p = this.pending;
        if (p == null) return false;
        this.pending = null;
        applyMove(p[0], p[1], p[2], (float) p[3], (float) p[4], p[5] != 0);
        return true;
    }

    public RegionPos regionPos(int regionSize) {
        return RegionGrid.regionPos(x, z, regionSize);
    }

    public void markDisconnected() { this.disconnected = true; }
    public boolean isDisconnected() { return disconnected; }
    public int viewDistance() { return viewDistance; }
    public void viewDistance(int vd) { this.viewDistance = Math.max(Config.MIN_VIEW_DISTANCE, vd); }
    public RegionPos currentRegion() { return currentRegion; }
    public void currentRegion(RegionPos r) { this.currentRegion = r; }

    /** 패킷을 이 플레이어 채널로 전송(IO eventLoop 마샬). region 스레드서 호출 안전. */
    public void send(Packet packet) {
        channel.eventLoop().execute(() -> {
            ByteBuf buf = channel.alloc().buffer();
            Types.writeVarInt(buf, packet.id());
            packet.write(buf);
            channel.writeAndFlush(buf);
        });
    }

    /** 사유 패킷을 보낸 뒤 연결을 닫는다(중복 로그인 킥 등). 어느 스레드에서든 안전. */
    public void kick(Packet packet) {
        channel.eventLoop().execute(() -> {
            ByteBuf buf = channel.alloc().buffer();
            Types.writeVarInt(buf, packet.id());
            packet.write(buf);
            channel.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        });
    }

    public int entityId() { return entityId; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }

    // 세션 서버가 준 프로필 속성(textures 등) — 탭 목록 add_player 로 다른 클라에 전달돼 스킨이 보인다.
    private volatile java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties = java.util.List.of();

    public java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties() { return profileProperties; }

    // 현재 게임 모드. 지금은 접속 시 크리에이티브로 들어간다(LoginPlay·탭 목록과 같은 값) — 2-7 에서 server.properties·저장값을 따른다.
    private volatile org.domi.furnace.server.world.GameMode gameMode = org.domi.furnace.server.world.GameMode.CREATIVE;

    public org.domi.furnace.server.world.GameMode gameMode() { return gameMode; }

    public void gameMode(org.domi.furnace.server.world.GameMode m) { this.gameMode = m; }

    // 실제 접속 주소(Velocity 포워딩이면 프록시가 알려 준 주소).
    private volatile java.net.SocketAddress remoteAddress;

    public void remoteAddress(java.net.SocketAddress a) { this.remoteAddress = a; }

    public java.net.SocketAddress remoteAddress() {
        java.net.SocketAddress a = remoteAddress;
        return a != null ? a : channel.remoteAddress();
    }

    /** 바닐라 ServerPlayer.getIpAddress — 포트 없는 IP 문자열. */
    public String ipAddress() {
        java.net.SocketAddress a = remoteAddress();
        if (a instanceof java.net.InetSocketAddress isa && isa.getAddress() != null) return isa.getAddress().getHostAddress();
        return "<unknown>";
    }

    // 클라이언트 설정 allowServerListings — 거부하면 상태 응답 sample 에 익명으로 나온다.
    private volatile boolean allowsListing = true;

    public boolean allowsListing() { return allowsListing; }

    public void allowsListing(boolean v) { this.allowsListing = v; }

    public void profileProperties(java.util.List<org.domi.furnace.server.players.GameProfile.Property> props) {
        this.profileProperties = java.util.List.copyOf(props);
    }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean onGround() { return onGround; }
    private ChunkPos queuedCenter;               // 시야 청크를 대기열에 올린 마지막 중심(null=아직 안 함)
    public ChunkPos lastCenter() { return lastCenter; }
    public ChunkPos queuedCenter() { return queuedCenter; }
    public void queuedCenter(ChunkPos c) { this.queuedCenter = c; }
    public void lastCenter(ChunkPos c) { this.lastCenter = c; }
    public Set<ChunkPos> loadedChunks() { return loadedChunks; }
    public Set<ChunkPos> pendingChunks() { return pendingChunks; }
    public Map<Integer, double[]> tracked() { return tracked; }
    public Map<Integer, Integer> missingTicks() { return missingTicks; }
    public Map<Integer, UUID> listed() { return listed; }

    private volatile int latencyMs;
    /** 탭 목록에 표시되는 핑(ms). KeepAlive 왕복으로 갱신. */
    public int latencyMs() { return latencyMs; }
    /** 바닐라와 같은 평활화: (이전*3 + 새값)/4. 첫 측정은 그대로. */
    public void recordLatency(int sampleMs) {
        int old = latencyMs;
        latencyMs = old == 0 ? sampleMs : (old * 3 + sampleMs) / 4;
    }

    /** 체스트 열기 기록(containerId + 좌표). netty 스레드 전용 writer. */
    public void openChest(int containerId, int x, int y, int z) {
        this.openChestX = x; this.openChestY = y; this.openChestZ = z;
        this.openContainerId = containerId;
    }

    public void closeChest() { this.openContainerId = -1; }

    public int openContainerId() { return openContainerId; }

    /** 열려있으면 {x,y,z}, 아니면 null. */
    public int[] openChestPos() {
        return openContainerId == -1 ? null : new int[]{openChestX, openChestY, openChestZ};
    }
}
