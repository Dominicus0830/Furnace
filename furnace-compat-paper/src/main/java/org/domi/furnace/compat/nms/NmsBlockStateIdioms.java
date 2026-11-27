package org.domi.furnace.compat.nms;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.domi.furnace.compat.nms.link.LinkIdiom;

/**
 * T-263 S4 블록 상태 관용구(설계 3.2 G1). 바닐라 블록 클래스에서 같은 모양으로 반복되는 짧은 몸통 — 받은 상태의 속성 하나를 읽거나 바꾸고
 * 다른 인자·받는 쪽은 쓰지 않는 것 — 을 한 번 구현한다. 블록 상태는 바뀌지 않는 값이라 검사는 pure.
 *
 * <p>연결 엔진은 [받는 쪽(인스턴스 멤버)] + 줄의 값 + 멤버 인자를 그대로 넘기므로, 멤버 인자 꼴마다 겹침을 둔다(같은 이름의 겹침 중 꼭 하나만 맞는다):
 * 인스턴스 (상태) · (상태, x, y) · (상태, x, y, z) · (x, y, 상태), 정적 (상태). 상태가 아닌 인자는 바닐라 몸통이 쓰지 않으므로 받기만 한다.
 * 속성 값 형식(정수·참거짓)과 반환 형식은 추론기가 바닐라 몸통의 형 변환 명령으로 맞춘 것만 잇는다. 관용구는 손 코드라 독립 검토를 받는다(결정 D2).
 */
public final class NmsBlockStateIdioms {

    private NmsBlockStateIdioms() {}

    // ── return state.getValue(P) (반환 형식으로의 형 변환·풀기는 연결 몸통이 바닐라와 같이 한다) ──

    @LinkIdiom(value = "block-state-get", check = "pure")
    public static Object get(Object self, Property<?> p, BlockState s) { return s.getValue(p); }

    @LinkIdiom(value = "block-state-get", check = "pure")
    public static Object get(Object self, Property<?> p, BlockState s, Object a, Object b) { return s.getValue(p); }

    @LinkIdiom(value = "block-state-get", check = "pure")
    public static Object get(Object self, Property<?> p, BlockState s, Object a, Object b, Object c) { return s.getValue(p); }

    @LinkIdiom(value = "block-state-get", check = "pure")
    public static Object get(Property<?> p, BlockState s) { return s.getValue(p); }

    // ── return state.getValue(P) ? a : b (참거짓 속성 → 정수) ──

    @LinkIdiom(value = "block-state-pick-int", check = "pure")
    public static int pick(Object self, Property<Boolean> p, int ifTrue, int ifFalse, BlockState s) { return s.getValue(p) ? ifTrue : ifFalse; }

    @LinkIdiom(value = "block-state-pick-int", check = "pure")
    public static int pick(Object self, Property<Boolean> p, int ifTrue, int ifFalse, BlockState s, Object a, Object b) { return s.getValue(p) ? ifTrue : ifFalse; }

    @LinkIdiom(value = "block-state-pick-int", check = "pure")
    public static int pick(Object self, Property<Boolean> p, int ifTrue, int ifFalse, BlockState s, Object a, Object b, Object c) {
        return s.getValue(p) ? ifTrue : ifFalse;
    }

    // ── return !state.getValue(P) ──

    @LinkIdiom(value = "block-state-not", check = "pure")
    public static boolean not(Object self, Property<Boolean> p, BlockState s) { return !s.getValue(p); }

    @LinkIdiom(value = "block-state-not", check = "pure")
    public static boolean not(Object self, Property<Boolean> p, BlockState s, Object a, Object b) { return !s.getValue(p); }

    @LinkIdiom(value = "block-state-not", check = "pure")
    public static boolean not(Object self, Property<Boolean> p, Object a, Object b, BlockState s) { return !s.getValue(p); }

    @LinkIdiom(value = "block-state-not", check = "pure")
    public static boolean not(Property<Boolean> p, BlockState s) { return !s.getValue(p); }

    // ── return state.getValue(P) <관계> n (정수 속성) ──

    @LinkIdiom(value = "block-state-int-lt", check = "pure")
    public static boolean lt(Object self, Property<Integer> p, int n, BlockState s) { return s.getValue(p) < n; }

    @LinkIdiom(value = "block-state-int-lt", check = "pure")
    public static boolean lt(Object self, Property<Integer> p, int n, Object a, Object b, BlockState s) { return s.getValue(p) < n; }

    @LinkIdiom(value = "block-state-int-lt", check = "pure")
    public static boolean lt(Property<Integer> p, int n, BlockState s) { return s.getValue(p) < n; }

    @LinkIdiom(value = "block-state-int-eq", check = "pure")
    public static boolean eq(Object self, Property<Integer> p, int n, BlockState s) { return s.getValue(p) == n; }

    @LinkIdiom(value = "block-state-int-eq", check = "pure")
    public static boolean eq(Property<Integer> p, int n, BlockState s) { return s.getValue(p) == n; }

    @LinkIdiom(value = "block-state-int-ne", check = "pure")
    public static boolean ne(Object self, Property<Integer> p, int n, Object a, Object b, BlockState s) { return s.getValue(p) != n; }

    // ── return state.setValue(P, …) · state.cycle(P) ──

    /** {@code state.setValue(P, 인자)} — 원시 인자는 연결 몸통이 바닐라와 같이 박싱해 넘긴다. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @LinkIdiom(value = "block-state-set", check = "pure")
    public static BlockState set(Object self, Property p, BlockState s, Object value) { return s.setValue(p, (Comparable) value); }

    @LinkIdiom(value = "block-state-set-int", check = "pure")
    public static BlockState setInt(Object self, Property<Integer> p, int value, BlockState s) { return s.setValue(p, value); }

    /** {@code state.cycle(P)} — 두 번째 인자(난수원)는 바닐라 몸통이 쓰지 않는다. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @LinkIdiom(value = "block-state-cycle", check = "pure")
    public static BlockState cycle(Object self, Property p, BlockState s, Object random) { return s.cycle(p); }
}
