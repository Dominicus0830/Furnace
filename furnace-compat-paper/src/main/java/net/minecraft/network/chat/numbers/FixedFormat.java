package net.minecraft.network.chat.numbers;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 숫자 대신 정해 둔 글을 보이는 형식(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같다(글 필드는 바닐라처럼 package-private). */
public class FixedFormat implements NumberFormat {

    final Component value;

    public FixedFormat(Component value) { this.value = value; }

    @Override
    public MutableComponent format(int number) { return value.copy(); }
}
