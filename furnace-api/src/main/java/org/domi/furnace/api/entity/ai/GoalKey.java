// 목표 이름표 "이름공간:이름"(예: minecraft:melee_attack, myplugin:guard). 같은 이름표로 찾고 뺀다.
// 바닐라 목표는 minecraft: 아래 목표 종류 이름(MeleeAttackGoal → melee_attack)이다. 판마다 바뀔 수 있는 내부 클래스 대신 이 문자열로 다룬다.
package org.domi.furnace.api.entity.ai;

import java.util.Locale;
import java.util.regex.Pattern;

public record GoalKey(String namespace, String name) {

    private static final Pattern PART = Pattern.compile("[a-z0-9_.\\-/]+");

    public GoalKey {
        if (namespace == null || !PART.matcher(namespace).matches()) throw new IllegalArgumentException("이름공간이 잘못됐다: " + namespace);
        if (name == null || !PART.matcher(name).matches()) throw new IllegalArgumentException("이름이 잘못됐다: " + name);
    }

    public static GoalKey of(String namespace, String name) { return new GoalKey(namespace.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)); }

    public static GoalKey minecraft(String name) { return of("minecraft", name); }

    /** "ns:name" 또는 "name"(minecraft 로 본다). */
    public static GoalKey parse(String key) {
        int i = key.indexOf(':');
        return i < 0 ? minecraft(key) : of(key.substring(0, i), key.substring(i + 1));
    }

    @Override
    public String toString() { return namespace + ':' + name; }
}
