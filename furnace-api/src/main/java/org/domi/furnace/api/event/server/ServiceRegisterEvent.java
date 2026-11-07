// 서비스 제공자가 등록된 직후. 전역 스레드가 아닐 수 있다(등록을 부른 스레드에서 불린다).
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.service.ServiceRegistration;

public final class ServiceRegisterEvent extends Event {

    private final ServiceRegistration<?> registration;

    public ServiceRegisterEvent(ServiceRegistration<?> registration) { super(true); this.registration = registration; }

    public ServiceRegistration<?> registration() { return registration; }
}
