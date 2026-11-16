// 플레이어 정보 갱신(player_info_update). 탭 목록 항목과 플레이어 스킨·이름을 바꾼다.
// 서버에 접속하지 않은 가짜 항목(NPC, 가짜 탭 줄)을 만들 때 쓴다.
//
// actions 에 든 동작에 해당하는 entries 필드만 뜻이 있다. ADD_PLAYER 가 없으면 이미 알려진 uuid 를 갱신한다.
//
// 채팅 세션 초기화(INITIALIZE_CHAT)는 Entry.chatSession 으로 한다. null 은 세션 없음이다.
// 바닐라도 서명 채팅을 끈 플레이어에게는 빈 값을 보낸다.
package org.domi.furnace.api.packet.clientbound;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundPlayerInfoUpdatePacket(Set<Action> actions, List<Entry> entries) implements ClientboundPacket {

    /** 동작 종류. 순서는 와이어 비트 순서와 같다. */
    public enum Action { ADD_PLAYER, INITIALIZE_CHAT, UPDATE_GAME_MODE, UPDATE_LISTED, UPDATE_LATENCY, UPDATE_DISPLAY_NAME }

    /**
     * 항목 하나. 필드마다 어느 동작에서 뜻이 있는지는 다음과 같다.
     *
     * <ul>
     * <li>name, properties: ADD_PLAYER
     * <li>gameMode: UPDATE_GAME_MODE
     * <li>listed: UPDATE_LISTED
     * <li>latencyMs: UPDATE_LATENCY
     * <li>displayName: UPDATE_DISPLAY_NAME (null 은 표시 이름 없음)
     * <li>chatSession: INITIALIZE_CHAT (null 은 세션 없음)
     * </ul>
     */
    public record Entry(UUID uuid, String name, List<ProfileProperty> properties, GameMode gameMode, boolean listed, int latencyMs, Text displayName,
                        ChatSession chatSession) {
        public Entry {
            Objects.requireNonNull(uuid, "uuid");
            properties = properties == null ? List.of() : List.copyOf(properties);
            if (gameMode == null) gameMode = GameMode.SURVIVAL;
        }

        /** 채팅 세션이 없는 항목. INITIALIZE_CHAT 을 쓰지 않을 때 쓴다. */
        public Entry(UUID uuid, String name, List<ProfileProperty> properties, GameMode gameMode, boolean listed, int latencyMs, Text displayName) {
            this(uuid, name, properties, gameMode, listed, latencyMs, displayName, null);
        }
    }

    /**
     * 플레이어 채팅 세션(바닐라 RemoteChatSession.Data). 다음 값을 가진다.
     *
     * <ul>
     * <li>세션 번호
     * <li>공개 키 만료 시각
     * <li>공개 키(X.509 RSA, 512바이트까지)
     * <li>Mojang 이 붙인 키 서명(4096바이트까지)
     * </ul>
     *
     * <p>바이트 배열은 복사해 들고, 같은지는 내용으로 비교한다.
     * 키가 RSA 가 아니면 보낼 때 IllegalArgumentException 을 던진다. 바닐라 클라이언트가 읽다가 연결을 끊기 때문이다.
     */
    public record ChatSession(UUID sessionId, Instant expiresAt, byte[] publicKey, byte[] keySignature) {
        public ChatSession {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(expiresAt, "expiresAt");
            publicKey = Objects.requireNonNull(publicKey, "publicKey").clone();
            keySignature = Objects.requireNonNull(keySignature, "keySignature").clone();
            if (publicKey.length > 512) throw new IllegalArgumentException("the public key can be at most 512 bytes: " + publicKey.length);
            if (keySignature.length > 4096) throw new IllegalArgumentException("the key signature can be at most 4096 bytes: " + keySignature.length);
        }

        @Override
        public byte[] publicKey() { return publicKey.clone(); }

        @Override
        public byte[] keySignature() { return keySignature.clone(); }

        @Override
        public boolean equals(Object o) {
            return o instanceof ChatSession c && sessionId.equals(c.sessionId) && expiresAt.equals(c.expiresAt) && Arrays.equals(publicKey, c.publicKey)
                    && Arrays.equals(keySignature, c.keySignature);
        }

        @Override
        public int hashCode() { return Objects.hash(sessionId, expiresAt, Arrays.hashCode(publicKey), Arrays.hashCode(keySignature)); }

        @Override
        public String toString() {
            return "ChatSession[sessionId=" + sessionId + ", expiresAt=" + expiresAt + ", publicKey=" + publicKey.length + "B, keySignature=" + keySignature.length + "B]";
        }
    }

    public ClientboundPlayerInfoUpdatePacket {
        actions = actions.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(actions));
        entries = List.copyOf(entries);
        if (name(actions, Action.ADD_PLAYER)) for (Entry e : entries) {
            if (e.name() == null || e.name().length() > 16) throw new IllegalArgumentException("the profile name must be 1~16 characters: " + e.name());
        }
    }

    private static boolean name(Set<Action> a, Action x) { return a.contains(x); }

    /** 새 항목 하나를 모든 동작으로 올린다. 이름·스킨·게임 모드·목록 표시·지연·표시 이름을 함께 정한다. */
    public static ClientboundPlayerInfoUpdatePacket add(UUID uuid, String name, List<ProfileProperty> properties, GameMode mode, boolean listed, int latencyMs, Text displayName) {
        return new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_GAME_MODE, Action.UPDATE_LISTED, Action.UPDATE_LATENCY, Action.UPDATE_DISPLAY_NAME),
                List.of(new Entry(uuid, name, properties, mode, listed, latencyMs, displayName)));
    }

    public static ClientboundPlayerInfoUpdatePacket displayName(UUID uuid, Text displayName) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_DISPLAY_NAME), List.of(new Entry(uuid, null, null, null, false, 0, displayName)));
    }

    public static ClientboundPlayerInfoUpdatePacket latency(UUID uuid, int latencyMs) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LATENCY), List.of(new Entry(uuid, null, null, null, false, latencyMs, null)));
    }

    public static ClientboundPlayerInfoUpdatePacket gameMode(UUID uuid, GameMode mode) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_GAME_MODE), List.of(new Entry(uuid, null, null, mode, false, 0, null)));
    }

    public static ClientboundPlayerInfoUpdatePacket listed(UUID uuid, boolean listed) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LISTED), List.of(new Entry(uuid, null, null, null, listed, 0, null)));
    }

    @Override
    public PacketType<ClientboundPlayerInfoUpdatePacket> type() { return PacketTypes.Clientbound.PLAYER_INFO_UPDATE; }
}
