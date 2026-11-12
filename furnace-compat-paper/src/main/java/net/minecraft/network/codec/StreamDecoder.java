package net.minecraft.network.codec;

/** 읽는 쪽 shim(Mojang 이름, N3 1단계). */
@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I input);
}
