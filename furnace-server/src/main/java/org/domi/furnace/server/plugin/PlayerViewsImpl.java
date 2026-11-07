// 플레이어별 화면 덮어쓰기 API 구현. 층(가상 블록·바이옴·장비·개인 보더)은 서버 Player 의 ViewLayers 에 두고,
// 청크·블록 변경·장비·보더 패킷에 덧입히는 일은 연결마다 ViewRewriter 가 한다. 여기서는 층을 바꾸고, 이미 보내진 화면을 맞추는 패킷만 보낸다:
// 걸 때는 덮어쓴 값을 바로 보내고, 걷을 때는 그 칸을 가진 region 스레드에서 진짜 값을 읽어 보낸다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.view.PlayerBorder;
import org.domi.furnace.api.view.PlayerViews;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.entity.view.BlockLayer;
import org.domi.furnace.server.entity.view.ViewLayers;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunksBiomes;
import org.domi.furnace.server.net.protocol.play.InitializeBorder;
import org.domi.furnace.server.net.protocol.play.SectionBlocksUpdate;
import org.domi.furnace.server.net.protocol.play.SetEquipment;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.WorldBorder;

public final class PlayerViewsImpl implements PlayerViews {

    private final PluginHost host;

    PlayerViewsImpl(PluginHost host) { this.host = host; }

    private static org.domi.furnace.server.entity.Player viewer(Player p) {
        Objects.requireNonNull(p, "viewer");
        if (p instanceof PlayerHandle ph) return ph.handle();
        throw new IllegalArgumentException("이 서버의 플레이어가 아니다: " + p);
    }

    private static WorldHandle world(org.domi.furnace.api.world.World w) {
        Objects.requireNonNull(w, "world");
        if (w instanceof WorldHandle wh) return wh;
        throw new IllegalArgumentException("이 서버의 월드가 아니다: " + w);
    }

    private static BlockLayer.Dim dimOf(WorldHandle w) {
        var t = w.handle().dimensionType();
        return new BlockLayer.Dim(t.minY(), t.height(), w.handle().blockDirectBits(), w.handle().biomeDirectBits());
    }

    // ------------------------------------------------------------------ 가상 블록

    @Override
    public void setBlock(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, BlockPos pos, BlockData data) {
        setBlocks(plugin, viewer, world, Map.of(Objects.requireNonNull(pos, "pos"), Objects.requireNonNull(data, "data")));
    }

