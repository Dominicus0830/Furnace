// 바닐라 ChatType(1.21.1 기본 데이터팩) — 장식 번역 키·매개변수·스타일, 그리고 Bound(타입 + 보낸이 이름 + 대상 이름).
package org.domi.furnace.server.chat;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Text;
import org.domi.furnace.server.net.registry.RegistrySet;

public enum ChatType {
    CHAT("chat", "chat.type.text", Param.SENDER_CONTENT, false),
    SAY_COMMAND("say_command", "chat.type.announcement", Param.SENDER_CONTENT, false),
    MSG_COMMAND_INCOMING("msg_command_incoming", "commands.message.display.incoming", Param.SENDER_CONTENT, true),
    MSG_COMMAND_OUTGOING("msg_command_outgoing", "commands.message.display.outgoing", Param.TARGET_CONTENT, true),
    TEAM_MSG_COMMAND_INCOMING("team_msg_command_incoming", "chat.type.team.text", Param.TARGET_SENDER_CONTENT, false),
    TEAM_MSG_COMMAND_OUTGOING("team_msg_command_outgoing", "chat.type.team.sent", Param.TARGET_SENDER_CONTENT, false),
    EMOTE_COMMAND("emote_command", "chat.type.emote", Param.SENDER_CONTENT, false);

    enum Param { SENDER_CONTENT, TARGET_CONTENT, TARGET_SENDER_CONTENT }

    private static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    private final String id;
    private final String translationKey;
    private final Param params;
    private final boolean grayItalic;   // incomingDirectMessage·outgoingDirectMessage 장식 스타일

    ChatType(String id, String translationKey, Param params, boolean grayItalic) {
        this.id = id;
        this.translationKey = translationKey;
        this.params = params;
        this.grayItalic = grayItalic;
    }

    /** 레지스트리 동기화 순서상의 번호(클라가 RegistryData 로 받은 순서). */
    public int registryIndex() { return REGISTRIES.indexOf("minecraft:chat_type", "minecraft:" + id); }

    public Bound bind(Text name) { return new Bound(this, name, null); }

    public record Bound(ChatType type, Text name, Text targetName) {
        public Bound withTargetName(Text target) { return new Bound(type, name, target); }

        /** ChatTypeDecoration.decorate — 서버 로그·콘솔 표시용 완성 문장. */
        public Text decorate(Text content) {
            Text target = targetName != null ? targetName : Text.empty();
            Object[] args = switch (type.params) {
                case SENDER_CONTENT -> new Object[]{name, content};
                case TARGET_CONTENT -> new Object[]{target, content};
                case TARGET_SENDER_CONTENT -> new Object[]{target, name, content};
            };
            Text t = Text.translatable(type.translationKey, args);
            if (type.grayItalic) t.color("gray").italic(true);
            return t;
        }

        /** 네트워크 형식: holder(레지스트리 참조 = id+1), 이름, 대상(선택). */
        public void write(ByteBuf buf) {
            Types.writeVarInt(buf, type.registryIndex() + 1);
            new NbtWriter(buf).writeCompound(name.toNbt());
            buf.writeBoolean(targetName != null);
            if (targetName != null) new NbtWriter(buf).writeCompound(targetName.toNbt());
        }
    }
}
