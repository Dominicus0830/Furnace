package org.bukkit.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

/**
 * 서비스 지도(Bukkit SimpleServicesManager). 같은 서비스는 우선순위가 높은 것이 앞서고(같은 우선순위는 Bukkit 과 같은 이진 탐색 자리),
 * 등록·해제 때 {@link ServiceRegisterEvent}·{@link ServiceUnregisterEvent} 가 불린다. 플러그인이 꺼지면 호환 어댑터가 {@link #unregisterAll} 를 부른다.
 */
public class SimpleServicesManager implements ServicesManager {

    private final Map<Class<?>, List<RegisteredServiceProvider<?>>> providers = new HashMap<>();

    @Override
    public <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority) {
        if (service == null || provider == null || plugin == null || priority == null) throw new IllegalArgumentException("register 인자가 null");
        RegisteredServiceProvider<T> registered;
        synchronized (providers) {
            List<RegisteredServiceProvider<?>> list = providers.computeIfAbsent(service, k -> new ArrayList<>());
            registered = new RegisteredServiceProvider<>(service, provider, priority, plugin);
            int position = Collections.binarySearch(list, registered);
            if (position < 0) list.add(-(position + 1), registered);
            else list.add(position, registered);
        }
        org.domi.furnace.compat.paper.CompatServices.ownedBy(plugin, this);
        Bukkit.getPluginManager().callEvent(new ServiceRegisterEvent(registered));
    }

    @Override
    public void unregisterAll(Plugin plugin) {
        List<RegisteredServiceProvider<?>> removed = new ArrayList<>();
        synchronized (providers) {
            Iterator<Map.Entry<Class<?>, List<RegisteredServiceProvider<?>>>> it = providers.entrySet().iterator();
            while (it.hasNext()) {
                List<RegisteredServiceProvider<?>> list = it.next().getValue();
                for (Iterator<RegisteredServiceProvider<?>> li = list.iterator(); li.hasNext(); ) {
                    RegisteredServiceProvider<?> r = li.next();
                    if (r.getPlugin() == plugin) {
                        li.remove();
                        removed.add(r);
                    }
                }
                if (list.isEmpty()) it.remove();
            }
        }
        fireUnregister(removed);
    }

    @Override
    public void unregister(Class<?> service, Object provider) {
        List<RegisteredServiceProvider<?>> removed = new ArrayList<>();
        synchronized (providers) {
            List<RegisteredServiceProvider<?>> list = providers.get(service);
            if (list == null) return;
            for (Iterator<RegisteredServiceProvider<?>> li = list.iterator(); li.hasNext(); ) {
                RegisteredServiceProvider<?> r = li.next();
                if (r.getProvider() == provider) {
                    li.remove();
                    removed.add(r);
                }
            }
            if (list.isEmpty()) providers.remove(service);
        }
        fireUnregister(removed);
    }

    @Override
    public void unregister(Object provider) {
        List<RegisteredServiceProvider<?>> removed = new ArrayList<>();
        synchronized (providers) {
            Iterator<Map.Entry<Class<?>, List<RegisteredServiceProvider<?>>>> it = providers.entrySet().iterator();
            while (it.hasNext()) {
                List<RegisteredServiceProvider<?>> list = it.next().getValue();
                for (Iterator<RegisteredServiceProvider<?>> li = list.iterator(); li.hasNext(); ) {
                    RegisteredServiceProvider<?> r = li.next();
                    if (r.getProvider() == provider) {
                        li.remove();
                        removed.add(r);
                    }
                }
                if (list.isEmpty()) it.remove();
            }
        }
        fireUnregister(removed);
    }

    private static void fireUnregister(List<RegisteredServiceProvider<?>> removed) {
        for (RegisteredServiceProvider<?> r : removed) Bukkit.getPluginManager().callEvent(new ServiceUnregisterEvent(r));
    }

    @Override
    public <T> T load(Class<T> service) {
        RegisteredServiceProvider<T> r = getRegistration(service);
        return r == null ? null : r.getProvider();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RegisteredServiceProvider<T> getRegistration(Class<T> service) {
        synchronized (providers) {
            List<RegisteredServiceProvider<?>> list = providers.get(service);
            if (list == null || list.isEmpty()) return null;
            return (RegisteredServiceProvider<T>) list.get(0);
        }
    }

    @Override
    public List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin) {
        List<RegisteredServiceProvider<?>> out = new ArrayList<>();
        synchronized (providers) {
            for (List<RegisteredServiceProvider<?>> list : providers.values()) {
                for (RegisteredServiceProvider<?> r : list) if (r.getPlugin() == plugin) out.add(r);
            }
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service) {
        synchronized (providers) {
            List<RegisteredServiceProvider<?>> list = providers.get(service);
            if (list == null) return Collections.emptyList();
            return Collections.unmodifiableList(new ArrayList<>((List) list));
        }
    }

    @Override
    public Collection<Class<?>> getKnownServices() {
        synchronized (providers) {
            return Collections.unmodifiableSet(new HashSet<>(providers.keySet()));
        }
    }

    @Override
    public boolean isProvidedFor(Class<?> service) {
        synchronized (providers) {
            return providers.containsKey(service);
        }
    }
}
