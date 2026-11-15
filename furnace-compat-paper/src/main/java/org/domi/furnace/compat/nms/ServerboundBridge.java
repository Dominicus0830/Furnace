package org.domi.furnace.compat.nms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundChatPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundClientCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundClientInformationPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundInteractPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundMovePlayerPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundPlayerActionPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundPlayerCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSetCarriedItemPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSignUpdatePacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSwingPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundUseItemOnPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundUseItemPacket;
import org.domi.furnace.api.world.BlockFace;

/**
 * 클라이언트가 보낸 패킷(serverbound) ↔ 패킷 shim(ProtocolLib 다리 2b-3). 접속자가 보낸 패킷을 패킷 라이브러리의 받는 리스너(반치트·채팅 필터)에 보여 주고,
 * 리스너가 바꾼 값을 서버에 되돌린다. 플러그인이 {@code receiveClientPacket} 으로 넣은 shim 도 이것으로 Furnace 패킷이 된다. 플러그인용이 아니다.
 *
 * <ul>
 *   <li>Furnace 타입 레코드 12종(이동·상호작용·블록 캐기·블록에 쓰기·채팅·명령 등)은 여기서 직접 바꾼다. 값은 바닐라 와이어가 담는 그대로다 —
 *       블록에 쓰기의 칸 안 위치는 float(바닐라가 맞은 점 − 블록 좌표를 float 로 쓴다), 상호작용 위치도 float.</li>
 *   <li>그 밖(범용 GenericPacket 종류)은 스키마 다리({@link SchemaPacketBridge#toFurnaceReceived}·{@link SchemaPacketReverse}).</li>
 *   <li>플러그인 메시지(custom_payload)·인벤토리 클릭(container_click)은 아직 바꾸지 않는다 — 까닭을 밝혀 거절한다.</li>
 * </ul>
 * 왕복: {@code toFurnace(fromFurnace(p)).equals(p)}.
 */
public final class ServerboundBridge {

    private ServerboundBridge() {}

    private static final PacketSchema SCHEMA = PacketSchema.protocol767();

    /** 서버가 play 연결로 받는 단계. */
    private static final Set<String> PLAY_STATES = Set.of("game", "common", "cookie", "ping");


    // ------------------------------------------------------------------ 종류

    /** shim 클래스 → Furnace serverbound 종류(play 단계가 아니거나 없으면 null). 이동 패킷 넷은 MOVE_PLAYER 하나다. */
    public static PacketType<?> furnaceType(Class<?> shim) {
        if (shim == null) return null;
        PacketDef d = SCHEMA.packetByClass(shim.getName()).orElse(null);
        if (d == null || d.direction() != PacketDirection.SERVERBOUND || !PLAY_STATES.contains(d.state())) return null;
        if (d.wireName().startsWith("move_player_")) return PacketTypes.Serverbound.MOVE_PLAYER;
        return PacketTypes.byName(PacketDirection.SERVERBOUND, "minecraft:" + d.wireName());
    }

    /** 이 종류를 shim 으로 오갈 수 없는 까닭(되면 null). 종류만 본다 — 값에 따라 실패할 수는 있다. */
    public static String refusal(PacketType<?> t) {
        if (t.direction() != PacketDirection.SERVERBOUND) return "not a serverbound kind";
        if (t == PacketTypes.Serverbound.CUSTOM_PAYLOAD) {
            return "there is no shim yet for the plugin message body (CustomPacketPayload) - receive it with Bukkit registerIncomingPluginChannel or the Furnace packet API";
        }
        if (t.typed()) return TYPED.contains(t) ? null : "there is no shim bridge for this typed record kind yet";
        return SchemaPacketReverse.refusal(t);
    }

    public static boolean supports(PacketType<?> t) { return refusal(t) == null; }

    private static final Set<PacketType<?>> TYPED = Set.of(PacketTypes.Serverbound.CHAT_COMMAND, PacketTypes.Serverbound.CHAT,
            PacketTypes.Serverbound.CLIENT_COMMAND, PacketTypes.Serverbound.CLIENT_INFORMATION, PacketTypes.Serverbound.INTERACT,
            PacketTypes.Serverbound.MOVE_PLAYER, PacketTypes.Serverbound.PLAYER_ACTION, PacketTypes.Serverbound.PLAYER_COMMAND,
            PacketTypes.Serverbound.SET_CARRIED_ITEM, PacketTypes.Serverbound.SIGN_UPDATE, PacketTypes.Serverbound.SWING,
            PacketTypes.Serverbound.USE_ITEM_ON, PacketTypes.Serverbound.USE_ITEM);

    // ------------------------------------------------------------------ Furnace → shim

