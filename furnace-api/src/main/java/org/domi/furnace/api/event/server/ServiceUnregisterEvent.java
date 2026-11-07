// 서비스 제공자가 풀린 직후(플러그인이 꺼져 풀린 경우 포함). 해제를 부른 스레드에서 불린다.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.service.ServiceRegistration;

public final class ServiceUnregisterEvent extends Event {

    private final ServiceRegistration<?> registration;

    public ServiceUnregisterEvent(ServiceRegistration<?> registration) { super(true); this.registration = registration; }

    public ServiceRegistration<?> registration() { return registration; }
}
