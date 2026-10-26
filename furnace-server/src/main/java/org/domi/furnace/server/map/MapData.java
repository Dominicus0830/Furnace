// 바닐라 MapItemSavedData — 지도 한 장의 저장 데이터: 중심·배율·차원, 128x128 색 바이트, 표식(decoration), 깃발·액자 목록.
// 저장 형식은 바닐라 data/map_<id>.dat({data:{dimension, xCenter, zCenter, scale, colors, trackingPosition, unlimitedTracking, locked, banners, frames}}).
// 여러 region 스레드(지도를 든 플레이어마다)가 같은 지도를 만질 수 있어 변경·읽기 메서드는 모두 잠근다.
package org.domi.furnace.server.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.MapItemData;

public final class MapData {

    public static final int WIDTH = 128, HEIGHT = 128, MAX_BANNERS_OR_FRAMES = 256;
    public static final String OVERWORLD = "minecraft:overworld", THE_END = "minecraft:the_end";

    /** MapDecoration(eqn): 종류·위치(-128..127, 절반 칸)·회전(0..15). 이름은 깃발에만 있다. */
    public record Decoration(MapDecorationType type, byte x, byte y, byte rot, Text name) {
        public Decoration {
            rot = (byte) (rot & 15);
        }
    }

    /** 아이템의 map_decorations 컴포넌트 항목(MapDecorations.Entry). */
    public record Marker(MapDecorationType type, double x, double z, float rotation) { }

    /** MapBanner: 깃발 위치·색(DyeColor 순서)·이름(JSON 문자열 그대로 보존). */
    public record Banner(int x, int y, int z, String color, String nameJson) {
        String key() { return "banner-" + x + "," + y + "," + z; }

        MapDecorationType decorationType() {
            for (int i = 0; i < MapDecorationType.DYES.length; i++) if (MapDecorationType.DYES[i].equals(color)) return MapDecorationType.banner(i);
            return MapDecorationType.banner(0);
        }
    }

    /** MapFrame: 액자 위치·방향(그 액자의 2D 방향값 * 90)·엔티티 id. */
    public record Frame(int x, int y, int z, int rotation, int entityId) {
        public static String keyOf(int x, int y, int z) { return "frame-" + x + "," + y + "," + z; }

        String key() { return keyOf(x, y, z); }
    }

    /** 지도를 가진 플레이어 쪽 입력(Player 어댑터 — 시험에서는 가짜). */
    public interface Viewer {
        String name();

        /** Entity.isRemoved. */
        boolean removed();

        String dimension();

        double x();

        double z();

        float yaw();

        /** inventory.contains(predicate) — 칸 전체(갑옷·왼손 포함)에서 하나라도 맞는가. */
        boolean holdsMap(Predicate<ItemStack> predicate);
    }

    private final int centerX, centerZ;
    private final String dimension;
    private final byte scale;
    private final boolean trackingPosition, unlimitedTracking, locked;
    private final byte[] colors = new byte[WIDTH * HEIGHT];
    private final List<Holding> carriedBy = new ArrayList<>();
    private final Map<Viewer, Holding> carriedByPlayers = new java.util.HashMap<>();
    private final Map<String, Banner> bannerMarkers = new java.util.HashMap<>();
    private final Map<String, Decoration> decorations = new LinkedHashMap<>();
    private final Map<String, Frame> frameMarkers = new java.util.HashMap<>();
    private int trackedDecorationCount;
    private volatile boolean dirty;

    private MapData(int x, int z, byte scale, boolean tracking, boolean unlimited, boolean locked, String dimension) {
        this.scale = scale;
        this.centerX = x;
        this.centerZ = z;
        this.dimension = dimension;
        this.trackingPosition = tracking;
        this.unlimitedTracking = unlimited;
        this.locked = locked;
        setDirty();
    }

    /** MapItemSavedData.createFresh: 플레이어 위치를 배율 격자에 맞춰 중심으로 삼는다. */
    public static MapData createFresh(double x, double z, byte scale, boolean tracking, boolean unlimited, String dimension) {
        int size = 128 * (1 << scale);
        int ix = (int) Math.floor((x + 64.0) / size);
        int iz = (int) Math.floor((z + 64.0) / size);
        int cx = ix * size + size / 2 - 64;
        int cz = iz * size + size / 2 - 64;
        return new MapData(cx, cz, scale, tracking, unlimited, false, dimension);
    }

