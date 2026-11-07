package org.bukkit.plugin;

/** 서비스 제공자 등록 한 건(Bukkit RegisteredServiceProvider). 우선순위가 높은 것이 앞서도록 정렬된다. */
public class RegisteredServiceProvider<T> implements Comparable<RegisteredServiceProvider<?>> {

    private final Class<T> service;
    private final Plugin plugin;
    private final T provider;
    private final ServicePriority priority;

    public RegisteredServiceProvider(Class<T> service, T provider, ServicePriority priority, Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
        this.provider = provider;
        this.priority = priority;
    }

    public Class<T> getService() { return service; }

    public Plugin getPlugin() { return plugin; }

    public T getProvider() { return provider; }

    public ServicePriority getPriority() { return priority; }

    @Override
    public int compareTo(RegisteredServiceProvider<?> other) {
        if (priority.ordinal() == other.getPriority().ordinal()) return 0;
        return priority.ordinal() < other.getPriority().ordinal() ? 1 : -1;
    }
}
