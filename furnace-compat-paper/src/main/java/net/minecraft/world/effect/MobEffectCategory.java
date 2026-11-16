package net.minecraft.world.effect;

import net.minecraft.ChatFormatting;

/** 효과 분류(Mojang 이름 shim, easy-nms E4c-1b). 툴팁 색은 바닐라와 같다(이로움·중립 파랑, 해로움 빨강). */
public enum MobEffectCategory {
    BENEFICIAL(ChatFormatting.BLUE),
    HARMFUL(ChatFormatting.RED),
    NEUTRAL(ChatFormatting.BLUE);

    private final ChatFormatting tooltipFormatting;

    MobEffectCategory(ChatFormatting tooltipFormatting) { this.tooltipFormatting = tooltipFormatting; }

    public ChatFormatting getTooltipFormatting() { return tooltipFormatting; }
}
