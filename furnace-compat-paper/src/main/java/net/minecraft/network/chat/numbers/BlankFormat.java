package net.minecraft.network.chat.numbers;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 숫자를 숨기는 형식(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같다. */
public class BlankFormat implements NumberFormat {

    public static final BlankFormat INSTANCE = new BlankFormat();

    public BlankFormat() {}

    @Override
    public MutableComponent format(int number) { return Component.empty(); }
}
