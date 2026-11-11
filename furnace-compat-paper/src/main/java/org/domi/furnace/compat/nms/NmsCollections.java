package org.domi.furnace.compat.nms;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** NMS shim 도우미: 바닐라 Guava ImmutableSet 처럼 넣은 순서를 지키는 바꿀 수 없는 집합(equals·hashCode 는 Set 규칙이라 같다). */
public final class NmsCollections {

    private NmsCollections() {}

    public static <T> Collection<T> orderedSet(List<T> values) { return Collections.unmodifiableSet(new LinkedHashSet<>(values)); }
}
