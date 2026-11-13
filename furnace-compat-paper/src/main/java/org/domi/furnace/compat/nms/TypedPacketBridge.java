package org.domi.furnace.compat.nms;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundAnimatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundBlockUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundClearTitlesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundDisconnectPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoRemovePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundRemoveEntitiesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundRotateHeadPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetActionBarTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityMotionPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetSubtitleTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTimePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitlesAnimationPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSoundPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundTeleportEntityPacket;
import org.domi.furnace.api.world.SoundCategory;

/**
 * Furnace 타입 레코드 패킷 ↔ shim(ProtocolLib 다리 2b-2). 플러그인용이 아니다.
 *
 * <p>손으로 쓴 shim({@link NmsPacket})은 이미 shim → Furnace 가 있어 반대(Furnace → shim)만 둔다. 스키마로 생성한 shim 중 타입 레코드 종류
 * (엔티티 생성·머리 회전·애니메이션·순간 이동·블록 바꾸기·소리)는 두 방향을 모두 여기서 한다 — 바닐라 와이어 값(1/256 바퀴 각도, 1/8000 속도,
 * 1/8 좌표)을 shim 필드에 그대로 담고, Furnace 쪽은 서버 코덱이 읽는 것과 같은 값으로 바꾼다.
 * 메타데이터·플레이어 정보·플러그인 메시지는 아직 shim 이 없다(2b-4) — {@link #supports} 가 false.
 */
public final class TypedPacketBridge {

    private TypedPacketBridge() {}

    /** Furnace → shim 이 있는 타입 레코드 종류. */
    private static final java.util.Set<PacketType<?>> SUPPORTED = java.util.Set.of(
            PacketTypes.Clientbound.SET_TITLE_TEXT, PacketTypes.Clientbound.SET_SUBTITLE_TEXT, PacketTypes.Clientbound.SET_ACTION_BAR_TEXT,
            PacketTypes.Clientbound.CLEAR_TITLES, PacketTypes.Clientbound.SET_TITLES_ANIMATION, PacketTypes.Clientbound.SYSTEM_CHAT,
            PacketTypes.Clientbound.REMOVE_ENTITIES, PacketTypes.Clientbound.SET_ENTITY_MOTION, PacketTypes.Clientbound.SET_TIME,
            PacketTypes.Clientbound.PLAYER_INFO_REMOVE, PacketTypes.Clientbound.DISCONNECT,
            PacketTypes.Clientbound.ADD_ENTITY, PacketTypes.Clientbound.ROTATE_HEAD, PacketTypes.Clientbound.ANIMATE,
            PacketTypes.Clientbound.TELEPORT_ENTITY, PacketTypes.Clientbound.BLOCK_UPDATE, PacketTypes.Clientbound.SOUND);

    public static boolean supports(PacketType<?> t) { return SUPPORTED.contains(t); }

