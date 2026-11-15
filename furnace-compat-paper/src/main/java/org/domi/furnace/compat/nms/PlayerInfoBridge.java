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
 *   <li>Furnace → shim: ADD_PLAYER 항목은 이름·속성으로 프로필을 만들고, 그 밖은 바닐라 클라이언트 해독처럼 {@code GameProfile(uuid, "")}.
 *       authlib 이 없으면 되돌리지 않는다(UnsupportedNmsCall — ProtocolLib 다리는 처음 한 번 INFO 를 남기고 그대로 보낸다).</li>
 *   <li>shim → Furnace: ADD_PLAYER 항목에 프로필이 없으면 바닐라도 쓰다 실패하므로 IllegalArgumentException.</li>
 * </ul>
 */
public final class PlayerInfoBridge {

    private PlayerInfoBridge() {}

    /** 되돌리지 못하는 까닭(되면 null). */
    static String irreversibleReason() { return Profiles.available() ? null : Profiles.MISSING; }

    public static net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket fromFurnace(ClientboundPlayerInfoUpdatePacket p) {
        if (!Profiles.available()) throw new UnsupportedNmsCall("fromFurnace(" + p.type() + ")", Profiles.MISSING);
        EnumSet<net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action> actions =
                EnumSet.noneOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.class);
        for (ClientboundPlayerInfoUpdatePacket.Action a : p.actions()) {
            actions.add(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.valueOf(a.name()));
        }
        boolean add = p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
        List<net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry> out = new ArrayList<>();
        for (ClientboundPlayerInfoUpdatePacket.Entry e : p.entries()) {
            out.add(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(e.uuid(),
                    add ? Profiles.of(e.uuid(), e.name(), e.properties()) : Profiles.of(e.uuid(), "", List.of()), e.listed(), e.latencyMs(),
                    GameType.valueOf(e.gameMode().name()), e.displayName() == null ? null : Component.fromFurnace(e.displayName()), session(e.chatSession())));
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
            GameMode gm = mode ? GameMode.valueOf(need(e.gameMode(), eat + ".gameMode(UPDATE_GAME_MODE)").name())
                    : e.gameMode() == null ? null : GameMode.valueOf(e.gameMode().name());
            out.add(new ClientboundPlayerInfoUpdatePacket.Entry(need(e.profileId(), eat + ".profileId"), name, props, gm, e.listed(), e.latency(),
                    e.displayName() == null ? null : Component.furnace(e.displayName()), chat ? session(e.chatSession(), eat + ".chatSession") : null));
        }
        return new ClientboundPlayerInfoUpdatePacket(actions, out);
    }

    private static RemoteChatSession.Data session(ClientboundPlayerInfoUpdatePacket.ChatSession s) {
        if (s == null) return null;
        try {
            return new RemoteChatSession.Data(s.sessionId(), new ProfilePublicKey.Data(s.expiresAt(),
                    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(s.publicKey())), s.keySignature()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("player_info_update 채팅 세션 공개 키가 RSA 공개 키(X.509)가 아니다: " + e.getMessage(), e);
        }
    }

    private static ClientboundPlayerInfoUpdatePacket.ChatSession session(RemoteChatSession.Data d, String at) {
        if (d == null) return null;
        ProfilePublicKey.Data k = need(d.profilePublicKey(), at + ".profilePublicKey");
        return new ClientboundPlayerInfoUpdatePacket.ChatSession(need(d.sessionId(), at + ".sessionId"), need(k.expiresAt(), at + ".profilePublicKey.expiresAt"),
                need(k.key(), at + ".profilePublicKey.key").getEncoded(), need(k.keySignature(), at + ".profilePublicKey.keySignature"));
    }

    private static <T> T need(T v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " 가 null 이다(바닐라도 쓰다 실패한다)");
        return v;
    }
}
