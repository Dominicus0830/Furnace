// ItemMeta ↔ 설정 맵(Paper 1.21.1 의 {@code ==: ItemMeta} 모양). 키 이름·값 모양은 Paper 를 따른다:
// meta-type, display-name·item-name·lore(JSON 문자열), custom-model-data, enchants, repair-cost, ItemFlags, hide-tool-tip, Unbreakable,
// enchantment-glint-override, fire-resistant, max-stack-size, rarity, Damage, max-damage, unhandled·removed·custom(base64 압축 NBT·목록), PublicBukkitValues(SNBT).
// 호환 ItemMeta 가 다루지 못하는 컴포넌트(포션·머리·가죽 색 …)는 Paper 처럼 "unhandled" 에 컴포넌트 패치(NBT)째 담아 잃지 않는다.
// 다른 점(meta-type 은 항상 UNSPECIFIC, 종류별 키 없음 등)은 docs/feature-parity/compat-item-serialization-2026-11-08.md.
package org.domi.furnace.compat.paper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemEnchantments;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.item.Rarity;
import org.domi.furnace.api.item.Unbreakable;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.Snbt;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;

public final class ItemMetaSerialization {

    /** Paper 1.21.1 의 데이터 버전 — ItemStack 맵의 {@code v}. */
    public static final int DATA_VERSION = 3955;
    public static final String TYPE_FIELD = "meta-type";
    /** 호환 ItemMeta 는 종류별 하위 메타가 없어 항상 이 값이다(Paper 는 아이템에 따라 ARMOR·POTION·SKULL 등). */
    static final String UNSPECIFIC = "UNSPECIFIC";

    /** Paper 1.21.1 SerializableMeta 가 알아보는 meta-type — 읽을 때 모두 받아들이고(종류별 키는 무시, "unhandled" 로 컴포넌트는 산다), 모르는 값은 거절한다. */
    private static final Set<String> META_TYPES = Set.of("ARMOR", "ARMOR_STAND", "BANNER", "TILE_ENTITY", "BOOK", "BOOK_SIGNED", "SKULL", "LEATHER_ARMOR",
            "COLORABLE_ARMOR", "MAP", "POTION", "SHIELD", "SPAWN_EGG", "ENCHANTED", "FIREWORK", "FIREWORK_EFFECT", "KNOWLEDGE_BOOK", "TROPICAL_FISH_BUCKET",
            "AXOLOTL_BUCKET", "CROSSBOW", "SUSPICIOUS_STEW", "ENTITY_TAG", "COMPASS", "BUNDLE", "MUSIC_INSTRUMENT", "OMINOUS_BOTTLE", UNSPECIFIC);

    private static final String CUSTOM_NAME = "minecraft:custom_name";
    private static final String ITEM_NAME = "minecraft:item_name";
    private static final String LORE = "minecraft:lore";
    private static final String CUSTOM_MODEL_DATA = "minecraft:custom_model_data";
    private static final String ENCHANTMENTS = "minecraft:enchantments";
    private static final String REPAIR_COST = "minecraft:repair_cost";
    private static final String HIDE_TOOLTIP = "minecraft:hide_tooltip";
    private static final String HIDE_ADDITIONAL_TOOLTIP = "minecraft:hide_additional_tooltip";
    private static final String UNBREAKABLE = "minecraft:unbreakable";
    private static final String GLINT = "minecraft:enchantment_glint_override";
    private static final String FIRE_RESISTANT = "minecraft:fire_resistant";
    private static final String MAX_STACK_SIZE = "minecraft:max_stack_size";
    private static final String RARITY = "minecraft:rarity";
    private static final String DAMAGE = "minecraft:damage";
    private static final String MAX_DAMAGE = "minecraft:max_damage";
    private static final String CUSTOM_DATA = "minecraft:custom_data";
    private static final String PBV = "PublicBukkitValues";

    /** 전용 키로 직렬화하는 컴포넌트 — 나머지는 "unhandled". */
    private static final Set<String> HANDLED = Set.of(CUSTOM_NAME, ITEM_NAME, LORE, CUSTOM_MODEL_DATA, ENCHANTMENTS, REPAIR_COST, HIDE_TOOLTIP,
            HIDE_ADDITIONAL_TOOLTIP, UNBREAKABLE, GLINT, FIRE_RESISTANT, MAX_STACK_SIZE, RARITY, DAMAGE, MAX_DAMAGE, CUSTOM_DATA);

    /** 읽는 동안 컴포넌트를 쌓아 두는 임시 종류 — 나중에 stack.applyTo 가 실제 종류에 옮긴다. 정수 컴포넌트는 날 SNBT 로 넣어 아이템 기본값과 같다고 지워지지 않게 한다. */
    private static final String SCRATCH_TYPE = "minecraft:stone";

    private ItemMetaSerialization() {}

    // ------------------------------------------------------------------ 쓰기