    /** Furnace serverbound 패킷 → shim. 못 바꾸면 까닭을 밝힌 {@link UnsupportedNmsCall}. */
    public static Packet<?> fromFurnace(ServerboundPacket p) {
        String why = refusal(p.type());
        if (why != null) throw new UnsupportedNmsCall("fromFurnace(" + p.type() + ")", why);
        return switch (p) {
            case GenericPacket g -> SchemaPacketReverse.fromFurnace(g);
            case ServerboundChatCommandPacket c -> new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(c.command());
            case ServerboundChatPacket c -> new net.minecraft.network.protocol.game.ServerboundChatPacket(c.message(), c.timestamp(), c.salt(),
                    c.signature() == null ? null : new net.minecraft.network.chat.MessageSignature(c.signature()),
                    new net.minecraft.network.chat.LastSeenMessages.Update(c.lastSeenOffset(), ChatValues.ackBits(c.lastSeenAcknowledged())));
            case ServerboundClientCommandPacket c -> new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.valueOf(c.action().name()));
            case ServerboundClientInformationPacket c -> new net.minecraft.network.protocol.common.ServerboundClientInformationPacket(
                    new net.minecraft.server.level.ClientInformation(c.locale(), c.viewDistance(),
                            net.minecraft.world.entity.player.ChatVisiblity.valueOf(c.chatVisibility().name()), c.chatColors(), c.skinParts(),
                            net.minecraft.world.entity.HumanoidArm.valueOf(c.mainArm().name()), c.textFiltering(), c.allowsListing()));
            case ServerboundInteractPacket c -> Interact.make(c);
            case ServerboundMovePlayerPacket m -> move(m);
            case ServerboundPlayerActionPacket a -> new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.valueOf(a.action().name()), BlockPos.fromFurnace(a.pos()),
                    Direction.valueOf(a.face().name()), a.sequence());
            case ServerboundPlayerCommandPacket c -> PlayerCommand.make(c);
            case ServerboundSetCarriedItemPacket c -> new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(c.slot());
            case ServerboundSignUpdatePacket c -> new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(BlockPos.fromFurnace(c.pos()), c.frontText(),
                    c.lines().get(0), c.lines().get(1), c.lines().get(2), c.lines().get(3));
            case ServerboundSwingPacket c -> new net.minecraft.network.protocol.game.ServerboundSwingPacket(hand(c.hand()));
            case ServerboundUseItemOnPacket c -> {
                BlockPos pos = BlockPos.fromFurnace(c.pos());
                // 바닐라 읽기와 같다: 맞은 점 = 블록 좌표 + (double) 칸 안 위치(float)
                Vec3 at = new Vec3((double) pos.getX() + (double) c.cursorX(), (double) pos.getY() + (double) c.cursorY(), (double) pos.getZ() + (double) c.cursorZ());
                yield new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(hand(c.hand()),
                        new BlockHitResult(at, Direction.valueOf(c.face().name()), pos, c.insideBlock()), c.sequence());
            }
            case ServerboundUseItemPacket c -> new net.minecraft.network.protocol.game.ServerboundUseItemPacket(hand(c.hand()), c.sequence(), c.yaw(), c.pitch());
            default -> throw new UnsupportedNmsCall("fromFurnace(" + p.type() + ")", "there is no shim bridge for this typed record kind yet");
        };
    }

    private static Packet<?> move(ServerboundMovePlayerPacket m) {
        if (m.hasPosition() && m.hasRotation()) {
            return new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(m.x(), m.y(), m.z(), m.yaw(), m.pitch(), m.onGround());
        }
        if (m.hasPosition()) return new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(m.x(), m.y(), m.z(), m.onGround());
        if (m.hasRotation()) return new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(m.yaw(), m.pitch(), m.onGround());
        return new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(m.onGround());
    }

    private static InteractionHand hand(Hand h) { return InteractionHand.valueOf(h.name()); }

    private static Hand hand(InteractionHand h, String at) { return Hand.valueOf(need(h, at).name()); }

    // ------------------------------------------------------------------ shim → Furnace

    /**
     * shim → Furnace serverbound 패킷. 못 바꾸는 종류면 까닭을 밝힌 {@link UnsupportedNmsCall}, 값이 바닐라 모양이 아니면(필수 값 null·범위 밖) IllegalArgumentException.
     */
    public static ServerboundPacket toFurnace(Packet<?> shim) {
        String call = "Connection#read(" + (shim == null ? "null" : shim.getClass().getName()) + ")";
        if (shim == null) throw new IllegalArgumentException("패킷이 null 이다");
        PacketType<?> t = furnaceType(shim.getClass());
        if (t == null) {
            throw new UnsupportedNmsCall(call, "not a packet shim this server receives on a play connection (clientbound, login stage, a class made by a plugin, etc.) - to inject it as received, use Furnace PacketService.receive");
        }
        String why = refusal(t);
        if (why != null) throw new UnsupportedNmsCall(call, why);
        if (!t.typed()) return SchemaPacketBridge.toFurnaceReceived((SchemaPacket) shim);
        return switch (shim) {
            case net.minecraft.network.protocol.game.ServerboundChatCommandPacket c -> new ServerboundChatCommandPacket(need(c.command(), "command"));
            case net.minecraft.network.protocol.game.ServerboundChatPacket c -> chat(c);
            case net.minecraft.network.protocol.game.ServerboundClientCommandPacket c ->
                    new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.valueOf(need(c.getAction(), "action").name()));
            case net.minecraft.network.protocol.common.ServerboundClientInformationPacket c -> clientInformation(need(c.information(), "information"));
            case net.minecraft.network.protocol.game.ServerboundInteractPacket c -> Interact.read(c);
            case net.minecraft.network.protocol.game.ServerboundMovePlayerPacket m ->
                    new ServerboundMovePlayerPacket(m.hasPosition(), m.getX(0), m.getY(0), m.getZ(0), m.hasRotation(), m.getYRot(0), m.getXRot(0), m.isOnGround());
            case net.minecraft.network.protocol.game.ServerboundPlayerActionPacket a -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.valueOf(need(a.getAction(), "action").name()), need(a.getPos(), "pos").furnace(),
                    BlockFace.valueOf(need(a.getDirection(), "direction").name()), a.getSequence());
            case net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket c -> PlayerCommand.read(c);
            case net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket c -> new ServerboundSetCarriedItemPacket(c.getSlot());
            case net.minecraft.network.protocol.game.ServerboundSignUpdatePacket c -> {
                String[] lines = need(c.getLines(), "lines");
                if (lines.length != 4) throw new IllegalArgumentException("표지판 줄은 4개여야 한다(바닐라도 4줄만 쓴다): " + lines.length);
                for (int i = 0; i < 4; i++) need(lines[i], "lines[" + i + "]");
                yield new ServerboundSignUpdatePacket(need(c.getPos(), "pos").furnace(), c.isFrontText(), List.of(lines));
            }
            case net.minecraft.network.protocol.game.ServerboundSwingPacket c -> new ServerboundSwingPacket(hand(c.getHand(), "hand"));
            case net.minecraft.network.protocol.game.ServerboundUseItemOnPacket c -> useItemOn(c);
            case net.minecraft.network.protocol.game.ServerboundUseItemPacket c ->
                    new ServerboundUseItemPacket(hand(c.getHand(), "hand"), c.getSequence(), c.getYRot(), c.getXRot());
            default -> throw new UnsupportedNmsCall(call, "there is no shim bridge for this packet kind yet");
        };
    }

    private static ServerboundChatPacket chat(net.minecraft.network.protocol.game.ServerboundChatPacket c) {
        var update = need(c.lastSeenMessages(), "lastSeenMessages");
        int ack = ChatValues.ackMask(update.acknowledged(), "lastSeenMessages.acknowledged");
        return new ServerboundChatPacket(need(c.message(), "message"), need(c.timeStamp(), "timeStamp"), c.salt(),
                c.signature() == null ? null : c.signature().bytes(), update.offset(), ack);
    }

    private static ServerboundClientInformationPacket clientInformation(net.minecraft.server.level.ClientInformation i) {
        return new ServerboundClientInformationPacket(need(i.language(), "language"), i.viewDistance(),
                ServerboundClientInformationPacket.ChatVisibility.valueOf(need(i.chatVisibility(), "chatVisibility").name()), i.chatColors(), i.modelCustomisation(),
                ServerboundClientInformationPacket.MainArm.valueOf(need(i.mainHand(), "mainHand").name()), i.textFilteringEnabled(), i.allowsListing());
    }

    private static ServerboundUseItemOnPacket useItemOn(net.minecraft.network.protocol.game.ServerboundUseItemOnPacket c) {
        BlockHitResult hit = need(c.getHitResult(), "blockHit");
        BlockPos pos = need(hit.getBlockPos(), "blockHit.blockPos");
        Vec3 at = need(hit.getLocation(), "blockHit.location");
        // 바닐라 쓰기와 같다: 칸 안 위치 = (float) (맞은 점 − 블록 좌표)
        return new ServerboundUseItemOnPacket(hand(c.getHand(), "hand"), pos.furnace(), BlockFace.valueOf(need(hit.getDirection(), "blockHit.direction").name()),
                (float) (at.x - (double) pos.getX()), (float) (at.y - (double) pos.getY()), (float) (at.z - (double) pos.getZ()), hit.isInside(), c.getSequence());
    }

    // ------------------------------------------------------------------ 바닐라에서도 private 인 모양(반사)

    /** 상호작용 패킷: 바닐라처럼 생성자·동작 클래스가 private 이다(패킷 라이브러리도 반사로 다룬다). */
    private static final class Interact {
        private static final Class<?> PACKET = net.minecraft.network.protocol.game.ServerboundInteractPacket.class;
        private static final Constructor<?> NEW = ctor(PACKET, int.class, nested("Action"), boolean.class);
        private static final Object ATTACK = staticValue(PACKET, "ATTACK_ACTION");
        private static final Constructor<?> INTERACT = ctor(nested("InteractionAction"), InteractionHand.class);
        private static final Constructor<?> INTERACT_AT = ctor(nested("InteractionAtLocationAction"), InteractionHand.class, Vec3.class);
        private static final Field ENTITY = field(PACKET, "entityId");
        private static final Field ACTION = field(PACKET, "action");
        private static final Method TYPE = method(nested("Action"), "getType");

        static Packet<?> make(ServerboundInteractPacket c) {
            try {
                Object action = switch (c.action()) {
                    case ATTACK -> ATTACK;
                    case INTERACT -> INTERACT.newInstance(hand(c.hand()));
                    case INTERACT_AT -> INTERACT_AT.newInstance(hand(c.hand()), new Vec3(c.targetX(), c.targetY(), c.targetZ()));
                };
                return (Packet<?>) NEW.newInstance(c.entityId(), action, c.sneaking());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static ServerboundInteractPacket read(net.minecraft.network.protocol.game.ServerboundInteractPacket p) {
            try {
                int id = ENTITY.getInt(p);
                Object action = need(ACTION.get(p), "action");
                String type = ((Enum<?>) need(TYPE.invoke(action), "action.type")).name();
                boolean sneaking = p.isUsingSecondaryAction();
                return switch (type) {
                    case "ATTACK" -> ServerboundInteractPacket.attack(id, sneaking);
                    case "INTERACT" -> ServerboundInteractPacket.interact(id, hand((InteractionHand) value(action, "hand"), "action.hand"), sneaking);
                    case "INTERACT_AT" -> {
                        Vec3 at = need((Vec3) value(action, "location"), "action.location");
                        // 바닐라 쓰기와 같다: 위치는 float 셋
                        yield new ServerboundInteractPacket(id, ServerboundInteractPacket.Action.INTERACT_AT, hand((InteractionHand) value(action, "hand"), "action.hand"),
                                (float) at.x, (float) at.y, (float) at.z, sneaking);
                    }
                    default -> throw new IllegalArgumentException("모르는 상호작용 동작 " + type);
                };
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Class<?> nested(String simple) {
            for (Class<?> c : PACKET.getDeclaredClasses()) if (c.getSimpleName().equals(simple)) return c;
            throw new IllegalStateException("ServerboundInteractPacket shim 에 " + simple + " 이 없다");
        }

        private static Object value(Object owner, String name) throws ReflectiveOperationException {
            Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(owner);
        }
    }

    /** 웅크리기·달리기 등: 엔티티 번호를 받는 생성자는 바닐라처럼 private 이다(공개 생성자는 Entity 를 받는다). */
    private static final class PlayerCommand {
        private static final Class<?> PACKET = net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.class;
        private static final Constructor<?> NEW = ctor(PACKET, int.class, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.class, int.class);
        private static final Field ID = field(PACKET, "id");

        static Packet<?> make(ServerboundPlayerCommandPacket c) {
            try {
                return (Packet<?>) NEW.newInstance(c.entityId(),
                        net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.valueOf(c.action().name()), c.data());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static ServerboundPlayerCommandPacket read(net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket p) {
            try {
                return new ServerboundPlayerCommandPacket(ID.getInt(p), ServerboundPlayerCommandPacket.Action.valueOf(need(p.getAction(), "action").name()), p.getData());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static Constructor<?> ctor(Class<?> c, Class<?>... types) {
        try {
            Constructor<?> k = c.getDeclaredConstructor(types);
            k.setAccessible(true);
            return k;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(c.getName() + " shim 생성자가 바닐라 모양이 아니다", e);
        }
    }

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(c.getName() + " shim 에 " + name + " 이 없다", e);
        }
    }

    private static Method method(Class<?> c, String name) {
        try {
            Method m = c.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(c.getName() + " shim 에 " + name + " 이 없다", e);
        }
    }

    private static Object staticValue(Class<?> c, String name) {
        try {
            return field(c, name).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T need(T v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " 이 null 이다");
        return v;
    }

    /** 시험용: 이 종류의 스키마 정의(serverbound play). */
    static Optional<PacketDef> def(PacketType<?> t) { return SchemaPacketReverse.def(t); }
}
