// 연결마다 하나: 나가는 엔티티 패킷을 그 플레이어에게 보일 모습(변장)으로 바꾼다. 연결 이벤트 루프에서만 돈다(상태에 락이 없다).
// 스폰(add_entity·플레이어 스폰)을 지날 때 그때의 규칙으로 모습을 정해 엔티티 번호에 묶고, 지울 때(remove_entities·차원 이동) 푼다.
// 그 사이의 메타데이터·속성·장비 패킷은 묶인 모습 기준으로 거른다 — 규칙이 도중에 바뀌어도 클라이언트가 가진 엔티티와 어긋나지 않는다
// (규칙이 바뀌면 추적기가 지웠다 다시 스폰해 새 모습을 묶는다). 묶인 것이 없고 규칙도 없으면 패킷을 그대로 돌려준다.
package org.domi.furnace.server.entity.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.AddEntity;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunksBiomes;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SectionBlocksUpdate;
import org.domi.furnace.server.net.protocol.play.SetBorderCenter;
import org.domi.furnace.server.net.protocol.play.SetBorderLerpSize;
import org.domi.furnace.server.net.protocol.play.SetBorderSize;
import org.domi.furnace.server.net.protocol.play.SetBorderWarningDelay;
import org.domi.furnace.server.net.protocol.play.SetBorderWarningDistance;
import org.domi.furnace.server.net.protocol.play.LoginPlay;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.Respawn;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetEquipment;
import org.domi.furnace.server.net.protocol.play.SetPlayerTeam;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.UpdateAttributes;
import org.domi.furnace.server.scores.ChatFormatting;

public final class ViewRewriter {

    /** 이름표를 숨기는 팀 이름 머리(뒤에 엔티티 번호 16진). */
    static final String TEAM_PREFIX = "fdis_";
    /** Player.DATA_PLAYER_MODE_CUSTOMISATION(1.21.1 칸 17, byte) — 스킨 겉 레이어 비트. */
    private static final int SKIN_PARTS = 17;

    /** 한 엔티티에 묶인 모습. prefix: 진짜 엔티티 메타데이터 중 넘길 앞쪽 칸 수. fakeUuid·team 은 가짜 프로필일 때만. */
    private record Applied(Appearance appearance, int prefix, UUID fakeUuid, String team, String profileName) {}

    private final Map<Integer, Applied> applied = new HashMap<>();
    private volatile String dimension;

    /** 클라이언트가 지금 있는 차원 키(아직 모르면 null). */
    public String dimension() { return dimension; }

    /** 시험용: 지금 묶인 엔티티 수. */
    public int appliedCount() { return applied.size(); }

    /**
     * 보낼 것: 같은 패킷(그대로), null(보내지 않음), 또는 Packet[](차례대로 보냄). 이벤트 루프에서만 부른다.
     */
    public Object rewrite(Player viewer, Packet p) {
        if (p instanceof Respawn r) return levelChanged(p, r.dimensionName());
        if (p instanceof LoginPlay l) return levelChanged(p, l.dimensionName());
        ViewLayers layers = viewer.viewLayers();
        if (layers.active()) {
            Packet q = layer(layers, p);
            if (q == null) return null;
            if (q != p) p = q;
            if (layers.equipment.active() && (p instanceof AddEntity || p instanceof SpawnEntity)) return withEquipment(layers, p, entities(viewer, p));
        }
        return entities(viewer, p);
    }

    // ------------------------------------------------------------------ 가상 블록·바이옴·보더·장비 층

    /** 층을 덧입힌 패킷(바뀌지 않으면 같은 것), 보내지 않을 것이면 null. */
    private Packet layer(ViewLayers layers, Packet p) {
        if (p instanceof LevelChunkWithLight c) return layers.blocks.active() ? chunk(layers.blocks, c) : p;
        if (p instanceof BlockUpdate b) return layers.blocks.active() ? blockUpdate(layers.blocks, b) : p;
        if (p instanceof SectionBlocksUpdate s) return layers.blocks.active() ? sectionUpdate(layers.blocks, s) : p;
        if (p instanceof ChunksBiomes b) return layers.blocks.active() ? biomes(layers.blocks, b) : p;
        if (p instanceof SetEquipment e) return layers.equipment.active() ? equipment(layers.equipment, e) : p;
        var border = layers.border();
        if (border != null) {
            // 개인 보더가 있는 사람에게는 서버 보더의 변경을 보내지 않는다. 접속·차원 이동 때 서버가 보내는 초기화는 개인 보더 상태로 바꿔 보낸다.
            // (개인 보더를 바꿀 때도 이 초기화 패킷을 보낸다 — PlayerViewsImpl.pushBorder)
            if (p instanceof InitializeBorder) { p.discard(); return InitializeBorder.of(layers.borderFor(dimension)); }
            if (p instanceof SetBorderCenter || p instanceof SetBorderSize || p instanceof SetBorderLerpSize
                    || p instanceof SetBorderWarningDelay || p instanceof SetBorderWarningDistance) return null;
        }
        return p;
    }

