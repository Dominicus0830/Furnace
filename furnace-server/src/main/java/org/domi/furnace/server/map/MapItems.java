// 바닐라 MapItem(filled_map)·EmptyMapItem 의 서버 쪽 동작: 지도 만들기, 인벤토리 틱(들고 있는 표식·색 갱신·갱신 패킷),
// 제작 뒤처리(잠금·확대 때 새 번호), 목표 표식. ItemStack 이 불변이라 바꾸는 메서드는 새 스택을 돌려준다.
package org.domi.furnace.server.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.MapItemData;

public final class MapItems {
    private MapItems() { }

    public static final String FILLED_MAP = "minecraft:filled_map", MAP_ID = "minecraft:map_id", DECORATIONS = "minecraft:map_decorations",
            POST_PROCESSING = "minecraft:map_post_processing", MAP_COLOR = "minecraft:map_color";

    private static volatile int filledMapItemId = -1;

    /** 지도 아이템인가 — 번호 비교(매 틱 인벤토리 41칸을 훑으므로 이름 조회를 피한다). */
    public static boolean isFilledMap(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        int id = filledMapItemId;
        if (id < 0) filledMapItemId = id = ItemContext.id("minecraft:item", FILLED_MAP);
        return s.itemId() == id;
    }

    /** 스택의 map_id(없으면 null). */
    public static Integer mapId(ItemStack s) {
        return ItemTools.component(s, MAP_ID) instanceof Number n ? n.intValue() : null;
    }

    public static ItemStack withMapId(ItemStack s, int id) { return ItemTools.with(s, MAP_ID, id); }

    /**
     * MapItem.create(level, x, z, scale, trackingPosition, unlimitedTracking): 새 지도 데이터를 만들어 번호를 받고(idcounts 한 칸),
     * 그 번호가 붙은 filled_map 한 장을 돌려준다.
     */
    public static ItemStack create(String dimension, int x, int z, byte scale, boolean tracking, boolean unlimited) {
        MapData data = MapData.createFresh(x, z, scale, tracking, unlimited, dimension);
        int id = MapStore.nextId();
        MapStore.put(id, data);
        Integer item = ItemContext.id("minecraft:item", FILLED_MAP);
        return withMapId(new ItemStack(item, 1), id);
    }

    /**
     * MapItemSavedData.addTargetDecoration(stack, pos, id, type): 스택의 map_decorations 에 목표 표식(위치 x,z 는 정수 블록 좌표, 회전 180)을 더하고,
     * 종류에 지도 색이 있으면 map_color 도 붙인다.
     */
    public static ItemStack addTargetDecoration(ItemStack stack, int x, int z, String id, MapDecorationType type) {
        Map<String, Object> all = new LinkedHashMap<>();
        if (ItemTools.component(stack, DECORATIONS) instanceof Map<?, ?> cur) {
            for (var e : cur.entrySet()) all.put(String.valueOf(e.getKey()), e.getValue());
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type.name());
        entry.put("x", (double) x);
        entry.put("z", (double) z);
        entry.put("rotation", 180.0F);
        all.put(id, entry);
        ItemStack out = ItemTools.with(stack, DECORATIONS, all);
        if (type.mapColor() != -1) out = ItemTools.with(out, MAP_COLOR, type.mapColor());
        return out;
    }

