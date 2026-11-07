package org.bukkit.plugin;

import java.util.Collection;
import java.util.List;

/** 플러그인끼리 서비스(경제·권한 같은 공용 기능)를 주고받는 곳(Bukkit ServicesManager). */
public interface ServicesManager {

    <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority);

    void unregisterAll(Plugin plugin);

    void unregister(Class<?> service, Object provider);

    void unregister(Object provider);

    <T> T load(Class<T> service);

    <T> RegisteredServiceProvider<T> getRegistration(Class<T> service);

    List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin);

    <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service);

    Collection<Class<?>> getKnownServices();

    boolean isProvidedFor(Class<?> service);
}
