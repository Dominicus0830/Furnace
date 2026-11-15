package net.minecraft.network.protocol.game;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/**
 * 탭 목록 갱신 shim(Mojang 이름, T-181). 바닐라 1.21.1 과 같은 모양: 동작 집합(actions)과 항목 목록(entries), 항목의 프로필은 authlib GameProfile.
 * <p>안쪽 클래스 순서가 바닐라 jar 와 같아야 한다: {@code getClasses()} 가 {@code [Entry, Action]} — ProtocolLib 은 항목 클래스를
 * {@code getClasses()[0]}, 동작 enum 을 {@code getClasses()[1]} 로 찾는다. javac 는 InnerClasses 를 <b>상수 풀에서 처음 참조된 순서</b>로 적고
 * 참조되지 않은 멤버만 선언의 거꾸로 붙인다. 이 파일에서는 첫 생성자의 {@code Entry::new} 가 {@code Action} 보다 먼저 참조돼 {@code [Entry, Action]} 이 된다
 * — 선언 순서만으로 정해지지 않으니 참조 순서를 바꾸는 수정 뒤에는 시험 PlayerInfoShimTest 로 확인한다(PR #390 검토 R3).
 */
public class ClientboundPlayerInfoUpdatePacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final EnumSet<Action> actions;
    private final List<Entry> entries;

    /** 바닐라: 접속한 플레이어들의 항목을 이 동작으로. */
    public ClientboundPlayerInfoUpdatePacket(EnumSet<Action> actions, Collection<ServerPlayer> players) {
        this.actions = actions;
        this.entries = players.stream().map(Entry::new).toList();
    }

    /** 바닐라: 플레이어 한 명, 동작 하나. */
    public ClientboundPlayerInfoUpdatePacket(Action action, ServerPlayer player) {
        this.actions = EnumSet.of(action);
        this.entries = List.of(new Entry(player));
    }

    /** 항목을 그대로(ProtocolLib 이 빈 패킷을 만들 때도 쓴다 — 필드는 그 뒤 라이브러리가 채운다). */
    private ClientboundPlayerInfoUpdatePacket(EnumSet<Action> actions, List<Entry> entries) {
        this.actions = actions == null ? EnumSet.noneOf(Action.class) : actions;
        this.entries = entries == null ? List.of() : entries;
    }

    /** 이 모듈 전용: 동작·항목으로 만든다(Furnace 패킷에서 되돌릴 때). */
    public static ClientboundPlayerInfoUpdatePacket of(EnumSet<Action> actions, List<Entry> entries) {
        return new ClientboundPlayerInfoUpdatePacket(actions, entries);
    }

    /** 바닐라: 새로 들어온 플레이어들을 모든 동작으로(접속 때 서버가 보내는 것). */
    public static ClientboundPlayerInfoUpdatePacket createPlayerInitializing(Collection<ServerPlayer> players) {
        return new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.ADD_PLAYER, Action.INITIALIZE_CHAT, Action.UPDATE_GAME_MODE, Action.UPDATE_LISTED,
                Action.UPDATE_LATENCY, Action.UPDATE_DISPLAY_NAME), players);
    }

    public EnumSet<Action> actions() { return actions; }

    public List<Entry> entries() { return entries; }

    /** ADD_PLAYER 동작이 있을 때만 항목 전체(바닐라: 새로 보이는 플레이어 목록). */
    public List<Entry> newEntries() { return actions.contains(Action.ADD_PLAYER) ? entries : List.of(); }

    @Override
    public ClientboundPacket toFurnace() { return org.domi.furnace.compat.nms.PlayerInfoBridge.toFurnace(this); }

    @Override
    public String toString() { return "ClientboundPlayerInfoUpdatePacket{actions=" + actions + ", entries=" + entries + "}"; }

    // 안쪽 클래스 순서는 상수 풀 첫 참조 순서다(위 설명) — PlayerInfoShimTest 가 [Entry, Action] 을 지킨다

    /** 바닐라: 동작(와이어 비트 순서). */
    public enum Action {
        ADD_PLAYER, INITIALIZE_CHAT, UPDATE_GAME_MODE, UPDATE_LISTED, UPDATE_LATENCY, UPDATE_DISPLAY_NAME;
    }

    /**
     * 바닐라: 항목 하나. profile 은 ADD_PLAYER 때 이름·속성(스킨)을 싣는다(authlib 이 없으면 null), chatSession 은 INITIALIZE_CHAT(없으면 null).
     */
    public record Entry(UUID profileId, com.mojang.authlib.GameProfile profile, boolean listed, int latency, GameType gameMode, Component displayName,
                        RemoteChatSession.Data chatSession) {

        /** 바닐라: 접속한 플레이어의 지금 값. 채팅 세션은 Furnace 플레이어 API 로 읽지 못해 null 이다. */
        Entry(ServerPlayer player) {
            this(player.getUUID(), player.getGameProfile(), true, player.getBukkitEntity().furnaceHandle().ping(),
                    GameType.valueOf(player.getBukkitEntity().furnaceHandle().gameMode().name()), null, null);
        }
    }
}
