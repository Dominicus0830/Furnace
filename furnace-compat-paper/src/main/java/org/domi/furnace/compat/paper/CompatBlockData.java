package org.domi.furnace.compat.paper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

/** Furnace BlockData(종류 + 이름 기반 속성) 위의 Bukkit BlockData. NMS 상태와는 상태 번호로 오간다({@link CraftBlockData}). */
public final class CompatBlockData extends CraftBlockData {

    private final org.domi.furnace.api.world.BlockData data;
    /** 문자열에서 만들었으면 사용자가 적은 속성 이름, 세상에서 읽었으면 null(모두 적힘). */
    private final Set<String> specified;

    CompatBlockData(org.domi.furnace.api.world.BlockData data, Set<String> specified) {
        this.data = data;
        this.specified = specified;
    }

    public org.domi.furnace.api.world.BlockData furnace() { return data; }

    /** Material 의 기본 상태. 블록이 아니면 IllegalArgumentException. */
    public static BlockData create(Material material) {
        Objects.requireNonNull(material, "Material cannot be null");
        return new CompatBlockData(defaultOf(material), Set.of());
    }

    /** "oak_stairs[facing=north]" 같은 문자열(종류 앞 minecraft: 는 생략 가능) — 적지 않은 속성은 기본값. 잘못이면 IllegalArgumentException. */
    public static BlockData create(String text) {
        Objects.requireNonNull(text, "data cannot be null");
        org.domi.furnace.api.world.BlockData parsed = org.domi.furnace.api.world.BlockData.parse(text);
        return merged(parsed.type(), parsed.properties());
    }

    /** 종류와 속성 문자열("[facing=north]" 또는 "facing=north" 또는 null). */
    public static BlockData create(Material material, String properties) {
        Objects.requireNonNull(material, "Material cannot be null");
        if (properties == null || properties.isBlank()) return create(material);
        String p = properties.strip();
        if (!p.startsWith("[")) p = "[" + p + "]";
        return create(material.getKey() + p);
    }

    private static org.domi.furnace.api.world.BlockData defaultOf(Material m) {
        var t = Compat.furnace().blockTypes().get(m.getKey().toString());
        if (t.isEmpty() || !m.isBlock()) throw new IllegalArgumentException("Cannot get default block state for " + m);
        return t.get().defaultData();
    }

    private static BlockData merged(String type, Map<String, String> given) {
        var t = Compat.furnace().blockTypes().get(type);
        if (t.isEmpty()) throw new IllegalArgumentException("Could not parse data: unknown block " + type);
        Map<String, String> all = new TreeMap<>(t.get().defaultData().properties());
        for (var e : given.entrySet()) {
            var info = t.get().property(e.getKey());
            if (info.isEmpty()) throw new IllegalArgumentException("Could not parse data: " + type + " has no property " + e.getKey());
            if (!info.get().accepts(e.getValue())) throw new IllegalArgumentException("Could not parse data: " + e.getValue() + " is not a value of " + type + "[" + e.getKey() + "]");
            all.put(e.getKey(), e.getValue());
        }
        return new CompatBlockData(new org.domi.furnace.api.world.BlockData(type, all), Set.copyOf(given.keySet()));
    }

    static BlockData of(org.domi.furnace.api.world.BlockData data) { return new CompatBlockData(data, null); }

    /** NMS 상태 → 모든 속성이 적힌 BlockData. */
    public static CompatBlockData ofState(net.minecraft.world.level.block.state.BlockState state) {
        Objects.requireNonNull(state, "state");
        int id = state.furnaceStateId();
        var d = Compat.furnace().blockTypes().byStateId(id).orElseThrow(() -> new IllegalStateException("서버가 모르는 상태 번호 " + id));
        return new CompatBlockData(d, null);
    }

    @Override
    public net.minecraft.world.level.block.state.BlockState getState() {
        int id = Compat.furnace().blockTypes().stateId(data);
        if (id < 0) throw new IllegalStateException("서버가 모르는 상태 " + data);
        return net.minecraft.world.level.block.Block.stateById(id);
    }

    @Override
    public Material getMaterial() { return Compat.material(data.type()); }

    @Override
    public String getAsString() { return data.toString(); }

    @Override
    public String getAsString(boolean hideUnspecified) {
        if (!hideUnspecified || specified == null) return data.toString();
        Map<String, String> shown = new TreeMap<>();
        for (var e : data.properties().entrySet()) if (specified.contains(e.getKey())) shown.put(e.getKey(), e.getValue());
        return new org.domi.furnace.api.world.BlockData(data.type(), shown).toString();
    }

    @Override
    public boolean matches(BlockData other) {
        if (!(other instanceof CompatBlockData o) || !o.data.type().equals(data.type())) return false;
        for (var e : o.data.properties().entrySet()) {
            if (o.specified != null && !o.specified.contains(e.getKey())) continue;
            if (!e.getValue().equals(data.properties().get(e.getKey()))) return false;
        }
        return true;
    }

    @Override
    public CompatBlockData clone() { return new CompatBlockData(data, specified); }

    @Override
    public boolean equals(Object o) { return o instanceof CompatBlockData b && b.data.equals(data); }

    @Override
    public int hashCode() { return data.hashCode(); }

    @Override
    public String toString() { return "CraftBlockData{" + data + "}"; }
}
