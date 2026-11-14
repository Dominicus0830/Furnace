package org.bukkit.block.sign;

import org.bukkit.DyeColor;

/** Paper 1.21.1 SignSide: 표지판 한 면의 글·빛나는 글·색. Adventure 판(lines()·line(int))은 아직 없다. */
public interface SignSide extends org.bukkit.material.Colorable {

    /** 네 줄(§ 서식 레거시 글). */
    String[] getLines();

    String getLine(int index) throws IndexOutOfBoundsException;

    void setLine(int index, String line) throws IndexOutOfBoundsException;

    boolean isGlowingText();

    void setGlowingText(boolean glowing);

    @Override
    DyeColor getColor();

    @Override
    void setColor(DyeColor color);
}
