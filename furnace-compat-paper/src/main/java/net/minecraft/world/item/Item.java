package net.minecraft.world.item;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ItemLike;
import org.bukkit.Material;

/**
 * 아이템 종류 shim(Mojang 이름, H6 최소 표면). 같은 레지스트리 키는 같은 객체라 바닐라처럼 {@code ==} 로 비교할 수 있다.
 * 레지스트리({@code BuiltInRegistries.ITEM}·{@code Items.*}, N0)의 값도 이 객체다.
 */
public class Item implements net.minecraft.world.flag.FeatureElement, ItemLike {

    private static final ConcurrentHashMap<String, Item> BY_KEY = new ConcurrentHashMap<>();

    /** 공기(빈 스택의 종류). */
    static final Item AIR = byKey("minecraft:air");

    private final String key;

    /**
     * 생성기 전용 생성자(T-231): {@link #byKey} 가 바닐라 클래스(종류 표 — 블록 아이템은 {@code BlockItem}, 칼은 {@code SwordItem} …)로 만든다.
     * 표지에 레지스트리 키가 실려 온다. 바닐라 하위 클래스(생성 shim)는 이 생성자로 올라온다.
     */
    protected Item(org.domi.furnace.compat.nms.ShimCtor tag) {
        if (tag == null || !(tag.payload() instanceof String k)) throw new IllegalStateException("items are created by Item.byKey only");
        this.key = k;
    }

    /** 호환 모듈 전용 — 레지스트리 키(minecraft:stone)의 종류. */
    public static Item byKey(String key) {
        Objects.requireNonNull(key, "key");
        String k = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        return BY_KEY.computeIfAbsent(k, Item::create);
    }

    /**
     * 표에 있는 이름은 그 바닐라 클래스로, 없는 이름(바닐라에 없는 종류)은 Item 으로. BY_KEY.computeIfAbsent 안에서 불리므로 생성 Item 하위 클래스의
     * 정적 초기화가 byKey 를 부르면(예: 명세로 {@code Items.X} 값을 가진 필드를 달면) "Recursive update" 가 난다 — 그런 필드는 다른 곳에 둔다.
     */
    private static Item create(String key) {
        String cls = org.domi.furnace.compat.nms.NmsKinds.itemClass(key);
        if (cls == null) return new Item(org.domi.furnace.compat.nms.ShimCtor.carrying(key));
        return org.domi.furnace.compat.nms.NmsKinds.create(cls, Item.class, key);
    }

    /** 호환 모듈 전용 — 레지스트리 키. */
    public String furnaceKey() { return key; }

    @Override
    public Item asItem() { return this; }

