package org.domi.furnace.compat.nms;

import java.util.Map;
import org.objectweb.asm.Type;

/**
 * Paper 1.21.1 이 옛 플러그인을 위해 클래스를 올릴 때 바꿔 주는 Bukkit 필드 이름(CraftBukkit {@code FieldRename}) 가운데 이 호환 계층에
 * 해당 형이 있는 것. 필드 읽기({@code GETSTATIC})는 새 이름으로, 열거형의 {@code valueOf(String)} 은 옛 이름도 받는 다리로 바꾼다.
 * 모든 api-version 에 같은 규칙이다(Paper 의 {@code forAllVersions}). 1.21.1 뒤에 생긴 이름 바꿈(속성 GENERIC_ 떼기·소리 등)은 넣지 않는다.
 */
public final class ApiRenames {

    static final String INTERNAL = Type.getInternalName(ApiRenames.class);
    static final String ITEM_FLAG = "org/bukkit/inventory/ItemFlag";
    static final String PARTICLE = "org/bukkit/Particle";
    static final String BIOME = "org/bukkit/block/Biome";
    static final String ENTITY_TYPE = "org/bukkit/entity/EntityType";
    static final String POTION_TYPE = "org/bukkit/potion/PotionType";
    static final String ENCHANTMENT = "org/bukkit/enchantments/Enchantment";
    static final String POTION_EFFECT_TYPE = "org/bukkit/potion/PotionEffectType";

    /** 변환기가 클래스를 열어 볼지 고르는 바이트 열(상수 풀에 이 이름이 있어야 바꿀 것이 있다). */
    static final String[] OWNERS = {ITEM_FLAG, PARTICLE, BIOME, ENTITY_TYPE, POTION_TYPE, ENCHANTMENT, POTION_EFFECT_TYPE};

    /** 1.20.5 아이템 설명 숨김 표시. */
    private static final Map<String, String> ITEM_FLAG_RENAMES = Map.of("HIDE_POTION_EFFECTS", "HIDE_ADDITIONAL_TOOLTIP");

    /** 1.20.5 입자 이름(레지스트리 키와 맞춤). */
    private static final Map<String, String> PARTICLE_RENAMES = Map.ofEntries(
            Map.entry("EXPLOSION_NORMAL", "POOF"), Map.entry("EXPLOSION_LARGE", "EXPLOSION"), Map.entry("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
            Map.entry("FIREWORKS_SPARK", "FIREWORK"), Map.entry("WATER_BUBBLE", "BUBBLE"), Map.entry("WATER_SPLASH", "SPLASH"),
            Map.entry("WATER_WAKE", "FISHING"), Map.entry("SUSPENDED", "UNDERWATER"), Map.entry("SUSPENDED_DEPTH", "UNDERWATER"),
            Map.entry("CRIT_MAGIC", "ENCHANTED_HIT"), Map.entry("SMOKE_NORMAL", "SMOKE"), Map.entry("SMOKE_LARGE", "LARGE_SMOKE"),
            Map.entry("SPELL", "EFFECT"), Map.entry("SPELL_INSTANT", "INSTANT_EFFECT"), Map.entry("SPELL_MOB", "ENTITY_EFFECT"),
            Map.entry("SPELL_WITCH", "WITCH"), Map.entry("DRIP_WATER", "DRIPPING_WATER"), Map.entry("DRIP_LAVA", "DRIPPING_LAVA"),
            Map.entry("VILLAGER_ANGRY", "ANGRY_VILLAGER"), Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"), Map.entry("TOWN_AURA", "MYCELIUM"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"), Map.entry("REDSTONE", "DUST"), Map.entry("SNOWBALL", "ITEM_SNOWBALL"),
            Map.entry("SNOW_SHOVEL", "ITEM_SNOWBALL"), Map.entry("SLIME", "ITEM_SLIME"), Map.entry("ITEM_CRACK", "ITEM"),
            Map.entry("BLOCK_CRACK", "BLOCK"), Map.entry("BLOCK_DUST", "BLOCK"), Map.entry("WATER_DROP", "RAIN"),
            Map.entry("MOB_APPEARANCE", "ELDER_GUARDIAN"), Map.entry("TOTEM", "TOTEM_OF_UNDYING"), Map.entry("GUST_EMITTER", "GUST_EMITTER_LARGE"));

