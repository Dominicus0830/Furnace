package org.bukkit.block;

import org.bukkit.DyeColor;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

/**
 * Paper 1.21.1 Sign. 줄·빛나는 글·색 메서드(면 없는 판)는 앞면 것이다. Adventure 판(lines()·line(int))·getTargetSide·
 * getAllowedEditor·getInteractableSideFor 는 아직 없다.
 */
public interface Sign extends TileState, org.bukkit.material.Colorable {

    @Deprecated
    String[] getLines();

    @Deprecated
    String getLine(int index) throws IndexOutOfBoundsException;

    @Deprecated
    void setLine(int index, String line) throws IndexOutOfBoundsException;

    /** @deprecated Paper: !isWaxed. */
    @Deprecated
    boolean isEditable();

    @Deprecated
    void setEditable(boolean editable);

    boolean isWaxed();

    void setWaxed(boolean waxed);

    @Deprecated
    boolean isGlowingText();

    @Deprecated
    void setGlowingText(boolean glowing);

    @Deprecated
    @Override
    DyeColor getColor();

    @Deprecated
    @Override
    void setColor(DyeColor color);

    SignSide getSide(Side side);
}
