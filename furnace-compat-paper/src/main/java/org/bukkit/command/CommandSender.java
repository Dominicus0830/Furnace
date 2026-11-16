package org.bukkit.command;

import org.bukkit.Server;
import org.bukkit.permissions.Permissible;

public interface CommandSender extends Permissible {

    void sendMessage(String message);

    void sendMessage(String... messages);

    Server getServer();

    String getName();

    /** Spigot 채팅 컴포넌트 보내기(Paper 1.21.1 CommandSender.Spigot). 기본 몸은 Paper 처럼 UnsupportedOperationException. */
    class Spigot {

        @Deprecated
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent component) { throw new UnsupportedOperationException("Not supported yet."); }

        @Deprecated
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent... components) { throw new UnsupportedOperationException("Not supported yet."); }

        @Deprecated
        public void sendMessage(java.util.UUID sender, net.md_5.bungee.api.chat.BaseComponent component) { throw new UnsupportedOperationException("Not supported yet."); }

        @Deprecated
        public void sendMessage(java.util.UUID sender, net.md_5.bungee.api.chat.BaseComponent... components) { throw new UnsupportedOperationException("Not supported yet."); }
    }

    /**
     * Paper 의 서버 쪽 보내는 이(콘솔·명령 블록 — CraftBukkit ServerCommandSender)처럼 컴포넌트를 옛 § 글로 바꿔 sendMessage(String) 로 보낸다.
     * 엔티티·플레이어는 덮어쓴다.
     */
    default Spigot spigot() { return org.domi.furnace.compat.paper.BungeeChat.legacySpigot(this); }
}
