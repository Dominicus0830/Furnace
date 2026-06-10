// 바닐라 net.minecraft.util.ToFloatFunction 최소 포팅: float 값 변환 함수 인터페이스
package org.domi.furnace.server.world.gen.df;

public interface ToFloatFunction<C> {
    float apply(C object);
    float minValue();
    float maxValue();
}