    /** 1.18 바이옴 이름. */
    private static final Map<String, String> BIOME_RENAMES = Map.ofEntries(
            Map.entry("NETHER", "NETHER_WASTES"), Map.entry("TALL_BIRCH_FOREST", "OLD_GROWTH_BIRCH_FOREST"),
            Map.entry("GIANT_TREE_TAIGA", "OLD_GROWTH_PINE_TAIGA"), Map.entry("GIANT_SPRUCE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA"),
            Map.entry("SNOWY_TUNDRA", "SNOWY_PLAINS"), Map.entry("JUNGLE_EDGE", "SPARSE_JUNGLE"), Map.entry("STONE_SHORE", "STONY_SHORE"),
            Map.entry("MOUNTAINS", "WINDSWEPT_HILLS"), Map.entry("WOODED_MOUNTAINS", "WINDSWEPT_FOREST"),
            Map.entry("GRAVELLY_MOUNTAINS", "WINDSWEPT_GRAVELLY_HILLS"), Map.entry("SHATTERED_SAVANNA", "WINDSWEPT_SAVANNA"),
            Map.entry("WOODED_BADLANDS_PLATEAU", "WOODED_BADLANDS"));

    /** 1.20.5 엔티티 종류 이름(과 그 전의 옛 이름들). */
    private static final Map<String, String> ENTITY_TYPE_RENAMES = Map.ofEntries(
            Map.entry("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN"), Map.entry("DROPPED_ITEM", "ITEM"), Map.entry("LEASH_HITCH", "LEASH_KNOT"),
            Map.entry("ENDER_SIGNAL", "EYE_OF_ENDER"), Map.entry("SPLASH_POTION", "POTION"), Map.entry("THROWN_EXP_BOTTLE", "EXPERIENCE_BOTTLE"),
            Map.entry("PRIMED_TNT", "TNT"), Map.entry("FIREWORK", "FIREWORK_ROCKET"), Map.entry("MINECART_COMMAND", "COMMAND_BLOCK_MINECART"),
            Map.entry("MINECART_CHEST", "CHEST_MINECART"), Map.entry("MINECART_FURNACE", "FURNACE_MINECART"),
            Map.entry("MINECART_TNT", "TNT_MINECART"), Map.entry("MINECART_HOPPER", "HOPPER_MINECART"),
            Map.entry("MINECART_MOB_SPAWNER", "SPAWNER_MINECART"), Map.entry("MUSHROOM_COW", "MOOSHROOM"), Map.entry("SNOWMAN", "SNOW_GOLEM"),
            Map.entry("FISHING_HOOK", "FISHING_BOBBER"), Map.entry("LIGHTNING", "LIGHTNING_BOLT"), Map.entry("XP_ORB", "EXPERIENCE_ORB"),
            Map.entry("EYE_OF_ENDER_SIGNAL", "EYE_OF_ENDER"), Map.entry("XP_BOTTLE", "EXPERIENCE_BOTTLE"),
            Map.entry("FIREWORKS_ROCKET", "FIREWORK_ROCKET"), Map.entry("EVOCATION_FANGS", "EVOKER_FANGS"),
            Map.entry("EVOCATION_ILLAGER", "EVOKER"), Map.entry("VINDICATION_ILLAGER", "VINDICATOR"),
            Map.entry("ILLUSION_ILLAGER", "ILLUSIONER"), Map.entry("COMMANDBLOCK_MINECART", "COMMAND_BLOCK_MINECART"),
            Map.entry("VILLAGER_GOLEM", "IRON_GOLEM"), Map.entry("ENDER_CRYSTAL", "END_CRYSTAL"), Map.entry("ZOMBIE_PIGMAN", "ZOMBIFIED_PIGLIN"));

    /** 1.20.5 물약 종류 이름. */
    private static final Map<String, String> POTION_TYPE_RENAMES = Map.of(
            "JUMP", "LEAPING", "SPEED", "SWIFTNESS", "INSTANT_HEAL", "HEALING", "INSTANT_DAMAGE", "HARMING", "REGEN", "REGENERATION");