    static Map<String, Object> serialize(CompatItemMeta meta) {
        ItemStack w = meta.work();
        Map<String, String> comps = w.components();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(TYPE_FIELD, UNSPECIFIC);

        if (comps.containsKey(CUSTOM_NAME)) {
            Text t = w.get(ItemComponents.CUSTOM_NAME);
            if (t != null) out.put("display-name", t.toJson());
        }
        if (comps.containsKey(ITEM_NAME)) {
            Text t = w.get(ItemComponents.ITEM_NAME);
            if (t != null) out.put("item-name", t.toJson());
        }
        List<Text> lore = w.get(ItemComponents.LORE);
        if (lore != null && !lore.isEmpty()) {
            List<String> json = new ArrayList<>(lore.size());
            for (Text t : lore) json.add(t.toJson());
            out.put("lore", json);
        }
        if (comps.containsKey(CUSTOM_MODEL_DATA)) out.put("custom-model-data", w.get(ItemComponents.CUSTOM_MODEL_DATA));

        ItemEnchantments ench = w.get(ItemComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) out.put("enchants", new LinkedHashMap<String, Integer>(ench.levels()));

        if (meta.getRepairCost() > 0) out.put("repair-cost", meta.getRepairCost());

        List<String> flags = new ArrayList<>();
        for (ItemFlag f : meta.flagSet()) flags.add(f.name());
        if (!flags.isEmpty()) out.put("ItemFlags", flags);

        if (comps.containsKey(HIDE_TOOLTIP)) out.put("hide-tool-tip", true);
        if (meta.isUnbreakable()) out.put("Unbreakable", true);
        if (comps.containsKey(GLINT)) out.put("enchantment-glint-override", w.get(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE));
        if (comps.containsKey(FIRE_RESISTANT)) out.put("fire-resistant", true);
        if (comps.containsKey(MAX_STACK_SIZE)) out.put("max-stack-size", w.get(ItemComponents.MAX_STACK_SIZE));
        if (comps.containsKey(RARITY)) out.put("rarity", w.get(ItemComponents.RARITY).name());
        if (comps.containsKey(DAMAGE)) out.put("Damage", w.get(ItemComponents.DAMAGE));
        if (comps.containsKey(MAX_DAMAGE)) out.put("max-damage", w.get(ItemComponents.MAX_DAMAGE));

        NbtCompound unhandled = new NbtCompound();
        for (var e : comps.entrySet()) {
            if (!HANDLED.contains(e.getKey())) unhandled.put(e.getKey(), Snbt.parse(e.getValue()));
        }
        if (!unhandled.isEmpty()) out.put("unhandled", Base64.getEncoder().encodeToString(NbtBinary.writeCompressed(unhandled)));

        if (!w.removedComponents().isEmpty()) out.put("removed", new ArrayList<>(w.removedComponents()));

        // 영속 데이터는 바뀐 것까지 반영한 값, custom_data 의 나머지는 "custom"
        String cd = meta.effective().components().get(CUSTOM_DATA);
        if (cd != null && Snbt.parse(cd) instanceof NbtCompound custom) {
            Object pbv = custom.remove(PBV);
            if (pbv instanceof NbtCompound values && !values.isEmpty()) out.put(PBV, Snbt.write(values));
            if (!custom.isEmpty()) out.put("custom", Base64.getEncoder().encodeToString(NbtBinary.writeCompressed(custom)));
        }
        return out;
    }

    // ------------------------------------------------------------------ 읽기

