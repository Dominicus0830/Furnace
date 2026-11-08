package net.minecraft.core;

/**
 * 레지스트리 조회(Mojang 이름 shim, H7b — 이름표만). 1.21 의 직렬화 메서드({@code Component.Serializer.toJson(c, provider)} 등)가 인자로
 * 받으므로 모양만 있다. 조회 메서드(lookup·lookupOrThrow)는 아직 없다(없는 멤버 — 호출 위치에서 실패).
 */
public interface HolderLookup {

    /** 레지스트리 묶음 조회기. */
    interface Provider {}
}
