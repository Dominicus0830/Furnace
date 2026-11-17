package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;

/**
 * 현수막 무늬 겹 ↔ 바닐라 저장 모양({@code [{pattern: "minecraft:…", color: "red"}]}). 현수막 블록 상태(CraftBanner)와
 * 아이템 메타(CompatMetaBanner)가 함께 쓴다.
 */
public final class BannerLayers {

    private BannerLayers() {}

    /** 아이템 컴포넌트(banner_patterns)에서 읽을 수 있는 겹 수(Paper CraftMetaBanner 가 앞의 20개만 읽는다). */
    public static final int ITEM_LIMIT = 20;

    /**
     * 저장 모양 → 겹 목록. 앞의 limit 개 항목만 본다. 레지스트리 키가 아닌 인라인 무늬({asset_id, translation_key})와
     * 모르는 키·색은 Paper 처럼 건너뛴다.
     */
    public static List<Pattern> read(NbtList layers, int limit) {
        List<Pattern> out = new ArrayList<>();
        List<Object> values = layers.values();
        for (int i = 0; i < Math.min(values.size(), limit); i++) {
            if (!(values.get(i) instanceof NbtCompound layer) || !(layer.get("pattern") instanceof String id)) continue;   // 인라인 무늬
            NamespacedKey key = NamespacedKey.fromString(id);
            PatternType type = key == null ? null : Registry.BANNER_PATTERN.get(key);
            DyeColor color = dye(layer.getString("color"));
            if (type == null || color == null) continue;
            out.add(new Pattern(color, type));
        }
        return out;
    }

    /** 겹 목록 → 저장 모양. 잘못된 겹은 몇 번째인지 알려 준다(IllegalArgumentException). */
    public static NbtList write(List<Pattern> patterns) {
        NbtList out = new NbtList();
        for (int i = 0; i < patterns.size(); i++) {
            Pattern p = patterns.get(i);
            if (p == null) throw new IllegalArgumentException("Banner pattern " + i + " is null");
            if (p.getPattern() == null) throw new IllegalArgumentException("Banner pattern " + i + " has a null PatternType");
            if (p.getColor() == null) throw new IllegalArgumentException("Banner pattern " + i + " has a null DyeColor");
            NamespacedKey key = p.getPattern().getKey();
            if (Registry.BANNER_PATTERN.get(key) == null) {
                throw new IllegalArgumentException("No Reference holder found for " + p.getPattern()
                        + ", this can happen if a plugin creates its own banner pattern without properly registering it.");
            }
            NbtCompound layer = new NbtCompound();
            layer.putString("pattern", key.toString());
            layer.putString("color", p.getColor().name().toLowerCase(Locale.ROOT));
            out.add(layer);
        }
        return out;
    }

    /** 바닐라 색 이름(light_gray) → DyeColor. 모르면 null. */
    public static DyeColor dye(String vanillaName) {
        try {
            return DyeColor.valueOf(vanillaName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
