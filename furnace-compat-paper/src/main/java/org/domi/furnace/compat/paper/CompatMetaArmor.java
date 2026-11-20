// Bukkit ArmorMeta(투구·갑옷·바지·신발, Paper ARMOR) — 방어구 장식은 minecraft:trim 컴포넌트에서 읽고 쓴다.
// Paper 처럼 장식의 "툴팁에 보이기"는 메타 안에서 ItemFlag.HIDE_ARMOR_TRIM 으로만 다룬다(컴포넌트의 show_in_tooltip 은 적용할 때 쓴다).
package org.domi.furnace.compat.paper;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.domi.furnace.api.item.ItemComponent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.Snbt;

@DelegateDeserialization(SerializableMeta.class)
public class CompatMetaArmor extends CompatItemMeta implements ArmorMeta {

    static final String TRIM_ID = "minecraft:trim";
    /** 서버 코덱을 거치는 날 SNBT 컴포넌트(기본값 show_in_tooltip=true 는 적히지 않는 정규 모양으로 돌려준다). */
    private static final ItemComponent<String> TRIM = ItemComponent.raw("trim");

    private final MetaKind kind;

    CompatMetaArmor(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        this(MetaKind.ARMOR, work, flags, pdc);
    }

    CompatMetaArmor(MetaKind kind, ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        super(work, flags, pdc);
        this.kind = kind;
        normalizeTrim();
    }

    @Override
    MetaKind kind() { return kind; }

    /**
     * Paper 의 메타는 아이템에서 읽을 때 재료·무늬가 모두 레지스트리 값이면 장식을 들고(툴팁에 안 보이면 HIDE_ARMOR_TRIM 을 더한다), 아니면
     * 장식을 버린다(인라인 재료·무늬나 모르는 키). 같은 결과가 되도록 만들 때 맞춰 두고, 보이기 여부는 표시 항목 하나로만 들고 있는다
     * (그래야 표시 항목을 뺀 메타를 복제해도 다시 켜지지 않는다).
     */
    private void normalizeTrim() {
        NbtCompound raw = rawTrim();
        if (raw == null) return;
        ArmorTrim trim = toTrim(raw);
        if (trim == null) {
            clearComponent(TRIM_ID);
            return;
        }
        if (raw.contains("show_in_tooltip") && !raw.getBoolean("show_in_tooltip")) {
            flags.add(ItemFlag.HIDE_ARMOR_TRIM);
            store(trim);
        }
    }

    private NbtCompound rawTrim() {
        String s = work.components().get(TRIM_ID);
        if (s == null) return null;
        try {
            return Snbt.parse(s) instanceof NbtCompound c ? c : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static ArmorTrim toTrim(NbtCompound c) {
        if (!(c.get("material") instanceof String m) || !(c.get("pattern") instanceof String p)) return null;   // 인라인 재료·무늬
        NamespacedKey mk = NamespacedKey.fromString(m);
        NamespacedKey pk = NamespacedKey.fromString(p);
        TrimMaterial material = mk == null ? null : Registry.TRIM_MATERIAL.get(mk);
        TrimPattern pattern = pk == null ? null : Registry.TRIM_PATTERN.get(pk);
        return material == null || pattern == null ? null : new ArmorTrim(material, pattern);
    }

    private void store(ArmorTrim trim) {
        NbtCompound c = new NbtCompound();
        c.putString("material", trim.getMaterial().getKey().toString());
        c.putString("pattern", trim.getPattern().getKey().toString());
        work = work.with(TRIM, Snbt.write(c));
    }

    @Override
    public boolean hasTrim() { return getTrim() != null; }

    @Override
    public ArmorTrim getTrim() {
        NbtCompound raw = rawTrim();
        return raw == null ? null : toTrim(raw);
    }

    @Override
    public void setTrim(ArmorTrim trim) {
        if (trim == null) {
            clearComponent(TRIM_ID);
            return;
        }
        // Paper 는 적용할 때 던진다(bukkitToMinecraftHolder) — 이 계층은 값이 들어올 때 레지스트리 값인지 본다
        if (Registry.TRIM_MATERIAL.get(trim.getMaterial().getKey()) != trim.getMaterial()) {
            throw new IllegalArgumentException("No Reference holder found for " + trim.getMaterial()
                    + ", this can happen if a plugin creates its own trim material without properly registering it.");
        }
        if (Registry.TRIM_PATTERN.get(trim.getPattern().getKey()) != trim.getPattern()) {
            throw new IllegalArgumentException("No Reference holder found for " + trim.getPattern()
                    + ", this can happen if a plugin creates its own trim pattern without properly registering it.");
        }
        store(trim);
    }

    @Override
    ItemStack finishApply(ItemStack r) {
        String s = r.components().get(TRIM_ID);
        if (s == null || !(Snbt.parse(s) instanceof NbtCompound c)) return r;
        boolean show = !flags.contains(ItemFlag.HIDE_ARMOR_TRIM);
        if (c.contains("show_in_tooltip") && c.getBoolean("show_in_tooltip") == show) return r;
        c.putBoolean("show_in_tooltip", show);
        return r.with(TRIM, Snbt.write(c));
    }

    @Override
    public CompatMetaArmor clone() { return (CompatMetaArmor) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) {
        ArmorTrim trim = getTrim();
        if (trim == null) return;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("material", trim.getMaterial().getKey().toString());
        data.put("pattern", trim.getPattern().getKey().toString());
        out.put("trim", data);
    }

    @Override
    void deserializeKind(Map<String, Object> map) {
        Object t = map.get("trim");
        if (t == null) return;
        if (!(t instanceof Map<?, ?> data)) throw new IllegalArgumentException("trim(" + t + ") is not a valid " + Map.class);
        String m = text(data, "material");
        String p = text(data, "pattern");
        if (m == null || p == null) return;
        NamespacedKey mk = NamespacedKey.fromString(m);
        NamespacedKey pk = NamespacedKey.fromString(p);
        if (mk == null || pk == null) return;
        TrimMaterial material = Registry.TRIM_MATERIAL.get(mk);
        TrimPattern pattern = Registry.TRIM_PATTERN.get(pk);
        if (material != null && pattern != null) store(new ArmorTrim(material, pattern));
    }

    private static String text(Map<?, ?> data, String key) {
        Object v = data.get(key);
        if (v == null) return null;
        if (!(v instanceof String s)) throw new IllegalArgumentException(key + "(" + v + ") is not a valid " + String.class);
        return s;
    }
}
