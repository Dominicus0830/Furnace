// player-idle-timeout 판정(바닐라 ServerGamePacketListenerImpl.tick): 마지막 행동 뒤 timeout 분을 넘기면 자리 비움.
package org.domi.furnace.server.entity;

public final class PlayerIdle {
    private PlayerIdle() { }

    /** @param minutes 0 이하면 끔 */
    public static boolean timedOut(long nowMillis, long lastActionMillis, int minutes) {
        return minutes > 0 && nowMillis - lastActionMillis > minutes * 1000L * 60L;
    }
}
