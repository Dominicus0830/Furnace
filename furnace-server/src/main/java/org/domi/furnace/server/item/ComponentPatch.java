// 아이템 기본값 대비 바뀐 컴포넌트(바닐라 DataComponentPatch) — 불변. 값은 정규 값, null 은 "기본값을 지움" 표시.
// 순서는 넣은 순서(바닐라 Reference2ObjectArrayMap). 네트워크 바이트는 처음 쓸 때 한 번 만들어 둔다.
package org.domi.furnace.server.item;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class ComponentPatch {

    public static final ComponentPatch EMPTY = new ComponentPatch(new LinkedHashMap<>());

    private final Map<String, Object> entries;
    private volatile byte[] network;
    private int hash;
    /**
     * 플러그인 API 모양 — 처음 {@link #apiForm()} 때 한 번 만든다(T-033). volatile 이 아니어도 되는 까닭: ApiForm 은 final 필드뿐이고
     * 그 필드가 가리키는 맵·집합은 생성자 전에 다 채워진다 — final 필드 규칙(JLS 17.5)으로 경합 공개된 참조로도 다 채운 내용이 보인다.
     */
    private ApiForm api;

    private ComponentPatch(LinkedHashMap<String, Object> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    /** 정규 값으로 된 패치(검사·정규화 없이 받는다 — 값을 고치면 안 된다). */
    public static ComponentPatch ofNormalized(Map<String, Object> entries) {
        return entries.isEmpty() ? EMPTY : new ComponentPatch(new LinkedHashMap<>(entries));
    }

    /** 이름 → 값(null = 지움) 모양의 패치를 종류별 코덱으로 정규화해서 만든다. */
    public static ComponentPatch of(Map<String, Object> raw) {
        if (raw.isEmpty()) return EMPTY;
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            ComponentType t = DataComponents.require(e.getKey());
            out.put(t.name(), e.getValue() == null ? null : t.normalize(e.getValue()));
        }
        return new ComponentPatch(out);
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    public int size() { return entries.size(); }

    /** 이름 → 정규 값(null = 지움). 읽기 전용. */
    public Map<String, Object> entries() { return entries; }

    // ------------------------------------------------------------------ 저장(DataComponentPatch.CODEC)

    /** {"minecraft:x": 값, "!minecraft:y": {}} 꼴(JSON 꼴·NBT 꼴 모두). 저장하지 않는 종류는 오류. */
    public static ComponentPatch fromCodec(Object in) {
        Map<String, Object> m = Val.map(in);
        if (m.isEmpty()) return EMPTY;
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var e : m.entrySet()) {
            String key = e.getKey();
            boolean removed = key.startsWith("!");
            org.domi.furnace.server.data.ResourceId rid = org.domi.furnace.server.data.ResourceId.tryParse(removed ? key.substring(1) : key);
            ComponentType t = rid == null ? null : DataComponents.byName(rid.toString());
            if (t == null) throw new CodecException("No component with type: '" + rid + "'");
            if (t.isTransient()) throw new CodecException("'" + t.name() + "' is not a persistent component");
            if (!seen.add((removed ? "!" : "") + t.name())) throw new CodecException("Duplicate entry for key: '" + key + "'");
            if (removed && !Val.isMap(e.getValue())) throw new CodecException("Not a map: " + Val.describe(e.getValue()));
            out.put(t.name(), removed ? null : t.persistent().normalize(e.getValue()));   // "x" 와 "!x" 는 나중 것이 앞 자리에서 이긴다
        }
        return new ComponentPatch(out);
    }

    /** DataComponentPatch.CODEC 를 NbtOps 로 쓴 꼴. */
    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        for (var e : entries.entrySet()) {
            ComponentType t = DataComponents.require(e.getKey());
            if (t.isTransient()) continue;
            if (e.getValue() == null) c.put("!" + t.name(), new NbtCompound());
            else c.put(t.name(), t.persistent().toNbt(e.getValue()));
        }
        return c;
    }

    /** JSON 꼴 정규 값({"minecraft:x": 값, "!minecraft:y": {}}). */
    public Map<String, Object> toCodecMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (var e : entries.entrySet()) {
            if (DataComponents.require(e.getKey()).isTransient()) continue;
            if (e.getValue() == null) m.put("!" + e.getKey(), Map.of());
            else m.put(e.getKey(), e.getValue());
        }
        return m;
    }

    // ------------------------------------------------------------------ 네트워크(DataComponentPatch.STREAM_CODEC)

    /** 더한 수, 지운 수, (종류 id, 값)…, 지운 종류 id… */
    public void write(ByteBuf buf) {
        byte[] b = network;
        if (b == null) network = b = encode();
        buf.writeBytes(b);
    }

    /** 네트워크 바이트(캐시된 배열의 사본). */
    public byte[] networkBytes() {
        byte[] b = network;
        if (b == null) network = b = encode();
        return b.clone();
    }

    private byte[] encode() {
        if (entries.isEmpty()) return new byte[] {0, 0};
        ByteBuf out = Unpooled.buffer();
        try {
            int added = 0;
            for (Object v : entries.values()) if (v != null) added++;
            Types.writeVarInt(out, added);
            Types.writeVarInt(out, entries.size() - added);
            for (var e : entries.entrySet()) {
                if (e.getValue() == null) continue;
                ComponentType t = DataComponents.require(e.getKey());
                Types.writeVarInt(out, DataComponents.networkId(t));
                t.network().write(out, e.getValue());
            }
            for (var e : entries.entrySet()) {
                if (e.getValue() != null) continue;
                Types.writeVarInt(out, DataComponents.networkId(DataComponents.require(e.getKey())));
            }
            byte[] b = new byte[out.readableBytes()];
            out.readBytes(b);
            return b;
        } finally {
            out.release();
        }
    }

    public static ComponentPatch read(ByteBuf buf) {
        int added = Types.readVarInt(buf);
        int removed = Types.readVarInt(buf);
        if (added == 0 && removed == 0) return EMPTY;
        if (added < 0 || removed < 0 || added + removed > DataComponents.count() * 2) {
            throw new CodecException("Invalid component patch size: " + added + "+" + removed);
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (int k = 0; k < added; k++) {
            ComponentType t = DataComponents.byNetworkId(Types.readVarInt(buf));
            out.put(t.name(), t.network().read(buf));
        }
        for (int k = 0; k < removed; k++) {
            ComponentType t = DataComponents.byNetworkId(Types.readVarInt(buf));
            out.put(t.name(), null);
        }
        return new ComponentPatch(out);
    }

    // ------------------------------------------------------------------ 기본값 대비 정리

    /**
     * 바닐라 PatchedDataComponentMap.applyPatch: 기본값과 같은 값은 빼고, 기본값이 없는 것을 지우는 표시도 뺀다.
     * 바뀐 것이 없으면 자기 자신.
     */
    public ComponentPatch normalizedFor(String item) {
        if (entries.isEmpty() || item == null) return this;
        LinkedHashMap<String, Object> out = null;
        int k = 0;
        for (var e : entries.entrySet()) {
            Object def = ItemPrototypes.defaultValue(item, e.getKey());
            boolean drop = e.getValue() == null ? def == null : DataComponents.require(e.getKey()).same(e.getValue(), def);
            if (drop && out == null) {
                out = new LinkedHashMap<>();
                int j = 0;
                for (var p : entries.entrySet()) {
                    if (j++ >= k) break;
                    out.put(p.getKey(), p.getValue());
                }
            } else if (!drop && out != null) out.put(e.getKey(), e.getValue());
            k++;
        }
        if (out == null) return this;
        return out.isEmpty() ? EMPTY : new ComponentPatch(out);
    }

    // ------------------------------------------------------------------ 플러그인 API 모양(T-033, O09-01 (a))

    /**
     * 플러그인 API 아이템의 컴포넌트 모양: 붙은 것 이름 → SNBT, 지운 것 이름 — 둘 다 이름순, 읽기 전용.
     * 패치가 불변이라 한 번 만든 모양을 계속 쓴다(인챈트·이름 아이템 변환 때마다 Snbt.write 를 하던 비용).
     */
    public record ApiForm(java.util.SortedMap<String, String> components, java.util.SortedSet<String> removed) {
        static final ApiForm EMPTY = new ApiForm(Collections.emptySortedMap(), Collections.emptySortedSet());
    }

    /**
     * 이보다 SNBT 글자 수 합이 크면 모양을 저장하지 않고 부를 때마다 만든다(PR #323 검토 R3). 캐시는 패치 수명 동안 SNBT 를 붙잡으므로
     * 책 100쪽(SNBT 약 2.6만 자)·책 든 셜커(약 70만 자) 같은 큰 패치가 청크에 남아 있는 내내 메모리를 더 쓰게 된다. 뜨거운 경우(인챈트·이름
     * 붙은 도구 약 100~300자)는 한참 아래다. 큰 패치는 변환 자체가 비싸 캐시 이득 비율은 같지만, 드물고 메모리 비용이 커서 저장하지 않는다.
     */
    static final int API_FORM_CACHE_LIMIT = 8192;

    /**
     * API 모양. 옛 ApiConvert.item 과 같은 순서로 같은 값을 만든다(값 → 코덱 NBT → SNBT). 변환이 예외를 내면 저장하지 않고 그대로 던진다.
     * 경합해서 둘이 만들어도 내용이 같으므로 아무 쪽이나 남는다. 큰 모양(API_FORM_CACHE_LIMIT 초과)은 저장하지 않는다.
     */
    public ApiForm apiForm() {
        ApiForm a = api;
        if (a == null) {
            if (entries.isEmpty()) return ApiForm.EMPTY;
            java.util.TreeMap<String, String> comps = new java.util.TreeMap<>();
            java.util.TreeSet<String> removed = new java.util.TreeSet<>();
            long chars = 0;
            for (var e : entries.entrySet()) {
                if (e.getValue() == null) removed.add(e.getKey());
                else {
                    String snbt = Snbt.write(apiNbt(e.getKey(), e.getValue()));
                    chars += snbt.length();
                    comps.put(e.getKey(), snbt);
                }
            }
            a = new ApiForm(Collections.unmodifiableSortedMap(comps), Collections.unmodifiableSortedSet(removed));
            if (chars <= API_FORM_CACHE_LIMIT) api = a;
        }
        return a;
    }

    /**
     * 패치의 정규 값은 NBT 가 아니라 코덱의 값(마법 부여는 Map 등)일 수 있다 — 저장할 때처럼 그 컴포넌트의 코덱으로 NBT 로 바꾼다.
     * 저장되지 않는(transient) 컴포넌트는 값이 이미 NBT 꼴이다.
     */
    private static Object apiNbt(String component, Object value) {
        ComponentType t = DataComponents.byName(component);
        return t == null || t.isTransient() ? value : t.persistent().toNbt(value);
    }

    // ------------------------------------------------------------------ 같음

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentPatch p) || p.entries.size() != entries.size()) return false;
        for (var e : entries.entrySet()) {
            if (!p.entries.containsKey(e.getKey())) return false;
            Object a = e.getValue(), b = p.entries.get(e.getKey());
            if (a == null || b == null ? a != b : !DataComponents.require(e.getKey()).same(a, b)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && !entries.isEmpty()) {
            for (var e : entries.entrySet()) h += e.getKey().hashCode() ^ DataComponents.require(e.getKey()).hash(e.getValue());
            hash = h;
        }
        return h;
    }

    @Override
    public String toString() { return entries.isEmpty() ? "{}" : toCodecMap().toString(); }
}
