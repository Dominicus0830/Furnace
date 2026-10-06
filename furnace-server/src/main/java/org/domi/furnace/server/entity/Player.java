// 플레이어 엔티티 상태(region 스레드 소유). 채널로 패킷 전송(eventLoop 마샬).
package org.domi.furnace.server.entity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.RegionGrid;

public final class Player {

    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final Channel channel;

    private double x, y, z;
    private float yaw, pitch;
    private boolean onGround;

    private ChunkPos lastCenter;                 // 마지막으로 SetCenterChunk 보낸 청크(null=미전송)
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    private final Map<Integer, double[]> tracked = new HashMap<>(); // 관찰중 엔티티 → 마지막 전송 {x,y,z}
    private final Map<Integer, UUID> trackedUuids = new HashMap<>(); // 관찰중 엔티티 → uuid(디스폰 시 탭 제거용)

    private volatile double[] pending;            // {x,y,z,yaw,pitch,onGround} IO→region 발행
    private volatile boolean disconnected;
    private volatile RegionPos currentRegion;     // 현재 소유 region(adopt가 설정)

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

    public int entityId() { return entityId; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean onGround() { return onGround; }
    public ChunkPos lastCenter() { return lastCenter; }
    public void lastCenter(ChunkPos c) { this.lastCenter = c; }
    public Set<ChunkPos> loadedChunks() { return loadedChunks; }
    public Map<Integer, double[]> tracked() { return tracked; }
    public Map<Integer, UUID> trackedUuids() { return trackedUuids; }
}
