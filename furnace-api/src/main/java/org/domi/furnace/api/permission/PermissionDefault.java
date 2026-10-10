// 권한 기본값(Bukkit PermissionDefault 와 같은 의미): 누구나·아무도·OP·OP 아닌 사람.
package org.domi.furnace.api.permission;

public enum PermissionDefault {
    TRUE, FALSE, OP, NOT_OP;

    public boolean value(boolean op) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> op;
            case NOT_OP -> !op;
        };
    }

    /** plugin.yml 표기(true, false, op, not op/!op/notop, isop/operator 등). 모르면 null. */
    public static PermissionDefault byName(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT).replace("_", " ").trim()) {
            case "true", "t", "yes", "y" -> TRUE;
            case "false", "f", "no", "n" -> FALSE;
            case "op", "isop", "operator", "isoperator", "admin", "isadmin" -> OP;
            case "not op", "!op", "notop", "!operator", "notoperator", "!admin", "notadmin" -> NOT_OP;
            default -> null;
        };
    }
}
