package org.bukkit.plugin.messaging;

/** 플러그인 채널의 방향. */
public enum PluginChannelDirection {
    /** 클라이언트 → 서버. */
    INCOMING,
    /** 서버 → 클라이언트. */
    OUTGOING
}