    public int centerX() { return centerX; }

    public int centerZ() { return centerZ; }

    public String dimension() { return dimension; }

    public byte scale() { return scale; }

    public boolean locked() { return locked; }

    public boolean trackingPosition() { return trackingPosition; }

    public boolean unlimitedTracking() { return unlimitedTracking; }

    /** 색 바이트 배열(직접 읽기 — 쓰기는 updateColor/setColor 로). */
    public byte[] colors() { return colors; }

    public synchronized byte color(int x, int y) { return colors[x + y * WIDTH]; }

    public boolean isDirty() { return dirty; }

    public void setDirty(boolean d) { dirty = d; }

    public void setDirty() { dirty = true; }

    // ------------------------------------------------------------------ 저장

    /** MapItemSavedData.load: dimension 이 없으면(옛 시험 파일) 오버월드로 읽는다. */
    public static MapData load(NbtCompound tag) {
        String dim = tag.tags().get("dimension") instanceof String s ? s : OVERWORLD;
        int x = tag.getIntOr0("xCenter"), z = tag.getIntOr0("zCenter");
        byte scale = (byte) Math.clamp(tag.getIntOr0("scale"), 0, 4);
        boolean tracking = !(tag.tags().get("trackingPosition") instanceof Number) || tag.getBoolean("trackingPosition");
        boolean unlimited = tag.getBoolean("unlimitedTracking"), locked = tag.getBoolean("locked");
        MapData d = new MapData(x, z, scale, tracking, unlimited, locked, dim);
        if (tag.tags().get("colors") instanceof byte[] c && c.length == WIDTH * HEIGHT) System.arraycopy(c, 0, d.colors, 0, c.length);
        if (tag.tags().get("banners") instanceof NbtList banners) {
            for (Object o : banners.items()) {
                if (!(o instanceof NbtCompound b) || !(b.tags().get("pos") instanceof int[] p) || p.length < 3) continue;
                String color = b.tags().get("color") instanceof String s ? s : "white";
                String name = b.tags().get("name") instanceof String s ? s : null;
                Banner banner = new Banner(p[0], p[1], p[2], color, name);
                d.bannerMarkers.put(banner.key(), banner);
                d.addDecoration(banner.decorationType(), null, banner.key(), p[0], p[2], 180.0, null);
            }
        }
        if (tag.tags().get("frames") instanceof NbtList frames) {
            for (Object o : frames.items()) {
                if (!(o instanceof NbtCompound f) || !(f.tags().get("pos") instanceof int[] p) || p.length < 3) continue;
                Frame frame = new Frame(p[0], p[1], p[2], f.getIntOr0("rotation"), f.getIntOr0("entity_id"));
                d.frameMarkers.put(frame.key(), frame);
                d.addDecoration(MapDecorationType.FRAME, null, "frame-" + frame.entityId(), p[0], p[2], frame.rotation(), null);
            }
        }
        d.dirty = false;   // 읽은 직후는 저장할 것이 없다
        return d;
    }

    /** MapItemSavedData.save — data 안쪽 컴파운드. */
    public synchronized NbtCompound save() {
        NbtCompound tag = new NbtCompound();
        tag.putString("dimension", dimension);
        tag.putInt("xCenter", centerX);
        tag.putInt("zCenter", centerZ);
        tag.putByte("scale", scale);
        tag.putByteArray("colors", colors.clone());
        tag.putBoolean("trackingPosition", trackingPosition);
        tag.putBoolean("unlimitedTracking", unlimitedTracking);
        tag.putBoolean("locked", locked);
        NbtList banners = new NbtList((byte) (bannerMarkers.isEmpty() ? 0 : 10));
        for (Banner b : bannerMarkers.values()) {
            NbtCompound c = new NbtCompound();
            c.putIntArray("pos", new int[] {b.x(), b.y(), b.z()});
            c.putString("color", b.color());
            if (b.nameJson() != null) c.putString("name", b.nameJson());
            banners.add(c);
        }
        tag.putList("banners", banners);
        NbtList frames = new NbtList((byte) (frameMarkers.isEmpty() ? 0 : 10));
        for (Frame f : frameMarkers.values()) {
            NbtCompound c = new NbtCompound();
            c.putIntArray("pos", new int[] {f.x(), f.y(), f.z()});
            c.putInt("rotation", f.rotation());
            c.putInt("entity_id", f.entityId());
            frames.add(c);
        }
        tag.putList("frames", frames);
        return tag;
    }

