// 추가 월드를 내리기 직전(Bukkit WorldUnloadEvent). 취소하면 내리지 않는다. 이때 월드 안에 플레이어는 없다.
package org.domi.furnace.api.event.world;

import org.domi.furnace.api.event.Cancellable;

public final class WorldUnloadEvent extends WorldEvent implements Cancellable {

    private final String name;
    private final boolean save;
    private boolean cancelled;

    public WorldUnloadEvent(String world, String name, boolean save) {
        super(world);
        this.name = name;
        this.save = save;
    }

    /** 월드 이름(폴더 이름). */
    public String name() { return name; }

    /** 내리기 전에 저장하는가. */
    public boolean save() { return save; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
