package org.bukkit.entity;

/** Paper 1.21.1 Tameable — 길들이는 동물·말 무리. 지금은 isTamed 만(FAWE 가 엔티티 복사 때 본다). */
public interface Tameable extends Animals {

    /** 길들었나. */
    boolean isTamed();
}
