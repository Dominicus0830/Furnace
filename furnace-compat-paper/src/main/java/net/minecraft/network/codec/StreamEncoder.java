package net.minecraft.network.codec;

/** 쓰는 쪽 shim(Mojang 이름, N3 1단계). */
@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O output, T value);
}
