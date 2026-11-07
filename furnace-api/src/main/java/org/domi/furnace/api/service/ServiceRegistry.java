// 서비스 레지스트리 — 플러그인끼리 이름이 아니라 인터페이스로 만난다(Bukkit ServicesManager·Vault 식 연결).
// 권한·경제·플레이스홀더 같은 공용 인터페이스(PermissionService·EconomyService·PlaceholderService)가 이 위에 놓이고, 플러그인이 자기 인터페이스를 올려도 된다.
// 서비스 클래스는 보통 소비자와 제공자가 함께 보는 API 클래스로더에 있어야 한다. 제공 플러그인이 꺼지면 그 등록은 자동으로 풀린다.
// 어느 스레드에서 불러도 된다. 등록·해제는 ServiceRegisterEvent·ServiceUnregisterEvent 로 알린다.
package org.domi.furnace.api.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface ServiceRegistry {

    /** 같은 제공자 객체를 같은 서비스에 두 번 올리면 IllegalArgumentException. 꺼진 플러그인은 IllegalStateException. */
    <T> ServiceRegistration<T> register(Class<T> service, T provider, FurnacePlugin owner, ServicePriority priority);

    default <T> ServiceRegistration<T> register(Class<T> service, T provider, FurnacePlugin owner) {
        return register(service, provider, owner, ServicePriority.NORMAL);
    }

    /** 가장 우선인 제공자(같은 우선순위면 먼저 등록한 쪽). 없으면 null. */
    <T> T load(Class<T> service);

    <T> Optional<ServiceRegistration<T>> registration(Class<T> service);

    /** 우선순위 순(높은 쪽 먼저, 같으면 등록 순). */
    <T> List<ServiceRegistration<T>> registrations(Class<T> service);

    /** 그 플러그인이 올린 모든 등록. */
    List<ServiceRegistration<?>> registrations(FurnacePlugin owner);

    /** 하나라도 등록된 서비스 종류. */
    Set<Class<?>> services();

    /** 그 제공자 하나를 푼다. 없었으면 false. */
    boolean unregister(Class<?> service, Object provider);

    /** 그 플러그인의 등록을 모두 푼다(코어가 플러그인을 끌 때도 부른다). */
    void unregisterAll(FurnacePlugin owner);
}
