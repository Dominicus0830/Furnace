package org.bukkit.plugin;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** 등록된 처리기 하나. */
public class RegisteredListener {

    private final Listener listener;
    private final EventPriority priority;
    private final Plugin plugin;
    private final EventExecutor executor;
    private final boolean ignoreCancelled;

    public RegisteredListener(Listener listener, EventExecutor executor, EventPriority priority, Plugin plugin, boolean ignoreCancelled) {
        this.listener = listener;
        this.priority = priority;
        this.plugin = plugin;
        this.executor = executor;
        this.ignoreCancelled = ignoreCancelled;
    }

    public Listener getListener() { return listener; }

    public Plugin getPlugin() { return plugin; }

    public EventPriority getPriority() { return priority; }

    public boolean isIgnoringCancelled() { return ignoreCancelled; }

    public void callEvent(Event event) throws EventException {
        if (event instanceof Cancellable c && c.isCancelled() && ignoreCancelled) return;
        executor.execute(listener, event);
    }
}
