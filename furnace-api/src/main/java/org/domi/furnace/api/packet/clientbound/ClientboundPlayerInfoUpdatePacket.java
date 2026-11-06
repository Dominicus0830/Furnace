// 플레이어 정보 갱신(player_info_update) — 탭 목록 항목과 플레이어 스킨/이름. 서버에 접속하지 않은 가짜 항목(NPC·가짜 탭 줄)을 만들 때 쓴다.
// actions 에 든 동작만 entries 의 해당 필드가 뜻이 있다. ADD_PLAYER 가 없으면 이미 알려진 uuid 의 갱신이다.
// 채팅 세션 초기화(INITIALIZE_CHAT)는 읽지 못한다 — 그런 패킷은 관찰·취소는 되지만 이 래퍼로는 만들 수 없다.
package org.domi.furnace.api.packet.clientbound;

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

    /** 와이어 비트 순서와 같다. */
    public enum Action { ADD_PLAYER, INITIALIZE_CHAT, UPDATE_GAME_MODE, UPDATE_LISTED, UPDATE_LATENCY, UPDATE_DISPLAY_NAME }

    /** name·properties = ADD_PLAYER, gameMode, listed, latencyMs, displayName(null = 없음). */
    public record Entry(UUID uuid, String name, List<ProfileProperty> properties, GameMode gameMode, boolean listed, int latencyMs, Text displayName) {
        public Entry {
            Objects.requireNonNull(uuid, "uuid");
            properties = properties == null ? List.of() : List.copyOf(properties);
            if (gameMode == null) gameMode = GameMode.SURVIVAL;
        }
    }

    public ClientboundPlayerInfoUpdatePacket {
        actions = actions.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(actions));
        entries = List.copyOf(entries);
        if (name(actions, Action.ADD_PLAYER)) for (Entry e : entries) {
            if (e.name() == null || e.name().length() > 16) throw new IllegalArgumentException("프로필 이름은 1~16자: " + e.name());
        }
    }

    private static boolean name(Set<Action> a, Action x) { return a.contains(x); }

    /** 새 항목 하나를 모든 동작으로 올린다(이름·스킨·게임 모드·목록 표시·지연·표시 이름). */
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