    /** 스키마 키가 이 다리가 두 방향을 맡는 타입 레코드 종류인가(생성 shim → Furnace). */
    static boolean handlesSchema(String schemaKey) {
        return switch (schemaKey) {
            case "game/clientbound/add_entity", "game/clientbound/rotate_head", "game/clientbound/animate", "game/clientbound/teleport_entity",
                    "game/clientbound/block_update", "game/clientbound/sound" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ Furnace → shim

    /** 타입 레코드 → shim. 지원하지 않는 종류면 UnsupportedNmsCall. */
    public static net.minecraft.network.protocol.Packet<?> fromFurnace(ClientboundPacket p) {
        return switch (p) {
            case ClientboundSetTitleTextPacket t -> new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(component(t.text()));
            case ClientboundSetSubtitleTextPacket t -> new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(component(t.text()));
            case ClientboundSetActionBarTextPacket t -> new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(component(t.text()));
            case ClientboundClearTitlesPacket t -> new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(t.resetTimes());
            case ClientboundSetTitlesAnimationPacket t -> new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(t.fadeIn(), t.stay(), t.fadeOut());
            case ClientboundSystemChatPacket t -> new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(component(t.message()), t.actionBar());
            case ClientboundRemoveEntitiesPacket t -> new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(t.entityIds().stream().mapToInt(Integer::intValue).toArray());
            case ClientboundSetEntityMotionPacket t -> net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket.ofWire(t.entityId(),
                    velocity(t.velocityX()), velocity(t.velocityY()), velocity(t.velocityZ()));
            // 하루 시간은 이미 와이어 값(멈춤이면 음수) — shim 이 다시 뒤집지 않게 순환 켬으로 만든다
            case ClientboundSetTimePacket t -> new net.minecraft.network.protocol.game.ClientboundSetTimePacket(t.gameTime(), t.dayTime(), true);
            case ClientboundPlayerInfoRemovePacket t -> new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(t.uuids());
            case ClientboundDisconnectPacket t -> new net.minecraft.network.protocol.common.ClientboundDisconnectPacket(component(t.reason()));
            case ClientboundAddEntityPacket t -> make(ADD_ENTITY, t.entityId(), t.uuid(), net.minecraft.world.entity.EntityType.byString(t.entityType())
                            .orElseThrow(() -> new IllegalArgumentException("모르는 엔티티 종류 " + t.entityType())),
                    t.x(), t.y(), t.z(), velocity(t.velocityX()), velocity(t.velocityY()), velocity(t.velocityZ()),
                    angle(t.pitch()), angle(t.yaw()), angle(t.headYaw()), t.data());
            case ClientboundRotateHeadPacket t -> make(ROTATE_HEAD, t.entityId(), angle(t.headYaw()));
            case ClientboundAnimatePacket t -> make(ANIMATE, t.entityId(), switch (t.animation()) {
                case SWING_MAIN_HAND -> 0;
                case WAKE_UP -> 2;
                case SWING_OFF_HAND -> 3;
                case CRITICAL_HIT -> 4;
                case MAGIC_CRITICAL_HIT -> 5;
            });
            case ClientboundTeleportEntityPacket t -> make(TELEPORT_ENTITY, t.entityId(), t.x(), t.y(), t.z(), angle(t.yaw()), angle(t.pitch()), t.onGround());
            case ClientboundBlockUpdatePacket t -> new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
                    net.minecraft.core.BlockPos.fromFurnace(t.pos()), SchemaPacketReverse.blockState(t.block(), "block_update.block"));
            case ClientboundSoundPacket t -> make(SOUND, sound(t.sound(), t.fixedRange()), net.minecraft.sounds.SoundSource.valueOf(t.category().name()),
                    (int) (t.x() * 8.0), (int) (t.y() * 8.0), (int) (t.z() * 8.0), t.volume(), t.pitch(), t.seed());
            default -> throw new UnsupportedNmsCall("fromFurnace(" + p.type() + ")", "이 타입 레코드 종류는 아직 shim 으로 되돌리지 못한다(2b-4)");
        };
    }

    private static net.minecraft.network.chat.Component component(org.domi.furnace.api.text.Text t) {
        return net.minecraft.network.chat.Component.fromFurnace(t);
    }

    /**
     * Furnace 속도 → 와이어 정수(1/8000). 플러그인 패킷 코덱(Protocol767.velocityUnits)과 같은 식 — 바닐라 (int) 버림이되, 와이어에서 읽은
     * {@code s / 8000} 처럼 정수에서 부동소수 오차만큼만 모자란 값은 그 정수(버리면 16380 등 374개 값에서 s−1, 검토 R4).
     */
    static int velocity(double v) {
        double x = Math.clamp(v, -3.9, 3.9) * 8000.0;
        double r = Math.rint(x);
        return (int) (Math.abs(x - r) < 1e-6 ? r : x);
    }

    /** 서버 코덱의 writeAngle 과 같다(바닐라 Mth.floor(각 * 256 / 360) 의 아래 8비트). */
    private static byte angle(float degrees) { return (byte) (int) Math.floor(degrees * 256.0f / 360.0f); }

    private static Object sound(String name, Float fixedRange) {
        var loc = net.minecraft.resources.ResourceLocation.parse(name);
        if (fixedRange != null) return net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createFixedRangeEvent(loc, fixedRange));
        Optional<? extends net.minecraft.core.Holder<?>> ref = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getHolder(loc);
        if (ref.isPresent()) return ref.get();
        return net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(loc));
    }

    private static final Constructor<?> ADD_ENTITY = allFields(net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class, int.class, java.util.UUID.class,
            net.minecraft.world.entity.EntityType.class, double.class, double.class, double.class, int.class, int.class, int.class, byte.class, byte.class, byte.class,
            int.class);
    private static final Constructor<?> ROTATE_HEAD = allFields(net.minecraft.network.protocol.game.ClientboundRotateHeadPacket.class, int.class, byte.class);
    private static final Constructor<?> ANIMATE = allFields(net.minecraft.network.protocol.game.ClientboundAnimatePacket.class, int.class, int.class);
    private static final Constructor<?> TELEPORT_ENTITY = allFields(net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.class, int.class,
            double.class, double.class, double.class, byte.class, byte.class, boolean.class);
    private static final Constructor<?> SOUND = allFields(net.minecraft.network.protocol.game.ClientboundSoundPacket.class, net.minecraft.core.Holder.class,
            net.minecraft.sounds.SoundSource.class, int.class, int.class, int.class, float.class, float.class, long.class);

    private static Constructor<?> allFields(Class<?> c, Class<?>... types) {
        try {
            Constructor<?> k = c.getDeclaredConstructor(types);
            k.setAccessible(true);
            return k;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static net.minecraft.network.protocol.Packet<?> make(Constructor<?> c, Object... args) {
        try {
            return (net.minecraft.network.protocol.Packet<?>) c.newInstance(args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ 생성 shim → Furnace

    /** 스키마로 생성한 타입 레코드 종류 shim → 타입 레코드(서버 코덱이 그 와이어를 읽은 것과 같은 값). */
    static ClientboundPacket toFurnace(SchemaPacket s) {
        Object[] v = s.schemaValues();
        String at = s.schemaKey();
        return switch (s.schemaKey()) {
            case "game/clientbound/add_entity" -> new ClientboundAddEntityPacket(i(v[0]), (java.util.UUID) need(v[1], at + ".uuid"),
                    ((net.minecraft.world.entity.EntityType<?>) need(v[2], at + ".type")).furnaceKey().toString(), d(v[3]), d(v[4]), d(v[5]),
                    deg(v[9]), deg(v[10]), deg(v[11]), i(v[12]), vel(v[6]), vel(v[7]), vel(v[8]));
            case "game/clientbound/rotate_head" -> new ClientboundRotateHeadPacket(i(v[0]), deg(v[1]));
            case "game/clientbound/animate" -> new ClientboundAnimatePacket(i(v[0]), switch (i(v[1]) & 0xFF) {
                case 0 -> ClientboundAnimatePacket.Animation.SWING_MAIN_HAND;
                case 2 -> ClientboundAnimatePacket.Animation.WAKE_UP;
                case 3 -> ClientboundAnimatePacket.Animation.SWING_OFF_HAND;
                case 4 -> ClientboundAnimatePacket.Animation.CRITICAL_HIT;
                case 5 -> ClientboundAnimatePacket.Animation.MAGIC_CRITICAL_HIT;
                default -> throw new IllegalArgumentException(at + ".action " + v[1] + " 은 바닐라 클라이언트가 모르는 애니메이션이다(1 = 맞음은 1.21.1 에서 hurt_animation 패킷)");
            });
            case "game/clientbound/teleport_entity" -> new ClientboundTeleportEntityPacket(i(v[0]), d(v[1]), d(v[2]), d(v[3]), deg(v[4]), deg(v[5]), (Boolean) v[6]);
            case "game/clientbound/block_update" -> new ClientboundBlockUpdatePacket(((net.minecraft.core.BlockPos) need(v[0], at + ".pos")).furnace(),
                    org.domi.furnace.api.Furnace.server().blockTypes().byStateId(((net.minecraft.world.level.block.state.BlockState) need(v[1], at + ".blockState")).furnaceStateId())
                            .orElseThrow(() -> new IllegalArgumentException(at + ": 모르는 블록 상태")));
            case "game/clientbound/sound" -> soundToFurnace(v, at);
            default -> throw new IllegalStateException("타입 레코드 다리가 없는 스키마 키: " + at);
        };
    }

    private static ClientboundSoundPacket soundToFurnace(Object[] v, String at) {
        String name;
        Float range = null;
        Object h = need(v[0], at + ".sound");
        if (h instanceof net.minecraft.core.Holder<?> holder && holder.unwrapKey().isPresent()) {
            name = holder.unwrapKey().get().location().toString();
        } else if (h instanceof net.minecraft.core.Holder<?> holder && holder.value() instanceof net.minecraft.sounds.SoundEvent e) {
            name = e.getLocation().toString();
            range = SchemaPacketBridge.fixedRange(e).orElse(null);
        } else {
            throw new IllegalArgumentException(at + ".sound 은 SoundEvent Holder 여야 한다: " + h);
        }
        var src = (net.minecraft.sounds.SoundSource) need(v[1], at + ".source");
        return new ClientboundSoundPacket(name, range, SoundCategory.valueOf(src.name()), i(v[2]) / 8.0, i(v[3]) / 8.0, i(v[4]) / 8.0,
                (Float) v[5], (Float) v[6], (Long) v[7]);
    }

    private static int i(Object o) { return ((Number) o).intValue(); }

    private static double d(Object o) { return ((Number) o).doubleValue(); }

    /** 와이어 각도 byte → 서버 코덱이 읽는 도(byte * 360 / 256). */
    private static float deg(Object o) { return ((Number) o).byteValue() * 360.0f / 256.0f; }

    /** 와이어 속도(short) → 서버 코덱이 읽는 값. */
    private static double vel(Object o) { return (short) ((Number) o).intValue() / 8000.0; }

    private static Object need(Object v, String at) { return SchemaPacketReverse.need(v, at); }

    /** 시험·문서용: Furnace → shim 이 있는 타입 레코드 종류 목록. */
    static List<PacketType<?>> supported() { return List.copyOf(SUPPORTED); }
}
