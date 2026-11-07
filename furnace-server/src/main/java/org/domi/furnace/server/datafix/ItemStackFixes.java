// 아이템 묶음({id, Count, tag} → 1.20.5 부터 {id, count, components}) 고침.
// 핵심은 3818_5 ItemStackComponentizationFix 의 그대로 이식이고, 그 앞뒤의 tag 시대 고침(이름·마법 부여·설명·능력치)과 뒤의 컴포넌트 고침을 함께 둔다.
package org.domi.furnace.server.datafix;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class ItemStackFixes {
    private ItemStackFixes() {}

    // ---- V705 아이템 훅: tag 시대 아이템을 읽을 때 BlockEntityTag·EntityTag 에 id 를 넣는다(쓸 때 지우지 않는다)

    private static final String ITEM_TO_BLOCK_ENTITY =
        "furnace lit_furnace=furnace chest trapped_chest=chest ender_chest jukebox dispenser dropper sign mob_spawner " +
        "spawner=mob_spawner noteblock brewing_stand enhanting_table=enchanting_table command_block beacon skull " +
        "daylight_detector hopper banner flower_pot repeating_command_block=command_block " +
        "chain_command_block=command_block shulker_box white_shulker_box=shulker_box orange_shulker_box=shulker_box " +
        "magenta_shulker_box=shulker_box light_blue_shulker_box=shulker_box yellow_shulker_box=shulker_box " +
        "lime_shulker_box=shulker_box pink_shulker_box=shulker_box gray_shulker_box=shulker_box " +
        "silver_shulker_box=shulker_box cyan_shulker_box=shulker_box purple_shulker_box=shulker_box " +
        "blue_shulker_box=shulker_box brown_shulker_box=shulker_box green_shulker_box=shulker_box " +
        "red_shulker_box=shulker_box black_shulker_box=shulker_box bed light_gray_shulker_box=shulker_box banner " +
        "white_banner=banner orange_banner=banner magenta_banner=banner light_blue_banner=banner yellow_banner=banner " +
        "lime_banner=banner pink_banner=banner gray_banner=banner silver_banner=banner light_gray_banner=banner " +
        "cyan_banner=banner purple_banner=banner blue_banner=banner brown_banner=banner green_banner=banner " +
        "red_banner=banner black_banner=banner standing_sign=sign wall_sign=sign piston_head=piston " +
        "daylight_detector_inverted=daylight_detector unpowered_comparator=comparator powered_comparator=comparator " +
        "wall_banner=banner standing_banner=banner structure_block end_portal end_gateway sign shield=banner " +
        "white_bed=bed orange_bed=bed magenta_bed=bed light_blue_bed=bed yellow_bed=bed lime_bed=bed pink_bed=bed " +
        "gray_bed=bed silver_bed=bed light_gray_bed=bed cyan_bed=bed purple_bed=bed blue_bed=bed brown_bed=bed " +
        "green_bed=bed red_bed=bed black_bed=bed oak_sign=sign spruce_sign=sign birch_sign=sign jungle_sign=sign " +
        "acacia_sign=sign dark_oak_sign=sign crimson_sign=sign warped_sign=sign skeleton_skull=skull " +
        "wither_skeleton_skull=skull zombie_head=skull player_head=skull creeper_head=skull dragon_head=skull barrel " +
        "conduit smoker blast_furnace lectern bell jigsaw campfire bee_nest=beehive beehive sculk_sensor decorated_pot " +
        "crafter";
    private static final String ITEM_TO_ENTITY =
        "armor_stand painting armadillo_spawn_egg=armadillo allay_spawn_egg=allay axolotl_spawn_egg=axolotl " +
        "bat_spawn_egg=bat bee_spawn_egg=bee blaze_spawn_egg=blaze bogged_spawn_egg=bogged breeze_spawn_egg=breeze " +
        "cat_spawn_egg=cat camel_spawn_egg=camel cave_spider_spawn_egg=cave_spider chicken_spawn_egg=chicken " +
        "cod_spawn_egg=cod cow_spawn_egg=cow creeper_spawn_egg=creeper dolphin_spawn_egg=dolphin " +
        "donkey_spawn_egg=donkey drowned_spawn_egg=drowned elder_guardian_spawn_egg=elder_guardian " +
        "ender_dragon_spawn_egg=ender_dragon enderman_spawn_egg=enderman endermite_spawn_egg=endermite " +
        "evoker_spawn_egg=evoker fox_spawn_egg=fox frog_spawn_egg=frog ghast_spawn_egg=ghast " +
        "glow_squid_spawn_egg=glow_squid goat_spawn_egg=goat guardian_spawn_egg=guardian hoglin_spawn_egg=hoglin " +
        "horse_spawn_egg=horse husk_spawn_egg=husk iron_golem_spawn_egg=iron_golem llama_spawn_egg=llama " +
        "magma_cube_spawn_egg=magma_cube mooshroom_spawn_egg=mooshroom mule_spawn_egg=mule ocelot_spawn_egg=ocelot " +
        "panda_spawn_egg=panda parrot_spawn_egg=parrot phantom_spawn_egg=phantom pig_spawn_egg=pig " +
        "piglin_spawn_egg=piglin piglin_brute_spawn_egg=piglin_brute pillager_spawn_egg=pillager " +
        "polar_bear_spawn_egg=polar_bear pufferfish_spawn_egg=pufferfish rabbit_spawn_egg=rabbit " +
        "ravager_spawn_egg=ravager salmon_spawn_egg=salmon sheep_spawn_egg=sheep shulker_spawn_egg=shulker " +
        "silverfish_spawn_egg=silverfish skeleton_spawn_egg=skeleton skeleton_horse_spawn_egg=skeleton_horse " +
        "slime_spawn_egg=slime sniffer_spawn_egg=sniffer snow_golem_spawn_egg=snow_golem spider_spawn_egg=spider " +
        "squid_spawn_egg=squid stray_spawn_egg=stray strider_spawn_egg=strider tadpole_spawn_egg=tadpole " +
        "trader_llama_spawn_egg=trader_llama tropical_fish_spawn_egg=tropical_fish turtle_spawn_egg=turtle " +
        "vex_spawn_egg=vex villager_spawn_egg=villager vindicator_spawn_egg=vindicator " +
        "wandering_trader_spawn_egg=wandering_trader warden_spawn_egg=warden witch_spawn_egg=witch " +
        "wither_spawn_egg=wither wither_skeleton_spawn_egg=wither_skeleton wolf_spawn_egg=wolf zoglin_spawn_egg=zoglin " +
        "zombie_spawn_egg=zombie zombie_horse_spawn_egg=zombie_horse zombie_villager_spawn_egg=zombie_villager " +
        "zombified_piglin_spawn_egg=zombified_piglin item_frame boat oak_boat=boat oak_chest_boat=chest_boat " +
        "spruce_boat=boat spruce_chest_boat=chest_boat birch_boat=boat birch_chest_boat=chest_boat jungle_boat=boat " +
        "jungle_chest_boat=chest_boat acacia_boat=boat acacia_chest_boat=chest_boat cherry_boat=boat " +
        "cherry_chest_boat=chest_boat dark_oak_boat=boat dark_oak_chest_boat=chest_boat mangrove_boat=boat " +
        "mangrove_chest_boat=chest_boat bamboo_raft=boat bamboo_chest_raft=chest_boat minecart chest_minecart " +
        "furnace_minecart tnt_minecart hopper_minecart";

    private static final Map<String, String> BE_HOOK = parse(ITEM_TO_BLOCK_ENTITY);
    private static final Map<String, String> ENTITY_HOOK = parse(ITEM_TO_ENTITY);

    private static Map<String, String> parse(String s) {
        Map<String, String> m = new HashMap<>();
        for (String e : s.split(" ")) {
            int i = e.indexOf('=');
            String k = i < 0 ? e : e.substring(0, i), v = i < 0 ? e : e.substring(i + 1);
            m.put("minecraft:" + k, "minecraft:" + v);
        }
        return m;
    }

    /** V705 addNames 훅. BlockEntityTag 는 알려진 아이템이면 id 를 덮어쓰고, EntityTag 는 id 가 없을 때만 넣는다. */
    static void addNames(NbtCompound item) {
        if (!(item.tags().get("tag") instanceof NbtCompound tag)) return;
        String itemId = item.tags().get("id") instanceof String s ? FixUtil.ns(s) : "minecraft:air";
        if (tag.tags().get("BlockEntityTag") instanceof NbtCompound be && !"minecraft:air".equals(itemId)) {
            String beId = BE_HOOK.get(itemId);
            if (beId != null) be.putString("id", beId);
        }
        if (tag.tags().get("EntityTag") instanceof NbtCompound et && !et.contains("id")) {
            String entId = ENTITY_HOOK.get(item.tags().get("id") instanceof String s ? FixUtil.ns(s) : "");
            if (entId != null) et.putString("id", entId);
        }
    }

    // ---- 이름 바꾸기(ItemRenameFix)

    static UnaryOperator<NbtCompound> rename(UnaryOperator<String> f) {
        return item -> {
            String id = FixUtil.str(item, "id");
            if (id != null) {
                String n = f.apply(id);
                if (!n.equals(id)) item.putString("id", n);
            }
            return item;
        };
    }

    /** tag 가 있으면 tag 를 고친다. */
    static UnaryOperator<NbtCompound> tag(UnaryOperator<NbtCompound> f) {
        return item -> {
            if (item.tags().get("tag") instanceof NbtCompound t) item.put("tag", f.apply(t));
            return item;
        };
    }

    /** ItemStackTagFix: 아이템 id 가 맞고 tag 가 있을 때만. */
    static UnaryOperator<NbtCompound> tagIf(Set<String> ids, UnaryOperator<NbtCompound> f) {
        return item -> ids.contains(FixUtil.id(item)) ? tag(f).apply(item) : item;
    }

    // ---- 1458 ItemCustomNameToComponentFix

    static NbtCompound customName(NbtCompound tag) {
        if (tag.tags().get("display") instanceof NbtCompound d && d.tags().get("Name") instanceof String name) {
            d.putString("Name", FixUtil.plainText(name));
        }
        return tag;
    }

    // ---- 1494 ItemStackEnchantmentNamesFix

    private static final Map<Integer, String> ENCHANT_IDS = Map.ofEntries(
        Map.entry(0, "minecraft:protection"), Map.entry(1, "minecraft:fire_protection"), Map.entry(2, "minecraft:feather_falling"),
        Map.entry(3, "minecraft:blast_protection"), Map.entry(4, "minecraft:projectile_protection"), Map.entry(5, "minecraft:respiration"),
        Map.entry(6, "minecraft:aqua_affinity"), Map.entry(7, "minecraft:thorns"), Map.entry(8, "minecraft:depth_strider"),
        Map.entry(9, "minecraft:frost_walker"), Map.entry(10, "minecraft:binding_curse"), Map.entry(16, "minecraft:sharpness"),
        Map.entry(17, "minecraft:smite"), Map.entry(18, "minecraft:bane_of_arthropods"), Map.entry(19, "minecraft:knockback"),
        Map.entry(20, "minecraft:fire_aspect"), Map.entry(21, "minecraft:looting"), Map.entry(22, "minecraft:sweeping"),
        Map.entry(32, "minecraft:efficiency"), Map.entry(33, "minecraft:silk_touch"), Map.entry(34, "minecraft:unbreaking"),
        Map.entry(35, "minecraft:fortune"), Map.entry(48, "minecraft:power"), Map.entry(49, "minecraft:punch"),
        Map.entry(50, "minecraft:flame"), Map.entry(51, "minecraft:infinity"), Map.entry(61, "minecraft:luck_of_the_sea"),
        Map.entry(62, "minecraft:lure"), Map.entry(65, "minecraft:loyalty"), Map.entry(66, "minecraft:impaling"),
        Map.entry(67, "minecraft:riptide"), Map.entry(68, "minecraft:channeling"), Map.entry(70, "minecraft:mending"),
        Map.entry(71, "minecraft:vanishing_curse"));

    private static Object numericEnchantIds(Object v) {
        if (!(v instanceof NbtList l)) return v;
        return FixUtil.mapCompounds(l, e -> {
            e.putString("id", ENCHANT_IDS.getOrDefault(e.getInt("id"), "null"));
            return e;
        });
    }

    static NbtCompound enchantmentNames(NbtCompound tag) {
        if (tag.tags().get("ench") instanceof NbtList ench) {
            tag.tags().remove("ench");
            tag.put("Enchantments", numericEnchantIds(ench));
        }
        FixUtil.update(tag, "StoredEnchantments", ItemStackFixes::numericEnchantIds);
        return tag;
    }

    // ---- 1803 ItemLoreFix

    static NbtCompound lore(NbtCompound tag) {
        if (tag.tags().get("display") instanceof NbtCompound d && d.tags().get("Lore") instanceof NbtList lore) {
            NbtList out = new NbtList(lore.elementType());
            for (Object o : lore.items()) out.add(FixUtil.wrapLiteral(o));
            d.putList("Lore", out);
        }
        return tag;
    }

    // ---- 1948 OminousBannerRenameFix (white_banner)

    static NbtCompound ominousBannerName(NbtCompound tag) {
        if (tag.tags().get("display") instanceof NbtCompound d && d.tags().get("Name") instanceof String name) {
            d.putString("Name", name.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\""));
        }
        return tag;
    }

    // ---- 2514 ItemStackUUIDFix

    static NbtCompound uuids(NbtCompound item) {
        if (!(item.tags().get("tag") instanceof NbtCompound tag)) return item;
        if (tag.tags().get("AttributeModifiers") instanceof NbtList mods) {
            for (Object o : mods.items()) if (o instanceof NbtCompound m) FixUtil.uuidFromMostLeast(m, "UUID", "UUID");
        }
        if ("minecraft:player_head".equals(FixUtil.id(item)) && tag.tags().get("SkullOwner") instanceof NbtCompound owner) {
            FixUtil.uuidFromString(owner, "Id", "Id");
        }
        return item;
    }

    // ---- 2523·3814 AttributesRenameFix (ItemStack)

    static UnaryOperator<NbtCompound> renameAttributes(UnaryOperator<String> f) {
        return tag(t -> {
            if (t.tags().get("AttributeModifiers") instanceof NbtList mods) {
                for (Object o : mods.items()) {
                    if (o instanceof NbtCompound m) FixUtil.update(m, "AttributeName", v -> v instanceof String s ? f.apply(s) : v);
                }
            }
            return t;
        });
    }

    // ---- 3097 FilteredBooksFix

    static NbtCompound removeFilteredBook(NbtCompound tag) {
        tag.tags().remove("filtered_title");
        tag.tags().remove("filtered_pages");
        return tag;
    }

    // ---- 3803 EnchantmentRenameFix(sweeping → sweeping_edge)

    static NbtCompound sweepingEdge(NbtCompound tag) {
        for (String k : new String[] {"Enchantments", "StoredEnchantments"}) {
            FixUtil.update(tag, k, v -> v instanceof NbtList l ? FixUtil.mapCompounds(l, e -> {
                if (e.tags().get("id") instanceof String id && FixUtil.ns(id).equals("minecraft:sweeping")) e.putString("id", "minecraft:sweeping_edge");
                return e;
            }) : v);
        }
        return tag;
    }

    // ---- 3813 BlockPosFormatAndRenamesFix (나침반 LodestonePos)

    static NbtCompound lodestonePos(NbtCompound tag) {
        FixUtil.update(tag, "LodestonePos", FixUtil::blockPos);
        return tag;
    }

    // ---- 3825 ItemStackCustomNameToOverrideComponentFix

    private static final Set<String> MAP_NAMES = Set.of(
        "filled_map.buried_treasure", "filled_map.explorer_jungle", "filled_map.explorer_swamp", "filled_map.mansion", "filled_map.monument",
        "filled_map.trial_chambers", "filled_map.village_desert", "filled_map.village_plains", "filled_map.village_savanna",
        "filled_map.village_snowy", "filled_map.village_taiga");

    static NbtCompound customNameToItemName(NbtCompound item) {
        String id = FixUtil.id(item);
        boolean banner = id.equals("minecraft:white_banner"), map = id.equals("minecraft:filled_map");
        if (!banner && !map) return item;
        if (!(item.tags().get("components") instanceof NbtCompound comps)) return item;
        if (!(comps.tags().get("minecraft:custom_name") instanceof String name)) return item;
        Optional<String> key = FixUtil.translationKey(name);
        if (key.filter(k -> banner ? k.equals("block.minecraft.ominous_banner") : MAP_NAMES.contains(k)).isPresent()) {
            FixUtil.rename(comps, "minecraft:custom_name", "minecraft:item_name");
        }
        return item;
    }

    // ---- 3945 AttributeIdFix (ItemStack)

    static NbtCompound attributeModifierIds(NbtCompound item) {
        if (item.tags().get("components") instanceof NbtCompound comps
            && comps.tags().get("minecraft:attribute_modifiers") instanceof NbtCompound am
            && am.tags().get("modifiers") instanceof NbtList mods) {
            am.putList("modifiers", EntityFixes.fixModifierIds(mods));
        }
        return item;
    }

    // =====================================================================================================
    // 3818_5 ItemStackComponentizationFix

    private static final Set<String> POTIONS = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");
    private static final Set<String> BUCKETS = Set.of(
        "minecraft:pufferfish_bucket", "minecraft:salmon_bucket", "minecraft:cod_bucket", "minecraft:tropical_fish_bucket",
        "minecraft:axolotl_bucket", "minecraft:tadpole_bucket");
    private static final List<String> BUCKET_KEYS = List.of(
        "NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health", "Age", "Variant", "HuntingCooldown", "BucketVariantTag");
    private static final Set<String> BOOLEAN_STATE_KEYS = Set.of(
        "attached", "bottom", "conditional", "disarmed", "drag", "enabled", "extended", "eye", "falling", "hanging", "has_bottle_0",
        "has_bottle_1", "has_bottle_2", "has_record", "has_book", "inverted", "in_wall", "lit", "locked", "occupied", "open", "persistent",
        "powered", "short", "signal_fire", "snowy", "triggered", "unstable", "waterlogged", "berries", "bloom", "shrieking", "can_summon",
        "up", "down", "north", "east", "south", "west", "slot_0_occupied", "slot_1_occupied", "slot_2_occupied", "slot_3_occupied",
        "slot_4_occupied", "slot_5_occupied", "cracked", "crafting");
    private static final Set<String> CONTAINERS = Set.of(
        "minecraft:shulker_box", "minecraft:chest", "minecraft:trapped_chest", "minecraft:furnace", "minecraft:ender_chest", "minecraft:dispenser",
        "minecraft:dropper", "minecraft:brewing_stand", "minecraft:hopper", "minecraft:barrel", "minecraft:smoker", "minecraft:blast_furnace",
        "minecraft:campfire", "minecraft:chiseled_bookshelf", "minecraft:crafter");

    /** 바닐라 ItemStackData: 고치는 동안 tag 에서 꺼낸 것은 지우고, 컴포넌트를 모은다. 남은 tag 는 custom_data 로 간다. */
    private static final class Data {
        final String item;
        final int count;
        final NbtCompound remainder;
        NbtCompound tag;
        final NbtCompound components = new NbtCompound();

        Data(String item, int count, NbtCompound original) {
            this.item = FixUtil.ns(item);
            this.count = count;
            NbtCompound rest = new NbtCompound();
            for (Map.Entry<String, Object> e : original.tags().entrySet()) {
                if (!e.getKey().equals("id") && !e.getKey().equals("Count") && !e.getKey().equals("tag")) rest.put(e.getKey(), e.getValue());
            }
            this.remainder = rest;
            this.tag = original.tags().get("tag") instanceof NbtCompound t ? t.copy() : new NbtCompound();
        }

        /** removeTag: 꺼내고 지운다(없으면 null). */
        Object take(String k) { return tag.tags().remove(k); }

        void set(String comp, Object v) {
            components.tags().remove(comp);
            components.tags().put(comp, v);
        }

        void setIfPresent(String comp, Object v) { if (v != null) set(comp, v); }

        /** moveTagToComponent(k, comp): 있으면 옮긴다. */
        void move(String k, String comp) { setIfPresent(comp, take(k)); }

        /** moveTagToComponent(k, comp, default): 기본값과 다를 때만 옮긴다(꺼내기는 늘 한다). */
        void move(String k, String comp, Object dflt) {
            Object v = take(k);
            if (v != null && !nbtEquals(v, dflt)) set(comp, v);
        }

        /** moveTagInto(k, target, newKey). */
        void moveInto(String k, NbtCompound target, String newKey) {
            Object v = take(k);
            if (v != null) target.put(newKey, v);
        }

        /** fixSubTag: tag[k] 를 f 로 고치고 비면 지운다. onlyIfPresent 면 없을 때 건너뛴다. */
        void fixSubTag(String k, boolean onlyIfPresent, UnaryOperator<NbtCompound> f) {
            Object v = tag.tags().get(k);
            if (onlyIfPresent && v == null) return;
            NbtCompound c = v instanceof NbtCompound cc ? cc : new NbtCompound();
            NbtCompound r = f.apply(c);
            if (r.tags().isEmpty()) tag.tags().remove(k);
            else tag.put(k, r);
        }

        boolean is(String id) { return item.equals(id); }

        boolean has(String comp) { return components.contains(comp); }

        NbtCompound write() {
            NbtCompound out = new NbtCompound();
            for (Map.Entry<String, Object> e : remainder.tags().entrySet()) out.put(e.getKey(), e.getValue());
            out.tags().remove("id");
            out.tags().remove("count");
            out.tags().remove("components");
            out.putString("id", item);
            out.putInt("count", count);
            if (!tag.tags().isEmpty()) set("minecraft:custom_data", tag);
            if (!components.tags().isEmpty()) out.putCompound("components", components);
            return out;
        }
    }

    static NbtCompound componentize(NbtCompound item) {
        if (!(item.tags().get("id") instanceof String id) || !(item.tags().get("Count") instanceof Number count)) return item;
        Data d = new Data(id, count.intValue(), item);
        fixItemStack(d);
        return d.write();
    }

    private static int asInt(Object v, int dflt) { return v instanceof Number n ? n.intValue() : dflt; }

    private static void fixItemStack(Data d) {
        int hide = asInt(d.take("HideFlags"), 0);
        d.move("Damage", "minecraft:damage", 0);
        d.move("RepairCost", "minecraft:repair_cost", 0);
        d.move("CustomModelData", "minecraft:custom_model_data");
        Object bst = d.take("BlockStateTag");
        if (bst != null) d.set("minecraft:block_state", fixBlockStateTag(bst));
        d.move("EntityTag", "minecraft:entity_data");
        d.fixSubTag("BlockEntityTag", false, be -> {
            String beId = be.tags().get("id") instanceof String s ? FixUtil.ns(s) : "";
            fixBlockEntityTag(d, be, beId);
            NbtCompound withoutId = be.copy();
            withoutId.tags().remove("id");
            return withoutId.tags().isEmpty() ? withoutId : be;
        });
        d.move("BlockEntityTag", "minecraft:block_entity_data");
        Object unbreakable = d.take("Unbreakable");
        if (unbreakable instanceof Number n && n.byteValue() != 0) {
            NbtCompound u = new NbtCompound();
            if ((hide & 4) != 0) u.putBoolean("show_in_tooltip", false);
            d.set("minecraft:unbreakable", u);
        }
        fixEnchantments(d, "Enchantments", "minecraft:enchantments", (hide & 1) != 0);
        if (d.is("minecraft:enchanted_book")) fixEnchantments(d, "StoredEnchantments", "minecraft:stored_enchantments", (hide & 32) != 0);
        d.fixSubTag("display", false, disp -> {
            fixDisplay(d, disp, hide);
            return disp;
        });
        fixAdventureModeChecks(d, "CanDestroy", "minecraft:can_break", (hide & 8) != 0);
        fixAdventureModeChecks(d, "CanPlaceOn", "minecraft:can_place_on", (hide & 16) != 0);
        fixAttributeModifiers(d, (hide & 2) != 0);
        Object trim = d.take("Trim");
        if (trim != null) {
            if ((hide & 128) != 0 && trim instanceof NbtCompound t) t.putBoolean("show_in_tooltip", false);
            d.set("minecraft:trim", trim);
        }
        if ((hide & 32) != 0) d.set("minecraft:hide_additional_tooltip", new NbtCompound());
        if (d.is("minecraft:crossbow")) {
            d.take("Charged");
            d.move("ChargedProjectiles", "minecraft:charged_projectiles", new NbtList(NbtIo.TAG_END));
        }
        if (d.is("minecraft:bundle")) d.move("Items", "minecraft:bundle_contents", new NbtList(NbtIo.TAG_END));
        if (d.is("minecraft:filled_map")) {
            d.move("map", "minecraft:map_id");
            Object decos = d.take("Decorations");
            NbtCompound map = new NbtCompound();
            if (decos instanceof NbtList l) {
                for (Object o : l.items()) {
                    if (!(o instanceof NbtCompound deco)) continue;
                    Object key = deco.tags().containsKey("id") ? deco.tags().get("id") : "";
                    if (!(key instanceof String ks) || map.contains(ks)) continue;   // Collectors.toMap 의 (a, b) -> a
                    NbtCompound v = new NbtCompound();
                    v.putString("type", mapDecorationType(deco.getInt("type")));
                    v.putDouble("x", deco.getDouble("x"));
                    v.putDouble("z", deco.getDouble("z"));
                    v.putFloat("rotation", (float) deco.getDouble("rot"));
                    map.putCompound(ks, v);
                }
            }
            if (!map.tags().isEmpty()) d.set("minecraft:map_decorations", map);
        }
        if (POTIONS.contains(d.item)) fixPotionContents(d);
        if (d.is("minecraft:writable_book")) fixWritableBook(d);
        if (d.is("minecraft:written_book")) fixWrittenBook(d);
        if (d.is("minecraft:suspicious_stew")) d.move("effects", "minecraft:suspicious_stew_effects");
        if (d.is("minecraft:debug_stick")) d.move("DebugProperty", "minecraft:debug_stick_state");
        if (BUCKETS.contains(d.item)) fixBucketedMob(d);
        if (d.is("minecraft:goat_horn")) d.move("instrument", "minecraft:instrument");
        if (d.is("minecraft:knowledge_book")) d.move("Recipes", "minecraft:recipes");
        if (d.is("minecraft:compass")) fixLodestoneTracker(d);
        if (d.is("minecraft:firework_rocket")) fixFireworkRocket(d);
        if (d.is("minecraft:firework_star")) fixFireworkStar(d);
        if (d.is("minecraft:player_head")) {
            Object owner = d.take("SkullOwner");
            if (owner != null) d.set("minecraft:profile", fixProfile(owner));
        }
    }

    /** fixBlockStateTag: 알려진 불 키의 불 값은 "true"/"false", 다른 숫자는 문자열로. */
    private static Object fixBlockStateTag(Object v) {
        if (!(v instanceof NbtCompound c)) return v;
        NbtCompound out = new NbtCompound();
        for (Map.Entry<String, Object> e : c.tags().entrySet()) {
            Object val = e.getValue();
            if (BOOLEAN_STATE_KEYS.contains(e.getKey()) && val instanceof Number n) {
                out.putString(e.getKey(), String.valueOf(n.byteValue() != 0));
            } else if (val instanceof Number n) {
                out.putString(e.getKey(), n.toString());
            } else {
                out.put(e.getKey(), val);
            }
        }
        return out;
    }

    private static void fixDisplay(Data d, NbtCompound disp, int hide) {
        d.setIfPresent("minecraft:custom_name", disp.tags().get("Name"));
        d.setIfPresent("minecraft:lore", disp.tags().get("Lore"));
        Object color = disp.tags().get("color");
        boolean hideDye = (hide & 64) != 0;
        if (color instanceof Number || hideDye) {
            NbtCompound dyed = new NbtCompound();
            dyed.putInt("rgb", color instanceof Number n ? n.intValue() : 10511680);
            if (hideDye) dyed.putBoolean("show_in_tooltip", false);
            d.set("minecraft:dyed_color", dyed);
        }
        if (disp.tags().get("LocName") instanceof String loc) d.set("minecraft:item_name", FixUtil.translatable(loc));
        if (d.is("minecraft:filled_map")) {
            d.setIfPresent("minecraft:map_color", disp.tags().get("MapColor"));
            disp.tags().remove("MapColor");
        }
        disp.tags().remove("Name");
        disp.tags().remove("Lore");
        disp.tags().remove("color");
        disp.tags().remove("LocName");
    }

    private static void fixBlockEntityTag(Data d, NbtCompound be, String beId) {
        d.setIfPresent("minecraft:lock", be.tags().get("Lock"));
        be.tags().remove("Lock");
        Object loot = be.tags().get("LootTable");
        if (loot != null) {
            NbtCompound cl = new NbtCompound();
            cl.put("loot_table", loot);
            long seed = be.getLong("LootTableSeed");
            if (seed != 0L) cl.putLong("seed", seed);
            d.set("minecraft:container_loot", cl);
            be.tags().remove("LootTable");
            be.tags().remove("LootTableSeed");
        }
        switch (beId) {
            case "minecraft:skull" -> {
                d.setIfPresent("minecraft:note_block_sound", be.tags().get("note_block_sound"));
                be.tags().remove("note_block_sound");
            }
            case "minecraft:decorated_pot" -> {
                d.setIfPresent("minecraft:pot_decorations", be.tags().get("sherds"));
                Object it = be.tags().get("item");
                if (it != null) {
                    NbtList c = new NbtList(NbtIo.TAG_COMPOUND);
                    NbtCompound slot = new NbtCompound();
                    slot.putInt("slot", 0);
                    slot.put("item", it);
                    c.add(slot);
                    d.set("minecraft:container", c);
                }
                be.tags().remove("sherds");
                be.tags().remove("item");
            }
            case "minecraft:banner" -> {
                d.setIfPresent("minecraft:banner_patterns", be.tags().get("patterns"));
                if (be.tags().get("Base") instanceof Number base) d.set("minecraft:base_color", BlockEntityFixes.dyeColor(base.intValue()));
                be.tags().remove("patterns");
                be.tags().remove("Base");
            }
            case "minecraft:beehive" -> {
                d.setIfPresent("minecraft:bees", be.tags().get("bees"));
                be.tags().remove("bees");
            }
            default -> {
                if (CONTAINERS.contains(beId)) {
                    NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
                    if (be.tags().get("Items") instanceof NbtList items) {
                        for (Object o : items.items()) {
                            if (!(o instanceof NbtCompound e)) continue;
                            NbtCompound slot = new NbtCompound();
                            slot.putInt("slot", e.getByte("Slot") & 255);
                            NbtCompound stack = e.copy();
                            stack.tags().remove("Slot");
                            slot.putCompound("item", stack);
                            out.add(slot);
                        }
                    }
                    if (!out.items().isEmpty()) d.set("minecraft:container", out);
                    be.tags().remove("Items");
                }
            }
        }
    }

    private static void fixEnchantments(Data d, String key, String comp, boolean hide) {
        Object v = d.take(key);
        NbtCompound levels = new NbtCompound();
        boolean any = false;
        if (v instanceof NbtList l) {
            for (Object o : l.items()) {
                if (!(o instanceof NbtCompound e)) continue;
                if (!(e.tags().get("id") instanceof String id) || !(e.tags().get("lvl") instanceof Number lvl)) continue;
                levels.putInt(id, Math.clamp(lvl.intValue(), 0, 255));
                any = true;
            }
        }
        if (any || hide) {
            NbtCompound c = new NbtCompound();
            c.putCompound("levels", levels);
            if (hide) c.putBoolean("show_in_tooltip", false);
            d.set(comp, c);
        }
        if (v != null && !any) d.set("minecraft:enchantment_glint_override", (byte) 1);
    }

    private static void fixAdventureModeChecks(Data d, String key, String comp, boolean hide) {
        Object v = d.take(key);
        if (v == null) return;
        NbtList preds = new NbtList(NbtIo.TAG_COMPOUND);
        if (v instanceof NbtList l) {
            for (Object o : l.items()) preds.add(o instanceof String s ? parseBlockPredicate(s) : o);
        }
        NbtCompound c = new NbtCompound();
        c.putList("predicates", preds);
        if (hide) c.putBoolean("show_in_tooltip", false);
        d.set(comp, c);
    }

    /** "블록[상태]{nbt}" 문자열 → {blocks, state?, nbt?}. */
    private static NbtCompound parseBlockPredicate(String s) {
        int bracket = s.indexOf('['), brace = s.indexOf('{');
        int end = s.length();
        if (bracket != -1) end = bracket;
        if (brace != -1) end = Math.min(end, brace);
        NbtCompound p = new NbtCompound();
        p.putString("blocks", s.substring(0, end).trim());
        int close = s.indexOf(']');
        if (bracket != -1 && close != -1) {
            NbtCompound state = new NbtCompound();
            for (String kv : s.substring(bracket + 1, close).split(",", -1)) {
                int eq = kv.indexOf('=');
                if (eq != -1) state.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
            p.putCompound("state", state);
        }
        int closeBrace = s.indexOf('}');
        if (brace != -1 && closeBrace != -1) p.putString("nbt", s.substring(brace, closeBrace + 1));
        return p;
    }

    private static void fixAttributeModifiers(Data d, boolean hide) {
        Object v = d.take("AttributeModifiers");
        if (v == null) return;
        NbtList mods = new NbtList(NbtIo.TAG_COMPOUND);
        if (v instanceof NbtList l) {
            for (Object o : l.items()) mods.add(o instanceof NbtCompound m ? fixAttributeModifier(m) : o);
        }
        NbtCompound c = new NbtCompound();
        c.putList("modifiers", mods);
        if (hide) c.putBoolean("show_in_tooltip", false);
        d.set("minecraft:attribute_modifiers", c);
    }

    private static NbtCompound fixAttributeModifier(NbtCompound m) {
        NbtCompound out = new NbtCompound();
        out.putString("name", "");
        out.putDouble("amount", 0.0);
        out.putString("operation", "add_value");
        copy(m, "AttributeName", out, "type");
        copy(m, "Slot", out, "slot");
        copy(m, "UUID", out, "uuid");
        copy(m, "Name", out, "name");
        copy(m, "Amount", out, "amount");
        if (m.contains("Operation")) {
            out.putString("operation", switch (asInt(m.tags().get("Operation"), 0)) {
                case 1 -> "add_multiplied_base";
                case 2 -> "add_multiplied_total";
                default -> "add_value";
            });
        }
        return out;
    }

    /** Dynamic.copyField: 있으면 덮어쓴다(키 자리는 처음 넣은 곳). */
    private static void copy(NbtCompound from, String k, NbtCompound to, String nk) {
        if (from.contains(k)) to.put(nk, from.tags().get(k));
    }

    private static String mapDecorationType(int t) {
        return switch (t) {
            case 1 -> "frame";
            case 2 -> "red_marker";
            case 3 -> "blue_marker";
            case 4 -> "target_x";
            case 5 -> "target_point";
            case 6 -> "player_off_map";
            case 7 -> "player_off_limits";
            case 8 -> "mansion";
            case 9 -> "monument";
            case 10 -> "banner_white";
            case 11 -> "banner_orange";
            case 12 -> "banner_magenta";
            case 13 -> "banner_light_blue";
            case 14 -> "banner_yellow";
            case 15 -> "banner_lime";
            case 16 -> "banner_pink";
            case 17 -> "banner_gray";
            case 18 -> "banner_light_gray";
            case 19 -> "banner_cyan";
            case 20 -> "banner_purple";
            case 21 -> "banner_blue";
            case 22 -> "banner_brown";
            case 23 -> "banner_green";
            case 24 -> "banner_red";
            case 25 -> "banner_black";
            case 26 -> "red_x";
            case 27 -> "village_desert";
            case 28 -> "village_plains";
            case 29 -> "village_savanna";
            case 30 -> "village_snowy";
            case 31 -> "village_taiga";
            case 32 -> "jungle_temple";
            case 33 -> "swamp_hut";
            default -> "player";
        };
    }

    private static void fixPotionContents(Data d) {
        NbtCompound c = new NbtCompound();
        if (d.take("Potion") instanceof String p && !p.equals("minecraft:empty")) c.putString("potion", p);
        d.moveInto("CustomPotionColor", c, "custom_color");
        d.moveInto("custom_potion_effects", c, "custom_effects");
        if (!c.tags().isEmpty()) d.set("minecraft:potion_contents", c);
    }

    private static void fixWritableBook(Data d) {
        NbtList pages = fixBookPages(d);
        if (pages != null) {
            NbtCompound c = new NbtCompound();
            c.putList("pages", pages);
            d.set("minecraft:writable_book_content", c);
        }
    }

    private static void fixWrittenBook(Data d) {
        NbtList pages = fixBookPages(d);
        Object titleV = d.take("title");
        String title = titleV instanceof String s ? s : "";
        Object filteredTitle = d.take("filtered_title");
        NbtCompound c = new NbtCompound();
        c.putCompound("title", filterable(title, filteredTitle instanceof String s ? s : null));
        d.moveInto("author", c, "author");
        d.moveInto("resolved", c, "resolved");
        d.moveInto("generation", c, "generation");
        if (pages != null) c.putList("pages", pages);
        d.set("minecraft:written_book_content", c);
    }

    private static NbtList fixBookPages(Data d) {
        Object pv = d.take("pages");
        Object fv = d.take("filtered_pages");
        List<Object> pages = pv instanceof NbtList l ? l.items() : List.of();
        if (pages.isEmpty()) return null;
        NbtCompound filtered = fv instanceof NbtCompound f ? f : new NbtCompound();
        NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
        for (int i = 0; i < pages.size(); i++) {
            String raw = pages.get(i) instanceof String s ? s : "";
            Object f = filtered.tags().get(String.valueOf(i));
            out.add(filterable(raw, f == null ? null : (f instanceof String fs ? fs : "")));
        }
        return out;
    }

    private static NbtCompound filterable(String raw, String filtered) {
        NbtCompound c = new NbtCompound();
        c.putString("raw", raw);
        if (filtered != null) c.putString("filtered", filtered);
        return c;
    }

    private static void fixBucketedMob(Data d) {
        NbtCompound c = new NbtCompound();
        for (String k : BUCKET_KEYS) d.moveInto(k, c, k);
        if (!c.tags().isEmpty()) d.set("minecraft:bucket_entity_data", c);
    }

    private static void fixLodestoneTracker(Data d) {
        Object pos = d.take("LodestonePos");
        Object dim = d.take("LodestoneDimension");
        if (pos == null && dim == null) return;
        Object trackedV = d.take("LodestoneTracked");
        boolean tracked = !(trackedV instanceof Number n) || n.byteValue() != 0;
        NbtCompound c = new NbtCompound();
        if (pos != null && dim != null) {
            NbtCompound target = new NbtCompound();
            target.put("pos", pos);
            target.put("dimension", dim);
            c.putCompound("target", target);
        }
        if (!tracked) c.putBoolean("tracked", false);
        d.set("minecraft:lodestone_tracker", c);
    }

    private static void fixFireworkStar(Data d) {
        d.fixSubTag("Explosion", true, ex -> {
            d.set("minecraft:firework_explosion", fixExplosion(ex.copy()));
            for (String k : new String[] {"Type", "Colors", "FadeColors", "Trail", "Flicker"}) ex.tags().remove(k);
            return ex;
        });
    }

    private static void fixFireworkRocket(Data d) {
        d.fixSubTag("Fireworks", true, fw -> {
            NbtList explosions = new NbtList(NbtIo.TAG_COMPOUND);
            if (fw.tags().get("Explosions") instanceof NbtList l) {
                for (Object o : l.items()) explosions.add(o instanceof NbtCompound e ? fixExplosion(e.copy()) : o);
            }
            int flight = asInt(fw.tags().get("Flight"), 0);
            NbtCompound c = new NbtCompound();
            c.putList("explosions", explosions);
            c.putByte("flight_duration", (byte) flight);
            d.set("minecraft:fireworks", c);
            fw.tags().remove("Explosions");
            fw.tags().remove("Flight");
            return fw;
        });
    }

    private static NbtCompound fixExplosion(NbtCompound e) {
        e.putString("shape", switch (e.getInt("Type")) {
            case 1 -> "large_ball";
            case 2 -> "star";
            case 3 -> "creeper";
            case 4 -> "burst";
            default -> "small_ball";
        });
        e.tags().remove("Type");
        FixUtil.rename(e, "Colors", "colors");
        FixUtil.rename(e, "FadeColors", "fade_colors");
        FixUtil.rename(e, "Trail", "has_trail");
        FixUtil.rename(e, "Flicker", "has_twinkle");
        return e;
    }

    /** fixProfile: 문자열 이름이나 {Name, Id, Properties} → {name?, id?, properties?}. */
    static NbtCompound fixProfile(Object v) {
        NbtCompound out = new NbtCompound();
        if (v instanceof String s) {
            if (isValidPlayerName(s)) out.putString("name", s);
            return out;
        }
        if (!(v instanceof NbtCompound c)) return out;
        String name = c.tags().get("Name") instanceof String s ? s : "";
        if (isValidPlayerName(name)) out.putString("name", name);
        if (c.contains("Id")) out.put("id", c.tags().get("Id"));
        if (c.tags().get("Properties") instanceof NbtCompound props) {
            NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
            for (Map.Entry<String, Object> e : props.tags().entrySet()) {
                if (!(e.getValue() instanceof NbtList values)) continue;
                for (Object o : values.items()) {
                    if (!(o instanceof NbtCompound pv)) continue;
                    NbtCompound p = new NbtCompound();
                    p.putString("name", e.getKey());
                    p.putString("value", pv.tags().get("Value") instanceof String s ? s : "");
                    if (pv.tags().get("Signature") instanceof String sig) p.putString("signature", sig);
                    list.add(p);
                }
            }
            if (!list.items().isEmpty()) out.putList("properties", list);
        }
        return out;
    }

    private static boolean isValidPlayerName(String s) {
        if (s.length() > 16) return false;
        return s.chars().noneMatch(ch -> ch <= 32 || ch >= 127);
    }

    // ---- NBT 값 같음(바닐라 Dynamic.equals 는 태그 equals)

    static boolean nbtEquals(Object a, Object b) {
        if (a instanceof NbtCompound ca && b instanceof NbtCompound cb) {
            if (ca.tags().size() != cb.tags().size()) return false;
            for (Map.Entry<String, Object> e : ca.tags().entrySet()) {
                if (!cb.contains(e.getKey()) || !nbtEquals(e.getValue(), cb.tags().get(e.getKey()))) return false;
            }
            return true;
        }
        if (a instanceof NbtList la && b instanceof NbtList lb) {
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) if (!nbtEquals(la.get(i), lb.get(i))) return false;
            return true;
        }
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof int[] x && b instanceof int[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof long[] x && b instanceof long[] y) return java.util.Arrays.equals(x, y);
        return java.util.Objects.equals(a, b);
    }
}
