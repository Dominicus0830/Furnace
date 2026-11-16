// ItemMeta 의 종류(Paper 1.21.1 SerializableMeta 의 meta-type 한 값에 해당) — 어느 아이템이 어느 종류의 메타를 쓰는지, 그 종류가 맡는 컴포넌트가 무엇인지, 만드는 법.
// 종류별 값은 메타 안에 따로 두지 않고 컴포넌트 사본(CompatItemMeta#work) 위에서 읽고 쓴다 — 그래서 종류를 바꿔 옮기거나 되읽어도 값이 새지 않는다.
package org.domi.furnace.compat.paper;

import java.util.EnumSet;
import java.util.Set;
import org.domi.furnace.api.item.ItemStack;

public enum MetaKind {

    /** 종류별 데이터가 없는 메타(Paper CraftMetaItem). 모든 아이템에 붙일 수 있다. */
    UNSPECIFIC("UNSPECIFIC", Set.of()) {
        @Override
        CompatItemMeta create(ItemStack work, EnumSet<org.bukkit.inventory.ItemFlag> flags, ItemPdc pdc) { return new CompatItemMeta(work, flags, pdc); }

        @Override
        boolean appliesTo(String itemType) { return true; }
    },

    /** 가죽 방어구·가죽 말 갑옷·늑대 갑옷: 염색 색(Paper LEATHER_ARMOR·COLORABLE_ARMOR). */
    LEATHER_ARMOR("LEATHER_ARMOR", Set.of("minecraft:dyed_color")) {
        @Override
        CompatItemMeta create(ItemStack work, EnumSet<org.bukkit.inventory.ItemFlag> flags, ItemPdc pdc) { return new CompatMetaLeatherArmor(work, flags, pdc); }

        @Override
        boolean appliesTo(String itemType) {
            return switch (itemType) {
                case "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots",
                        "minecraft:leather_horse_armor", "minecraft:wolf_armor" -> true;
                default -> false;
            };
        }
    };

    private final String serialName;
    private final Set<String> handled;

    MetaKind(String serialName, Set<String> handled) {
        this.serialName = serialName;
        this.handled = handled;
    }

    /** 설정 파일의 {@code meta-type} 값. */
    public String serialName() { return serialName; }

    /** 이 종류가 전용 키로 다루는 컴포넌트(직렬화의 "unhandled" 에 넣지 않는다). */
    Set<String> handledComponents() { return handled; }

    abstract CompatItemMeta create(ItemStack work, EnumSet<org.bukkit.inventory.ItemFlag> flags, ItemPdc pdc);

    /** 이 종류의 메타를 붙일 수 있는 아이템 종류(UNSPECIFIC 은 따로 본다). */
    abstract boolean appliesTo(String itemType);

    /** 아이템 종류(예: minecraft:leather_helmet)가 쓰는 메타 종류. 데이터 팩·플러그인 아이템은 UNSPECIFIC. */
    public static MetaKind of(String itemType) {
        for (MetaKind k : values()) {
            if (k != UNSPECIFIC && k.appliesTo(itemType)) return k;
        }
        return UNSPECIFIC;
    }

    /** 설정 파일의 meta-type 이름 → 종류. 아직 지원하지 않는 종류(BOOK 등)와 모르는 이름은 null. */
    public static MetaKind bySerialName(String name) {
        for (MetaKind k : values()) {
            if (k.serialName.equals(name)) return k;
        }
        return null;
    }
}