    private Packet chunk(BlockLayer layer, LevelChunkWithLight c) {
        String dim = dimension;
        if (dim == null) return c;
        BlockLayer.Dim d = layer.dim(dim);
        if (d == null) return c;
        BlockLayer.Chunk ch = layer.chunk(dim, chunkX(c), chunkZ(c));
        if (ch == null) return c;
        byte[] body = c.shared() != null ? c.shared() : bytesOf(c);
        byte[] out = ChunkBodyRewriter.rewrite(body, d, ch);
        if (out == null) return c;
        c.discard();
        return new LevelChunkWithLight(io.netty.buffer.Unpooled.wrappedBuffer(out), null);
    }

    private static byte[] bytesOf(LevelChunkWithLight c) {
        var b = c.body();
        byte[] out = new byte[b.readableBytes()];
        b.getBytes(b.readerIndex(), out);
        return out;
    }

    private static int chunkX(LevelChunkWithLight c) { return c.body().getInt(c.body().readerIndex()); }

    private static int chunkZ(LevelChunkWithLight c) { return c.body().getInt(c.body().readerIndex() + 4); }

    private Packet blockUpdate(BlockLayer layer, BlockUpdate b) {
        if (dimension == null) return b;
        BlockLayer.Entry e = layer.block(dimension, b.x(), b.y(), b.z());
        return e == null || e.value() == b.stateId() ? b : new BlockUpdate(b.x(), b.y(), b.z(), e.value());
    }

    private Packet sectionUpdate(BlockLayer layer, SectionBlocksUpdate s) {
        if (dimension == null) return s;
        int[] states = null;
        for (int i = 0; i < s.positions().length; i++) {
            int pos = s.positions()[i] & 0xFFF;
            BlockLayer.Entry e = layer.block(dimension, (s.sectionX() << 4) | ((pos >> 8) & 15), (s.sectionY() << 4) | (pos & 15), (s.sectionZ() << 4) | ((pos >> 4) & 15));
            if (e != null && e.value() != s.states()[i]) {
                if (states == null) states = s.states().clone();
                states[i] = e.value();
            }
        }
        return states == null ? s : new SectionBlocksUpdate(s.sectionX(), s.sectionY(), s.sectionZ(), s.positions(), states);
    }

    private Packet biomes(BlockLayer layer, ChunksBiomes b) {
        if (dimension == null) return b;
        BlockLayer.Dim d = layer.dim(dimension);
        if (d == null) return b;
        List<ChunksBiomes.Entry> out = null;
        for (int i = 0; i < b.chunks().size(); i++) {
            ChunksBiomes.Entry e = b.chunks().get(i);
            BlockLayer.Chunk ch = layer.chunk(dimension, e.chunkX(), e.chunkZ());
            ChunksBiomes.Entry ne = e;
            if (ch != null && !ch.biomes().isEmpty()) {
                int[][] secs = new int[e.sectionBiomes().length][];
                for (int s = 0; s < secs.length; s++) secs[s] = e.sectionBiomes()[s].clone();
                for (var en : ch.biomes().entrySet()) {
                    int k = en.getKey(), s = k >> 6;
                    if (s >= 0 && s < secs.length) secs[s][k & 63] = en.getValue().value();
                }
                ne = new ChunksBiomes.Entry(e.chunkX(), e.chunkZ(), secs);
            }
            if (ne != e && out == null) { out = new ArrayList<>(b.chunks()); }
            if (out != null) out.set(i, ne);
        }
        return out == null ? b : new ChunksBiomes(out, b.biomeDirectBits());
    }