    // ------------------------------------------------------------------ 복사

    /** MapItemSavedData.locked(): 잠근 복사본(깃발·표식·색 그대로, 액자 목록은 없다). */
    public synchronized MapData lockedCopy() {
        MapData d = new MapData(centerX, centerZ, scale, trackingPosition, unlimitedTracking, true, dimension);
        d.bannerMarkers.putAll(bannerMarkers);
        d.decorations.putAll(decorations);
        d.trackedDecorationCount = trackedDecorationCount;
        System.arraycopy(colors, 0, d.colors, 0, colors.length);
        d.setDirty();
        return d;
    }

    /** MapItemSavedData.scaled(): 배율 + 1(최대 4)의 새 지도(같은 중심 칸의 더 큰 격자), 색은 비어 있다. */
    public MapData scaled() {
        return createFresh(centerX, centerZ, (byte) Math.clamp(scale + 1, 0, 4), trackingPosition, unlimitedTracking, dimension);
    }

    // ------------------------------------------------------------------ 색

    /** updateColor(x, y, color): 바뀌었으면 쓰고 참. */
    public synchronized boolean updateColor(int x, int y, byte color) {
        if (colors[x + y * WIDTH] != color) {
            setColor(x, y, color);
            return true;
        }
        return false;
    }

    public synchronized void setColor(int x, int y, byte color) {
        colors[x + y * WIDTH] = color;
        setColorsDirty(x, y);
    }

    private void setColorsDirty(int x, int y) {
        setDirty();
        for (Holding h : carriedBy) h.markColorsDirty(x, y);
    }

    private void setDecorationsDirty() {
        setDirty();
        for (Holding h : carriedBy) h.markDecorationsDirty();
    }

    /** checkBanners(level, x, z): 그 열의 깃발이 사라졌으면 표식을 지운다. bannerAt 은 그 칸에 지금 깃발 블록이 있는가. */
    public synchronized void checkBanners(java.util.function.BiPredicate<int[], Banner> stillThere, int x, int z) {
        if (bannerMarkers.isEmpty()) return;
        var it = bannerMarkers.values().iterator();
        while (it.hasNext()) {
            Banner b = it.next();
            if (b.x() == x && b.z() == z && !stillThere.test(new int[] {b.x(), b.y(), b.z()}, b)) {
                it.remove();
                removeDecoration(b.key());
            }
        }
    }

    // ------------------------------------------------------------------ 표식

    public synchronized Map<String, Decoration> decorations() { return new LinkedHashMap<>(decorations); }

    public synchronized Frame frame(int x, int y, int z) { return frameMarkers.get(Frame.keyOf(x, y, z)); }

    public synchronized Iterable<Banner> banners() { return new ArrayList<>(bannerMarkers.values()); }

    /** isExplorationMap: 탐험 지도 표식(mansion·monument·village·trial_chambers …)이 하나라도 있다. */
    public synchronized boolean isExplorationMap() {
        for (Decoration d : decorations.values()) if (d.type().explorationMapElement()) return true;
        return false;
    }

    /** isTrackedCountOverLimit(256). */
    public synchronized boolean isTrackedCountOverLimit(int limit) { return trackedDecorationCount >= limit; }

    private void removeDecoration(String id) {
        Decoration d = decorations.remove(id);
        if (d != null && d.type().trackCount()) trackedDecorationCount--;
        setDecorationsDirty();
    }

