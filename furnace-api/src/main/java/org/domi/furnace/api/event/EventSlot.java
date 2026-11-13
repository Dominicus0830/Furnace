// 이벤트 붙임 칸 — 이벤트 객체 하나에 파생 값 하나를 붙이는 열쇠.
//
// 같은 이벤트를 받는 여러 처리기가 한 번만 만들어 나눠 쓸 값(무거운 변환 결과, 호환 계층의 Bukkit 이벤트 객체 등)을
// 전역 맵(WeakHashMap + 잠금) 대신 이벤트 객체 자체에 붙인다. 값은 이벤트 객체와 함께 사라진다.
//
// 열쇠 객체가 곧 접근 권한이다: 열쇠를 가진 코드만 그 칸을 읽고 쓴다(다른 플러그인·서버의 칸과 섞이지 않는다).
// 열쇠는 보통 static final 로 한 번 만든다 — 열쇠마다 이벤트에 (열쇠, 값) 두 칸이 붙는다.
package org.domi.furnace.api.event;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * 이벤트 객체에 값을 붙이는 열쇠.
 *
 * <p>스레드: 이벤트는 보통 한 스레드에서 처리되지만, 같은 이벤트 객체를 여러 스레드가 동시에 만져도 안전하다.
 * {@link #computeIfAbsent} 가 경합하면 만들기 함수는 여러 번 돌 수 있지만 칸에는 먼저 넣은 값 하나만 남고 모두 그 값을 돌려받는다.
 *
 * <p>수명: 값은 이벤트 객체가 살아 있는 동안 강하게 붙잡힌다. 이벤트를 오래 보관하면 붙은 값(과 그 값의 클래스로더)도 같이 남는다.
 *
 * @param <T> 붙일 값의 타입(null 은 붙일 수 없다)
 */
public final class EventSlot<T> {

    private final String name;

    /** @param name 디버깅용 이름 */
    public EventSlot(String name) { this.name = Objects.requireNonNull(name, "name"); }

    /** 이 칸에 붙은 값, 없으면 null. */
    @SuppressWarnings("unchecked")
    public T get(Event event) {
        Object[] a = (Object[]) Event.SLOTS.getVolatile(event);
        if (a != null) {
            for (int i = 0; i < a.length; i += 2) if (a[i] == this) return (T) a[i + 1];
        }
        return null;
    }

    /**
     * 붙은 값을 돌려주고, 없으면 {@code make} 로 만들어 붙인다. 이미 붙어 있으면 {@code make} 는 불리지 않는다.
     * 만들기 함수 안에서 같은 이벤트에 다른 칸을 붙여도 된다. 같은 칸을 다시 부르면 그 안쪽 값이 먼저 붙고 바깥 결과는 버려진다.
     *
     * @param make 값을 만드는 함수 — null 을 돌려주면 안 된다(없음을 붙이려면 따로 정한 표지 값을 쓴다)
     * @return 칸에 붙은 값(경합에서 진 쪽도 이긴 쪽 값을 받는다)
     */
    @SuppressWarnings("unchecked")
    public <E extends Event> T computeIfAbsent(E event, Function<? super E, ? extends T> make) {
        T have = get(event);
        if (have != null) return have;
        T made = Objects.requireNonNull(make.apply(event), "EventSlot 값은 null 일 수 없다");
        while (true) {
            Object[] a = (Object[]) Event.SLOTS.getVolatile(event);
            int n = 0;
            if (a != null) {
                for (int i = 0; i < a.length; i += 2) if (a[i] == this) return (T) a[i + 1];
                n = a.length;
            }
            Object[] next = a == null ? new Object[2] : Arrays.copyOf(a, n + 2);
            next[n] = this;
            next[n + 1] = made;
            if (Event.SLOTS.compareAndSet(event, a, next)) return made;
        }
    }

    @Override
    public String toString() { return "EventSlot[" + name + "]"; }
}
