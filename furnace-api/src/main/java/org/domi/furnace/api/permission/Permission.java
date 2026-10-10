// 등록된 권한 노드 — 이름·설명·기본값·자식(부모를 가지면 자식 값도 정해진다, Bukkit 과 같다).
package org.domi.furnace.api.permission;

import java.util.Map;
import java.util.Objects;

/** @param children 자식 노드 → 부모가 참일 때 그 자식이 가질 값 */
public record Permission(String name, String description, PermissionDefault defaultValue, Map<String, Boolean> children) {

    /** 등록되지 않은 권한의 기본값(Bukkit Permission.DEFAULT_PERMISSION). */
    public static final PermissionDefault DEFAULT = PermissionDefault.OP;

    public Permission {
        Objects.requireNonNull(name, "name");
        name = name.toLowerCase(java.util.Locale.ROOT);
        if (description == null) description = "";
        if (defaultValue == null) defaultValue = DEFAULT;
        children = Map.copyOf(children == null ? Map.of() : children);
    }

    public Permission(String name, PermissionDefault defaultValue) {
        this(name, "", defaultValue, Map.of());
    }
}