    /** 1.20.5 마법 부여 상수 이름(레지스트리 키와 맞춤). */
    private static final Map<String, String> ENCHANTMENT_RENAMES = Map.ofEntries(
            Map.entry("PROTECTION_ENVIRONMENTAL", "PROTECTION"), Map.entry("PROTECTION_FIRE", "FIRE_PROTECTION"),
            Map.entry("PROTECTION_FALL", "FEATHER_FALLING"), Map.entry("PROTECTION_EXPLOSIONS", "BLAST_PROTECTION"),
            Map.entry("PROTECTION_PROJECTILE", "PROJECTILE_PROTECTION"), Map.entry("OXYGEN", "RESPIRATION"),
            Map.entry("WATER_WORKER", "AQUA_AFFINITY"), Map.entry("DAMAGE_ALL", "SHARPNESS"), Map.entry("DAMAGE_UNDEAD", "SMITE"),
            Map.entry("DAMAGE_ARTHROPODS", "BANE_OF_ARTHROPODS"), Map.entry("LOOT_BONUS_MOBS", "LOOTING"), Map.entry("DIG_SPEED", "EFFICIENCY"),
            Map.entry("DURABILITY", "UNBREAKING"), Map.entry("LOOT_BONUS_BLOCKS", "FORTUNE"), Map.entry("ARROW_DAMAGE", "POWER"),
            Map.entry("ARROW_KNOCKBACK", "PUNCH"), Map.entry("ARROW_FIRE", "FLAME"), Map.entry("ARROW_INFINITE", "INFINITY"),
            Map.entry("LUCK", "LUCK_OF_THE_SEA"), Map.entry("SWEEPING", "SWEEPING_EDGE"));

    /** 1.20.5 상태 효과 상수 이름. */
    private static final Map<String, String> POTION_EFFECT_TYPE_RENAMES = Map.of(
            "SLOW", "SLOWNESS", "FAST_DIGGING", "HASTE", "SLOW_DIGGING", "MINING_FATIGUE", "INCREASE_DAMAGE", "STRENGTH",
            "HEAL", "INSTANT_HEALTH", "HARM", "INSTANT_DAMAGE", "JUMP", "JUMP_BOOST", "CONFUSION", "NAUSEA", "DAMAGE_RESISTANCE", "RESISTANCE");

    private static final Map<String, Map<String, String>> FIELDS = Map.of(
            ITEM_FLAG, ITEM_FLAG_RENAMES, PARTICLE, PARTICLE_RENAMES, BIOME, BIOME_RENAMES, ENTITY_TYPE, ENTITY_TYPE_RENAMES,
            POTION_TYPE, POTION_TYPE_RENAMES, ENCHANTMENT, ENCHANTMENT_RENAMES, POTION_EFFECT_TYPE, POTION_EFFECT_TYPE_RENAMES);

    /** valueOf 다리가 있는 열거형 → 다리 메서드 이름. */
    private static final Map<String, String> VALUE_OF = Map.of(
            ITEM_FLAG, "valueOfItemFlag", PARTICLE, "valueOfParticle", BIOME, "valueOfBiome", ENTITY_TYPE, "valueOfEntityType",
            POTION_TYPE, "valueOfPotionType");

    private ApiRenames() {}

    /** owner 의 정적 필드 이름을 이 서버의 이름으로. 바꿀 것이 없으면 그대로. */
    static String field(String owner, String name) {
        Map<String, String> m = FIELDS.get(owner);
        return m == null ? name : m.getOrDefault(name, name);
    }

    static boolean reroutesValueOf(String owner, String desc) {
        return VALUE_OF.containsKey(owner) && desc.equals("(Ljava/lang/String;)L" + owner + ";");
    }

    static String valueOfName(String owner) { return VALUE_OF.get(owner); }

    private static String renamed(Map<String, String> m, String name) { return name == null ? null : m.getOrDefault(name, name); }

    /** {@code ItemFlag.valueOf} 다리: 옛 이름도 받는다(Paper 의 {@code FieldRename.valueOf_ItemFlag}). */
    public static org.bukkit.inventory.ItemFlag valueOfItemFlag(String name) {
        return org.bukkit.inventory.ItemFlag.valueOf(renamed(ITEM_FLAG_RENAMES, name));
    }

    /** {@code Particle.valueOf} 다리. */
    public static org.bukkit.Particle valueOfParticle(String name) { return org.bukkit.Particle.valueOf(renamed(PARTICLE_RENAMES, name)); }

    /** {@code Biome.valueOf} 다리. */
    public static org.bukkit.block.Biome valueOfBiome(String name) { return org.bukkit.block.Biome.valueOf(renamed(BIOME_RENAMES, name)); }

    /** {@code EntityType.valueOf} 다리. */
    public static org.bukkit.entity.EntityType valueOfEntityType(String name) {
        return org.bukkit.entity.EntityType.valueOf(renamed(ENTITY_TYPE_RENAMES, name));
    }

    /** {@code PotionType.valueOf} 다리. */
    public static org.bukkit.potion.PotionType valueOfPotionType(String name) {
        return org.bukkit.potion.PotionType.valueOf(renamed(POTION_TYPE_RENAMES, name));
    }
}
