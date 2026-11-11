package org.bukkit.craftbukkit;

import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.Compat;

/**
 * 접속 중이 아닐 수 있는 플레이어(CraftBukkit CraftOfflinePlayer 와 같은 이름). UUID 와, 알면 이름을 든다.
 * 같음은 UUID 로 가른다(CraftBukkit 처럼 — 접속 중 Player 와도 UUID 가 같으면 같은 플레이어로 본다).
 */
public final class CraftOfflinePlayer implements OfflinePlayer {

    private final UUID id;
    /** 만들 때 알려진 이름(모르면 null — 그때는 서버 기록에서 찾는다). */
    private final String knownName;

    CraftOfflinePlayer(UUID id, String knownName) {
        this.id = id;
        this.knownName = knownName;
    }

    private org.domi.furnace.api.entity.OfflinePlayer furnace() { return Compat.furnace().offlinePlayer(id); }

    @Override
    public boolean isOnline() { return getPlayer() != null; }

    /** CraftBukkit 처럼: 접속 중이면 지금 이름, 아니면 만들 때 받은 이름, 그것도 없으면 서버 기록(usercache·저장 데이터)의 이름, 없으면 null. */
    @Override
    public String getName() {
        Player p = getPlayer();
        if (p != null) return p.getName();
        if (knownName != null) return knownName;
        return furnace().name();
    }

    @Override
    public UUID getUniqueId() { return id; }

    @Override
    public Player getPlayer() { return Compat.player(Compat.furnace().player(id)); }

    @Override
    public boolean isOp() { return furnace().isOp(); }

    /** CraftBukkit 처럼 값이 같으면 아무것도 안 한다. 이름을 모르는 플레이어를 OP 로 만들면 IllegalStateException(Furnace 규칙). */
    @Override
    public void setOp(boolean value) {
        if (value == isOp()) return;
        furnace().setOpLevel(value ? 4 : 0);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OfflinePlayer other && other.getUniqueId() != null && id.equals(other.getUniqueId());
    }

    @Override
    public int hashCode() { return 97 * 5 + id.hashCode(); }

    @Override
    public String toString() { return "CraftOfflinePlayer[UUID=" + id + "]"; }
}
