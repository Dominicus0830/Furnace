package org.domi.furnace.compat.nms;

/**
 * shim 생성기(T-229) 전용 생성자 표지. 생성된 shim 클래스는 {@code protected C(ShimCtor tag)} 만 가져 바닐라에 없는 기본 생성자({@code public C()})를
 * 얻지 않고, 생성된 하위 클래스가 이 생성자로 상위를 부른다. 플러그인 바이트코드는 이 서술자를 부르지 않는다(바닐라에 없는 Furnace 형식).
 * 생성 클래스를 상속하는 손 shim 도 이 생성자로 상위를 부른다.
 *
 * <p>종류 객체(T-231 — 블록·아이템을 바닐라 클래스로 만들기)는 표지에 만들 재료를 실어 보낸다({@link #carrying}). 사슬 맨 위의 손 shim
 * ({@code Block}·{@code Item})이 {@link #payload()} 로 받는다.
 */
public final class ShimCtor {
    /** 재료 없는 표지 값. */
    public static final ShimCtor TAG = new ShimCtor(null);

    private final Object payload;

    private ShimCtor(Object payload) { this.payload = payload; }

    /** 호환 모듈 전용 — 재료를 실은 표지(종류 객체 만들기, {@link NmsKinds}). */
    public static ShimCtor carrying(Object payload) { return new ShimCtor(java.util.Objects.requireNonNull(payload, "payload")); }

    /** 실은 재료(없으면 null). */
    public Object payload() { return payload; }
}
