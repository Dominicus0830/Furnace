// 꺼진 플러그인이 작업을 예약하려 했다(Bukkit IllegalPluginAccessException 에 해당).
package org.domi.furnace.compat.sched;

public final class PluginDisabledException extends IllegalStateException {

    public PluginDisabledException(String message) { super(message); }
}