    /**
     * addDecoration(type, level, id, x, z, rot, name): 지도 칸으로 환산해 넣는다. 지도 밖이면 플레이어만 가장자리 표식(PLAYER_OFF_MAP,
     * 무제한 추적이면 멀리서도 PLAYER_OFF_LIMITS)으로, 나머지는 지운다. dayTime: 엔드 지도의 회전 흔들림용(레벨 시각, 모르면 null).
     */
    public synchronized void addDecoration(MapDecorationType type, Long dayTime, String id, double x, double z, double rot, Text name) {
        int i = 1 << scale;
        float f = (float) (x - centerX) / i;
        float g = (float) (z - centerZ) / i;
        byte b = (byte) (f * 2.0F + 0.5);
        byte c = (byte) (g * 2.0F + 0.5);
        byte d;
        if (f >= -63.0F && g >= -63.0F && f <= 63.0F && g <= 63.0F) {
            rot += rot < 0.0 ? -8.0 : 8.0;
            d = (byte) (rot * 16.0 / 360.0);
            if (THE_END.equals(dimension) && dayTime != null) {
                int k = (int) (dayTime / 10L);
                d = (byte) (k * k * 34187121 + k * 121 >> 15 & 15);
            }
        } else {
            if (type != MapDecorationType.PLAYER) {
                removeDecoration(id);
                return;
            }
            if (Math.abs(f) < 320.0F && Math.abs(g) < 320.0F) {
                type = MapDecorationType.PLAYER_OFF_MAP;
            } else {
                if (!unlimitedTracking) {
                    removeDecoration(id);
                    return;
                }
                type = MapDecorationType.PLAYER_OFF_LIMITS;
            }
            d = 0;
            if (f <= -63.0F) b = -128;
            if (g <= -63.0F) c = -128;
            if (f >= 63.0F) b = 127;
            if (g >= 63.0F) c = 127;
        }
        Decoration nd = new Decoration(type, b, c, d, name);
        Decoration old = decorations.put(id, nd);
        if (!nd.equals(old)) {
            if (old != null && old.type().trackCount()) trackedDecorationCount--;
            if (type.trackCount()) trackedDecorationCount++;
            setDecorationsDirty();
        }
    }

    /** MapItemSavedData.addDecorationsNbt/addTargetDecoration 의 데이터 쪽: 액자에 걸린 지도를 위해 액자 표식을 지운다(removedFromFrame). */
    public synchronized void removedFromFrame(int x, int y, int z, int entityId) {
        removeDecoration("frame-" + entityId);
        frameMarkers.remove(Frame.keyOf(x, y, z));
        setDirty();
    }

    // ------------------------------------------------------------------ 들고 있는 플레이어

    /** 이 스택과 같은 지도인가 — 같은 스택이거나, 지도 아이템이고 map_id 가 같다(MapItemSavedData.mapMatcher). */
    private static Predicate<ItemStack> mapMatcher(ItemStack stack, Integer mapId) {
        return s -> s == stack || (MapItems.isFilledMap(s) && Objects.equals(mapId, MapItems.mapId(s)));
    }

