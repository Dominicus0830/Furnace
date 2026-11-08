package org.bukkit.permissions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 권한 기본값 — Bukkit 과 같은 이름·별칭. */
public enum PermissionDefault {
    TRUE("true"),
    FALSE("false"),
    OP("op", "isop", "operator", "isoperator", "admin", "isadmin"),
    NOT_OP("!op", "notop", "!operator", "notoperator", "!admin", "notadmin");

    private static final Map<String, PermissionDefault> LOOKUP = new HashMap<>();

    static {
        for (PermissionDefault value : values()) {
            for (String name : value.names) LOOKUP.put(name, value);
        }
    }

    private final String[] names;

    PermissionDefault(String... names) { this.names = names; }

    /** OP 여부에 따른 값. */
    public boolean getValue(boolean op) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> op;
            case NOT_OP -> !op;
        };
    }

    /** 이름(대소문자·밑줄 무시)으로 찾는다. 모르면 null. */
    public static PermissionDefault getByName(String name) {
        return LOOKUP.get(name.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z!]", ""));
    }

    @Override
    public String toString() { return names[0]; }
}
