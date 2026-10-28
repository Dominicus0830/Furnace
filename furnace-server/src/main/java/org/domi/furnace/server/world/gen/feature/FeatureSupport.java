// feature 트리 검사: 하위 어디에라도 미구현(NoopFeature)이 있으면 결과를 믿을 수 없다(AGENTS.md 규칙 3).
package org.domi.furnace.server.world.gen.feature;

public final class FeatureSupport {

    private FeatureSupport() {}

    /** feature 트리(필드로 이어진 ConfiguredFeature·PlacedFeature·목록)에 Noop 이 있는가. */
    public static boolean containsNoop(Object o, java.util.Set<Object> seen) {
        if (o == null || !seen.add(o)) return false;
        if (o instanceof NoopFeature) return true;
        if (o instanceof Iterable<?> it) {
            for (Object e : it) if (containsNoop(e, seen)) return true;
            return false;
        }
        if (o instanceof Object[] arr) {
            for (Object e : arr) if (containsNoop(e, seen)) return true;
            return false;
        }
        if (o instanceof java.util.Map<?, ?> m) return containsNoop(m.values(), seen);
        if (!o.getClass().getName().startsWith("org.domi.furnace.server.world.gen.feature")) return false;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    if (containsNoop(f.get(o), seen)) return true;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // 읽지 못하는 필드는 건너뛴다
                }
            }
        }
        return false;
    }


    /** 새 방문 집합으로 검사. */
    public static boolean containsNoop(Object o) {
        return containsNoop(o, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }
}
