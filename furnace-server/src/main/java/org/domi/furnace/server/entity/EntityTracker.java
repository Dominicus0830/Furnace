// 바닐라 ChunkMap.TrackedEntity + ServerEntity: 누가 이 엔티티를 보는지(seenBy)와 보는 사람에게 보낼 변화.
// 보임 판정은 엔티티가 구역을 옮기거나 플레이어가 움직일 때(이동 패킷마다)·처음 넣을 때만 다시 한다. 변화는 갱신 주기(종류별)마다,
// 또는 속도 변화(hasImpulse)·데이터 변화 때 위치(1/4096 델타 → 넘치거나 400 번째·땅 닿음 변화면 순간이동)·속도·데이터·머리를 보낸다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.AddEntity;
import org.domi.furnace.server.net.protocol.play.BundleDelimiter;
import org.domi.furnace.server.net.protocol.play.MoveEntity;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.RotateHead;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetEntityMotion;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;

public final class EntityTracker {

    /** 보는 플레이어 쪽에서 필요한 것: 시야 거리(청크)·그 청크가 보내졌는지. */
    public interface Viewers {
        int viewDistance(Player p);

        boolean chunkTracked(Player p, int chunkX, int chunkZ);
    }

    private final Entity entity;
    private final int range;
    private final int updateInterval;
    private final boolean trackDelta;
    /** 보는 플레이어 → 이 region 에 없는 채로 지난 틱 수(넘겨진 플레이어를 잠시 기다린다). */
    private final Map<Player, Integer> seenBy = new LinkedHashMap<>();
    public long lastSectionKey;
    // ServerEntity
    private double baseX, baseY, baseZ;
    private double lastVx, lastVy, lastVz;
    private int yRotp, xRotp, yHeadRotp;
    private int tickCount, teleportDelay;
    private boolean wasOnGround;
    private List<SetEntityData.Item> trackedDataValues;
    /** ServerEntity.lastPassengers·wasRiding. */
    private List<Rider> lastPassengers = List.of();
    private boolean wasRiding;

    /** range: 바닐라 getEffectiveRange(블록, 방송 거리 비율 적용). */
    public EntityTracker(Entity entity, int range) {
        this.entity = entity;
        this.range = range;
        this.updateInterval = entity.type().updateInterval();
        this.trackDelta = entity.type().trackDeltas();
        this.lastSectionKey = entity.sectionKey();
        this.baseX = entity.x();
        this.baseY = entity.y();
        this.baseZ = entity.z();
        this.lastVx = entity.vx();
        this.lastVy = entity.vy();
        this.lastVz = entity.vz();
        this.yRotp = angle(entity.yRot());
        this.xRotp = angle(entity.xRot());
        this.yHeadRotp = angle(entity.yHeadRot());
        this.wasOnGround = entity.onGround();
        this.trackedDataValues = entity.entityData().nonDefaultValues();
    }

    /** Mth.floor(deg * 256 / 360). */
    static int angle(float deg) { return Entity.floor(deg * 256.0F / 360.0F); }

    public Entity entity() { return entity; }

    public boolean isSeenBy(Player p) { return seenBy.containsKey(p); }

    public int seenByCount() { return seenBy.size(); }

    // ------------------------------------------------------------------ 보임(TrackedEntity.updatePlayer)

    public void updatePlayers(Iterable<Player> players, Viewers v) {
        for (Player p : players) updatePlayer(p, v);
    }

    public void updatePlayer(Player p, Viewers v) {
        if (p.isDisconnected()) {
            seenBy.remove(p);
            return;
        }
        double dx = p.x() - entity.x(), dz = p.z() - entity.z();
        double r = Math.min(range, v.viewDistance(p) * 16);
        boolean visible = dx * dx + dz * dz <= r * r && entity.broadcastToPlayer(p) && v.chunkTracked(p, entity.chunkX(), entity.chunkZ());
        if (visible) {
            if (!seenBy.containsKey(p)) {
                seenBy.put(p, 0);
                addPairing(p);
            } else {
                seenBy.put(p, 0);
            }
        } else if (seenBy.remove(p) != null) {
            removePairing(p);
        }
    }

    /**
     * region 이 매 틱: 이 region 에 없는 보는 사람(다른 region 으로 넘겨졌거나 나감)을 grace 틱 뒤 뺀다. 나간 사람에게는 보내지 않는다.
     */
    public void dropAbsent(java.util.function.Predicate<Player> present, int graceTicks) {
        for (Iterator<Map.Entry<Player, Integer>> it = seenBy.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = e.getKey();
            if (p.isDisconnected()) {
                it.remove();
            } else if (present.test(p)) {
                e.setValue(0);
            } else if (e.getValue() + 1 >= graceTicks) {
                it.remove();
                p.send(new RemoveEntities(List.of(entity.id())));
                entity.onStopSeenBy(p);
            } else {
                e.setValue(e.getValue() + 1);
            }
        }
    }