    /** 설정 맵 → ItemMeta. 아이템 종류는 모르므로 임시 종류 위에 만들고, ItemStack#setItemMeta 가 실제 종류에 옮긴다. */
    public static ItemMeta deserialize(Map<String, Object> map) {
        String type = get(map, TYPE_FIELD, String.class);
        if (type == null) throw new NoSuchElementException(map + " does not contain " + TYPE_FIELD);
        if (!META_TYPES.contains(type)) throw new IllegalArgumentException(type + " is not a valid " + TYPE_FIELD);

        TreeMap<String, String> comps = new TreeMap<>();
        TreeSet<String> removed = new TreeSet<>();

        String unhandled = get(map, "unhandled", String.class);
        if (unhandled != null) {
            try {
                NbtCompound patch = NbtBinary.readCompressed(Base64.getDecoder().decode(unhandled));
                for (String key : patch.keys()) {
                    // 컴포넌트 패치의 "!id" 는 지운 표시(Paper 가 쓰는 건 "removed" 지만 읽는 쪽은 둘 다 받는다)
                    if (key.startsWith("!")) removed.add(key.substring(1));
                    else comps.put(key, Snbt.write(patch.get(key)));
                }
            } catch (IOException ex) {
                Logger.getLogger(ItemMetaSerialization.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Iterable<?> removedIds = get(map, "removed", Iterable.class);
        if (removedIds != null) for (Object id : removedIds) removed.add((String) id);
        for (String id : comps.keySet()) removed.remove(id);

        ItemStack w = new ItemStack(SCRATCH_TYPE, 1, comps, removed);

        String name = get(map, "display-name", String.class);
        if (name != null) w = w.with(ItemComponents.CUSTOM_NAME, text(name));
        String itemName = get(map, "item-name", String.class);
        if (itemName != null) w = w.with(ItemComponents.ITEM_NAME, text(itemName));
        Iterable<?> lore = get(map, "lore", Iterable.class);
        if (lore != null) {
            List<Text> lines = new ArrayList<>();
            for (Object line : lore) lines.add(line instanceof String s ? text(s) : Text.empty());
            if (!lines.isEmpty()) w = w.with(ItemComponents.LORE, lines);
        }
        Integer cmd = get(map, "custom-model-data", Integer.class);
        if (cmd != null) w = w.withComponent(CUSTOM_MODEL_DATA, Snbt.write(cmd));

        Map<?, ?> ench = get(map, "enchants", Map.class);
        if (ench != null) {
            Map<String, Integer> levels = new LinkedHashMap<>();
            for (var e : ench.entrySet()) {
                Enchantment known = Enchantment.fromKey(e.getKey().toString());
                if (known != null && e.getValue() instanceof Integer lv && lv >= 1 && lv <= 255) levels.put(known.getKey().toString(), lv);
            }
            if (!levels.isEmpty()) w = w.with(ItemComponents.ENCHANTMENTS, new ItemEnchantments(levels));
        }

        Integer repair = get(map, "repair-cost", Integer.class);
        if (repair != null && repair > 0) w = w.withComponent(REPAIR_COST, Snbt.write(repair));
        if (Boolean.TRUE.equals(get(map, "hide-tool-tip", Boolean.class))) w = w.with(ItemComponents.HIDE_TOOLTIP);
        if (Boolean.TRUE.equals(get(map, "Unbreakable", Boolean.class))) w = w.with(ItemComponents.UNBREAKABLE, new Unbreakable(true));
        Boolean glint = get(map, "enchantment-glint-override", Boolean.class);
        if (glint != null) w = w.with(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
        if (Boolean.TRUE.equals(get(map, "fire-resistant", Boolean.class))) w = w.with(ItemComponents.FIRE_RESISTANT);
        Integer maxStack = get(map, "max-stack-size", Integer.class);
        if (maxStack != null) w = w.withComponent(MAX_STACK_SIZE, Snbt.write(maxStack));
        String rarity = get(map, "rarity", String.class);
        if (rarity != null) w = w.withComponent(RARITY, Snbt.write(Rarity.valueOf(rarity).id()));
        Integer damage = get(map, "Damage", Integer.class);
        if (damage != null) w = w.withComponent(DAMAGE, Snbt.write(damage));
        Integer maxDamage = get(map, "max-damage", Integer.class);
        if (maxDamage != null) w = w.withComponent(MAX_DAMAGE, Snbt.write(maxDamage));

        // 영속 데이터(PublicBukkitValues: SNBT 문자열)와 custom_data 의 나머지("custom": base64 압축 NBT)
        NbtCompound customData = new NbtCompound();
        String custom = get(map, "custom", String.class);
        if (custom != null) {
            try {
                customData = NbtBinary.readCompressed(Base64.getDecoder().decode(custom));
            } catch (IOException ex) {
                Logger.getLogger(ItemMetaSerialization.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Object pbv = map.get(PBV);
        if (pbv != null) {
            if (!(pbv instanceof String snbt)) throw new IllegalArgumentException(PBV + "(" + pbv + ") is not a valid " + String.class);
            customData.put(PBV, NbtCompound.parse(snbt));
        }
        if (!customData.isEmpty()) w = w.withComponent(CUSTOM_DATA, Snbt.write(customData));

        CompatItemMeta meta = CompatItemMeta.of(w);
        Iterable<?> flags = get(map, "ItemFlags", Iterable.class);
        if (flags != null) {
            for (Object f : flags) {
                try {
                    meta.flagSet().add(ItemFlag.valueOf((String) f));
                } catch (IllegalArgumentException ex) {
                    // 지금은 없는 옛 이름은 무시한다(Paper 와 같다)
                }
            }
        }
        // 표시 항목을 컴포넌트(showInTooltip)에 반영한 모양으로 맞춘다 — 스택에서 꺼낸 메타와 같은 꼴이어야 equals 가 같다
        CompatItemMeta normalized = CompatItemMeta.of(meta.applyTo(w));
        normalized.flagSet().addAll(meta.flagSet());
        return normalized;
    }

    /** JSON 으로 보이면 JSON, 아니면(또는 JSON 이 깨졌으면) § 서식 문자열로 읽는다(Paper 의 fromJSONOrString). */
    private static Text text(String s) {
        String t = s.strip();
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"")) {
            try {
                return JsonTextSerializer.json().deserialize(t);
            } catch (RuntimeException e) {
                // 아래 § 서식으로
            }
        }
        return Compat.text(s);
    }

    private static <T> T get(Map<String, Object> map, String key, Class<T> clazz) {
        Object o = map.get(key);
        if (o == null) return null;
        if (clazz.isInstance(o)) return clazz.cast(o);
        throw new IllegalArgumentException(key + "(" + o + ") is not a valid " + clazz);
    }
}