    /** 스택의 map_decorations → 표식 목록(없으면 빈 맵). */
    static Map<String, MapData.Marker> markers(ItemStack stack) {
        if (!(ItemTools.component(stack, DECORATIONS) instanceof Map<?, ?> m) || m.isEmpty()) return Map.of();
        Map<String, MapData.Marker> out = new LinkedHashMap<>();
        for (var e : m.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> v) || !(v.get("type") instanceof String t)) continue;
            MapDecorationType type = MapDecorationType.byName(t);
            if (type == null) continue;
            double x = v.get("x") instanceof Number n ? n.doubleValue() : 0.0, z = v.get("z") instanceof Number n ? n.doubleValue() : 0.0;
            float rot = v.get("rotation") instanceof Number n ? n.floatValue() : 0.0F;
            out.put(String.valueOf(e.getKey()), new MapData.Marker(type, x, z, rot));
        }
        return out;
    }

    /**
     * MapItem.onCraftedPostProcess(stack, level): map_post_processing 을 떼고 — lock 이면 잠근 복사본, scale 이면 한 단계 큰 지도를 새 번호로 두고
     * map_id 를 바꾼다. 지도 데이터가 없으면 표시만 뗀다. 표시가 없으면 그대로.
     */
    public static ItemStack postProcess(ItemStack stack) {
        if (!isFilledMap(stack)) return stack;
        Object mode = ItemTools.component(stack, POST_PROCESSING);
        if (mode == null) return stack;
        ItemStack out = ItemTools.with(stack, POST_PROCESSING, null);
        MapData data = MapStore.of(out);
        if (data == null) return out;
        int id = MapStore.nextId();
        if ("lock".equals(mode)) MapStore.put(id, data.lockedCopy());
        else if ("scale".equals(mode)) MapStore.put(id, data.scaled());
        else return out;
        return withMapId(out, id);
    }

    // ------------------------------------------------------------------ 액자 속 지도

    /**
     * ServerEntity.sendChanges 의 액자 지도 부분(10틱마다): 액자가 지도를 담고 있으면, 그 액자를 보는 플레이어마다 액자 표식을 갱신하고(tickCarriedBy, framed)
     * 갱신 패킷을 보낸다. 액자 표식은 위치와 액자 방향(2D 값 * 90)·엔티티 번호.
     */
    public static void tickFrame(org.domi.furnace.server.entity.ItemFrame frame, Iterable<Player> viewers, long dayTime) {
        ItemStack stack = frame.getItem();
        if (!isFilledMap(stack) || frame.pos() == null) return;
        Integer id = mapId(stack);
        MapData data = id == null ? null : MapStore.get(id);
        if (data == null) return;
        var pos = frame.pos();
        MapData.Frame info = new MapData.Frame(pos.x(), pos.y(), pos.z(), org.domi.furnace.server.block.Dir.to2D(frame.direction()) * 90, frame.id());
        Map<String, MapData.Marker> markers = markers(stack);
        for (Player p : viewers) {
            data.tickCarriedBy(p, stack, id, info, dayTime, markers);
            MapItemData packet = data.getUpdatePacket(id, p);
            if (packet != null) p.send(packet);
        }
    }

    // ------------------------------------------------------------------ 인벤토리 틱

    /**
     * Inventory.tick(지도 부분) + ServerPlayer.doTick 의 갱신 패킷 보내기. 칸 순서는 바닐라 Inventory: 핫바·주(0..35), 갑옷(36..39), 왼손(40).
     * 지도를 든 칸마다 표식 갱신(tickCarriedBy), 잠기지 않았고 선택한 칸(또는 왼손)이면 색 갱신, 그다음 칸마다 갱신 패킷.
     * terrain 은 지도를 실제로 칠할 때만 만든다.
     */
    public static void tick(Player p, Supplier<MapRenderer.Terrain> terrain, String dimension, long dayTime) {
        PlayerInventory inv = p.inventory();
        ItemStack[] stacks = null;   // 지도가 있을 때만 만든다(대부분의 틱은 지도가 없다)
        for (int i = 0; i < 41; i++) {
            ItemStack s = inv.get(PlayerInventory.windowSlot(i));
            if (isFilledMap(s)) {
                if (stacks == null) stacks = new ItemStack[41];
                stacks[i] = s;
            }
        }
        if (stacks == null) return;
        int selected = inv.selected();
        ItemStack offhand = inv.get(45);
        MapRenderer.Terrain t = null;
        for (int i = 0; i < 41; i++) {
            ItemStack s = stacks[i];
            if (s == null) continue;
            Integer id = mapId(s);
            MapData data = id == null ? null : MapStore.get(id);
            if (data == null) continue;
            data.tickCarriedBy(p, s, id, null, dayTime, markers(s));
            int compartmentIndex = i < 36 ? i : i < 40 ? i - 36 : 0;
            if (!data.locked() && (selected == compartmentIndex || offhand == s)) {
                if (t == null) t = terrain.get();
                MapRenderer.update(t, dimension, p.x(), p.z(), data, data.holding(p));
            }
        }
        for (int i = 0; i < 41; i++) {
            ItemStack s = stacks[i];
            if (s == null) continue;
            Integer id = mapId(s);
            MapData data = id == null ? null : MapStore.get(id);
            if (data == null) continue;
            MapItemData packet = data.getUpdatePacket(id, p);
            if (packet != null) p.send(packet);
        }
    }
}
