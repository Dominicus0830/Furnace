// S→C 플레이어 정보 갱신(탭 목록). 바닐라 createPlayerInitializing과 같은 액션 구성으로 여러 명을 한 패킷에 묶는다.
// 엔티티 스폰 전 필수(1.20.2+).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerInfoUpdate(List<Entry> entries) implements Packet {

    public static final int ID = 0x3E;
    /** 바닐라 createPlayerInitializing: add_player | initialize_chat | update_game_mode | update_listed | update_latency | update_display_name. */
    private static final int ACTIONS = 0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20;
    private static final int INITIALIZE_CHAT = 0x02;
    /** LoginPlay가 알리는 서버 기본 게임모드(1 = creative)와 일치해야 한다. */
    public static final int DEFAULT_GAME_MODE = 1;

    public record Entry(UUID uuid, String name, List<org.domi.furnace.server.players.GameProfile.Property> properties,
                        org.domi.furnace.server.chat.RemoteChatSession.Data chatSession, int gameMode, int latencyMs) {
        public Entry(UUID uuid, String name, int gameMode, int latencyMs) {
            this(uuid, name, List.of(), null, gameMode, latencyMs);
        }
    }

    /** 채팅 세션 갱신만(바닐라 resetPlayerChatState 의 broadcastAll(INITIALIZE_CHAT)). */
    public static Packet initializeChat(UUID uuid, org.domi.furnace.server.chat.RemoteChatSession.Data session) {
        return new Packet() {
            @Override
            public int id() { return ID; }

            @Override
            public void write(ByteBuf buf) {
                buf.writeByte(INITIALIZE_CHAT);
                Types.writeVarInt(buf, 1);
                Types.writeUuid(buf, uuid);
                buf.writeBoolean(session != null);
                if (session != null) session.write(buf);
            }
        };
    }

    /** 게임 모드만 갱신(바닐라 broadcastAll(UPDATE_GAME_MODE)). */
    public static Packet updateGameMode(UUID uuid, int gameMode) {
        return new Packet() {
            @Override
            public int id() { return ID; }

            @Override
            public void write(ByteBuf buf) {
                buf.writeByte(0x04);
                Types.writeVarInt(buf, 1);
                Types.writeUuid(buf, uuid);
                Types.writeVarInt(buf, gameMode);
            }
        };
    }

    public PlayerInfoUpdate(UUID uuid, String name) {
        this(List.of(new Entry(uuid, name, DEFAULT_GAME_MODE, 0)));
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(ACTIONS);
        Types.writeVarInt(buf, entries.size());
        for (Entry e : entries) {
            Types.writeUuid(buf, e.uuid());
            Types.writeString(buf, e.name());   // add_player: name
            Types.writeVarInt(buf, e.properties().size());   // add_player: 프로필 속성(textures 등)
            for (var p : e.properties()) {
                Types.writeString(buf, p.name());
                Types.writeString(buf, p.value());
                buf.writeBoolean(p.signature() != null);
                if (p.signature() != null) Types.writeString(buf, p.signature());
            }
            buf.writeBoolean(e.chatSession() != null);           // initialize_chat
            if (e.chatSession() != null) e.chatSession().write(buf);
            Types.writeVarInt(buf, e.gameMode());
            buf.writeBoolean(true);             // update_listed
            Types.writeVarInt(buf, e.latencyMs());
            buf.writeBoolean(false);            // update_display_name: 없음
        }
    }
}
