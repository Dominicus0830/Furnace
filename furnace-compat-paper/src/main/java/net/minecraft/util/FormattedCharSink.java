package net.minecraft.util;

import net.minecraft.network.chat.Style;

/** 글자 하나씩 받는 소비자(Mojang 이름 shim): 위치·스타일·코드포인트, false 를 돌려주면 멈춘다. */
@FunctionalInterface
public interface FormattedCharSink {
    boolean accept(int position, Style style, int codePoint);
}
