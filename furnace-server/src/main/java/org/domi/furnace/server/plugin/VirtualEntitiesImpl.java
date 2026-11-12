// 가상 엔티티·가짜 플레이어(NPC)·가짜 탭 줄 구현. 패킷 API 위에 쌓였다: 서버 월드에는 아무것도 만들지 않고 번호만 예약한다.
// 보이는 범위 추적은 전역 스레드에서 2틱마다 한 번(엔티티 수 × 접속자 수 거리 비교 — 가상 엔티티가 없으면 바로 끝난다).
// 한 엔티티의 상태 변경과 추적은 그 엔티티의 모니터 아래에서 순서대로 일어난다(패킷은 연결 큐에 넣기만 하므로 오래 잡지 않는다).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundAnimatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoRemovePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundRemoveEntitiesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundRotateHeadPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.packet.clientbound.ClientboundTeleportEntityPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundInteractPacket;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.virtual.VirtualEntities;
import org.domi.furnace.api.virtual.VirtualEntity;
import org.domi.furnace.api.virtual.VirtualInteraction;
import org.domi.furnace.api.virtual.VirtualPlayer;
import org.domi.furnace.api.virtual.VirtualTabList;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.net.protocol.play.SetEquipment;
import org.domi.furnace.server.net.protocol.play.SetPlayerTeam;
import org.domi.furnace.server.scores.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VirtualEntitiesImpl implements VirtualEntities {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    /** 이미 보이는 플레이어는 이 거리만큼 더 멀어져야 사라진다(경계에서 깜박이지 않게). */
    private static final double HYSTERESIS = 4.0;
    private static final int TRACK_PERIOD_TICKS = 2;
    private static final String NPC_TEAM_PREFIX = "fnpc_";

    private final PluginHost host;
    private final ConcurrentHashMap<Integer, Entity> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<org.domi.furnace.server.entity.Player, TabList> tabLists = new ConcurrentHashMap<>();
    private boolean started;   // this 아래

    VirtualEntitiesImpl(PluginHost host) { this.host = host; }

    // ------------------------------------------------------------------ 시작·정리

    private synchronized void ensureStarted() {
        if (started) return;
        started = true;
        host.packetsImpl().listenInternal(PacketTypes.Serverbound.INTERACT, EventPriority.NORMAL, this::onInteractPacket);
        host.core().scheduler().global().runAtFixedRate(() -> {
            try {
                tick();
            } catch (Throwable t) {
                log.error("가상 엔티티 추적 오류", t);
            }
        }, TRACK_PERIOD_TICKS, TRACK_PERIOD_TICKS);
    }

    /** 플러그인이 꺼질 때: 그 플러그인의 가상 엔티티와 탭 줄을 모두 치운다(핸들러가 쥔 옛 클래스도 함께 놓는다). */
    public void removeAll(FurnacePlugin plugin) {
        for (Entity e : List.copyOf(byId.values())) if (e.owner == plugin) e.remove();
        for (TabList t : tabLists.values()) t.removeOwned(plugin);
    }

    private org.domi.furnace.server.entity.Player server(org.domi.furnace.api.entity.Player p) {
        Objects.requireNonNull(p, "player");
        if (p instanceof PlayerHandle ph) return ph.handle();
        throw new IllegalArgumentException("이 서버의 플레이어가 아니다: " + p);
    }

    private void send(org.domi.furnace.server.entity.Player sp, ClientboundPacket packet) {
        if (sp.isDisconnected()) return;
        try {
            host.packets().send(host.player(sp), packet);
        } catch (RuntimeException e) {
            log.warn("가상 엔티티 패킷을 보내지 못했다({}): {}", packet.type(), e.toString());
        }
    }

    private static boolean sameWorld(org.domi.furnace.server.entity.Player sp, Location l) { return sp.dimension().equals(l.world()); }

    // ------------------------------------------------------------------ 만들기

    @Override
    public VirtualEntity spawn(FurnacePlugin owner, String entityType, Location location) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
        String type = entityType.indexOf(':') < 0 ? "minecraft:" + entityType : entityType;
        if (type.equals("minecraft:player")) throw new IllegalArgumentException("가짜 플레이어는 spawnPlayer 로 만든다");
        Entity e = new Entity(owner, type, location);
        register(e);
        return e;
    }

    @Override
    public VirtualPlayer spawnPlayer(FurnacePlugin owner, String name, List<ProfileProperty> skin, Location location) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
        if (name == null || name.isEmpty() || name.length() > 16) throw new IllegalArgumentException("프로필 이름은 1~16자: " + name);
        Player e = new Player(owner, name, skin == null ? List.of() : List.copyOf(skin), location);
        register(e);
        return e;
    }

    private void register(Entity e) {
        e.validateType();   // 없는 엔티티 종류면 여기서 IllegalArgumentException
        ensureStarted();
        byId.put(e.id, e);
        e.trackNow();
    }

    @Override
    public VirtualTabList tabList(org.domi.furnace.api.entity.Player viewer) {
        org.domi.furnace.server.entity.Player sp = server(viewer);
        ensureStarted();
        return tabLists.computeIfAbsent(sp, TabList::new);
    }

    @Override
    public Optional<VirtualEntity> byEntityId(int entityId) { return Optional.ofNullable(byId.get(entityId)); }

    @Override
    public Collection<VirtualEntity> entities(FurnacePlugin owner) {
        List<VirtualEntity> out = new ArrayList<>();
        for (Entity e : byId.values()) if (e.owner == owner) out.add(e);
        return out;
    }

    // ------------------------------------------------------------------ 추적

    /** 시험용: 추적 한 번을 지금 돌린다. */
    void tickForTest() { tick(); }

    private void tick() {
        Collection<org.domi.furnace.server.entity.Player> players = host.core().players();
        tabLists.keySet().removeIf(org.domi.furnace.server.entity.Player::isDisconnected);
        if (byId.isEmpty()) return;
        for (Entity e : byId.values()) e.track(players);
    }

    private void onInteractPacket(org.domi.furnace.api.packet.PacketEvent<ServerboundInteractPacket> ev) {
        ServerboundInteractPacket pk = ev.packet();
        Entity e = byId.get(pk.entityId());
        if (e == null) return;
        ev.setCancelled(true);   // 서버에 없는 번호지만 다른 처리가 건드리지 않게
        boolean attack = pk.action() == ServerboundInteractPacket.Action.ATTACK;
        if (!attack && !(pk.action() == ServerboundInteractPacket.Action.INTERACT && pk.hand() == Hand.MAIN_HAND)) return;
        Consumer<VirtualInteraction> h = e.handler;
        if (h == null || !e.viewers.containsKey(ev.player().uniqueId())) return;
        VirtualInteraction vi = new VirtualInteraction(ev.player(), e, attack, pk.sneaking());
        ev.player().scheduler().execute(() -> {
            try {
                h.accept(vi);
            } catch (Throwable t) {
                log.error("[{}] 가상 엔티티 클릭 처리 오류", e.owner.name(), t);
            }
        });
    }

    // ================================================================== 엔티티

    private record Viewer(org.domi.furnace.server.entity.Player player, String dimension) {}

    private class Entity implements VirtualEntity {

        final int id = host.packets().nextEntityId();
        final UUID uuid;
        final String type;
        final FurnacePlugin owner;
        final ConcurrentHashMap<UUID, Viewer> viewers = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, Boolean> overrides = new ConcurrentHashMap<>();
        private final Map<Integer, Value> meta = new LinkedHashMap<>();       // this 아래
        private final EnumMap<EquipmentSlot, org.domi.furnace.server.entity.ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);   // this 아래
        private volatile Location loc;
        private volatile float headYaw;
        private volatile double range = 48;
        private volatile boolean visibleDefault = true;
        private volatile boolean removed;
        volatile Consumer<VirtualInteraction> handler;
        private int flags;   // 상태 비트(칸 0)

        Entity(FurnacePlugin owner, String type, Location loc) {
            this(owner, type, loc, UUID.randomUUID());
        }

        Entity(FurnacePlugin owner, String type, Location loc, UUID uuid) {
            this.owner = owner;
            this.type = type;
            this.loc = loc;
            this.headYaw = loc.yaw();
            this.uuid = uuid;
        }

        void validateType() {
            io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.buffer();
            try {
                host.packetsImpl().codec().encode(addPacket(), b);
            } finally {
                b.release();
            }
        }

        ClientboundAddEntityPacket addPacket() {
            Location l = loc;
            return new ClientboundAddEntityPacket(id, uuid, type, l.x(), l.y(), l.z(), l.pitch(), l.yaw(), headYaw, 0, 0, 0, 0);
        }

        // ---- 접근

        @Override public int entityId() { return id; }
        @Override public UUID uuid() { return uuid; }
        @Override public String entityType() { return type; }
        @Override public FurnacePlugin owner() { return owner; }
        @Override public boolean isRemoved() { return removed; }
        @Override public Location location() { return loc; }
        @Override public double trackingRange() { return range; }
        @Override public boolean isVisibleByDefault() { return visibleDefault; }

        @Override
        public Collection<org.domi.furnace.api.entity.Player> viewers() {
            List<org.domi.furnace.api.entity.Player> out = new ArrayList<>();
            for (Viewer v : viewers.values()) out.add(host.player(v.player));
            return out;
        }

        // ---- 이동

        @Override
        public synchronized void teleport(Location to) {
            Objects.requireNonNull(to, "to");
            if (removed) return;
            boolean newWorld = !to.world().equals(loc.world());
            loc = to;
            headYaw = to.yaw();
            if (newWorld) {
                for (Viewer v : List.copyOf(viewers.values())) despawn(v);
                trackNow();
            } else {
                broadcast(new ClientboundTeleportEntityPacket(id, to.x(), to.y(), to.z(), to.yaw(), to.pitch(), false));
                broadcast(new ClientboundRotateHeadPacket(id, headYaw));
            }
        }

        @Override
        public synchronized void setRotation(float yaw, float pitch) {
            if (removed) return;
            Location l = loc.withRotation(yaw, pitch);
            loc = l;
            headYaw = yaw;
            broadcast(new ClientboundTeleportEntityPacket(id, l.x(), l.y(), l.z(), yaw, pitch, false));
            broadcast(new ClientboundRotateHeadPacket(id, yaw));
        }

        @Override
        public synchronized void setHeadYaw(float yaw) {
            if (removed) return;
            headYaw = yaw;
            broadcast(new ClientboundRotateHeadPacket(id, yaw));
        }

        // ---- 보이는 범위

        @Override
        public void setTrackingRange(double blocks) {
            if (!(blocks > 0)) throw new IllegalArgumentException("범위는 0보다 커야 한다: " + blocks);
            range = blocks;
        }

        @Override
        public synchronized void setVisibleByDefault(boolean visible) {
            visibleDefault = visible;
            trackNow();
        }

        @Override
        public synchronized void show(org.domi.furnace.api.entity.Player player) {
            overrides.put(player.uniqueId(), Boolean.TRUE);
            trackNow();
        }

        @Override
        public synchronized void hide(org.domi.furnace.api.entity.Player player) {
            overrides.put(player.uniqueId(), Boolean.FALSE);
            trackNow();
        }

        @Override
        public synchronized void resetVisibility(org.domi.furnace.api.entity.Player player) {
            overrides.remove(player.uniqueId());
            trackNow();
        }

        void trackNow() { track(host.core().players()); }

        synchronized void track(Collection<org.domi.furnace.server.entity.Player> players) {
            if (removed) return;
            Location l = loc;
            double r = range, r2 = r * r, keep = (r + HYSTERESIS) * (r + HYSTERESIS);
            for (var sp : players) {
                if (sp.isDisconnected()) continue;
                Viewer v = viewers.get(sp.uuid());
                if (v != null && v.player != sp) {   // 같은 계정이 다시 접속: 옛 연결의 상태는 버린다
                    viewers.remove(sp.uuid());
                    v = null;
                }
                if (v != null && !v.dimension.equals(sp.dimension())) {   // 차원을 옮김 — 클라이언트가 엔티티를 이미 잃었다
                    despawn(v);
                    v = null;
                }
                boolean want = false;
                if (sameWorld(sp, l) && overrides.getOrDefault(sp.uuid(), visibleDefault)) {
                    double dx = sp.x() - l.x(), dy = sp.y() - l.y(), dz = sp.z() - l.z();
                    double d2 = dx * dx + dy * dy + dz * dz;
                    want = d2 <= (v != null ? keep : r2);
                }
                if (want && v == null) spawnTo(sp);
                else if (!want && v != null) despawn(v);
            }
            for (Viewer v : List.copyOf(viewers.values())) {   // 나간 사람
                if (v.player.isDisconnected()) viewers.remove(v.player.uuid());
            }
        }

        private void spawnTo(org.domi.furnace.server.entity.Player sp) {
            viewers.put(sp.uuid(), new Viewer(sp, sp.dimension()));
            sendSpawn(sp);
        }

        void sendSpawn(org.domi.furnace.server.entity.Player sp) {
            send(sp, addPacket());
            List<ClientboundSetEntityDataPacket.Entry> es = dataEntries();
            if (!es.isEmpty()) send(sp, new ClientboundSetEntityDataPacket(id, es));
            if (!equipment.isEmpty()) sp.send(equipmentPacket(equipment));
            send(sp, new ClientboundRotateHeadPacket(id, headYaw));
        }

        void despawn(Viewer v) {
            viewers.remove(v.player.uuid());
            sendDespawn(v.player);
        }

        void sendDespawn(org.domi.furnace.server.entity.Player sp) {
            send(sp, ClientboundRemoveEntitiesPacket.of(id));
        }

        /** 지금 보는 모두에게(보낼 때 잡은 목록 기준). */
        void broadcast(ClientboundPacket p) {
            for (Viewer v : viewers.values()) send(v.player, p);
        }

        // ---- 겉모습

        List<ClientboundSetEntityDataPacket.Entry> dataEntries() {
            List<ClientboundSetEntityDataPacket.Entry> es = new ArrayList<>(meta.size());
            meta.forEach((i, v) -> es.add(new ClientboundSetEntityDataPacket.Entry(i, v)));
            return es;
        }

        @Override
        public synchronized void setMetadata(int index, Value value) {
            if (removed) return;
            Objects.requireNonNull(value, "value");
            if (value instanceof ClientboundSetEntityDataPacket.Unsupported) throw new IllegalArgumentException("읽지 못한 값 종류는 정할 수 없다");
            if (index == 0 && value instanceof ClientboundSetEntityDataPacket.ByteValue b) flags = b.value() & 0xFF;
            meta.put(index, value);
            broadcast(ClientboundSetEntityDataPacket.of(id, new ClientboundSetEntityDataPacket.Entry(index, value)));
        }

        @Override
        public <T> void set(org.domi.furnace.api.entity.data.EntityDataKey<T> key, T value) {
            Objects.requireNonNull(key, "key");
            var packets = host.packets();
            if (!packets.hasEntityData(type, key)) throw new IllegalArgumentException(type + " 에는 메타데이터 칸 " + key.id() + " 이 없다");
            setMetadata(packets.entityDataIndex(key).getAsInt(), Value.of(key.type(), value));
        }

        private void setFlag(int bit, boolean on) {
            int f = on ? flags | bit : flags & ~bit;
            setMetadata(0, Value.ofByte(f));
        }

        @Override public void setCustomName(Text name) { setMetadata(2, Value.ofOptionalText(name)); }
        @Override public void setCustomNameVisible(boolean visible) { setMetadata(3, Value.ofBoolean(visible)); }
        @Override public synchronized void setInvisible(boolean invisible) { setFlag(0x20, invisible); }
        @Override public synchronized void setGlowing(boolean glowing) { setFlag(0x40, glowing); }
        @Override public synchronized void setOnFire(boolean onFire) { setFlag(0x01, onFire); }
        @Override public void setSilent(boolean silent) { setMetadata(4, Value.ofBoolean(silent)); }
        @Override public void setNoGravity(boolean noGravity) { setMetadata(5, Value.ofBoolean(noGravity)); }

        @Override
        public synchronized void setSneaking(boolean sneaking) {
            setFlag(0x02, sneaking);
            setMetadata(6, Value.ofPose(sneaking ? 5 : 0));   // 웅크린 모습은 상태 비트와 자세 둘 다 필요하다
        }

        @Override
        public synchronized void setEquipment(EquipmentSlot slot, ItemStack item) {
            Objects.requireNonNull(slot, "slot");
            org.domi.furnace.server.entity.ItemStack s = org.domi.furnace.server.plugin.ApiConvert.item(item, host.core().world().itemRegistry());
            if (removed) return;
            if (s.isEmpty()) equipment.remove(slot);
            else equipment.put(slot, s);
            EnumMap<EquipmentSlot, org.domi.furnace.server.entity.ItemStack> one = new EnumMap<>(EquipmentSlot.class);
            one.put(slot, s);
            SetEquipment pk = equipmentPacket(one);
            for (Viewer v : viewers.values()) v.player.send(pk);
        }

        private SetEquipment equipmentPacket(Map<EquipmentSlot, org.domi.furnace.server.entity.ItemStack> m) {
            List<SetEquipment.Entry> es = new ArrayList<>(m.size());
            m.forEach((slot, st) -> es.add(new SetEquipment.Entry(serverSlot(slot), st)));
            return new SetEquipment(id, es);
        }

        @Override
        public void swingMainHand() { broadcast(new ClientboundAnimatePacket(id, ClientboundAnimatePacket.Animation.SWING_MAIN_HAND)); }

        @Override
        public void onInteract(Consumer<VirtualInteraction> handler) { this.handler = handler; }

        @Override
        public synchronized void remove() {
            if (removed) return;
            removed = true;
            byId.remove(id);
            for (Viewer v : List.copyOf(viewers.values())) despawn(v);
            handler = null;
            overrides.clear();
            onRemoved();
        }

        void onRemoved() {}

        @Override
        public String toString() { return "VirtualEntity{" + type + "#" + id + "}"; }
    }

    private static org.domi.furnace.server.entity.EquipmentSlot serverSlot(EquipmentSlot s) {
        return switch (s) {
            case MAIN_HAND -> org.domi.furnace.server.entity.EquipmentSlot.MAINHAND;
            case OFF_HAND -> org.domi.furnace.server.entity.EquipmentSlot.OFFHAND;
            case FEET -> org.domi.furnace.server.entity.EquipmentSlot.FEET;
            case LEGS -> org.domi.furnace.server.entity.EquipmentSlot.LEGS;
            case CHEST -> org.domi.furnace.server.entity.EquipmentSlot.CHEST;
            case HEAD -> org.domi.furnace.server.entity.EquipmentSlot.HEAD;
        };
    }

    // ================================================================== 가짜 플레이어

    private final class Player extends Entity implements VirtualPlayer {

        private final String name;
        private volatile List<ProfileProperty> skin;
        private volatile boolean listed, nameTag;

        Player(FurnacePlugin owner, String name, List<ProfileProperty> skin, Location loc) {
            super(owner, "minecraft:player", loc);
            this.name = name;
            this.skin = skin;
            synchronized (this) {
                setMetadataQuiet(17, Value.ofByte(0x7F));
            }
        }

        private void setMetadataQuiet(int index, Value v) {
            // 생성 중에만: 아직 보는 사람이 없어 패킷은 나가지 않는다
            setMetadata(index, v);
        }

        private String teamName() { return NPC_TEAM_PREFIX + Integer.toHexString(id); }

        @Override public String profileName() { return name; }
        @Override public List<ProfileProperty> skin() { return skin; }
        @Override public boolean isListed() { return listed; }
        @Override public boolean isNameTagVisible() { return nameTag; }

        @Override
        public synchronized void setSkin(List<ProfileProperty> skin) {
            this.skin = List.copyOf(skin);
            for (Viewer v : List.copyOf(viewers.values())) {   // 스킨은 엔티티 생성 때만 읽힌다 — 다시 만든다
                sendDespawn(v.player);
                sendSpawn(v.player);
            }
        }

        @Override
        public synchronized void setListed(boolean listed) {
            this.listed = listed;
            broadcast(ClientboundPlayerInfoUpdatePacket.listed(uuid, listed));
        }

        @Override
        public synchronized void setNameTagVisible(boolean visible) {
            if (nameTag == visible) return;
            nameTag = visible;
            for (Viewer v : viewers.values()) {
                if (visible) v.player.send(teamStub());
                else v.player.send(teamAdd());
            }
        }

        @Override
        public void setSkinLayers(int mask) { setMetadata(17, Value.ofByte(mask)); }

        private SetPlayerTeam teamAdd() {
            var params = new SetPlayerTeam.Parameters(Text.empty(), 0, "never", "never", ChatFormatting.RESET, Text.empty(), Text.empty());
            return new SetPlayerTeam(teamName(), SetPlayerTeam.METHOD_ADD, params, List.of(name));
        }

        private SetPlayerTeam teamStub() { return new SetPlayerTeam(teamName(), SetPlayerTeam.METHOD_REMOVE, null, List.of()); }

        @Override
        void sendSpawn(org.domi.furnace.server.entity.Player sp) {
            send(sp, ClientboundPlayerInfoUpdatePacket.add(uuid, name, skin, GameMode.SURVIVAL, listed, 0, null));
            if (!nameTag) sp.send(teamAdd());
            super.sendSpawn(sp);
        }

        @Override
        void sendDespawn(org.domi.furnace.server.entity.Player sp) {
            super.sendDespawn(sp);
            send(sp, ClientboundPlayerInfoRemovePacket.of(uuid));
            if (!nameTag) sp.send(teamStub());
        }
    }

    // ================================================================== 탭 목록

    private final class TabList implements VirtualTabList {

        private final org.domi.furnace.server.entity.Player viewer;
        private final List<TabEntry> entries = new ArrayList<>();   // this 아래

        TabList(org.domi.furnace.server.entity.Player viewer) { this.viewer = viewer; }

        @Override
        public synchronized Entry add(FurnacePlugin owner, String name, List<ProfileProperty> skin, GameMode gameMode, int latencyMs, Text displayName) {
            Objects.requireNonNull(owner, "owner");
            if (name == null || name.isEmpty() || name.length() > 16) throw new IllegalArgumentException("이름은 1~16자: " + name);
            if (viewer.isDisconnected()) throw new IllegalStateException("접속이 끊긴 플레이어");
            TabEntry e = new TabEntry(owner, UUID.randomUUID(), name, gameMode == null ? GameMode.SURVIVAL : gameMode);
            e.latency = latencyMs;
            e.display = displayName;
            send(viewer, ClientboundPlayerInfoUpdatePacket.add(e.uuid, name, skin == null ? List.of() : skin, e.mode, true, latencyMs, displayName));
            entries.add(e);
            return e;
        }

        @Override
        public synchronized List<Entry> entries() { return List.copyOf(entries); }

        synchronized void removeOwned(FurnacePlugin plugin) {
            for (TabEntry e : List.copyOf(entries)) if (e.owner == plugin) e.remove();
        }

        private final class TabEntry implements Entry {

            final FurnacePlugin owner;
            final UUID uuid;
            final String name;
            GameMode mode;
            int latency;
            Text display;
            boolean removed;

            TabEntry(FurnacePlugin owner, UUID uuid, String name, GameMode mode) {
                this.owner = owner;
                this.uuid = uuid;
                this.name = name;
                this.mode = mode;
            }

            @Override public UUID uuid() { return uuid; }
            @Override public String name() { return name; }
            @Override public boolean isRemoved() { synchronized (TabList.this) { return removed; } }

            @Override
            public void setDisplayName(Text displayName) {
                synchronized (TabList.this) {
                    if (removed) return;
                    display = displayName;
                    send(viewer, ClientboundPlayerInfoUpdatePacket.displayName(uuid, displayName));
                }
            }

            @Override
            public void setLatency(int latencyMs) {
                synchronized (TabList.this) {
                    if (removed) return;
                    latency = latencyMs;
                    send(viewer, ClientboundPlayerInfoUpdatePacket.latency(uuid, latencyMs));
                }
            }

            @Override
            public void setGameMode(GameMode m) {
                synchronized (TabList.this) {
                    if (removed) return;
                    mode = m;
                    send(viewer, ClientboundPlayerInfoUpdatePacket.gameMode(uuid, m));
                }
            }

            @Override
            public void remove() {
                synchronized (TabList.this) {
                    if (removed) return;
                    removed = true;
                    entries.remove(this);
                    send(viewer, ClientboundPlayerInfoRemovePacket.of(uuid));
                }
            }
        }
    }
}
