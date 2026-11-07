package org.bukkit.event.server;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;

/** 서비스 제공자 등록·해제를 알린다(Bukkit ServiceRegisterEvent). 플러그인 코드가 직접 알리는 이벤트라 Furnace 이벤트 버스와는 이어져 있지 않다. */
public class ServiceRegisterEvent extends ServiceEvent {

    private static final HandlerList handlers = new HandlerList();

    public ServiceRegisterEvent(RegisteredServiceProvider<?> serviceProvider) { super(serviceProvider); }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
