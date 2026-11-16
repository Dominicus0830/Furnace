// Bukkit BannerMeta — 무늬 겹은 minecraft:banner_patterns 컴포넌트에서 읽고 쓴다(바탕 색은 아이템 종류가 정한다).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.bukkit.block.banner.Pattern;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.BannerMeta;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.nbt.Snbt;

@DelegateDeserialization(SerializableMeta.class)
public final class CompatMetaBanner extends CompatItemMeta implements BannerMeta {

    static final String PATTERNS = "minecraft:banner_patterns";

    CompatMetaBanner(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) {
        super(work, flags, pdc);
        normalizePatterns();
    }

    @Override
    MetaKind kind() { return MetaKind.BANNER; }

    /**
     * Paper 의 메타는 아이템에서 읽을 때 앞의 20겹 가운데 알아볼 수 있는 것만 들고, 적용할 때 그것만 쓴다 — 인라인 무늬·모르는 무늬는 메타를
     * 거치면 사라지고, 읽을 수 있는 겹이 하나도 없으면 컴포넌트를 쓰지 않는다. 같은 결과가 되도록 만들 때 한 번 맞춰 둔다.
     */
    private void normalizePatterns() {
        String raw = work.components().get(PATTERNS);
        if (raw == null) return;
        List<Pattern> valid = Snbt.parse(raw) instanceof NbtList l ? BannerLayers.read(l, BannerLayers.ITEM_LIMIT) : List.of();
        store(valid);
    }

    private void store(List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            clearComponent(PATTERNS);
            return;
        }
        String snbt = Snbt.write(BannerLayers.write(patterns));
        if (!snbt.equals(work.components().get(PATTERNS))) setComponent(PATTERNS, snbt);
    }

    private List<Pattern> load() {
        String raw = work.components().get(PATTERNS);
        if (raw == null || !(Snbt.parse(raw) instanceof NbtList l)) return new ArrayList<>();
        return BannerLayers.read(l, BannerLayers.ITEM_LIMIT);
    }

    @Override
    public List<Pattern> getPatterns() { return load(); }

    @Override
    public void setPatterns(List<Pattern> patterns) { store(new ArrayList<>(patterns)); }

    @Override
    public void addPattern(Pattern pattern) {
        List<Pattern> l = load();
        l.add(pattern);
        store(l);
    }

    @Override
    public Pattern getPattern(int i) { return load().get(i); }

    @Override
    public Pattern removePattern(int i) {
        List<Pattern> l = load();
        Pattern removed = l.remove(i);
        store(l);
        return removed;
    }

    @Override
    public void setPattern(int i, Pattern pattern) {
        List<Pattern> l = load();
        l.set(i, pattern);
        store(l);
    }

    @Override
    public int numberOfPatterns() { return load().size(); }

    @Override
    public CompatMetaBanner clone() { return (CompatMetaBanner) super.clone(); }

    @Override
    void serializeKind(Map<String, Object> out) {
        List<Pattern> patterns = load();
        if (!patterns.isEmpty()) out.put("patterns", List.copyOf(patterns));
    }

    @Override
    void deserializeKind(Map<String, Object> map) {
        Object raw = map.get("patterns");
        if (raw == null) return;
        if (!(raw instanceof Iterable<?> list)) throw new IllegalArgumentException("patterns(" + raw + ") is not a valid " + Iterable.class);
        for (Object o : list) {
            if (!(o instanceof Pattern p)) throw new IllegalArgumentException("Object (" + (o == null ? "null" : o.getClass()) + ") in pattern list is not valid");
            addPattern(p);
        }
    }
}
