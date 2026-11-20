package net.minecraft.network.codec;

/** 값 쪽이 먼저 오는 인코더(Mojang 이름, 바닐라 1.21.1 모양) — 패킷·페이로드가 자기 write 메서드를 코덱으로 내놓을 때 쓴다. */
@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T value, O output);
}
