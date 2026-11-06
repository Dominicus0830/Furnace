// 타입이 있는 아이템 컴포넌트 키 모음. ItemStack.get/with/without 과 함께 쓴다.
// 여기 없는 컴포넌트는 ItemComponent.raw(id) 로 SNBT 문자열 그대로 다룰 수 있다.
package org.domi.furnace.api.item;

import java.util.List;
import java.util.Map;
import org.domi.furnace.api.text.Text;

public final class ItemComponents {
    private ItemComponents() {}

    // ---- 이름·설명
    /** 모루로 붙인 이름처럼 아이템 이름을 덮어쓴다(기울임 기본). */
    public static final ItemComponent<Text> CUSTOM_NAME = new ItemComponent<>("custom_name", Text.class);
    /** 기본 아이템 이름을 바꾼다(기울임 없음, 모루로 바꿔도 이 값은 남는다). */
    public static final ItemComponent<Text> ITEM_NAME = new ItemComponent<>("item_name", Text.class);
    @SuppressWarnings("unchecked")
    public static final ItemComponent<List<Text>> LORE = new ItemComponent<>("lore", List.class);
    public static final ItemComponent<Rarity> RARITY = new ItemComponent<>("rarity", Rarity.class);

    // ---- 수치
    public static final ItemComponent<Integer> MAX_STACK_SIZE = new ItemComponent<>("max_stack_size", Integer.class);
    public static final ItemComponent<Integer> MAX_DAMAGE = new ItemComponent<>("max_damage", Integer.class);
    /** 닳은 내구도. */
    public static final ItemComponent<Integer> DAMAGE = new ItemComponent<>("damage", Integer.class);
    public static final ItemComponent<Integer> REPAIR_COST = new ItemComponent<>("repair_cost", Integer.class);
    public static final ItemComponent<Integer> CUSTOM_MODEL_DATA = new ItemComponent<>("custom_model_data", Integer.class);
    public static final ItemComponent<Integer> MAP_COLOR = new ItemComponent<>("map_color", Integer.class);
    public static final ItemComponent<Integer> MAP_ID = new ItemComponent<>("map_id", Integer.class);
    public static final ItemComponent<Integer> OMINOUS_BOTTLE_AMPLIFIER = new ItemComponent<>("ominous_bottle_amplifier", Integer.class);

    // ---- 상태
    public static final ItemComponent<Unbreakable> UNBREAKABLE = new ItemComponent<>("unbreakable", Unbreakable.class);
    public static final ItemComponent<ItemEnchantments> ENCHANTMENTS = new ItemComponent<>("enchantments", ItemEnchantments.class);
    public static final ItemComponent<ItemEnchantments> STORED_ENCHANTMENTS = new ItemComponent<>("stored_enchantments", ItemEnchantments.class);
    /** 참이면 마법 부여가 없어도 빛나고, 거짓이면 있어도 빛나지 않는다. */
    public static final ItemComponent<Boolean> ENCHANTMENT_GLINT_OVERRIDE = new ItemComponent<>("enchantment_glint_override", Boolean.class);
    public static final ItemComponent<DyedColor> DYED_COLOR = new ItemComponent<>("dyed_color", DyedColor.class);
    public static final ItemComponent<AttributeModifiers> ATTRIBUTE_MODIFIERS = new ItemComponent<>("attribute_modifiers", AttributeModifiers.class);

    // ---- 있고 없고만 뜻하는 것
    public static final ItemComponent<Marker> HIDE_TOOLTIP = new ItemComponent<>("hide_tooltip", Marker.class);
    public static final ItemComponent<Marker> HIDE_ADDITIONAL_TOOLTIP = new ItemComponent<>("hide_additional_tooltip", Marker.class);
    public static final ItemComponent<Marker> FIRE_RESISTANT = new ItemComponent<>("fire_resistant", Marker.class);
    public static final ItemComponent<Marker> INTANGIBLE_PROJECTILE = new ItemComponent<>("intangible_projectile", Marker.class);

    // ---- 문자열·목록·맵
    /** 통을 여는 열쇠 이름(아이템 이름이 같아야 열린다). */
    public static final ItemComponent<String> LOCK = new ItemComponent<>("lock", String.class);
    public static final ItemComponent<String> NOTE_BLOCK_SOUND = new ItemComponent<>("note_block_sound", String.class);
    /** 방패·깃발 바탕색(white, orange …). */
    public static final ItemComponent<String> BASE_COLOR = new ItemComponent<>("base_color", String.class);
    @SuppressWarnings("unchecked")
    public static final ItemComponent<List<String>> RECIPES = new ItemComponent<>("recipes", List.class);
    @SuppressWarnings("unchecked")
    public static final ItemComponent<Map<String, String>> BLOCK_STATE = new ItemComponent<>("block_state", Map.class);
}
