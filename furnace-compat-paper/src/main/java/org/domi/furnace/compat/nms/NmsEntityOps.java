package org.domi.furnace.compat.nms;

/** 엔티티 사슬 연결 명세(src/shimspec/entity.spec)의 위임 대상(T-232). 몸통은 핵심 층(서버 다리)에 있고 여기는 한 줄 잇기만 한다. */
public final class NmsEntityOps {

    private NmsEntityOps() {}

    /** 바닐라 {@code clearContent}(상자 배·상자/깔때기 수레): 전리품 표를 푼 뒤 칸을 비운다(서버 {@code ContainerEntity}). */
    public static void clearContent(net.minecraft.world.entity.Entity self) { self.furnaceClearContent(); }
}
