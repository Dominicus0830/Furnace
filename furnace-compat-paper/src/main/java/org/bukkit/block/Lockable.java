package org.bukkit.block;

/**
 * Paper 1.21.1 Lockable: 열쇠 이름(LockCode) 잠금. 잠긴 블록은 그 이름을 단 아이템을 주 손에 든 플레이어만 연다(관전자는 늘 연다).
 * 블록 상태에서 바꾼 열쇠는 {@link BlockState#update} 때 블록에 실린다.
 */
public interface Lockable {

    /** 열쇠가 있는가(빈 문자열이 아닌가). */
    boolean isLocked();

    /** 열쇠 이름(없으면 빈 문자열). */
    String getLock();

    /** 열쇠를 바꾼다. null·빈 문자열이면 잠금을 푼다. */
    void setLock(String key);
}
