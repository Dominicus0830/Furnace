package org.bukkit.metadata;

import org.bukkit.plugin.Plugin;

/** 플러그인이 대상(엔티티·플레이어·월드·블록)에 붙인 메모리 속 값 하나. 저장되지 않는다. */
public interface MetadataValue {

    Object value();

    int asInt();

    float asFloat();

    double asDouble();

    long asLong();

    short asShort();

    byte asByte();

    boolean asBoolean();

    String asString();

    /** 값을 만든 플러그인(약한 참조 — 그 플러그인 객체가 사라졌으면 null). */
    Plugin getOwningPlugin();

    /** 다음에 읽을 때 값을 다시 계산하게 한다. */
    void invalidate();
}
