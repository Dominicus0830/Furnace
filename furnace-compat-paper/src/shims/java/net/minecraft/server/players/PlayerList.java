package net.minecraft.server.players;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * 접속자 목록 shim(Mojang 이름, 바닐라 1.21.1 모양 — 플러그인이 쓰는 멤버만, 2번 묶음). 상태를 따로 들지 않고 부를 때마다 Furnace 의 접속자에서 만든다.
 * <p>
 * 바닐라·Paper 의 {@link #getPlayers()} 는 주 스레드에서만 바뀌는 안쪽 리스트 그 자체다. Furnace 에는 주 스레드가 없고 접속·퇴장이 다른 스레드에서
 * 일어나므로, 그 순간의 사본(바꿀 수 없는 리스트)을 준다 — 쥔 채 두면 나중 변화는 보이지 않고, 고치려 하면 UnsupportedOperationException.
 */
class PlayerList {

    protected PlayerList() {}

    public MinecraftServer getServer() { return MinecraftServer.getServer(); }

    /** 지금 접속한 플레이어들(접속 순서와 무관한 서버 순서)의 사본. */
    public List<ServerPlayer> getPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (org.bukkit.entity.Player p : CraftServer.INSTANCE.getOnlinePlayers()) out.add(((CraftPlayer) p).getHandle());
        return List.copyOf(out);
    }

    /** 접속한 플레이어만(바닐라 playersByUUID). */
    public ServerPlayer getPlayer(UUID id) {
        org.bukkit.entity.Player p = CraftServer.INSTANCE.getPlayer(id);
        return p != null && p.isOnline() ? ((CraftPlayer) p).getHandle() : null;
    }

    /** 이름은 대소문자를 가리지 않는다(Paper playersByName 은 소문자 열쇠). */
    public ServerPlayer getPlayerByName(String name) {
        org.bukkit.entity.Player p = CraftServer.INSTANCE.getPlayerExact(name);
        return p != null ? ((CraftPlayer) p).getHandle() : null;
    }

    public int getPlayerCount() { return CraftServer.INSTANCE.getOnlinePlayers().size(); }

    public int getMaxPlayers() { return CraftServer.INSTANCE.getMaxPlayers(); }

    /** 바닐라: 콘솔에 한 줄, 그리고 모든 접속자에게(overlay 면 액션바 자리). */
    public void broadcastSystemMessage(Component message, boolean overlay) { broadcastSystemMessage(message, p -> message, overlay); }

    /** 바닐라: 콘솔에는 원래 문장, 플레이어마다 함수가 준 문장(null 이면 그 플레이어는 건너뛴다). */
    public void broadcastSystemMessage(Component message, Function<ServerPlayer, Component> playerMessage, boolean overlay) {
        getServer().sendSystemMessage(message);
        for (ServerPlayer p : getPlayers()) {
            Component m = playerMessage.apply(p);
            if (m != null) p.sendSystemMessage(m, overlay);
        }
    }
}
