// 수명주기 훅 종류. R 은 훅이 받는 등록기 타입이다. 종류는 LifecycleEvents 의 상수로만 만든다.
// 이름은 마인크래프트 버전과 상관없이 유지된다(버전 중립) — 새 버전에서 없어지는 단계는 폐기 기간을 거친다.
package org.domi.furnace.api.plugin.lifecycle;

public final class LifecycleEventType<R> {

    private final String name;
    private final Class<R> registrarType;

    LifecycleEventType(String name, Class<R> registrarType) {
        this.name = name;
        this.registrarType = registrarType;
    }

    /** "furnace:datapack_discovery" 같은 이름. */
    public String name() { return name; }

    public Class<R> registrarType() { return registrarType; }

    @Override
    public String toString() { return name; }
}
