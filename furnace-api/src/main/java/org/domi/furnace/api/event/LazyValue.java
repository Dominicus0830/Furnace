// 게으른 이벤트 내용 — 이벤트가 생길 때 원본 참조만 잡고, API 형태로 바꾸는 일은 처음 읽을 때 한 번만 한다(O12 3.6 공용 도우미).
//
// 결과가 즉시 변환과 같으려면 잡은 원본이 바뀌지 않아야 한다. 그래서 이 도우미는 "불변 원본"만 받는다:
//   - 블록 상태 번호(int)·ItemStack(불변)·좌표 레코드처럼 값 자체가 불변인 것
//   - 바뀔 수 있는 묶음(인벤토리 칸 배열 등)은 원본을 그대로 넘기지 말고, 칸 참조를 복사한 배열(싸다)을 원본으로 넘긴다
//     — 칸 안의 값이 불변이면 그 사본도 불변 원본이다.
// 바뀔 수 있는 원본(엔티티 PDC 등)을 변경 번호로 지키는 방식은 넣지 않았다: 처음 읽을 때 번호가 달라졌음을 알아도 그때는 옛 값을 되살릴 수 없다
// (원본 쪽이 바뀌기 직전에 사본을 떠 두는 장치가 따로 있어야 한다 — docs/feature-parity/context-notes/2026-11-13-t032-event-bridge-slots.md).
package org.domi.furnace.api.event;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 처음 읽을 때 한 번 변환하고 그 결과를 고정하는 값.
 *
 * <p>여러 스레드가 동시에 처음 읽으면 변환 함수는 여러 번 돌 수 있지만, 모두 먼저 고정된 결과 하나를 받는다(같은 객체).
 * 변환 함수는 원본만 보고 같은 결과를 내야 하며(부수 효과 없음), null 을 돌려줘도 된다.
 *
 * @param <T> 변환 결과 타입
 */
public final class LazyValue<T> implements Supplier<T> {

    /** null 결과를 고정했다는 표지. 미변환은 필드 기본값 null 이다 — 이 객체가 경합으로 공개되어 생성자 쓰기가 늦게 보여도 "미변환"으로만 보인다. */
    private static final Object NULL = new Object();
    private static final VarHandle VALUE;

    static {
        try {
            VALUE = MethodHandles.lookup().findVarHandle(LazyValue.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Object source;
    /** ready 로 만든 값이면 null(변환 없음 — 값은 ready 에). */
    private final Function<Object, ? extends T> convert;
    /** ready 로 만든 값(null 결과는 NULL). final 이라 경합 공개에서도 보인다. of 로 만들면 null. */
    private final Object ready;
    @SuppressWarnings("unused")   // VALUE 로만 만진다. null = 미변환, NULL = null 결과
    private volatile Object value;

    @SuppressWarnings("unchecked")
    private LazyValue(Object source, Function<?, ? extends T> convert, Object ready) {
        this.source = source;
        this.convert = (Function<Object, ? extends T>) convert;
        this.ready = ready;
    }

    /**
     * 불변 원본과 변환 함수. 변환은 처음 {@link #get()} 할 때 한다.
     *
     * @param source 불변 원본(바뀌지 않는 값, 또는 다른 데서 고치지 않는 사본)
     */
    public static <S, T> LazyValue<T> of(S source, Function<? super S, ? extends T> convert) {
        return new LazyValue<>(source, Objects.requireNonNull(convert, "convert"), null);
    }

    /** 이미 만든 값(변환 없음). 공개 생성자처럼 값을 바로 받는 길과 게으른 길을 한 필드로 다룰 때 쓴다. */
    public static <T> LazyValue<T> ready(T value) {
        return new LazyValue<>(null, null, value == null ? NULL : value);
    }

    /** 값. 처음 부르면 변환하고, 그 뒤로는 같은 객체를 돌려준다. */
    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        Object v = ready;
        if (v == null) {
            v = VALUE.getVolatile(this);
            if (v == null) {
                T made = convert.apply(source);
                Object fixed = made == null ? NULL : made;
                Object witness = VALUE.compareAndExchange(this, null, fixed);
                v = witness == null ? fixed : witness;
            }
        }
        return v == NULL ? null : (T) v;
    }

    /** 이미 변환했는가(시험·진단용). */
    public boolean isResolved() { return ready != null || VALUE.getVolatile(this) != null; }

    @Override
    public String toString() { return isResolved() ? "LazyValue[" + get() + "]" : "LazyValue[미변환]"; }
}
