// 플러그인이 켜진 직후.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class PluginEnableEvent extends Event {

    private final FurnacePlugin plugin;

    public PluginEnableEvent(FurnacePlugin plugin) { this.plugin = plugin; }

    public FurnacePlugin plugin() { return plugin; }
}