    /** 바닐라: null 이면 0, 아니면 레지스트리 번호(모르는 아이템은 공기의 번호). */
    public static int getId(Item item) { return item == null ? 0 : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item); }

    /** 바닐라: 모르는 번호면 공기. */
    public static Item byId(int id) { return net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id); }

    /** 바닐라 Item.byBlock: 블록 아이템이 없으면 공기(벽 횃불 → 횃불). */
    public static Item byBlock(net.minecraft.world.level.block.Block block) { return block.asItem(); }

    public net.minecraft.core.Holder.Reference<Item> builtInRegistryHolder() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getHolder(net.minecraft.resources.ResourceLocation.parse(key)).orElseThrow();
    }

    /**
     * 바닐라 getDescriptionId: 블록 아이템(BlockItem·AirItem)은 그 블록의 번역 키 block.&lt;이름공간&gt;.&lt;경로&gt;, 그 밖은 item.&lt;이름공간&gt;.&lt;경로&gt;
     * (경로의 / 는 .). 1.21.1 에서 블록 아이템의 블록 이름은 아이템 이름과 같고, 이름이 다른 것(씨앗·당근 등 ItemNameBlockItem, 가루눈 양동이)은
     * 아이템 키를 쓴다. 블록과 이름이 같은데 블록 키가 아닌 아이템은 밀(그냥 Item)과 네더 사마귀(ItemNameBlockItem) 둘뿐이다(1.21.1 jar 의
     * Items 등록 대조).
     */
    public String getDescriptionId() {
        boolean block = !ITEM_NAMED.contains(key)
                && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse(key));
        int c = key.indexOf(':');
        return (block ? "block." : "item.") + key.substring(0, c) + "." + key.substring(c + 1).replace('/', '.');
    }

    /** 블록과 이름이 같지만 아이템 키를 쓰는 아이템. */
    private static final java.util.Set<String> ITEM_NAMED = java.util.Set.of("minecraft:wheat", "minecraft:nether_wart");

    /**
     * 바닐라 getDescriptionId(stack): 스택에 따라 키가 바뀌는 아이템 — 자석 나침반(lodestone_tracker 가 있는 나침반), 물약·투척·잔류 물약·물약 화살
     * (키 + ".effect." + 물약 이름, 물약이 없으면 empty), 바탕색이 있는 방패(키 + "." + 색). 그 밖은 {@link #getDescriptionId()}.
     */
    public String getDescriptionId(ItemStack stack) {
        switch (key) {
            case "minecraft:compass" -> {
                if (stack.furnaceComponent(org.domi.furnace.api.item.ItemComponent.raw("minecraft:lodestone_tracker")) != null) return "item.minecraft.lodestone_compass";
            }
            case "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow" -> {
                return getDescriptionId() + ".effect." + potionName(stack.furnaceComponent(org.domi.furnace.api.item.ItemComponent.raw("minecraft:potion_contents")));
            }
            case "minecraft:shield" -> {
                String color = stack.furnaceComponent(org.domi.furnace.api.item.ItemComponents.BASE_COLOR);
                if (color != null) return getDescriptionId() + "." + color;
            }
            default -> {}
        }
        return getDescriptionId();
    }

    /**
     * 바닐라 Potion.getName 의 이름: 물약에 기본 이름이 있으면 그것, 없으면 레지스트리 경로. 1.21.1 물약 46가지의 기본 이름은 경로에서 long_·strong_ 을
     * 뗀 것이다(Potions 등록 대조). 물약이 없으면 empty.
     */
    private static String potionName(String contentsSnbt) {
        if (contentsSnbt == null) return "empty";
        Object v = org.domi.furnace.api.nbt.Snbt.parse(contentsSnbt);
        Object id = v instanceof org.domi.furnace.api.nbt.NbtCompound c ? (c.contains("potion") ? c.getString("potion") : null) : v;
        if (!(id instanceof String s) || s.isEmpty()) return "empty";
        String path = s.indexOf(':') < 0 ? s : s.substring(s.indexOf(':') + 1);
        if (path.startsWith("long_")) return path.substring(5);
        if (path.startsWith("strong_")) return path.substring(7);
        return path;
    }

    /** 이 종류의 기본 최대 쌓기 개수(컴포넌트가 없을 때). Bukkit Material 표를 쓴다 — 표에 없는 종류는 UnsupportedNmsCall. */
    public int getDefaultMaxStackSize() {
        Material m = Material.matchMaterial(key);
        if (m == null) throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Item#getDefaultMaxStackSize(" + key + ")", "item is not in the Bukkit Material table (data pack or plugin item)");
        return m.getMaxStackSize();
    }

    /** 바닐라와 같다: 등록 이름(minecraft:stone). */
    @Override
    public String toString() { return key; }

    /**
     * 바닐라 {@code Item#use(level, player, hand)}(T-212): 그 손의 스택을 서버가 바닐라 그대로 쓴다. 지금은 바닐라 생성자로 만든 월드에 없는 플레이어만
     * 되고, 활·쇠뇌·삼지창·낚싯대는 아직 UnsupportedNmsCall 이다({@link org.domi.furnace.compat.nms.NmsItemUse}).
     */
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                                                                       net.minecraft.world.InteractionHand hand) {
        return org.domi.furnace.compat.nms.NmsItemUse.use(level, player, hand);
    }
}
