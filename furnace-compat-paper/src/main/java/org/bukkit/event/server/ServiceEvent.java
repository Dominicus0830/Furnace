package org.bukkit.event.server;

import org.bukkit.event.Event;
import org.bukkit.plugin.RegisteredServiceProvider;

/** 서비스 등록·해제 이벤트의 바탕. */
public abstract class ServiceEvent extends Event {

    private final RegisteredServiceProvider<?> provider;

    public ServiceEvent(RegisteredServiceProvider<?> provider) { this.provider = provider; }

    public RegisteredServiceProvider<?> getProvider() { return provider; }
}
