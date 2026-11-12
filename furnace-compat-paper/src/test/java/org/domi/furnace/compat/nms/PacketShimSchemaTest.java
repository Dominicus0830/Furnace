// 스키마 생성 패킷 shim(N3): 202종 모두 클래스가 있고, Mojang 이름·필드 순서·필드 타입이 스키마와 같고, 덫 색인이 "있음"으로 보며,
// 덧붙임(편의 생성자·변환 접근자·상수)이 바닐라처럼 값을 옮긴다. 바닐라 jar 대조는 tools/compat/packet-schema-check(로컬).
package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.Vec3;
import org.domi.furnace.api.packet.schema.FieldDef;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.StructDef;
import org.domi.furnace.api.packet.schema.TypeDecl;
import org.domi.furnace.api.packet.schema.TypeRef;
import org.junit.jupiter.api.Test;

class PacketShimSchemaTest {

    private static final PacketSchema S = PacketSchema.protocol767();

    /** 손으로 쓴 shim 중 스키마 필드와 일부러 다른 것(이유). */
    private static final Map<String, String> HAND_WRITTEN_DIFFERENCES = Map.of(
            "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket", "fastutil IntList 가 컴파일 경로에 없어 int[] 로 담는다");

    @Test
    void everySchemaPacketHasAShimOfTheSameShape() throws Exception {
        List<String> bad = new ArrayList<>();
        for (PacketDef p : S.packets()) {
            Class<?> c = Class.forName(p.binaryName());
            assertTrue(Packet.class.isAssignableFrom(c), p.binaryName());
            assertTrue(NmsSymbols.RUNTIME.hasClass(p.binaryName().replace('.', '/')), p.binaryName());
            if (HAND_WRITTEN_DIFFERENCES.containsKey(p.binaryName())) continue;
            switch (p.kind()) {
                case RECORD -> {
                    if (!c.isRecord()) { bad.add(p.className() + ": record 가 아니다"); continue; }
                    compare(bad, p.className(), p.fields(), p.javaPackage(),
                            Arrays.stream(c.getRecordComponents()).map(RecordComponent::getName).toList(),
                            Arrays.stream(c.getRecordComponents()).map(RecordComponent::getType).toList());
                }
                case SINGLETON -> {
                    Field inst = c.getField("INSTANCE");
                    assertSame(c, inst.get(null).getClass(), p.binaryName());
                    assertTrue(instanceFields(c).isEmpty(), p.binaryName());
                }
                case CLASS -> {
                    List<FieldDef> want = p.fields();
                    Class<?> owner = c;
                    if (p.superType() != null && want.isEmpty() && S.struct(S.resolve(p.superType().name(), p.javaPackage())).isPresent()) {
                        StructDef base = S.struct(S.resolve(p.superType().name(), p.javaPackage())).orElseThrow();
                        want = base.fields();
                        owner = c.getSuperclass();
                        assertEquals(base.binaryName(), owner.getName(), p.binaryName());
                    }
                    List<Field> have = instanceFields(owner);
                    compare(bad, p.className(), want, p.javaPackage(), have.stream().map(Field::getName).toList(),
                            have.stream().map(Field::getType).<Class<?>>map(t -> t).toList());
                    for (FieldDef f : want) {
                        if (f.accessor() != null) assertNotNull(owner.getMethod(f.accessor()), p.className() + "#" + f.accessor());
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void helperTypesExistWithEnumConstants() throws Exception {
        for (TypeDecl d : S.types().values()) {
            if (d.kind() == TypeDecl.Kind.LIB) continue;
            Class<?> c = Class.forName(d.binaryName());
            assertTrue(NmsSymbols.RUNTIME.hasClass(d.binaryName().replace('.', '/')), d.binaryName());
            if (d.kind() == TypeDecl.Kind.ENUM && !d.constants().isEmpty()) {
                assertEquals(d.constants(), Arrays.stream(c.getEnumConstants()).map(e -> ((Enum<?>) e).name()).toList(), d.binaryName());
            }
        }
        for (StructDef s : S.structs()) {
            Class<?> c = Class.forName(s.binaryName());
            if (s.kind() == StructDef.Kind.ENUM) {
                assertEquals(s.constants(), Arrays.stream(c.getEnumConstants()).map(e -> ((Enum<?>) e).name()).toList(), s.binaryName());
            }
            assertEquals(s.privateAccess(), Modifier.isPrivate(c.getModifiers()), s.binaryName());
        }
    }

    @Test
    void generatedPacketsReportSchemaKeyAndValues() {
        var pos = new ClientboundMoveEntityPacket.Pos(7, (short) 1, (short) 2, (short) 3, true);
        assertEquals("game/clientbound/move_entity_pos", ((SchemaPacket) pos).schemaKey());
        assertEquals(List.of(7, (short) 1, (short) 2, (short) 3, (byte) 0, (byte) 0, true, false, true), List.of(((SchemaPacket) pos).schemaValues()));
        assertTrue(pos.hasPosition());
        assertFalse(pos.hasRotation());
        var rot = new ClientboundMoveEntityPacket.Rot(7, (byte) 64, (byte) -32, false);
        assertEquals(64, rot.getyRot());
        assertFalse(rot.hasPosition());
    }

    @Test
    void extrasConvertLikeVanilla() {
        UUID u = new UUID(1, 2);
        var add = new ClientboundAddEntityPacket(5, u, 1.5, 64, -3, 90.0F, -45.0F, null, 0, new Vec3(5.0, -0.5, 0.25), 180.0);
        assertEquals(5, add.getId());
        assertEquals(u, add.getUUID());
        assertEquals(3.9, add.getXa(), 1e-9, "속도는 ±3.9 로 잘린다");
        assertEquals(-0.5, add.getYa(), 1e-9);
        assertEquals(90.0F, add.getXRot(), 1e-4F);
        assertEquals(-45.0F, add.getYRot(), 1e-4F);
        assertEquals(-180.0F, add.getYHeadRot(), 1e-4F, "180도는 byte 로 -128 이 된다");

        var sound = new ClientboundSoundPacket(null, null, 1.0, 2.5, -3.0, 1.0F, 1.0F, 42L);
        assertEquals(2.5, sound.getY(), 1e-9);
        assertEquals(42L, sound.getSeed());

        assertEquals(3, typeId(ClientboundGameEventPacket.CHANGE_GAME_MODE));
        assertEquals(13, typeId(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START));

        var rot = new ServerboundMovePlayerPacket.Rot(10.0F, 20.0F, true);
        assertEquals(99.0, rot.getX(99.0), "위치 없는 이동 패킷은 기본값");
        assertEquals(10.0F, rot.getYRot(0.0F));
        var status = new ServerboundMovePlayerPacket.StatusOnly(false);
        assertFalse(status.hasPosition() || status.hasRotation() || status.isOnGround());

        var act = new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.DROP_ITEM, null, null);
        assertEquals(0, act.getSequence());
        assertEquals(ServerboundPlayerActionPacket.Action.DROP_ITEM, act.getAction());

        var bundle = new ClientboundBundlePacket(List.of(pos()));
        assertEquals(1, bundle.subPackets().spliterator().getExactSizeIfKnown());
    }

    private static int typeId(ClientboundGameEventPacket.Type t) {
        try {
            Field f = ClientboundGameEventPacket.Type.class.getDeclaredField("id");   // 바닐라도 접근자 없이 필드뿐이다
            f.setAccessible(true);
            return f.getInt(t);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ClientboundMoveEntityPacket.Pos pos() { return new ClientboundMoveEntityPacket.Pos(1, (short) 0, (short) 0, (short) 0, true); }

    private static void compare(List<String> bad, String where, List<FieldDef> want, String pkg, List<String> names, List<Class<?>> types) {
        List<String> wantNames = want.stream().map(FieldDef::name).toList();
        if (!wantNames.equals(names)) { bad.add(where + ": 필드 " + names + " / 스키마 " + wantNames); return; }
        for (int i = 0; i < want.size(); i++) {
            String expect = erasure(want.get(i).type(), pkg);
            if (!expect.equals(types.get(i).getName())) bad.add(where + "." + names.get(i) + ": " + types.get(i).getName() + " / 스키마 " + expect);
        }
    }

    /** 스키마 타입의 소거(Class#getName 모양). 라이브러리 타입은 Object 로 지워진다. */
    private static String erasure(TypeRef t, String pkg) {
        String bin = S.resolve(t.name(), pkg);
        if (S.type(bin).map(d -> d.kind() == TypeDecl.Kind.LIB).orElse(false)) bin = "java.lang.Object";
        if (t.arrayDimensions() == 0) return bin;
        String elem = switch (bin) {
            case "int" -> "I"; case "long" -> "J"; case "short" -> "S"; case "byte" -> "B"; case "float" -> "F";
            case "double" -> "D"; case "boolean" -> "Z"; case "char" -> "C"; default -> "L" + bin + ";";
        };
        return "[".repeat(t.arrayDimensions()) + elem;
    }

    private static List<Field> instanceFields(Class<?> c) {
        return Arrays.stream(c.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()).toList();
    }
}
