package org.bukkit.material;

import org.bukkit.DyeColor;

/** Paper 1.21.1 Colorable. 구현은 Sign·SignSide·Bed 블록 상태뿐(Paper 와 같다). */
public interface Colorable {

    DyeColor getColor();

    void setColor(DyeColor color);
}
