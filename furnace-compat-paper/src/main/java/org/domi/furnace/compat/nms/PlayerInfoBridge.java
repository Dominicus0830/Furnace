package org.domi.furnace.compat.nms;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoUpdatePacket;

/**
 * 탭 목록 갱신(player_info_update) shim ↔ Furnace 타입 레코드(T-181). 프로필은 authlib GameProfile({@link Profiles}).
 * <ul>
 *   <li>Furnace → shim: Paper 는 항목을 늘 {@code Entry(ServerPlayer)} 로 만들어 동작 밖 칸도 그 플레이어 값이다. 그래서 접속 중인 uuid 면
 *       그 플레이어의 같은 GameProfile 객체·listed=true·지연·모드로 채우고 동작에 든 칸만 패킷 값으로 덮는다(PR #390 검토 R1).
 *       접속자가 아니면 ADD_PLAYER 항목은 이름·속성으로 프로필을 만들고, 그 밖은 바닐라 클라이언트 해독처럼 {@code GameProfile(uuid, "")}.
 *       authlib 이 없으면 되돌리지 않는다(UnsupportedNmsCall — ProtocolLib 다리는 처음 한 번 INFO 를 남기고 그대로 보낸다).</li>
 *   <li>shim → Furnace: ADD_PLAYER 항목에 프로필이 없으면 바닐라도 쓰다 실패하므로 IllegalArgumentException. 동작 밖 칸은 기본값으로 맞춘다.</li>
 * </ul>
 */
public final class PlayerInfoBridge {

    private PlayerInfoBridge() {}

    /** 되돌리지 못하는 까닭(되면 null). */
    static String irreversibleReason() { return Profiles.available() ? null : Profiles.missing(); }

