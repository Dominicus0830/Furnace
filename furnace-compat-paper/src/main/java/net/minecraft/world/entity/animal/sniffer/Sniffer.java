package net.minecraft.world.entity.animal.sniffer;

/** 스니퍼(Mojang 이름 shim): 메타데이터 값 종류 자리인 상태 enum 만. */
public final class Sniffer {
    private Sniffer() {}

    public enum State { IDLING, FEELING_HAPPY, SCENTING, SNIFFING, SEARCHING, DIGGING, RISING }
}
