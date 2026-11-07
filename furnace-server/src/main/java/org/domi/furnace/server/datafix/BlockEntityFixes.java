// 블록 엔티티(구조물 blocks[].nbt) 고침. 각 손잡이는 바닐라 NamedEntityFix 처럼 해당 id 의 블록 엔티티만 고친다.
package org.domi.furnace.server.datafix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class BlockEntityFixes {
    private BlockEntityFixes() {}

    /** NamedEntityFix: id(이름공간 붙임)가 같을 때만. */
    static UnaryOperator<NbtCompound> named(String id, UnaryOperator<NbtCompound> f) {
        return be -> id.equals(FixUtil.id(be)) ? f.apply(be) : be;
    }

    /** BlockEntityRenameFix: id 바꾸기. */
    static UnaryOperator<NbtCompound> renameId(UnaryOperator<String> f) {
        return be -> {
            String id = FixUtil.str(be, "id");
            if (id != null) {
                String n = f.apply(id);
                if (!n.equals(id)) be.putString("id", n);
            }
            return be;
        };
    }

    // ---- 1458 BlockEntityCustomNameToComponentFix / EntityCustomNameToComponentFix 의 본체

    /** CustomName 문자열 → 평문 컴포넌트 JSON. 빈 문자열이거나 문자열이 아니면 지운다. */
    static NbtCompound customNameToComponent(NbtCompound c) {
        String name = c.tags().get("CustomName") instanceof String s ? s : "";
        if (name.isEmpty()) c.tags().remove("CustomName");
        else c.putString("CustomName", FixUtil.plainText(name));
        return c;
    }

    static NbtCompound customNameExceptCommandBlock(NbtCompound be) {
        return "minecraft:command_block".equals(FixUtil.id(be)) ? be : customNameToComponent(be);
    }

    // ---- 1914 WeaponSmithChestLootTableFix (minecraft:chest)

    static NbtCompound weaponsmithLoot(NbtCompound be) {
        if ("minecraft:chests/village_blacksmith".equals(FixUtil.str(be, "LootTable"))) {
            be.putString("LootTable", "minecraft:chests/village/village_weaponsmith");
        }
        return be;
    }

    // ---- 2501 FurnaceRecipeFix (furnace·blast_furnace·smoker): RecipeLocationN/RecipeAmountN → RecipesUsed{조리법: 개수}

    static NbtCompound furnaceRecipes(NbtCompound be) {
        int size = be.tags().get("RecipesUsedSize") instanceof Number n ? n.intValue() : 0;
        be.tags().remove("RecipesUsedSize");
        NbtCompound used = new NbtCompound();
        for (int i = 0; i < size; i++) {
            String loc = "RecipeLocation" + i, amt = "RecipeAmount" + i;
            int amount = be.tags().get(amt) instanceof Number n ? n.intValue() : 0;
            // 바닐라: 개수가 0 보다 클 때만, 이름은 NamespacedString 으로 읽힐 때(문자열)만 넣는다
            if (amount > 0 && be.tags().get(loc) instanceof String recipe) used.putInt(FixUtil.ns(recipe), amount);
            be.tags().remove(loc);
            be.tags().remove(amt);
        }
        be.put("RecipesUsed", used);   // 바닐라는 비어 있어도 RecipesUsed 를 쓴다
        return be;
    }

    // ---- 1953 OminousBannerBlockEntityRenameFix (minecraft:banner)

    static NbtCompound ominousBannerName(NbtCompound be) {
        String name = FixUtil.str(be, "CustomName");
        if (name != null) {
            be.putString("CustomName", name.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\""));
        }
        return be;
    }

    // ---- 2514 BlockEntityUUIDFix (conduit·skull)

    /** replaceUUIDMLTag(target_uuid → Target): target_uuid 는 {M, L} compound. */
    static NbtCompound conduitUuid(NbtCompound be) {
        if (be.tags().get("target_uuid") instanceof NbtCompound t) {
            long m = t.getLong("M"), l = t.getLong("L");
            if (m != 0L && l != 0L) {
                be.tags().remove("target_uuid");
                be.tags().remove("Target");
                be.tags().put("Target", FixUtil.uuidInts(m, l));
            }
        }
        return be;
    }

    static NbtCompound skullUuid(NbtCompound be) {
        if (!be.contains("Owner")) return be;
        Object owner = be.tags().remove("Owner");
        if (owner instanceof NbtCompound o) FixUtil.uuidFromString(o, "Id", "Id");
        be.tags().remove("SkullOwner");
        be.tags().put("SkullOwner", owner);
        return be;
    }

    // ---- 2518 JigsawPropertiesFix (minecraft:jigsaw)

    static NbtCompound jigsawProperties(NbtCompound be) {
        String attach = be.tags().get("attachement_type") instanceof String s ? s : "minecraft:empty";
        String pool = be.tags().get("target_pool") instanceof String s ? s : "minecraft:empty";
        be.putString("name", attach);
        be.putString("target", attach);
        be.tags().remove("attachement_type");
        be.putString("pool", pool);
        be.tags().remove("target_pool");
        return be;
    }

    // ---- 2831 UntaggedSpawnerFix (minecraft:mob_spawner·spawner_minecart 의 생성 자료)

    static NbtCompound untaggedSpawner(NbtCompound c) {
        if (c.tags().get("SpawnData") instanceof NbtCompound data) {
            NbtCompound wrapped = new NbtCompound();
            wrapped.putCompound("entity", data);
            c.putCompound("SpawnData", wrapped);
        }
        if (c.tags().get("SpawnPotentials") instanceof NbtList list) {
            NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
            for (Object o : list.items()) {
                if (!(o instanceof NbtCompound e)) continue;
                int weight = e.containsNumber("Weight") ? ((Number) e.tags().get("Weight")).intValue() : 1;
                NbtCompound data = new NbtCompound();
                if (e.tags().get("Entity") instanceof NbtCompound ent) data.putCompound("entity", ent);
                for (Map.Entry<String, Object> kv : e.tags().entrySet()) {
                    if (!kv.getKey().equals("Weight") && !kv.getKey().equals("Entity")) data.put(kv.getKey(), kv.getValue());
                }
                NbtCompound entry = new NbtCompound();
                entry.putCompound("data", data);
                entry.putInt("weight", weight);
                out.add(entry);
            }
            c.putList("SpawnPotentials", out);
        }
        return c;
    }

    // ---- 3097 FilteredSignsFix (minecraft:sign)

    static NbtCompound removeFilteredSignText(NbtCompound be) {
        for (int i = 1; i <= 4; i++) be.tags().remove("FilteredText" + i);
        return be;
    }

    // ---- 3438 EntityBrushableBlockFieldsRenameFix (minecraft:brushable_block)

    static NbtCompound brushableFields(NbtCompound be) {
        FixUtil.rename(be, "loot_table", "LootTable");
        FixUtil.rename(be, "loot_table_seed", "LootTableSeed");
        return be;
    }

    // ---- 3439 BlockEntitySignDoubleSidedEditableTextFix (sign·hanging_sign)

    static NbtCompound signDoubleSided(NbtCompound be) {
        String empty = FixUtil.plainText("");
        NbtCompound front = new NbtCompound();
        List<Object> messages = new ArrayList<>(4);
        NbtList msgs = new NbtList(NbtIo.TAG_STRING);
        for (int i = 1; i <= 4; i++) {
            Object t = be.tags().get("Text" + i);
            Object v = t != null ? t : empty;
            messages.add(v);
            msgs.add(v);
        }
        front.putList("messages", msgs);
        front.put("color", be.contains("Color") ? be.tags().get("Color") : "black");
        front.put("has_glowing_text", be.contains("GlowingText") ? be.tags().get("GlowingText") : (byte) 0);
        front.putBoolean("_filtered_correct", true);
        boolean anyFiltered = false;
        for (int i = 1; i <= 4; i++) anyFiltered |= be.contains("FilteredText" + i);
        if (anyFiltered) {
            NbtList filtered = new NbtList(NbtIo.TAG_STRING);
            for (int i = 1; i <= 4; i++) {
                Object f = be.tags().get("FilteredText" + i);
                filtered.add(f != null ? f : messages.get(i - 1));
            }
            front.putList("filtered_messages", filtered);
        }
        NbtCompound back = new NbtCompound();
        back.putList("messages", FixUtil.stringList(empty, empty, empty, empty));
        back.putString("color", "black");
        back.putBoolean("has_glowing_text", false);
        be.putCompound("front_text", front);
        be.putCompound("back_text", back);
        be.putBoolean("is_waxed", false);
        return be;
    }

    // ---- 3448 DecoratedPotFieldRenameFix (스키마 필드 이름 shards → sherds)

    static NbtCompound decoratedPotFields(NbtCompound be) {
        FixUtil.rename(be, "shards", "sherds");
        return be;
    }

    // ---- 3564 DropInvalidSignDataFix (sign·hanging_sign)

    private static final String[] OLD_SIGN_KEYS = {
        "Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4", "Color", "GlowingText"};

    static NbtCompound dropInvalidSignData(NbtCompound be) {
        FixUtil.update(be, "front_text", BlockEntityFixes::fixSignSide);
        FixUtil.update(be, "back_text", BlockEntityFixes::fixSignSide);
        for (String k : OLD_SIGN_KEYS) be.tags().remove(k);
        return be;
    }

    private static Object fixSignSide(Object v) {
        if (!(v instanceof NbtCompound side)) return v;
        if (side.getBoolean("_filtered_correct")) {
            side.tags().remove("_filtered_correct");
            return side;
        }
        if (!(side.tags().get("filtered_messages") instanceof NbtList filtered)) return side;
        String empty = FixUtil.plainText("");
        List<Object> messages = side.tags().get("messages") instanceof NbtList m ? m.items() : List.of();
        NbtList out = new NbtList(NbtIo.TAG_STRING);
        boolean allEmpty = true;
        for (int i = 0; i < filtered.size(); i++) {
            Object f = filtered.get(i);
            Object msg = i < messages.size() ? messages.get(i) : empty;
            Object r = empty.equals(f) ? msg : f;
            if (!empty.equals(r)) allEmpty = false;
            out.add(r);
        }
        if (allEmpty) side.tags().remove("filtered_messages");
        else side.putList("filtered_messages", out);
        return side;
    }

    // ---- 3813 BlockPosFormatAndRenamesFix (블록 엔티티 쪽)

    static NbtCompound beehivePos(NbtCompound be) {
        renameAndFixPos(be, "FlowerPos", "flower_pos");
        return be;
    }

    static NbtCompound endGatewayPos(NbtCompound be) {
        renameAndFixPos(be, "ExitPortal", "exit_portal");
        return be;
    }

    /** Dynamic.renameAndFixField(k, to, fixBlockPos). */
    static void renameAndFixPos(NbtCompound c, String from, String to) {
        if (!c.contains(from)) return;
        Object v = FixUtil.blockPos(c.tags().remove(from));
        c.tags().remove(to);
        c.tags().put(to, v);
    }

    // ---- 3818 BeehiveFieldRenameFix (minecraft:beehive) — Bees·EntityData 는 스키마 필드 이름이 바뀐다

    static NbtCompound beehiveFields(NbtCompound be) {
        FixUtil.rename(be, "Bees", "bees");
        if (be.tags().get("bees") instanceof NbtList bees) {
            for (Object o : bees.items()) {
                if (!(o instanceof NbtCompound bee)) continue;
                FixUtil.rename(bee, "EntityData", "entity_data");
                FixUtil.rename(bee, "TicksInHive", "ticks_in_hive");
                FixUtil.rename(bee, "MinOccupationTicks", "min_ticks_in_hive");
            }
        }
        return be;
    }

    // ---- 3818_1 BannerPatternFormatFix (minecraft:banner)

    private static final Map<String, String> PATTERN_IDS = Map.ofEntries(
        Map.entry("b", "minecraft:base"), Map.entry("bl", "minecraft:square_bottom_left"), Map.entry("br", "minecraft:square_bottom_right"),
        Map.entry("tl", "minecraft:square_top_left"), Map.entry("tr", "minecraft:square_top_right"), Map.entry("bs", "minecraft:stripe_bottom"),
        Map.entry("ts", "minecraft:stripe_top"), Map.entry("ls", "minecraft:stripe_left"), Map.entry("rs", "minecraft:stripe_right"),
        Map.entry("cs", "minecraft:stripe_center"), Map.entry("ms", "minecraft:stripe_middle"), Map.entry("drs", "minecraft:stripe_downright"),
        Map.entry("dls", "minecraft:stripe_downleft"), Map.entry("ss", "minecraft:small_stripes"), Map.entry("cr", "minecraft:cross"),
        Map.entry("sc", "minecraft:straight_cross"), Map.entry("bt", "minecraft:triangle_bottom"), Map.entry("tt", "minecraft:triangle_top"),
        Map.entry("bts", "minecraft:triangles_bottom"), Map.entry("tts", "minecraft:triangles_top"), Map.entry("ld", "minecraft:diagonal_left"),
        Map.entry("rd", "minecraft:diagonal_up_right"), Map.entry("lud", "minecraft:diagonal_up_left"), Map.entry("rud", "minecraft:diagonal_right"),
        Map.entry("mc", "minecraft:circle"), Map.entry("mr", "minecraft:rhombus"), Map.entry("vh", "minecraft:half_vertical"),
        Map.entry("hh", "minecraft:half_horizontal"), Map.entry("vhr", "minecraft:half_vertical_right"), Map.entry("hhb", "minecraft:half_horizontal_bottom"),
        Map.entry("bo", "minecraft:border"), Map.entry("cbo", "minecraft:curly_border"), Map.entry("gra", "minecraft:gradient"),
        Map.entry("gru", "minecraft:gradient_up"), Map.entry("bri", "minecraft:bricks"), Map.entry("glb", "minecraft:globe"),
        Map.entry("cre", "minecraft:creeper"), Map.entry("sku", "minecraft:skull"), Map.entry("flo", "minecraft:flower"),
        Map.entry("moj", "minecraft:mojang"), Map.entry("pig", "minecraft:piglin"));

    /** BannerPatternFormatFix.fixColor. */
    static String dyeColor(int c) {
        return switch (c) {
            case 1 -> "orange";
            case 2 -> "magenta";
            case 3 -> "light_blue";
            case 4 -> "yellow";
            case 5 -> "lime";
            case 6 -> "pink";
            case 7 -> "gray";
            case 8 -> "light_gray";
            case 9 -> "cyan";
            case 10 -> "purple";
            case 11 -> "blue";
            case 12 -> "brown";
            case 13 -> "green";
            case 14 -> "red";
            case 15 -> "black";
            default -> "white";
        };
    }

    static NbtCompound bannerPatterns(NbtCompound be) {
        if (!be.contains("Patterns")) return be;
        Object v = be.tags().remove("Patterns");
        if (v instanceof NbtList list) {
            NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
            for (Object o : list.items()) {
                if (o instanceof NbtCompound p) {
                    if (p.contains("Pattern")) {
                        Object pat = p.tags().remove("Pattern");
                        p.put("pattern", pat instanceof String s ? PATTERN_IDS.getOrDefault(s, s) : pat);
                    }
                    String color = dyeColor(p.getInt("Color"));
                    p.putString("color", color);
                    p.tags().remove("Color");
                    out.add(p);
                } else {
                    out.add(o);
                }
            }
            v = out;
        }
        be.tags().remove("patterns");
        be.tags().put("patterns", v);
        return be;
    }

    // ---- 3820 PlayerHeadBlockProfileFix (minecraft:skull)

    static NbtCompound skullProfile(NbtCompound be) {
        Object owner = be.tags().get("SkullOwner");
        if (owner == null) owner = be.tags().get("ExtraType");
        if (owner == null) return be;
        be.tags().remove("SkullOwner");
        be.tags().remove("ExtraType");
        be.putCompound("profile", ItemStackFixes.fixProfile(owner));
        return be;
    }

    // ---- 3825 BannerEntityCustomNameToOverrideComponentFix (minecraft:banner)

    static NbtCompound bannerOminousName(NbtCompound be) {
        String name = FixUtil.str(be, "CustomName");
        if (name == null || !FixUtil.translationKey(name).filter("block.minecraft.ominous_banner"::equals).isPresent()) return be;
        NbtCompound comps = be.tags().get("components") instanceof NbtCompound c ? c : new NbtCompound();
        comps.putString("minecraft:item_name", name);
        comps.putCompound("minecraft:hide_additional_tooltip", new NbtCompound());
        be.putCompound("components", comps);
        be.tags().remove("CustomName");
        return be;
    }

    // ---- 3833 RemoveEmptyItemInBrushableBlockFix

    static NbtCompound brushableEmptyItem(NbtCompound be) {
        if (be.tags().get("item") instanceof NbtCompound item) {
            String id = item.tags().get("id") instanceof String s ? FixUtil.ns(s) : "minecraft:air";
            if (id.equals("minecraft:air") || item.getInt("count") == 0) be.tags().remove("item");
        }
        return be;
    }

    // ---- 3945 JukeboxTicksSinceSongStartedFix

    static NbtCompound jukeboxTicks(NbtCompound be) {
        long ticks = be.getLong("TickCount") - be.getLong("RecordStartTick");
        be.tags().remove("IsPlaying");
        be.tags().remove("TickCount");
        be.tags().remove("RecordStartTick");
        if (ticks > 0L) be.putLong("ticks_since_song_started", ticks);
        return be;
    }
}
