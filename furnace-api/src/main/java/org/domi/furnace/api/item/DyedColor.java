// minecraft:dyed_color — 염색한 가죽 장비 등의 색(0xRRGGBB).
package org.domi.furnace.api.item;

public record DyedColor(int rgb, boolean showInTooltip) {
    public DyedColor(int rgb) { this(rgb, true); }
}
