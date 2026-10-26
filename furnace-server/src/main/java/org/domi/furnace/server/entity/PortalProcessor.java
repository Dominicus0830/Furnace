// 바닐라 PortalProcessor: 엔티티가 머무는 포털과 머문 시간.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public final class PortalProcessor {
    private final Portal portal;
    private Pos entryPosition;
    private int portalTime;
    private boolean insidePortalThisTick;

    public PortalProcessor(Portal portal, Pos entryPosition) {
        this.portal = portal;
        this.entryPosition = entryPosition;
        this.insidePortalThisTick = true;
    }

    /** processPortalTeleportation: 이번 틱에 안 들었으면 4씩 줄고 false. 들었으면 이동 허용일 때 시간을 채웠는가(후위 증가). */
    public boolean processPortalTeleportation(ChunkLevel level, Entity entity, boolean canChangeDimensions) {
        if (!insidePortalThisTick) {
            portalTime = Math.max(portalTime - 4, 0);
            return false;
        }
        insidePortalThisTick = false;
        return canChangeDimensions && portalTime++ >= portal.getPortalTransitionTime(level, entity);
    }

    public DimensionTransition getPortalDestination(ChunkLevel level, Entity entity) { return portal.getPortalDestination(level, entity, entryPosition); }

    public boolean hasExpired() { return portalTime <= 0; }

    public Pos getEntryPosition() { return entryPosition; }

    public void updateEntryPosition(Pos pos) { entryPosition = pos; }

    public int getPortalTime() { return portalTime; }

    public boolean isInsidePortalThisTick() { return insidePortalThisTick; }

    public void setAsInsidePortalThisTick(boolean v) { insidePortalThisTick = v; }

    public boolean isSamePortal(Portal p) { return portal == p; }
}
