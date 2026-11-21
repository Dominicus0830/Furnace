package org.domi.furnace.compat.nms;

/**
 * shim 생성기(T-229) 전용 생성자 표지. 생성된 shim 클래스는 {@code protected C(ShimCtor tag)} 만 가져 바닐라에 없는 기본 생성자({@code public C()})를
 * 얻지 않고, 생성된 하위 클래스가 이 생성자로 상위를 부른다. 플러그인 바이트코드는 이 서술자를 부르지 않는다(바닐라에 없는 Furnace 형식).
 * 생성 클래스를 상속하는 손 shim 도 이 생성자로 상위를 부른다.
 */
public final class ShimCtor {
    /** 하나뿐인 표지 값. */
    public static final ShimCtor TAG = new ShimCtor();

    private ShimCtor() {}
}
