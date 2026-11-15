// BungeeCord 채팅 컴포넌트(net.md_5.bungee.api.chat) 다리. 그 라이브러리는 쓰는 플러그인이 있을 때만 공유 라이브러리 로더에 붙으므로
// 컴포넌트 클래스를 만지는 코드는 이 클래스에만 둔다(spigot() 을 부른 적 없는 서버는 이 클래스를 올리지 않는다).
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket;
import org.domi.furnace.api.text.Text;

public final class BungeeChat {

    private BungeeChat() {}

    /** 컴포넌트 배열 → Furnace 글. Paper 처럼 ComponentSerializer 의 JSON 을 바닐라 JSON 해석기로 읽는다(여러 개면 빈 글 아래 extra). */
    public static Text toText(BaseComponent... components) {
        return net.minecraft.network.chat.Component.furnace(CraftChatMessage.fromJSON(ComponentSerializer.toString(components)));
    }

    /** CraftBukkit ServerCommandSender.spigot(): 옛 § 글로 바꿔 sendMessage(String). */
    public static CommandSender.Spigot legacySpigot(CommandSender to) {
        return new CommandSender.Spigot() {
            @Override
            public void sendMessage(BaseComponent component) { to.sendMessage(TextComponent.toLegacyText(component)); }

            @Override
            public void sendMessage(BaseComponent... components) { to.sendMessage(TextComponent.toLegacyText(components)); }

            @Override
            public void sendMessage(UUID sender, BaseComponent component) { sendMessage(component); }

            @Override
            public void sendMessage(UUID sender, BaseComponent... components) { sendMessage(components); }
        };
    }

    /**
     * CraftPlayer.spigot(): 시스템 채팅 패킷을 곧바로 보낸다(대화 중 가림·채팅 표시 설정을 거치지 않는다 — Paper 와 같다).
     * ACTION_BAR 만 핫바 위, SYSTEM·CHAT 은 채팅 창. sender 는 쓰지 않는다.
     */
    public static Player.Spigot playerSpigot(CraftPlayer p) {
        return new Player.Spigot() {
            @Override
            /** Paper: 소켓 상대 주소 — BungeeCord·Velocity 포워딩이면 프록시 주소(getAddress 는 포워딩된 플레이어 주소). */
            public java.net.InetSocketAddress getRawAddress() { return p.furnaceHandle().rawAddress(); }

            /** Paper: 접속 중이고 죽어 있을 때만 되살린다 — 살아 있거나 오프라인이면 아무 일도 없다. 되살리기 API 가 아직 없어 그때만 UnsupportedFeature. */
            @Override
            public void respawn() {
                if (!p.isOnline() || p.getHealth() > 0) return;
                throw new UnsupportedFeature("Player.Spigot#respawn", "플러그인이 죽은 플레이어를 되살리는 API 가 아직 없다");
            }

            /** Paper: 온라인 플레이어 중 이 플레이어가 못 보는 사람(숨기기 규칙 — Furnace EntityViews.canSee), 고칠 수 없는 집합. */
            @Override
            public Set<Player> getHiddenPlayers() {
                Set<Player> hidden = new java.util.HashSet<>();
                for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (other instanceof CraftPlayer cp && !p.furnaceHandle().canSee(cp.furnaceHandle())) hidden.add(other);
                }
                return java.util.Collections.unmodifiableSet(hidden);
            }

            @Override
            public void sendMessage(BaseComponent component) { sendMessage(new BaseComponent[] {component}); }

            @Override
            public void sendMessage(BaseComponent... components) { sendMessage(ChatMessageType.SYSTEM, components); }

            @Override
            public void sendMessage(UUID sender, BaseComponent component) { sendMessage(ChatMessageType.CHAT, sender, component); }

            @Override
            public void sendMessage(UUID sender, BaseComponent... components) { sendMessage(ChatMessageType.CHAT, sender, components); }

            @Override
            public void sendMessage(ChatMessageType position, BaseComponent component) { sendMessage(position, new BaseComponent[] {component}); }

            @Override
            public void sendMessage(ChatMessageType position, BaseComponent... components) { sendMessage(position, null, components); }

            @Override
            public void sendMessage(ChatMessageType position, UUID sender, BaseComponent component) { sendMessage(position, sender, new BaseComponent[] {component}); }

            @Override
            public void sendMessage(ChatMessageType position, UUID sender, BaseComponent... components) {
                if (!p.isOnline()) return;   // Paper: 접속이 없으면 조용히
                ((org.domi.furnace.api.entity.Player) p.furnaceHandle()).sendPacket(new ClientboundSystemChatPacket(toText(components), position == ChatMessageType.ACTION_BAR));
            }

            @Override
            public int getPing() { return p.getPing(); }
        };
    }
}
