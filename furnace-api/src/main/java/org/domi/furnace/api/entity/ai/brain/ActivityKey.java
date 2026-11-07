// 두뇌 활동 이름표 "이름공간:이름"(예: minecraft:work, myplugin:guard). 판마다 바뀔 수 있는 내부 활동 객체 대신 이 문자열로 다룬다.
// minecraft 이름공간은 바닐라 활동(아래 상수는 1.21.1 의 활동 전부), 다른 이름공간은 플러그인이 행동을 붙여 쓰는 새 활동이다.
package org.domi.furnace.api.entity.ai.brain;

import java.util.Locale;
import java.util.regex.Pattern;

public record ActivityKey(String namespace, String name) {

    private static final Pattern PART = Pattern.compile("[a-z0-9_.\\-/]+");

    public ActivityKey {
        if (namespace == null || !PART.matcher(namespace).matches()) throw new IllegalArgumentException("이름공간이 잘못됐다: " + namespace);
        if (name == null || !PART.matcher(name).matches()) throw new IllegalArgumentException("이름이 잘못됐다: " + name);
    }

    public static ActivityKey of(String namespace, String name) { return new ActivityKey(namespace.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)); }

    public static ActivityKey minecraft(String name) { return of("minecraft", name); }

    /** "ns:name" 또는 "name"(minecraft 로 본다). */
    public static ActivityKey parse(String key) {
        int i = key.indexOf(':');
        return i < 0 ? minecraft(key) : of(key.substring(0, i), key.substring(i + 1));
    }

    public boolean isVanilla() { return namespace.equals("minecraft"); }

    public static final ActivityKey CORE = minecraft("core");
    public static final ActivityKey IDLE = minecraft("idle");
    public static final ActivityKey WORK = minecraft("work");
    public static final ActivityKey PLAY = minecraft("play");
    public static final ActivityKey REST = minecraft("rest");
    public static final ActivityKey MEET = minecraft("meet");
    public static final ActivityKey PANIC = minecraft("panic");
    public static final ActivityKey RAID = minecraft("raid");
    public static final ActivityKey PRE_RAID = minecraft("pre_raid");
    public static final ActivityKey HIDE = minecraft("hide");
    public static final ActivityKey FIGHT = minecraft("fight");
    public static final ActivityKey CELEBRATE = minecraft("celebrate");
    public static final ActivityKey ADMIRE_ITEM = minecraft("admire_item");
    public static final ActivityKey AVOID = minecraft("avoid");
    public static final ActivityKey RIDE = minecraft("ride");
    public static final ActivityKey PLAY_DEAD = minecraft("play_dead");
    public static final ActivityKey LONG_JUMP = minecraft("long_jump");
    public static final ActivityKey RAM = minecraft("ram");
    public static final ActivityKey TONGUE = minecraft("tongue");
    public static final ActivityKey SWIM = minecraft("swim");
    public static final ActivityKey LAY_SPAWN = minecraft("lay_spawn");
    public static final ActivityKey SNIFF = minecraft("sniff");
    public static final ActivityKey INVESTIGATE = minecraft("investigate");
    public static final ActivityKey ROAR = minecraft("roar");
    public static final ActivityKey EMERGE = minecraft("emerge");
    public static final ActivityKey DIG = minecraft("dig");

    @Override
    public String toString() { return namespace + ':' + name; }
}
