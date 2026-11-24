package net.minecraft.world.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 텔레포트 상대 좌표 표시(NMS shim 5e-3 — Axiom 이 {@code unpack} 으로 패킷의 상대 표시 비트를 푼다).
 * 상수(인자 포함)와 순서는 명세가 정한다. 비트 번호·{@code ALL}·{@code ROTATION}·풀기·묶기는 Paper 1.21.1 jar(javap)와 같다:
 * 비트는 순서 번호와 같고, {@code ALL}·{@code ROTATION} 은 바꿀 수 없는 집합, {@code unpack} 은 새 {@link EnumSet}.
 */
enum RelativeMovement {

    public static final Set<RelativeMovement> ALL = Set.of(values());
    public static final Set<RelativeMovement> ROTATION = Set.of(X_ROT, Y_ROT);

    private final int bit;

    RelativeMovement(int bit) { this.bit = bit; }

    private int getMask() { return 1 << bit; }

    private boolean isSet(int flags) { return (flags & getMask()) == getMask(); }

    public static Set<RelativeMovement> unpack(int flags) {
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        for (RelativeMovement m : values()) if (m.isSet(flags)) set.add(m);
        return set;
    }

    public static int pack(Set<RelativeMovement> set) {
        int flags = 0;
        for (RelativeMovement m : set) flags |= m.getMask();
        return flags;
    }
}
