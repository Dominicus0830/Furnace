// 플러그인이 꺼지기 직전.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class PluginDisableEvent extends Event {

    private final FurnacePlugin plugin;

    public PluginDisableEvent(FurnacePlugin plugin) { this.plugin = plugin; }

    public FurnacePlugin plugin() { return plugin; }
}
