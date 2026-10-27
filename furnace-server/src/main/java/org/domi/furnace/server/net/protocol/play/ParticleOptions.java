// 입자 종류+옵션(바닐라 ParticleTypes.STREAM_CODEC): VarInt 입자 종류 id, 종류별 옵션 데이터.
// 종류 id 는 registries/particle_types-1.21.1.txt(데이터 생성기 registries.json 의 particle_type protocol_id 순서, 1.21.1 전용).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;

public sealed interface ParticleOptions {

    /** "minecraft:..." 입자 종류 이름. */
    String type();

    /** 종류 id 뒤에 오는 옵션 데이터. */
    void writeData(ByteBuf buf);

    default void write(ByteBuf buf) {
        Types.writeVarInt(buf, Registry.id(type()));
        writeData(buf);
    }

    /** 옵션이 없는 종류(smoke, sculk_soul, sonic_boom, explosion ...). */
    record Simple(String type) implements ParticleOptions {
        @Override public void writeData(ByteBuf buf) {}
    }

    /** BlockParticleOption: block, block_marker, falling_dust, dust_pillar — 블록 상태 id(VarInt). */
    record Block(String type, int blockState) implements ParticleOptions {
        @Override public void writeData(ByteBuf buf) { Types.writeVarInt(buf, blockState); }
    }

    /** DustParticleOptions(dust): 색 Vector3f(float×3), 크기 float(0.01~4 로 죔). */
    record Dust(float r, float g, float b, float scale) implements ParticleOptions {
        public Dust { scale = Math.max(0.01F, Math.min(4.0F, scale)); }
        @Override public String type() { return "minecraft:dust"; }
        @Override public void writeData(ByteBuf buf) {
            buf.writeFloat(r); buf.writeFloat(g); buf.writeFloat(b); buf.writeFloat(scale);
        }
    }

    /** DustColorTransitionOptions: 시작색 float×3, 끝색 float×3, 크기 float(0.01~4). */
    record DustTransition(float fr, float fg, float fb, float tr, float tg, float tb, float scale) implements ParticleOptions {
        public DustTransition { scale = Math.max(0.01F, Math.min(4.0F, scale)); }
        @Override public String type() { return "minecraft:dust_color_transition"; }
        @Override public void writeData(ByteBuf buf) {
            buf.writeFloat(fr); buf.writeFloat(fg); buf.writeFloat(fb);
            buf.writeFloat(tr); buf.writeFloat(tg); buf.writeFloat(tb);
            buf.writeFloat(scale);
        }
    }

    /** ColorParticleOption(entity_effect): ARGB int (가정: 1.21.1 은 Int 하나 — 알파 포함). */
    record Color(int argb) implements ParticleOptions {
        @Override public String type() { return "minecraft:entity_effect"; }
        @Override public void writeData(ByteBuf buf) { buf.writeInt(argb); }
    }

    /** ItemParticleOption(item): ItemStack 와이어(개수 VarInt, 아이템 id, 컴포넌트 패치). */
    record Item(ItemStack stack) implements ParticleOptions {
        @Override public String type() { return "minecraft:item"; }
        @Override public void writeData(ByteBuf buf) { ContainerSetContent.writeSlot(buf, stack); }
    }

    /** SculkChargeParticleOptions(sculk_charge): roll float. */
    record SculkCharge(float roll) implements ParticleOptions {
        @Override public String type() { return "minecraft:sculk_charge"; }
        @Override public void writeData(ByteBuf buf) { buf.writeFloat(roll); }
    }

    /** ShriekParticleOption(shriek): 지연 틱 VarInt. */
    record Shriek(int delay) implements ParticleOptions {
        @Override public String type() { return "minecraft:shriek"; }
        @Override public void writeData(ByteBuf buf) { Types.writeVarInt(buf, delay); }
    }

    /**
     * VibrationParticleOption(vibration): 위치 소스(VarInt 종류 0 블록 / 1 엔티티) + 데이터 + 도착 틱 VarInt.
     * 블록은 BlockPos, 엔티티는 VarInt id + float 눈높이 보정(가정: 1.21.1 EntityPositionSource 배치).
     */
    record Vibration(boolean entitySource, int x, int y, int z, int entityId, float yOffset, int arrivalTicks) implements ParticleOptions {
        public static Vibration toBlock(int x, int y, int z, int arrivalTicks) { return new Vibration(false, x, y, z, 0, 0.0F, arrivalTicks); }
        public static Vibration toEntity(int entityId, float yOffset, int arrivalTicks) { return new Vibration(true, 0, 0, 0, entityId, yOffset, arrivalTicks); }
        @Override public String type() { return "minecraft:vibration"; }
        @Override public void writeData(ByteBuf buf) {
            if (entitySource) {
                Types.writeVarInt(buf, 1);
                Types.writeVarInt(buf, entityId);
                buf.writeFloat(yOffset);
            } else {
                Types.writeVarInt(buf, 0);
                Types.writeBlockPos(buf, x, y, z);
            }
            Types.writeVarInt(buf, arrivalTicks);
        }
    }

    static ParticleOptions simple(String name) { return new Simple(name.indexOf(':') < 0 ? "minecraft:" + name : name); }

    /** 입자 종류 이름 → 레지스트리 id. */
    final class Registry {
        private static final List<String> NAMES = new ArrayList<>();
        private static final Map<String, Integer> IDS = new HashMap<>();

        static {
            try (var in = Registry.class.getClassLoader().getResourceAsStream("registries/particle_types-1.21.1.txt")) {
                if (in == null) throw new IllegalStateException("registries/particle_types-1.21.1.txt 없음");
                var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.isBlank()) continue;
                    IDS.put(line.trim(), NAMES.size());
                    NAMES.add(line.trim());
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private Registry() {}

        public static int id(String name) {
            Integer i = IDS.get(name);
            if (i == null) throw new IllegalArgumentException("알 수 없는 입자 종류: " + name);
            return i;
        }

        public static boolean has(String name) { return IDS.containsKey(name); }

        public static List<String> names() { return List.copyOf(NAMES); }
    }
}