    @Override
    public void setBlocks(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, Map<BlockPos, BlockData> blocks) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        WorldHandle w = world(world);
        List<int[]> cells = new ArrayList<>(blocks.size());
        Map<BlockData, Integer> ids = new HashMap<>();
        for (var e : blocks.entrySet()) {
            int state = ids.computeIfAbsent(e.getValue(), d -> ApiConvert.stateId(d, w.handle().blockRegistry()));   // 모르는 블록이면 IllegalArgumentException
            BlockPos p = e.getKey();
            cells.add(new int[] {p.x(), p.y(), p.z(), state});
        }
        applyBlocks(plugin, v, w, cells);
    }

    @Override
    public void fillBlocks(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, BlockPos min, BlockPos max, BlockData data) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        WorldHandle w = world(world);
        int x0 = Math.min(min.x(), max.x()), x1 = Math.max(min.x(), max.x());
        int y0 = Math.min(min.y(), max.y()), y1 = Math.max(min.y(), max.y());
        int z0 = Math.min(min.z(), max.z()), z1 = Math.max(min.z(), max.z());
        long count = ((long) x1 - x0 + 1) * ((long) y1 - y0 + 1) * ((long) z1 - z0 + 1);
        if (count > MAX_FILL) throw new IllegalArgumentException("한 번에 덮을 수 있는 칸은 최대 " + MAX_FILL + " 개: " + count);
        int state = ApiConvert.stateId(Objects.requireNonNull(data, "data"), w.handle().blockRegistry());
        List<int[]> cells = new ArrayList<>((int) count);
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) cells.add(new int[] {x, y, z, state});
        applyBlocks(plugin, v, w, cells);
    }

    private void applyBlocks(FurnacePlugin plugin, org.domi.furnace.server.entity.Player v, WorldHandle w, List<int[]> cells) {
        BlockLayer layer = v.viewLayers().blocks;
        BlockLayer.Dim dim = dimOf(w);
        cells = cells.stream().filter(c -> c[1] >= dim.minY() && c[1] < dim.minY() + dim.height()).toList();   // 높이 밖은 층에도 화면에도 넣지 않는다
        if (cells.isEmpty()) return;
        layer.putBlocks(plugin, w.dimension(), dim, cells);
        if (!sameWorldAsViewer(v, w)) return;   // 그 월드에 있을 때 청크 전송에서 덧입는다 — 지금 화면은 다른 월드다
        sendCells(v, cells);
    }

    /** 덮어쓴 값을 그대로 보낸다(칸이 하나면 block_update, 아니면 구역마다 section_blocks_update). */
    private static void sendCells(org.domi.furnace.server.entity.Player v, List<int[]> cells) {
        if (cells.size() == 1) {
            int[] c = cells.get(0);
            v.send(new BlockUpdate(c[0], c[1], c[2], c[3]));
            return;
        }
        Map<Long, List<int[]>> bySection = new HashMap<>();
        for (int[] c : cells) bySection.computeIfAbsent(sectionKey(c[0] >> 4, c[1] >> 4, c[2] >> 4), k -> new ArrayList<>()).add(c);
        for (List<int[]> sec : bySection.values()) {
            int[] first = sec.get(0);
            short[] pos = new short[sec.size()];
            int[] states = new int[sec.size()];
            for (int i = 0; i < pos.length; i++) {
                int[] c = sec.get(i);
                pos[i] = (short) (((c[0] & 15) << 8) | ((c[2] & 15) << 4) | (c[1] & 15));
                states[i] = c[3];
            }
            v.send(new SectionBlocksUpdate(first[0] >> 4, first[1] >> 4, first[2] >> 4, pos, states));
        }
    }

    private static long sectionKey(int sx, int sy, int sz) { return ((long) sx & 0x3FFFFF) << 42 | ((long) sz & 0x3FFFFF) << 20 | (long) sy & 0xFFFFF; }

    private static boolean sameWorldAsViewer(org.domi.furnace.server.entity.Player v, WorldHandle w) {
        String cur = v.viewRewriter().dimension();
        return cur == null || cur.equals(w.dimension());
    }

    @Override
    public void clearBlock(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, BlockPos pos) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        WorldHandle w = world(world);
        var r = v.viewLayers().blocks.removeBlocks(plugin, w.dimension(), List.of(new int[] {pos.x(), pos.y(), pos.z()}));
        restoreBlocks(v, w, r.blocks());
    }

    @Override
    public void clearBlocks(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        WorldHandle w = world(world);
        var r = v.viewLayers().blocks.removeWorld(plugin, w.dimension());
        restoreBlocks(v, w, r.blocks());
        restoreBiomes(v, w, r.biomeChunks());
    }

    @Override
    public void clearLayers(FurnacePlugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        clearLayers(plugin, viewer(viewer));
    }

    private void clearLayers(Object owner, org.domi.furnace.server.entity.Player v) {
        for (var e : v.viewLayers().blocks.removeAll(owner).entrySet()) {
            WorldHandle w = host.worldHandle(e.getKey());
            if (w == null) continue;
            restoreBlocks(v, w, e.getValue().blocks());
            restoreBiomes(v, w, e.getValue().biomeChunks());
        }
    }

    /** 걷은 칸의 진짜 블록을 그 칸을 가진 region 스레드에서 읽어 보낸다(청크가 올라와 있지 않으면 건너뛴다 — 올라올 때 진짜 청크가 간다). */
    private static void restoreBlocks(org.domi.furnace.server.entity.Player v, WorldHandle w, List<int[]> cells) {
        if (cells.isEmpty() || !sameWorldAsViewer(v, w)) return;
        Map<ChunkPos, List<int[]>> byChunk = new HashMap<>();
        for (int[] c : cells) byChunk.computeIfAbsent(new ChunkPos(c[0] >> 4, c[2] >> 4), k -> new ArrayList<>()).add(c);
        for (var e : byChunk.entrySet()) {
            ChunkPos cp = e.getKey();
            List<int[]> list = e.getValue();
            w.handle().submitAtOwnedChunk(cp, r -> {
                if (!r.chunkLoaded(cp)) return null;
                List<int[]> real = new ArrayList<>(list.size());
                for (int[] c : list) real.add(new int[] {c[0], c[1], c[2], r.getBlock(c[0], c[1], c[2])});
                sendCells(v, real);
                return null;
            });
        }
    }

    @Override
    public Optional<BlockData> virtualBlock(Player viewer, org.domi.furnace.api.world.World world, BlockPos pos) {
        var v = viewer(viewer);
        WorldHandle w = world(world);
        BlockLayer.Entry e = v.viewLayers().blocks.block(w.dimension(), pos.x(), pos.y(), pos.z());
        return e == null ? Optional.empty() : Optional.of(ApiConvert.block(e.value(), w.handle().blockRegistry()));
    }

    // ------------------------------------------------------------------ 가상 바이옴

    @Override
    public void setBiome(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, BlockPos pos, String biome) {
        fillBiome(plugin, viewer, world, pos, pos, biome);
    }

    @Override
    public void fillBiome(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, BlockPos min, BlockPos max, String biome) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        WorldHandle w = world(world);
        int id = w.biomeId(biome);
        int x0 = Math.min(min.x(), max.x()) >> 2, x1 = Math.max(min.x(), max.x()) >> 2;
        int y0 = Math.min(min.y(), max.y()) >> 2, y1 = Math.max(min.y(), max.y()) >> 2;
        int z0 = Math.min(min.z(), max.z()) >> 2, z1 = Math.max(min.z(), max.z()) >> 2;
        long count = ((long) x1 - x0 + 1) * ((long) y1 - y0 + 1) * ((long) z1 - z0 + 1);
        if (count > MAX_FILL / 64) throw new IllegalArgumentException("한 번에 덮을 수 있는 바이옴 칸은 최대 " + MAX_FILL / 64 + " 개: " + count);
        List<int[]> cells = new ArrayList<>((int) count);
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) cells.add(new int[] {x << 2, y << 2, z << 2, id});
        Set<Long> chunks = v.viewLayers().blocks.putBiomes(plugin, w.dimension(), dimOf(w), cells);
        restoreBiomes(v, w, chunks);   // 덮어쓴 모습으로 그 청크 바이옴을 다시 보낸다(ViewRewriter 가 층을 덧입힌다)
    }

    @Override
    public void clearBiome(FurnacePlugin plugin, Player viewer, org.domi.furnace.api.world.World world, BlockPos pos) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        WorldHandle w = world(world);
        var r = v.viewLayers().blocks.removeBiomes(plugin, w.dimension(), List.of(new int[] {pos.x(), pos.y(), pos.z()}));
        restoreBiomes(v, w, r.biomeChunks());
    }

    /** 그 청크들의 바이옴을 region 스레드에서 읽어 한 사람에게 다시 보낸다(층이 남아 있으면 덧입혀진다). */
    private static void restoreBiomes(org.domi.furnace.server.entity.Player v, WorldHandle w, Set<Long> chunks) {
        if (chunks.isEmpty() || !sameWorldAsViewer(v, w)) return;
        for (long key : chunks) {
            ChunkPos cp = new ChunkPos((int) (key >> 32), (int) key);
            w.handle().submitAtOwnedChunk(cp, r -> {
                ChunksBiomes.Entry e = r.biomeEntry(cp);
                if (e != null) v.send(new ChunksBiomes(List.of(e), w.handle().biomeDirectBits()));
                return null;
            });
        }
    }

    @Override
    public Optional<String> virtualBiome(Player viewer, org.domi.furnace.api.world.World world, BlockPos pos) {
        var v = viewer(viewer);
        WorldHandle w = world(world);
        BlockLayer.Entry e = v.viewLayers().blocks.biome(w.dimension(), pos.x(), pos.y(), pos.z());
        return e == null ? Optional.empty() : Optional.ofNullable(w.biomeName(e.value()));
    }

    // ------------------------------------------------------------------ 월드 보더

    @Override
    public PlayerBorder border(FurnacePlugin plugin, Player viewer) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        ViewLayers layers = v.viewLayers();
        synchronized (layers) {
            WorldBorder b = layers.border();
            if (b == null) {
                b = new WorldBorder();
                b.setAbsoluteMaxSize(host.core().border().getAbsoluteMaxSize());
                b.applySettings(host.core().border().createSettings());
                layers.border(b, plugin);
                v.send(InitializeBorder.vanillaDefault());   // ViewRewriter 가 개인 보더 상태로 바꿔 보낸다
            } else {
                layers.borderOwner(plugin);
            }
            return new BorderHandle(v, b);
        }
    }

    @Override
    public boolean hasBorder(Player viewer) { return viewer(viewer).viewLayers().border() != null; }

    @Override
    public void resetBorder(Player viewer) { resetBorder(viewer(viewer)); }

    private void resetBorder(org.domi.furnace.server.entity.Player v) {
        ViewLayers layers = v.viewLayers();
        synchronized (layers) {
            if (layers.border() == null) return;
            layers.border(null, null);
            v.send(InitializeBorder.of(host.core().border()));
        }
    }

    private static final class BorderHandle implements PlayerBorder {
        private final org.domi.furnace.server.entity.Player viewer;
        private final WorldBorder b;

        BorderHandle(org.domi.furnace.server.entity.Player viewer, WorldBorder b) {
            this.viewer = viewer;
            this.b = b;
        }

        private void push() {
            var layers = viewer.viewLayers();
            synchronized (layers) {   // resetBorder 와 같은 자물쇠 — 걷힌 뒤에 자리표시 패킷이 따라가 기본 보더로 덮이는 일을 막는다
                if (layers.border() == b) viewer.send(InitializeBorder.vanillaDefault());   // 걷힌 보더는 더 보내지 않는다
            }
        }

        @Override public double centerX() { return b.getCenterX(); }
        @Override public double centerZ() { return b.getCenterZ(); }
        @Override public void center(double x, double z) { b.setCenter(x, z); push(); }
        @Override public double size() { return b.getSize(); }
        @Override public void size(double blocks) { b.setSize(blocks); push(); }
        @Override public void lerpSize(double toSize, long millis) {
            if (millis <= 0) b.setSize(toSize); else b.lerpSizeBetween(b.getSize(), toSize, millis);
            push();
        }
        @Override public double lerpTarget() { return b.getLerpTarget(); }
        @Override public int warningDistance() { return b.getWarningBlocks(); }
        @Override public void warningDistance(int blocks) { b.setWarningBlocks(blocks); push(); }
        @Override public int warningTime() { return b.getWarningTime(); }
        @Override public void warningTime(int seconds) { b.setWarningTime(seconds); push(); }
        @Override public double damagePerBlock() { return b.getDamagePerBlock(); }
        @Override public void damagePerBlock(double damage) { b.setDamagePerBlock(damage); }
        @Override public double damageSafeZone() { return b.getDamageSafeZone(); }
        @Override public void damageSafeZone(double blocks) { b.setDamageSafeZone(blocks); }
        @Override public boolean enforced() { return viewer.viewLayers().border() == b && viewer.viewLayers().borderEnforced(); }
        @Override public void enforce(boolean enforce) { if (viewer.viewLayers().border() == b) viewer.viewLayers().borderEnforced(enforce); }
        @Override public boolean contains(double x, double z) { return b.isWithinBounds(x, z); }
    }

    // ------------------------------------------------------------------ 장비 표시

    private static org.domi.furnace.server.entity.EquipmentSlot slot(org.domi.furnace.api.entity.EquipmentSlot s) {
        return switch (Objects.requireNonNull(s, "slot")) {
            case MAIN_HAND -> org.domi.furnace.server.entity.EquipmentSlot.MAINHAND;
            case OFF_HAND -> org.domi.furnace.server.entity.EquipmentSlot.OFFHAND;
            case FEET -> org.domi.furnace.server.entity.EquipmentSlot.FEET;
            case LEGS -> org.domi.furnace.server.entity.EquipmentSlot.LEGS;
            case CHEST -> org.domi.furnace.server.entity.EquipmentSlot.CHEST;
            case HEAD -> org.domi.furnace.server.entity.EquipmentSlot.HEAD;
        };
    }

    /** 대상 엔티티의 번호와, 그 엔티티를 따라가는 작업 대기열(플레이어는 null). */
    private record Target(int id, org.domi.furnace.server.entity.EntityTasks tasks, org.domi.furnace.server.entity.Player player) {}

    private static Target target(Entity e) {
        Objects.requireNonNull(e, "target");
        if (e instanceof PlayerHandle ph) return new Target(ph.handle().entityId(), null, ph.handle());
        if (e instanceof EntityHandle eh) {
            if (!(eh.tasks().owner() instanceof org.domi.furnace.server.entity.LivingEntity le)) {
                throw new IllegalArgumentException("살아 있는 엔티티만 장비를 가진다 (지금 올라와 있지 않거나 장비가 없는 종류): " + e);
            }
            return new Target(le.id(), eh.tasks(), null);
        }
        throw new IllegalArgumentException("이 서버의 엔티티가 아니다: " + e);
    }

    @Override
    public void setEquipment(FurnacePlugin plugin, Player viewer, Entity target, org.domi.furnace.api.entity.EquipmentSlot slot, ItemStack item) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(item, "item");
        var v = viewer(viewer);
        Target t = target(target);
        var s = slot(slot);
        var stack = item.isEmpty() ? org.domi.furnace.server.entity.ItemStack.EMPTY : ApiConvert.item(item, host.core().world().itemRegistry());
        v.viewLayers().equipment.put(plugin, t.id, t, s, stack);
        v.send(new SetEquipment(t.id, List.of(new SetEquipment.Entry(s, stack))));   // 안 보이는 엔티티면 클라이언트가 무시한다
    }

    @Override
    public void clearEquipment(FurnacePlugin plugin, Player viewer, Entity target, org.domi.furnace.api.entity.EquipmentSlot slot) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        Target t = target(target);
        var s = slot(slot);
        if (v.viewLayers().equipment.remove(plugin, t.id, s)) restoreEquipment(v, t, Set.of(s));
    }

    @Override
    public void clearEquipment(FurnacePlugin plugin, Player viewer, Entity target) {
        Objects.requireNonNull(plugin, "plugin");
        var v = viewer(viewer);
        Target t = target(target);
        Set<org.domi.furnace.server.entity.EquipmentSlot> removed = v.viewLayers().equipment.removeEntity(plugin, t.id);
        if (!removed.isEmpty()) restoreEquipment(v, t, removed);
    }

    /** 걷은 칸의 진짜 장비를 그 엔티티를 가진 스레드에서 읽어 한 사람에게 다시 보낸다. */
    private static void restoreEquipment(org.domi.furnace.server.entity.Player v, Target t, Set<org.domi.furnace.server.entity.EquipmentSlot> slots) {
        Runnable send = () -> {
            List<SetEquipment.Entry> es = new ArrayList<>(slots.size());
            for (var s : slots) {
                org.domi.furnace.server.entity.ItemStack real;
                if (t.player != null) real = t.player.equipment(s);
                else if (t.tasks.owner() instanceof org.domi.furnace.server.entity.LivingEntity le) real = le.getItemBySlot(s);
                else return;
                es.add(new SetEquipment.Entry(s, real));
            }
            v.send(new SetEquipment(t.id, es));
        };
        if (t.player != null) t.player.runOnOwner(send);
        else t.tasks.schedule(send, null, 0, 0);
    }

    @Override
    public Optional<ItemStack> virtualEquipment(Player viewer, Entity target, org.domi.furnace.api.entity.EquipmentSlot slot) {
        var v = viewer(viewer);
        Target t = target(target);
        var m = v.viewLayers().equipment.of(t.id);
        var e = m == null ? null : m.get(slot(slot));
        return e == null ? Optional.empty() : Optional.of(ApiConvert.item(e.stack(), host.core().world().itemRegistry()));
    }

    // ------------------------------------------------------------------ 플러그인 정리

    /** 플러그인이 꺼질 때: 그 플러그인이 건 층을 모든 접속자에게서 거두고 화면을 맞춘다. */
    public void removeAll(FurnacePlugin plugin) {
        for (var p : host.core().players()) {
            ViewLayers layers = p.viewLayers();
            if (!layers.active()) continue;
            clearLayers(plugin, p);
            for (int id : layers.equipment.ids()) {
                Target t = (Target) layers.equipment.target(id);
                Set<org.domi.furnace.server.entity.EquipmentSlot> removed = layers.equipment.removeEntity(plugin, id);
                if (t != null && !removed.isEmpty()) restoreEquipment(p, t, removed);
            }
            if (plugin.equals(layers.borderOwner())) resetBorder(p);
        }
    }
}
