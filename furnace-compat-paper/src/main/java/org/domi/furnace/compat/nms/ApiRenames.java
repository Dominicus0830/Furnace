package org.domi.furnace.compat.nms;

import java.util.Map;
import org.objectweb.asm.Type;

/**
 * Paper 1.21.1 이 옛 플러그인을 위해 클래스를 올릴 때 바꿔 주는 Bukkit 필드 이름(CraftBukkit {@code FieldRename}) 가운데 이 호환 계층에
 * 해당 열거형이 있는 것. 필드 읽기({@code GETSTATIC})는 새 이름으로, 그 열거형의 {@code valueOf(String)} 은 옛 이름도 받는 다리로 바꾼다.
 * 모든 api-version 에 같은 규칙이다(Paper 의 {@code forAllVersions}).
 */
public final class ApiRenames {

    static final String INTERNAL = Type.getInternalName(ApiRenames.class);
    static final String ITEM_FLAG = "org/bukkit/inventory/ItemFlag";

    /** 1.20.5 에서 이름이 바뀐 아이템 설명 숨김 표시. */
    private static final Map<String, String> ITEM_FLAG_RENAMES = Map.of("HIDE_POTION_EFFECTS", "HIDE_ADDITIONAL_TOOLTIP");

    private ApiRenames() {}

    /** owner 의 정적 필드 이름을 이 서버의 이름으로. 바꿀 것이 없으면 그대로. */
    static String field(String owner, String name) {
        if (owner.equals(ITEM_FLAG)) return ITEM_FLAG_RENAMES.getOrDefault(name, name);
        return name;
    }

    static boolean reroutesValueOf(String owner, String desc) {
        return owner.equals(ITEM_FLAG) && desc.equals("(Ljava/lang/String;)L" + ITEM_FLAG + ";");
    }

    static String valueOfName(String owner) { return "valueOfItemFlag"; }

    /** {@code ItemFlag.valueOf} 다리: 옛 이름도 받는다(Paper 의 {@code FieldRename.valueOf_ItemFlag}). */
    public static org.bukkit.inventory.ItemFlag valueOfItemFlag(String name) {
        return org.bukkit.inventory.ItemFlag.valueOf(name == null ? null : ITEM_FLAG_RENAMES.getOrDefault(name, name));
    }
}
