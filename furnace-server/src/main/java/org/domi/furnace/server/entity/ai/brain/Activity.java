// 바닐라 Activity: 브레인의 활동 묶음 이름.
package org.domi.furnace.server.entity.ai.brain;

public final class Activity {
    public static final Activity CORE = of("core"), IDLE = of("idle"), WORK = of("work"), PLAY = of("play"), REST = of("rest"), MEET = of("meet"), PANIC = of("panic"),
            RAID = of("raid"), PRE_RAID = of("pre_raid"), HIDE = of("hide"), FIGHT = of("fight"), CELEBRATE = of("celebrate"), ADMIRE_ITEM = of("admire_item"),
            AVOID = of("avoid"), RIDE = of("ride"), PLAY_DEAD = of("play_dead"), LONG_JUMP = of("long_jump"), RAM = of("ram"), TONGUE = of("tongue"), SWIM = of("swim"),
            LAY_SPAWN = of("lay_spawn"), SNIFF = of("sniff"), INVESTIGATE = of("investigate"), ROAR = of("roar"), EMERGE = of("emerge"), DIG = of("dig");

    private final String name;

    private Activity(String name) { this.name = name; }

    private static Activity of(String name) { return new Activity(name); }

    public String getName() { return name; }

    @Override public String toString() { return name; }
}
