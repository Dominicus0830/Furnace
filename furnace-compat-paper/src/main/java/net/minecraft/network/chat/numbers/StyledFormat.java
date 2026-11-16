package net.minecraft.network.chat.numbers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** 숫자에 글자 모양을 입히는 형식(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같다(모양 필드는 바닐라처럼 package-private). */
public class StyledFormat implements NumberFormat {

    public static final StyledFormat NO_STYLE = new StyledFormat(Style.EMPTY);
    public static final StyledFormat SIDEBAR_DEFAULT = new StyledFormat(Style.EMPTY.withColor(ChatFormatting.RED));
    public static final StyledFormat PLAYER_LIST_DEFAULT = new StyledFormat(Style.EMPTY.withColor(ChatFormatting.YELLOW));

    final Style style;

    public StyledFormat(Style style) { this.style = style; }

    @Override
    public MutableComponent format(int number) { return Component.literal(Integer.toString(number)).withStyle(style); }
}
