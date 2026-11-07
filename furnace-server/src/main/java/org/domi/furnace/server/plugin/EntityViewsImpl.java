// 플레이어별 보이기·숨기기·변장 API 구현. 규칙은 서버 EntityViewRules 에 두고, 바뀌면 그 엔티티를 가진 region 이 추적을 다시 하게 한다:
// 몹·물건은 엔티티를 따라가는 작업 대기열(EntityTasks)로 다음 틱에 refreshViews, 플레이어 대상은 region 이 매 틱 다시 보므로
// 탭 목록만 다시 훑게 한다(숨긴 플레이어는 탭에서도 뺀다). 변장 패킷 변환은 연결마다 ViewRewriter 가 한다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.entity.Disguise;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.EntityViews;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.entity.EntityTasks;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.view.Appearance;
import org.domi.furnace.server.entity.view.EntityViewRules;
import org.domi.furnace.server.entity.view.EntityViewRules.Change;

public final class EntityViewsImpl implements EntityViews {

    private final PluginHost host;

    EntityViewsImpl(PluginHost host) { this.host = host; }

    /** 대상: uuid, 따라가는 대기열(플레이어는 null), 플레이어인가. */
    private record Target(UUID uuid, EntityTasks tasks, org.domi.furnace.server.entity.Player player) {}

    private static Target target(Entity e) {
        Objects.requireNonNull(e, "target");
        if (e instanceof PlayerHandle ph) return new Target(ph.handle().uuid(), null, ph.handle());
        if (e instanceof EntityHandle eh) return new Target(eh.uniqueId(), eh.tasks(), null);
        throw new IllegalArgumentException("이 서버의 엔티티가 아니다: " + e);
    }

    private static org.domi.furnace.server.entity.Player viewer(Player p) {
        Objects.requireNonNull(p, "viewer");
        if (p instanceof PlayerHandle ph) return ph.handle();
        throw new IllegalArgumentException("이 서버의 플레이어가 아니다: " + p);
    }

    /** 규칙이 바뀐 뒤: 그 엔티티를 가진 region 에서 viewer(null = 모두)에 대해 다시 추적, respawn 이면 지웠다 다시 스폰. */
    private static void refresh(Change c, org.domi.furnace.server.entity.Player viewer, boolean respawn) {
        if (!c.changed()) return;
        if (c.player()) {
            org.domi.furnace.server.entity.Player.bumpRoster();   // 탭 목록을 다시 훑는다(숨김·보임). 엔티티·변장은 region 이 매 틱 본다
            return;
        }
        EntityTasks t = c.tasks();
        if (t == null) return;   // 그 엔티티가 지금 올라와 있지 않다 — 올라올 때 추적기가 규칙을 읽는다
        t.schedule(() -> {
            if (t.owner() instanceof org.domi.furnace.server.entity.Entity e && e.level() != null) e.level().refreshViews(e, viewer, respawn);
        }, null, 0, 0);
    }

    // ------------------------------------------------------------------ 보이기·숨기기

