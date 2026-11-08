package net.minecraft.world.item.component;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * 설명 줄(Mojang 이름 shim, 일부). 바닐라 레코드는 (lines, styledLines) 이지만 여기는 lines 만 있다 — styledLines 는 없는 멤버라
 * 호출 위치에서 실패한다. 바닐라처럼 256 줄까지.
 */
public record ItemLore(List<Component> lines) {
    public static final int MAX_LINES = 256;
    public static final ItemLore EMPTY = new ItemLore(List.of());

    public ItemLore {
        if (lines.size() > MAX_LINES) throw new IllegalArgumentException("Got " + lines.size() + " lines, but maximum is " + MAX_LINES);
        lines = List.copyOf(lines);
    }

    public ItemLore withLineAdded(Component line) {
        java.util.ArrayList<Component> l = new java.util.ArrayList<>(lines);
        l.add(line);
        return new ItemLore(l);
    }
}
