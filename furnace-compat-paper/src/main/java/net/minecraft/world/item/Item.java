package net.minecraft.world.item;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ItemLike;
import org.bukkit.Material;

/**
 * 아이템 종류 shim(Mojang 이름, H6 최소 표면). 같은 레지스트리 키는 같은 객체라 바닐라처럼 {@code ==} 로 비교할 수 있다.
 * 레지스트리에서 얻기({@code BuiltInRegistries.ITEM}·{@code Items.*})는 H7 이 더한다 — 지금은 {@link ItemStack#getItem()} 으로만 얻는다.
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
