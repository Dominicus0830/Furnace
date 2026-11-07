// 바닐라 Activity: 브레인의 활동 묶음 이름. 바닐라 활동은 아래 상수, 플러그인 활동("ns:name")은 byKey 가 처음 불릴 때 만들어 같은 객체를 돌려준다.
package org.domi.furnace.server.entity.ai.brain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Activity {
    private static final Map<String, Activity> BY_KEY = new ConcurrentHashMap<>();

    public static final Activity CORE = of("core"), IDLE = of("idle"), WORK = of("work"), PLAY = of("play"), REST = of("rest"), MEET = of("meet"), PANIC = of("panic"),
            RAID = of("raid"), PRE_RAID = of("pre_raid"), HIDE = of("hide"), FIGHT = of("fight"), CELEBRATE = of("celebrate"), ADMIRE_ITEM = of("admire_item"),
            AVOID = of("avoid"), RIDE = of("ride"), PLAY_DEAD = of("play_dead"), LONG_JUMP = of("long_jump"), RAM = of("ram"), TONGUE = of("tongue"), SWIM = of("swim"),
            LAY_SPAWN = of("lay_spawn"), SNIFF = of("sniff"), INVESTIGATE = of("investigate"), ROAR = of("roar"), EMERGE = of("emerge"), DIG = of("dig");

    private final String name;
    private final String key;

    private Activity(String name, String key) {
        this.name = name;
        this.key = key;
    }

    private static Activity of(String name) {
        Activity a = new Activity(name, "minecraft:" + name);
        BY_KEY.put(a.key, a);
        return a;
    }

    /** "minecraft:work" 같은 바닐라 활동, 또는 플러그인 활동(처음 보면 만든다). */
    public static Activity byKey(String key) {
        Activity a = BY_KEY.get(key);
        if (a != null) return a;
        if (key.startsWith("minecraft:")) return null;   // 바닐라 이름공간에는 새 활동을 만들지 않는다
        return BY_KEY.computeIfAbsent(key, k -> new Activity(k, k));
    }

    public String getName() { return name; }

    /** "이름공간:이름". */
    public String key() { return key; }

    @Override public String toString() { return name; }
}
