package net.minecraft.world.item;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ItemLike;
import org.bukkit.Material;

/**
 * 아이템 종류 shim(Mojang 이름, H6 최소 표면). 같은 레지스트리 키는 같은 객체라 바닐라처럼 {@code ==} 로 비교할 수 있다.
 * 레지스트리({@code BuiltInRegistries.ITEM}·{@code Items.*}, N0)의 값도 이 객체다.
 */
public class Item implements ItemLike {

    private static final ConcurrentHashMap<String, Item> BY_KEY = new ConcurrentHashMap<>();

    /** 공기(빈 스택의 종류). */
    static final Item AIR = byKey("minecraft:air");

    private final String key;

    private Item(String key) { this.key = key; }

    /** 호환 모듈 전용 — 레지스트리 키(minecraft:stone)의 종류. */
    public static Item byKey(String key) {
        Objects.requireNonNull(key, "key");
        String k = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        return BY_KEY.computeIfAbsent(k, Item::new);
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

    /** 바닐라 규칙: item.&lt;이름공간&gt;.&lt;경로(/ 는 .)&gt;. */
    public String getDescriptionId() {
        int c = key.indexOf(':');
        return "item." + key.substring(0, c) + "." + key.substring(c + 1).replace('/', '.');
    }

    /** 이 종류의 기본 최대 쌓기 개수(컴포넌트가 없을 때). Bukkit Material 표를 쓴다 — 표에 없는 종류는 UnsupportedNmsCall. */
    public int getDefaultMaxStackSize() {
        Material m = Material.matchMaterial(key);
        if (m == null) throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Item#getDefaultMaxStackSize(" + key + ")", "Bukkit Material 표에 없는 아이템이다(데이터 팩·플러그인 아이템)");
        return m.getMaxStackSize();
    }

    /** 바닐라와 같다: 등록 이름(minecraft:stone). */
    @Override
    public String toString() { return key; }
}
