// Furnace.services(): 인터페이스 → 제공자 목록(우선순위 순). 제공 플러그인이 꺼지면 PluginManagerImpl.disable 이 unregisterAll 을 부른다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.event.server.ServiceRegisterEvent;
import org.domi.furnace.api.event.server.ServiceUnregisterEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.service.ServicePriority;
import org.domi.furnace.api.service.ServiceRegistration;
import org.domi.furnace.api.service.ServiceRegistry;

final class ServiceRegistryImpl implements ServiceRegistry {

    private static final Comparator<ServiceRegistration<?>> ORDER = Comparator.comparing((ServiceRegistration<?> r) -> r.priority(), Comparator.reverseOrder());

    private final PluginHost host;
    /** 서비스 → 등록 순서 목록. 바꿀 때만 잠그고 읽기는 불변 사본을 본다. */
    private final Map<Class<?>, List<ServiceRegistration<?>>> byService = new ConcurrentHashMap<>();

    ServiceRegistryImpl(PluginHost host) { this.host = host; }

    @Override
    public <T> ServiceRegistration<T> register(Class<T> service, T provider, FurnacePlugin owner, ServicePriority priority) {
        java.util.Objects.requireNonNull(service, "service");
        java.util.Objects.requireNonNull(provider, "provider");
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(priority, "priority");
        if (!service.isInstance(provider)) throw new IllegalArgumentException(provider.getClass().getName() + " 는 " + service.getName() + " 가 아니다");
        ServiceRegistration<T> reg = new ServiceRegistration<>(service, provider, owner, priority);
        synchronized (this) {
            if (!owner.isEnabled()) throw new IllegalStateException("꺼진 플러그인은 서비스를 올릴 수 없다: " + owner.getClass().getName());
            List<ServiceRegistration<?>> list = new ArrayList<>(byService.getOrDefault(service, List.of()));
            for (ServiceRegistration<?> r : list) if (r.provider() == provider) throw new IllegalArgumentException("이미 올린 제공자: " + service.getName());
            list.add(reg);
            byService.put(service, List.copyOf(list));
        }
        host.eventsImpl().call(new ServiceRegisterEvent(reg));
        return reg;
    }

    @SuppressWarnings("unchecked")
    private <T> List<ServiceRegistration<T>> sorted(Class<T> service) {
        List<ServiceRegistration<?>> list = byService.get(service);
        if (list == null) return List.of();
        List<ServiceRegistration<T>> out = new ArrayList<>(list.size());
        for (ServiceRegistration<?> r : list) out.add((ServiceRegistration<T>) r);
        out.sort(ORDER);   // 안정 정렬 — 같은 우선순위는 등록 순
        return List.copyOf(out);
    }

    @Override
    public <T> T load(Class<T> service) {
        List<ServiceRegistration<T>> l = sorted(service);
        return l.isEmpty() ? null : l.get(0).provider();
    }

    @Override
    public <T> Optional<ServiceRegistration<T>> registration(Class<T> service) {
        List<ServiceRegistration<T>> l = sorted(service);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    @Override
    public <T> List<ServiceRegistration<T>> registrations(Class<T> service) { return sorted(service); }

    @Override
    public List<ServiceRegistration<?>> registrations(FurnacePlugin owner) {
        List<ServiceRegistration<?>> out = new ArrayList<>();
        for (List<ServiceRegistration<?>> l : byService.values()) for (ServiceRegistration<?> r : l) if (r.owner() == owner) out.add(r);
        return List.copyOf(out);
    }

    @Override
    public Set<Class<?>> services() { return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(byService.keySet())); }

    @Override
    public boolean unregister(Class<?> service, Object provider) {
        ServiceRegistration<?> gone = null;
        synchronized (this) {
            List<ServiceRegistration<?>> list = byService.get(service);
            if (list == null) return false;
            List<ServiceRegistration<?>> next = new ArrayList<>(list);
            for (ServiceRegistration<?> r : list) {
                if (r.provider() == provider) {
                    gone = r;
                    next.remove(r);
                    break;
                }
            }
            if (gone == null) return false;
            if (next.isEmpty()) byService.remove(service);
            else byService.put(service, List.copyOf(next));
        }
        host.eventsImpl().call(new ServiceUnregisterEvent(gone));
        return true;
    }

    @Override
    public void unregisterAll(FurnacePlugin owner) {
        List<ServiceRegistration<?>> gone = new ArrayList<>();
        synchronized (this) {
            for (Class<?> service : List.copyOf(byService.keySet())) {
                List<ServiceRegistration<?>> next = new ArrayList<>();
                for (ServiceRegistration<?> r : byService.get(service)) {
                    if (r.owner() == owner) gone.add(r);
                    else next.add(r);
                }
                if (next.isEmpty()) byService.remove(service);
                else byService.put(service, List.copyOf(next));
            }
        }
        for (ServiceRegistration<?> r : gone) host.eventsImpl().call(new ServiceUnregisterEvent(r));
    }
}