    /**
     * tickCarriedBy(player, stack): 이 플레이어를 들고 있는 사람으로 등록하고, 같은 지도를 가진 사람마다 위치 표식을 갱신하며,
     * 안 가진/사라진 사람은 뺀다. frame 이 있으면 액자 속 지도(액자 표식 갱신, 플레이어 표식은 안 단다). stackMarkers 는 스택의 map_decorations.
     */
    public synchronized void tickCarriedBy(Viewer player, ItemStack stack, Integer mapId, Frame frame, Long dayTime, Map<String, Marker> stackMarkers) {
        Holding hp = carriedByPlayers.get(player);
        if (hp == null) {
            hp = new Holding(player);
            carriedByPlayers.put(player, hp);
            carriedBy.add(hp);
        }
        Predicate<ItemStack> matcher = mapMatcher(stack, mapId);
        if (!player.holdsMap(matcher)) removeDecoration(player.name());
        boolean framed = frame != null;
        for (int i = 0; i < carriedBy.size(); i++) {
            Holding h = carriedBy.get(i);
            String name = h.player.name();
            if (!h.player.removed() && (h.player.holdsMap(matcher) || framed)) {
                if (!framed && h.player.dimension().equals(dimension) && trackingPosition) {
                    addDecoration(MapDecorationType.PLAYER, dayTime, name, h.player.x(), h.player.z(), h.player.yaw(), null);
                }
            } else {
                carriedByPlayers.remove(h.player);
                carriedBy.remove(h);
                removeDecoration(name);
            }
        }
        if (framed && trackingPosition) {
            Frame existing = frameMarkers.get(frame.key());
            if (existing != null && frame.entityId() != existing.entityId() && frameMarkers.containsKey(existing.key())) {
                removeDecoration("frame-" + existing.entityId());
            }
            frameMarkers.put(frame.key(), frame);
            addDecoration(MapDecorationType.FRAME, dayTime, "frame-" + frame.entityId(), frame.x(), frame.z(), frame.rotation(), null);
            setDirty();
        }
        if (stackMarkers != null && !stackMarkers.isEmpty() && !decorations.keySet().containsAll(stackMarkers.keySet())) {
            for (var e : stackMarkers.entrySet()) {
                if (!decorations.containsKey(e.getKey())) {
                    Marker m = e.getValue();
                    addDecoration(m.type(), dayTime, e.getKey(), m.x(), m.z(), m.rotation(), null);
                }
            }
        }
    }

    /** 이 플레이어의 들고 있는 상태(없으면 만든다 — 갱신 격자 단계 step 을 올릴 때). */
    public synchronized Holding holding(Viewer player) {
        Holding hp = carriedByPlayers.get(player);
        if (hp == null) {
            hp = new Holding(player);
            carriedByPlayers.put(player, hp);
            carriedBy.add(hp);
        }
        return hp;
    }

    /** getUpdatePacket(mapId, player): 이 플레이어가 아직 이 지도를 든 것으로 등록돼 있지 않으면 null. */
    public synchronized MapItemData getUpdatePacket(int mapId, Viewer player) {
        Holding hp = carriedByPlayers.get(player);
        return hp == null ? null : hp.nextUpdatePacket(mapId);
    }

    /** 바닐라 HoldingPlayer — 플레이어별로 바뀐 사각형·표식 갱신 여부와 갱신 격자 단계. */
    public final class Holding {
        public final Viewer player;
        private boolean dirtyData = true;
        private int minDirtyX = 0, minDirtyY = 0, maxDirtyX = 127, maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        /** 이 플레이어가 이 지도를 갱신한 횟수 — 열 격자 (x & 15) == (step & 15) 에 쓴다. */
        public int step;

        Holding(Viewer player) { this.player = player; }

        private MapItemData.Patch createPatch() {
            int x = minDirtyX, y = minDirtyY;
            int w = maxDirtyX + 1 - minDirtyX, h = maxDirtyY + 1 - minDirtyY;
            byte[] data = new byte[w * h];
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) data[i + j * w] = colors[x + i + (y + j) * WIDTH];
            }
            return new MapItemData.Patch(x, y, w, h, data);
        }

        MapItemData nextUpdatePacket(int mapId) {
            MapItemData.Patch patch;
            if (dirtyData) {
                dirtyData = false;
                patch = createPatch();
            } else {
                patch = null;
            }
            List<Decoration> decos;
            if (dirtyDecorations && tick++ % 5 == 0) {
                dirtyDecorations = false;
                decos = new ArrayList<>(decorations.values());
            } else {
                decos = null;
            }
            return decos == null && patch == null ? null : new MapItemData(mapId, scale, locked, decos, patch);
        }

        void markColorsDirty(int x, int y) {
            if (dirtyData) {
                minDirtyX = Math.min(minDirtyX, x);
                minDirtyY = Math.min(minDirtyY, y);
                maxDirtyX = Math.max(maxDirtyX, x);
                maxDirtyY = Math.max(maxDirtyY, y);
            } else {
                dirtyData = true;
                minDirtyX = x;
                minDirtyY = y;
                maxDirtyX = x;
                maxDirtyY = y;
            }
        }

        void markDecorationsDirty() { dirtyDecorations = true; }
    }
}
