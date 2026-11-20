package net.minecraft.util.valueproviders;

/** 바닐라 정수 공급자(Mojang 이름 shim, NMS shim 5c): 난수로 정수 하나를 고른다. 지금은 {@link UniformInt} 만 있다. */
public abstract class IntProvider {

    public abstract int sample(net.minecraft.util.RandomSource random);

    public abstract int getMinValue();

    public abstract int getMaxValue();
}