    private Packet equipment(EquipmentLayer layer, SetEquipment e) {
        var o = layer.of(e.entityId());
        if (o == null) return e;
        List<SetEquipment.Entry> out = null;
        for (int i = 0; i < e.slots().size(); i++) {
            SetEquipment.Entry en = e.slots().get(i);
            var ov = o.get(en.slot());
            if (ov == null) continue;
            if (out == null) out = new ArrayList<>(e.slots());
            out.set(i, new SetEquipment.Entry(en.slot(), ov.stack()));
        }
        return out == null ? e : new SetEquipment(e.entityId(), out);
    }

    /** 스폰 패킷 뒤에 덮어쓴 장비를 붙인다(서버가 빈 장비라 따로 보내지 않아도 보이게). 서버의 장비 패킷이 뒤따르면 같은 값으로 한 번 더 간다. */
    private Object withEquipment(ViewLayers layers, Packet spawn, Object rewritten) {
        int eid = spawn instanceof AddEntity a ? a.entityId() : ((SpawnEntity) spawn).entityId();
        var o = layers.equipment.of(eid);
        if (o == null || rewritten == null) return rewritten;
        Applied ap = applied.get(eid);
        if (ap != null && !EntityKinds.isLiving(ap.appearance.kind())) return rewritten;   // 살아 있지 않은 모습엔 장비를 보내지 않는다
        List<SetEquipment.Entry> es = new ArrayList<>(o.size());
        for (var en : o.entrySet()) es.add(new SetEquipment.Entry(en.getKey(), en.getValue().stack()));
        Packet extra = new SetEquipment(eid, es);
        if (rewritten instanceof Packet[] many) {
            Packet[] out = java.util.Arrays.copyOf(many, many.length + 1);
            out[many.length] = extra;
            return out;
        }
        return new Packet[] {(Packet) rewritten, extra};
    }

    // ------------------------------------------------------------------ 엔티티 변장

    private Object entities(Player viewer, Packet p) {
        boolean any = !applied.isEmpty();
        if (!any && !EntityViewRules.active()) return p;
        if (p instanceof AddEntity a) return spawn(viewer, p, a.entityId(), a.uuid(), a.type(), a);
        if (p instanceof SpawnEntity s) return spawn(viewer, p, s.entityId(), s.uuid(), EntityKinds.PLAYER_TYPE_ID, null);
        if (!any) return p;
        if (p instanceof SetEntityData d) return entityData(d);
        if (p instanceof RemoveEntities r) return removed(r);
        if (p instanceof UpdateAttributes u) return livingOnly(p, u.entityId());
        if (p instanceof SetEquipment e) return livingOnly(p, e.entityId());
        return p;
    }

    // ------------------------------------------------------------------ 스폰

    private Object spawn(Player viewer, Packet original, int eid, UUID uuid, int realTypeId, AddEntity add) {
        List<Packet> out = null;
        Applied old = applied.remove(eid);
        if (old != null) out = cleanup(old, eid, new ArrayList<>(4));   // 지우지 않고 다시 스폰(같은 번호) — 옛 가짜 프로필을 먼저 걷는다
        Appearance a = EntityViewRules.appearanceFor(viewer, uuid);
        if (a == null) {
            if (out == null) return original;
            out.add(original);
            return out.toArray(new Packet[0]);
        }
        if (out == null) out = new ArrayList<>(4);
        int realKind = EntityKinds.kindOf(realTypeId);
        int prefix = EntityKinds.sharedPrefix(realTypeId, realKind, a.typeId(), a.kind());
        UUID shownUuid = uuid;
        String team = null, profileName = null;
        Appearance.Profile prof = a.profile();
        if (prof != null) {
            // 같은 uuid 면 클라이언트가 진짜 플레이어의 프로필(스킨)을 쓴다 — 변장마다 새 uuid
            shownUuid = UUID.randomUUID();
            profileName = prof.name();
            out.add(prof.infoAdd().apply(shownUuid));
            if (!prof.nameTagVisible()) {
                team = TEAM_PREFIX + Integer.toHexString(eid);
                var params = new SetPlayerTeam.Parameters(org.domi.furnace.api.text.Text.empty(), 0, "never", "never", ChatFormatting.RESET,
                        org.domi.furnace.api.text.Text.empty(), org.domi.furnace.api.text.Text.empty());
                out.add(new SetPlayerTeam(team, SetPlayerTeam.METHOD_ADD, params, List.of(prof.name())));
            }
        }
        if (add != null) {
            out.add(new AddEntity(eid, shownUuid, a.typeId(), add.x(), add.y(), add.z(), add.xRot(), add.yRot(), add.yHeadRot(), a.spawnData(),
                    add.vx(), add.vy(), add.vz()));
        } else {
            SpawnEntity s = (SpawnEntity) original;
            int pitch = angle(s.pitch()), yaw = angle(s.yaw());
            out.add(new AddEntity(eid, shownUuid, a.typeId(), s.x(), s.y(), s.z(), pitch, yaw, yaw, a.spawnData(), 0, 0, 0));
        }
        if (prof != null && realKind != EntityKinds.PLAYER && !a.overrides(SKIN_PARTS)) {
            // 진짜가 플레이어가 아니면 겉 레이어(모자·소매 …) 칸이 기본 0 이라 스킨이 반쯤 빈다 — 전부 켠다(바닐라 클라이언트 기본 설정과 같게)
            out.add(new SetEntityData(eid, List.of(new SetEntityData.Item(SKIN_PARTS, SetEntityData.SERIALIZER_BYTE, (byte) 0x7F))));
        }
        if (a.metadata() != null) out.add(a.metadata().apply(eid));
        original.discard();
        applied.put(eid, new Applied(a, prefix, prof != null ? shownUuid : null, team, profileName));
        return out.toArray(new Packet[0]);
    }