    @Override
    public void hide(FurnacePlugin plugin, Player viewer, Entity target) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        Target t = target(target);
        if (t.uuid.equals(v.uuid())) return;
        refresh(EntityViewRules.hide(plugin, v, t.uuid, t.tasks, t.player != null), v, false);
    }

    @Override
    public void show(FurnacePlugin plugin, Player viewer, Entity target) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        Target t = target(target);
        if (t.uuid.equals(v.uuid())) return;
        refresh(EntityViewRules.show(plugin, v, t.uuid, t.tasks, t.player != null), v, false);
    }

    @Override
    public boolean canSee(Player viewer, UUID target) {
        var v = viewer(viewer);
        return target.equals(v.uuid()) || EntityViewRules.canSee(v, target);
    }

    @Override
    public void setVisibleByDefault(FurnacePlugin plugin, Entity target, boolean visible) {
        Objects.requireNonNull(plugin, "plugin");
        Target t = target(target);
        refresh(EntityViewRules.setHiddenByDefault(plugin, t.uuid, !visible, t.tasks, t.player != null), null, false);
    }

    @Override
    public boolean isVisibleByDefault(UUID target) { return !EntityViewRules.isHiddenByDefault(target); }

    // ------------------------------------------------------------------ 변장

    @Override
    public void disguise(FurnacePlugin plugin, Entity target, Disguise disguise) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(disguise, "disguise");
        Target t = target(target);
        Appearance a = disguise.isNone() ? null : appearance(disguise);   // 전역 none = 변장 지우기
        refresh(EntityViewRules.setAppearance(plugin, t.uuid, null, a, t.tasks, t.player != null), null, true);
    }

    @Override
    public void disguise(FurnacePlugin plugin, Entity target, Player viewer, Disguise disguise) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(disguise, "disguise");
        var v = viewer(viewer);
        Target t = target(target);
        Appearance a = disguise.isNone() ? Appearance.REAL : appearance(disguise);
        refresh(EntityViewRules.setAppearance(plugin, t.uuid, v, a, t.tasks, t.player != null), v, true);
    }

    @Override
    public void undisguise(Entity target) {
        Target t = target(target);
        refresh(EntityViewRules.setAppearance(null, t.uuid, null, null, t.tasks, t.player != null), null, true);
    }

    @Override
    public void undisguise(Entity target, Player viewer) {
        var v = viewer(viewer);
        Target t = target(target);
        refresh(EntityViewRules.setAppearance(null, t.uuid, v, null, t.tasks, t.player != null), v, true);
    }

    @Override
    public Optional<Disguise> disguise(UUID target) {
        Appearance a = EntityViewRules.globalAppearance(target);
        return a == null || a.isReal() ? Optional.empty() : Optional.of((Disguise) a.source());
    }

    @Override
    public Optional<Disguise> disguiseSeenBy(UUID target, Player viewer) {
        Appearance a = EntityViewRules.appearanceFor(viewer(viewer), target);
        return a == null ? Optional.empty() : Optional.of((Disguise) a.source());
    }

    /** 플러그인이 꺼질 때: 그 플러그인이 건 규칙을 거두고 보던 사람에게 다시 맞춘다. */
    public void removeAll(FurnacePlugin plugin) {
        for (Change c : EntityViewRules.removeOwner(plugin)) refresh(c, null, true);
    }

    // ------------------------------------------------------------------ Disguise → 서버 모습

    /** API 변장을 버전 변환 계층으로 굳힌다(종류·메타데이터·프로필을 지금 한 번 인코딩해 틀린 값이면 여기서 IllegalArgumentException). */
    Appearance appearance(Disguise d) {
        String type = d.entityType();
        int typeId = EntityType.registryId(type);
        if (typeId < 0) throw new IllegalArgumentException("없는 엔티티 종류: " + type);
        var packets = host.packetsImpl();
        java.util.function.IntFunction<org.domi.furnace.server.net.protocol.Packet> meta = null;
        int[] overridden = new int[d.metadata().size()];
        if (!d.metadata().isEmpty()) {
            List<ClientboundSetEntityDataPacket.Entry> es = new ArrayList<>(d.metadata().size());
            int i = 0;
            for (Map.Entry<Integer, ClientboundSetEntityDataPacket.Value> e : d.metadata().entrySet()) {
                es.add(new ClientboundSetEntityDataPacket.Entry(e.getKey(), e.getValue()));
                overridden[i++] = e.getKey();
            }
            List<ClientboundSetEntityDataPacket.Entry> entries = List.copyOf(es);
            packets.encode(new ClientboundSetEntityDataPacket(0, entries));   // 인코딩 확인
            meta = eid -> packets.encode(new ClientboundSetEntityDataPacket(eid, entries));
        }
        Appearance.Profile profile = null;
        if (d.profileName() != null) {
            String name = d.profileName();
            var skin = d.skin();
            packets.encode(ClientboundPlayerInfoUpdatePacket.add(new UUID(0, 0), name, skin, GameMode.SURVIVAL, false, 0, null));
            profile = new Appearance.Profile(name, uuid -> packets.encode(ClientboundPlayerInfoUpdatePacket.add(uuid, name, skin, GameMode.SURVIVAL, false, 0, null)),
                    d.isNameTagVisible());
        }
        return new Appearance(typeId, type, d.spawnData(), overridden, meta, profile, d);
    }
}