    public static net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket fromFurnace(ClientboundPlayerInfoUpdatePacket p) {
        if (!Profiles.available()) throw new UnsupportedNmsCall("fromFurnace(" + p.type() + ")", Profiles.missing());
        EnumSet<net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action> actions =
                EnumSet.noneOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.class);
        for (ClientboundPlayerInfoUpdatePacket.Action a : p.actions()) {
            actions.add(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.valueOf(a.name()));
        }
        var acts = p.actions();
        boolean add = acts.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        List<net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry> out = new ArrayList<>();
        for (ClientboundPlayerInfoUpdatePacket.Entry e : p.entries()) {
            // Paper 는 항목을 늘 Entry(ServerPlayer)로 만든다: 동작 밖 칸도 그 플레이어의 지금 값이다(이름·listed=true·지연·모드). 접속 중이면 같게 채운다.
            var base = online(e.uuid());
            com.mojang.authlib.GameProfile profile;
            if (base != null && (!add || sameProfile(base.profile(), e))) profile = base.profile();   // 같은 GameProfile 객체(Paper 처럼)
            else profile = add ? Profiles.of(e.uuid(), e.name(), e.properties()) : Profiles.of(e.uuid(), "", List.of());
            boolean listed = acts.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED) || base == null ? e.listed() : base.listed();
            int latency = acts.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY) || base == null ? e.latencyMs() : base.latency();
            GameType mode = acts.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE) || base == null
                    ? (e.gameMode() == null ? null : GameType.valueOf(e.gameMode().name())) : base.gameMode();
            Component display = acts.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) || base == null
                    ? (e.displayName() == null ? null : Component.fromFurnace(e.displayName())) : base.displayName();
            RemoteChatSession.Data chat = acts.contains(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT) || base == null
                    ? session(e.chatSession()) : base.chatSession();
            out.add(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(e.uuid(), profile, listed, latency, mode, display, chat));
        }
        return net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.of(actions, out);
    }

    public static ClientboundPlayerInfoUpdatePacket toFurnace(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket p) {
        String at = "player_info_update";
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.noneOf(ClientboundPlayerInfoUpdatePacket.Action.class);
        for (var a : need(p.actions(), at + ".actions")) actions.add(ClientboundPlayerInfoUpdatePacket.Action.valueOf(need(a, at + ".actions[]").name()));
        boolean add = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        boolean chat = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT);
        boolean mode = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE);
        List<ClientboundPlayerInfoUpdatePacket.Entry> out = new ArrayList<>();
        int i = 0;
        for (var e : need(p.entries(), at + ".entries")) {
            String eat = at + ".entries[" + i++ + "]";
            need(e, eat);
            String name = null;
            List<org.domi.furnace.api.entity.ProfileProperty> props = List.of();
            if (add) {   // 바닐라 쓰기: entry.profile().getName()·getProperties() — 없으면 NullPointerException
                Object profile = need(e.profile(), eat + ".profile(ADD_PLAYER)");
                var gp = (com.mojang.authlib.GameProfile) profile;
                name = gp.getName();
                props = Profiles.properties(gp);
            }
            // 동작 밖 칸은 쓰지 않으므로 기본값으로 맞춘다(fromFurnace 가 접속자 값으로 채운 칸·리스너가 동작 밖에서 고친 칸이 "바뀜"으로 보이지 않게)
            GameMode gm = mode ? GameMode.valueOf(need(e.gameMode(), eat + ".gameMode(UPDATE_GAME_MODE)").name()) : null;
            boolean listed = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED) && e.listed();
            int latency = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY) ? e.latency() : 0;
            var display = actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) && e.displayName() != null
                    ? Component.furnace(e.displayName()) : null;
            out.add(new ClientboundPlayerInfoUpdatePacket.Entry(need(e.profileId(), eat + ".profileId"), name, props, gm, listed, latency,
                    display, chat ? session(e.chatSession(), eat + ".chatSession") : null));
        }
        return new ClientboundPlayerInfoUpdatePacket(actions, out);
    }

    /** 접속 중인 플레이어의 Paper Entry(ServerPlayer) 값(없으면 null). */
    private static Base online(java.util.UUID id) {
        if (id == null) return null;
        var server = org.domi.furnace.api.Furnace.serverOrNull();
        org.domi.furnace.api.entity.Player fp = server == null ? null : server.player(id);
        if (fp == null) return null;
        if (!(org.domi.furnace.compat.paper.Compat.player(fp) instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp)) return null;
        // Entry(ServerPlayer) 와 같은 값: 같은 프로필 객체, listed=true, 지연, 모드. 탭 이름은 Furnace 에 아직 없어 null(Paper 기본도 null), 채팅 세션은 읽지 못해 null.
        return new Base(cp.getHandle().getGameProfile(), true, fp.ping(), GameType.valueOf(fp.gameMode().name()), null, null);
    }

    private record Base(com.mojang.authlib.GameProfile profile, boolean listed, int latency, GameType gameMode, Component displayName,
                        RemoteChatSession.Data chatSession) {}

    /** ADD_PLAYER 항목이 그 플레이어 프로필과 같은 이름·속성인가(플러그인이 이름·스킨을 바꿔 보냈으면 그 값으로 새로 만든다). */
    private static boolean sameProfile(com.mojang.authlib.GameProfile gp, ClientboundPlayerInfoUpdatePacket.Entry e) {
        return gp != null && gp.getName().equals(e.name() == null ? "" : e.name()) && Profiles.properties(gp).equals(e.properties());
    }

    private static RemoteChatSession.Data session(ClientboundPlayerInfoUpdatePacket.ChatSession s) {
        if (s == null) return null;
        try {
            return new RemoteChatSession.Data(s.sessionId(), new ProfilePublicKey.Data(s.expiresAt(),
                    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(s.publicKey())), s.keySignature()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("the player_info_update chat session public key is not an RSA public key (X.509): " + e.getMessage(), e);
        }
    }

    private static ClientboundPlayerInfoUpdatePacket.ChatSession session(RemoteChatSession.Data d, String at) {
        if (d == null) return null;
        ProfilePublicKey.Data k = need(d.profilePublicKey(), at + ".profilePublicKey");
        return new ClientboundPlayerInfoUpdatePacket.ChatSession(need(d.sessionId(), at + ".sessionId"), need(k.expiresAt(), at + ".profilePublicKey.expiresAt"),
                need(k.key(), at + ".profilePublicKey.key").getEncoded(), need(k.keySignature(), at + ".profilePublicKey.keySignature"));
    }

    private static <T> T need(T v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " is null (vanilla also fails to write it)");
        return v;
    }
}
