// 바닐라 CustomBossEvents — id → CustomBossEvent. level.dat Data.CustomBossEvents 에 바닐라와 같은 형식으로 저장한다.
// 항목: {Name(JSON 문자열), Visible, Value, Max, Color, Overlay, DarkenScreen, PlayBossMusic, CreateWorldFog, Players(int[4] UUID 목록)}.
package org.domi.furnace.server.bossbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class CustomBossEvents {

    private final Map<ResourceId, CustomBossEvent> events = new LinkedHashMap<>();
    private final Function<UUID, Player> lookup;

    /** @param lookup UUID 로 접속 중인 플레이어를 찾는다(없으면 null). */
    public CustomBossEvents(Function<UUID, Player> lookup) { this.lookup = lookup; }

    public synchronized CustomBossEvent get(ResourceId id) { return events.get(id); }

    /** 바닐라 create — 같은 id 를 덮어쓰지 않는 확인은 부르는 쪽 몫(명령이 먼저 확인한다). */
    public synchronized CustomBossEvent create(ResourceId id, Text name) {
        CustomBossEvent e = new CustomBossEvent(id, name, lookup);
        events.put(id, e);
        return e;
    }

    /** 바닐라 remove: 대상에게 제거 패킷을 보내고 맵에서 뺀다. */
    public synchronized void remove(CustomBossEvent e) {
        events.remove(e.id());
        e.removeAllPlayers();
    }

    public synchronized Collection<ResourceId> ids() { return new ArrayList<>(events.keySet()); }

    public synchronized Collection<CustomBossEvent> events() { return new ArrayList<>(events.values()); }

    public synchronized void onPlayerConnect(Player p) {
        for (CustomBossEvent e : events.values()) e.onPlayerConnect(p);
    }

    public synchronized void onPlayerDisconnect(Player p) {
        for (CustomBossEvent e : events.values()) e.onPlayerDisconnect(p);
    }

    // ------------------------------------------------------------------ 저장

    public synchronized NbtCompound save() {
        NbtCompound out = new NbtCompound();
        for (CustomBossEvent e : events.values()) {
            NbtCompound t = new NbtCompound();
            t.putString("Name", e.name().toJson());
            t.putBoolean("Visible", e.isVisible());
            t.putInt("Value", e.value());
            t.putInt("Max", e.max());
            t.putString("Color", e.color().key());
            t.putString("Overlay", e.overlay().key());
            t.putBoolean("DarkenScreen", e.darkenScreen());
            t.putBoolean("PlayBossMusic", e.playBossMusic());
            t.putBoolean("CreateWorldFog", e.createWorldFog());
            NbtList players = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_INT_ARRAY);
            for (UUID u : e.playerIds()) players.add(uuidToInts(u));
            t.putList("Players", players);
            out.putCompound(e.id().toString(), t);
        }
        return out;
    }

    /** 바닐라 load — 읽는 순서는 Visible, Value, Max, Color, Overlay, 옵션 3개, Players. */
    public synchronized void load(NbtCompound tag) {
        events.clear();
        if (tag == null) return;
        for (var entry : tag.tags().entrySet()) {
            if (!(entry.getValue() instanceof NbtCompound t)) continue;
            ResourceId id = ResourceId.tryParse(entry.getKey());
            if (id == null) continue;
            Text name;
            try {
                name = JsonTextSerializer.json().deserialize(t.getString("Name"));
            } catch (RuntimeException ex) {
                continue;
            }
            CustomBossEvent e = new CustomBossEvent(id, name, lookup);
            e.setVisible(t.getBoolean("Visible"));
            e.setValue(t.getIntOr0("Value"));
            e.setMax(t.getIntOr0("Max"));
            e.setColor(CustomBossEvent.Color.byName(t.tags().get("Color") instanceof String s ? s : ""));
            e.setOverlay(CustomBossEvent.Overlay.byName(t.tags().get("Overlay") instanceof String s ? s : ""));
            e.setDarkenScreen(t.getBoolean("DarkenScreen"));
            e.setPlayBossMusic(t.getBoolean("PlayBossMusic"));
            e.setCreateWorldFog(t.getBoolean("CreateWorldFog"));
            if (t.tags().get("Players") instanceof NbtList list) {
                for (Object o : list.items()) if (o instanceof int[] a && a.length == 4) e.addOfflinePlayer(uuidFromInts(a));
            }
            events.put(id, e);
        }
    }

    public static int[] uuidToInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    public static UUID uuidFromInts(int[] a) {
        return new UUID((long) a[0] << 32 | a[1] & 0xFFFFFFFFL, (long) a[2] << 32 | a[3] & 0xFFFFFFFFL);
    }

    /** 테스트·진단용: 저장된 목록 이름들. */
    public synchronized List<String> idStrings() {
        List<String> out = new ArrayList<>();
        for (ResourceId id : events.keySet()) out.add(id.toString());
        return out;
    }
}