    /** 엔티티가 없어졌다(broadcastRemoved) — 보는 사람 모두에게 지운다. */
    public void broadcastRemoved() {
        for (Player p : seenBy.keySet()) {
            if (p.isDisconnected()) continue;
            p.send(new RemoveEntities(List.of(entity.id())));
            entity.onStopSeenBy(p);
        }
        seenBy.clear();
    }

    public void removePlayer(Player p) {
        if (seenBy.remove(p) != null) removePairing(p);
    }

    /** 패킷 없이 보는 사람에서 뺀다(차원 이동). */
    public void forgetPlayer(Player p) {
        if (seenBy.remove(p) != null) entity.onStopSeenBy(p);
    }

    /** addPairing: 스폰 묶음(add_entity, 데이터, 속도). */
    private void addPairing(Player p) {
        List<Packet> list = new ArrayList<>(4);
        Packet custom = entity.customSpawnPacket(baseX, baseY, baseZ);
        list.add(custom != null ? custom : new AddEntity(entity.id(), entity.uuid(), entity.type().id(), baseX, baseY, baseZ, xRotp, yRotp, yHeadRotp,
                entity.spawnData(), lastVx, lastVy, lastVz));
        if (trackedDataValues != null) list.add(new SetEntityData(entity.id(), trackedDataValues));
        if (entity instanceof LivingEntity le) {
            var syncable = le.attributes().syncable();
            if (!syncable.isEmpty()) list.add(new org.domi.furnace.server.net.protocol.play.UpdateAttributes(entity.id(), syncable));
        }
        if (trackDelta && !(entity instanceof LivingEntity)) list.add(new SetEntityMotion(entity.id(), lastVx, lastVy, lastVz));   // 살아 있는 것이 아니면
        if (entity instanceof LivingEntity le) {
            java.util.List<org.domi.furnace.server.net.protocol.play.SetEquipment.Entry> eq = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack st = le.getItemBySlot(slot);
                if (!st.isEmpty()) eq.add(new org.domi.furnace.server.net.protocol.play.SetEquipment.Entry(slot, st));
            }
            if (!eq.isEmpty()) list.add(new org.domi.furnace.server.net.protocol.play.SetEquipment(entity.id(), eq));
        }
        if (entity.isVehicle()) list.add(org.domi.furnace.server.net.protocol.play.SetPassengers.of(entity));
        if (entity.isPassenger()) list.add(org.domi.furnace.server.net.protocol.play.SetPassengers.of(entity.vehicle()));
        p.send(BundleDelimiter.INSTANCE);
        for (Packet pkt : list) p.send(pkt);
        p.send(BundleDelimiter.INSTANCE);
        entity.onStartSeenBy(p);
    }

    private void removePairing(Player p) {
        p.send(new RemoveEntities(List.of(entity.id())));
        entity.onStopSeenBy(p);
    }

    /** 보는 사람 모두에게(broadcast). */
    public void broadcast(Packet pkt) {
        for (Player p : seenBy.keySet()) p.send(pkt);
    }

    // ------------------------------------------------------------------ 변화(ServerEntity.sendChanges)

    public void sendChanges() {
        if (entity instanceof ItemFrame frame && tickCount % 10 == 0) {   // 액자 속 지도: 보는 사람마다 표식·갱신 패킷
            org.domi.furnace.server.map.MapItems.tickFrame(frame, seenBy.keySet(), entity.level().dayTime());
            sendDirtyEntityData();
        }
        List<Rider> riders = entity.passengers();
        if (riders != lastPassengers && !riders.equals(lastPassengers)) {
            broadcast(org.domi.furnace.server.net.protocol.play.SetPassengers.of(entity));
            for (Rider r : lastPassengers) {   // 내린 플레이어는 연결이 지금 자리로 다시 맞춘다
                if (r instanceof Player p && !riders.contains(p)) p.teleportTo(p.x(), p.y(), p.z());
            }
        }
        lastPassengers = riders;
        if (tickCount % updateInterval == 0 || entity.hasImpulse || entity.entityData().isDirty()) {
            if (entity.isPassenger()) {   // 탄 것은 회전만 보낸다(위치는 클라이언트가 탈것에 맞춘다)
                int yr = angle(entity.yRot()), xr = angle(entity.xRot());
                if (Math.abs(yr - yRotp) >= 1 || Math.abs(xr - xRotp) >= 1) {
                    broadcast(MoveEntity.rot(entity.id(), (byte) yr, (byte) xr, entity.onGround()));
                    yRotp = yr;
                    xRotp = xr;
                }
                baseX = entity.x();
                baseY = entity.y();
                baseZ = entity.z();
                sendDirtyEntityData();
                wasRiding = true;
                sendHeadRotation();
                entity.hasImpulse = false;
                tickCount++;
                sendHurtMarked();
                return;
            }
            teleportDelay++;
            int yr = angle(entity.yRot()), xr = angle(entity.xRot());
            double px = entity.x(), py = entity.y(), pz = entity.z();
            double ddx = px - baseX, ddy = py - baseY, ddz = pz - baseZ;
            boolean moved = ddx * ddx + ddy * ddy + ddz * ddz >= 7.6293945E-6F;
            Packet pkt = null;
            boolean sendPos = moved || tickCount % 60 == 0;
            boolean sendRot = Math.abs(yr - yRotp) >= 1 || Math.abs(xr - xRotp) >= 1;
            boolean posSent = false, rotSent = false;
            long ex = encode(px) - encode(baseX), ey = encode(py) - encode(baseY), ez = encode(pz) - encode(baseZ);
            boolean overflow = ex < -32768L || ex > 32767L || ey < -32768L || ey > 32767L || ez < -32768L || ez > 32767L;
            if (overflow || teleportDelay > 400 || wasRiding || wasOnGround != entity.onGround()) {
                wasOnGround = entity.onGround();
                teleportDelay = 0;
                pkt = new TeleportEntity(entity.id(), px, py, pz, entity.yRot(), entity.xRot(), entity.onGround());
                posSent = rotSent = true;
            } else if (!sendPos || !sendRot) {
                if (sendPos) {
                    pkt = MoveEntity.pos(entity.id(), (short) ex, (short) ey, (short) ez, entity.onGround());
                    posSent = true;
                } else if (sendRot) {
                    pkt = MoveEntity.rot(entity.id(), (byte) yr, (byte) xr, entity.onGround());
                    rotSent = true;
                }
            } else {
                pkt = MoveEntity.posRot(entity.id(), (short) ex, (short) ey, (short) ez, (byte) yr, (byte) xr, entity.onGround());
                posSent = rotSent = true;
            }
            if ((trackDelta || entity.hasImpulse) && tickCount > 0) {
                double vx = entity.vx(), vy = entity.vy(), vz = entity.vz();
                double mx = vx - lastVx, my = vy - lastVy, mz = vz - lastVz;
                double d = mx * mx + my * my + mz * mz;
                if (d > 1.0E-7 || d > 0.0 && vx * vx + vy * vy + vz * vz == 0.0) {
                    lastVx = vx;
                    lastVy = vy;
                    lastVz = vz;
                    broadcast(new SetEntityMotion(entity.id(), vx, vy, vz));
                }
            }
            if (pkt != null) broadcast(pkt);
            sendDirtyEntityData();
            if (posSent) {
                baseX = px;
                baseY = py;
                baseZ = pz;
            }
            if (rotSent) {
                yRotp = yr;
                xRotp = xr;
            }
            wasRiding = false;
            sendHeadRotation();
            entity.hasImpulse = false;
        }
        tickCount++;
        sendHurtMarked();
    }

    private void sendHeadRotation() {
        int yh = angle(entity.yHeadRot());
        if (Math.abs(yh - yHeadRotp) >= 1) {
            broadcast(new RotateHead(entity.id(), (byte) yh));
            yHeadRotp = yh;
        }
    }

    private void sendHurtMarked() {
        if (entity.hurtMarked) {
            entity.hurtMarked = false;
            broadcast(new SetEntityMotion(entity.id(), entity.vx(), entity.vy(), entity.vz()));
        }
    }

    private void sendDirtyEntityData() {
        if (entity instanceof LivingEntity le) {
            var changed = le.attributes().takeDirty();
            if (!changed.isEmpty()) broadcast(new org.domi.furnace.server.net.protocol.play.UpdateAttributes(entity.id(), changed));
        }
        List<SetEntityData.Item> dirty = entity.entityData().packDirty();
        if (dirty != null) {
            trackedDataValues = entity.entityData().nonDefaultValues();
            broadcast(new SetEntityData(entity.id(), dirty));
        }
    }

    /** VecDeltaCodec.encode: Math.round(v * 4096). */
    static long encode(double v) { return Math.round(v * 4096.0); }
}