    /** Mth.floor(deg * 256 / 360) — 바이트로 쓸 때 아래 8비트만 남는다. */
    private static int angle(float deg) { return (int) Math.floor(deg * 256.0F / 360.0F) & 0xFF; }

    // ------------------------------------------------------------------ 메타데이터·속성·장비

    private Object entityData(SetEntityData d) {
        Applied ap = applied.get(d.entityId());
        if (ap == null) return d;
        List<SetEntityData.Item> items = d.items();
        List<SetEntityData.Item> kept = null;
        for (int i = 0; i < items.size(); i++) {
            SetEntityData.Item it = items.get(i);
            boolean keep = it.index() < ap.prefix && !ap.appearance.overrides(it.index());
            if (keep) {
                if (kept != null) kept.add(it);
            } else if (kept == null) {
                kept = new ArrayList<>(items.size());
                kept.addAll(items.subList(0, i));
            }
        }
        if (kept == null) return d;
        if (kept.isEmpty()) return null;
        return new SetEntityData(d.entityId(), kept);
    }

    /** 살아 있지 않은 것으로 보이는 엔티티에 속성을 보내면 클라이언트가 끊는다(IllegalStateException) — 장비도 함께 뺀다. */
    private Object livingOnly(Packet p, int eid) {
        Applied ap = applied.get(eid);
        if (ap == null || EntityKinds.isLiving(ap.appearance.kind())) return p;
        p.discard();
        return null;
    }

    // ------------------------------------------------------------------ 지우기·차원

    private Object removed(RemoveEntities r) {
        List<Packet> extra = null;
        for (int eid : r.entityIds()) {
            Applied ap = applied.remove(eid);
            if (ap != null && (ap.fakeUuid != null)) extra = cleanup(ap, eid, extra == null ? new ArrayList<>(2) : extra);
        }
        if (extra == null) return r;
        extra.add(0, r);
        return extra.toArray(new Packet[0]);
    }

    /** 가짜 프로필(탭 정보)과 이름표 팀을 걷는다. */
    private static List<Packet> cleanup(Applied ap, int eid, List<Packet> out) {
        if (ap.fakeUuid != null) out.add(new PlayerInfoRemove(List.of(ap.fakeUuid)));
        if (ap.team != null) out.add(new SetPlayerTeam(ap.team, SetPlayerTeam.METHOD_REMOVE, null, List.of()));
        return out;
    }

    /** 다른 차원으로 가면 클라이언트가 엔티티를 모두 버린다(같은 차원 리스폰은 남긴다). 프로필 정보는 연결에 남으므로 걷는다. */
    private Object levelChanged(Packet p, String dim) {
        String before = dimension;
        dimension = dim;
        if (applied.isEmpty() || dim.equals(before)) return p;
        List<Packet> out = new ArrayList<>();
        out.add(p);
        for (var e : applied.entrySet()) cleanup(e.getValue(), e.getKey(), out);
        applied.clear();
        return out.size() == 1 ? p : out.toArray(new Packet[0]);
    }
}
