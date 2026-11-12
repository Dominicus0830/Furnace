// 블록 광선 판정이 유체를 맞은 것으로 치는 방식(바닐라 ClipContext.Fluid 의 NONE·SOURCE_ONLY·ANY).
package org.domi.furnace.api.world;

public enum FluidPick {
    /** 유체는 지나간다. */
    NONE,
    /** 유체 원천(가득 찬 칸)만 맞는다. */
    SOURCE_ONLY,
    /** 모든 유체가 맞는다. */
    ANY
}
