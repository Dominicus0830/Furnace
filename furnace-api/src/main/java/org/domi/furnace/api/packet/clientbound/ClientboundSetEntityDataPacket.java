// 엔티티 메타데이터(set_entity_data): (칸 번호, 값) 목록. 가상 엔티티의 이름표·투명·발광·자세 같은 겉모습을 정한다.
// 칸 번호와 값 종류는 바닐라 엔티티마다 정해져 있다(예: 0 = 상태 비트 byte[1 불 2 웅크림 8 달리기 32 투명 64 발광], 2 = 커스텀 이름(옵션 텍스트), 3 = 이름 보임(boolean), 6 = 자세).
// 읽을 수 없는 값 종류(아이템·입자·NBT 등)를 만나면 거기서 멈추고 Unsupported 로 남긴다 — 그런 패킷은 다시 보낼 수 없고(원본 그대로 전달), 관찰·취소는 된다.
package org.domi.furnace.api.packet.clientbound;

import java.util.List;
import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundSetEntityDataPacket(int entityId, List<Entry> entries) implements ClientboundPacket {

    public record Entry(int index, Value value) {
        public Entry {
            if (index < 0 || index > 254) throw new IllegalArgumentException("칸 번호 범위 밖: " + index);
            Objects.requireNonNull(value, "value");
        }
    }

    /** 값 종류. 서버 구현이 와이어 직렬화기 번호로 바꾼다(번호는 API 에 드러나지 않는다). */
    public sealed interface Value {
        static Value ofByte(int v) { return new ByteValue((byte) v); }
        static Value ofInt(int v) { return new IntValue(v); }
        static Value ofLong(long v) { return new LongValue(v); }
        static Value ofFloat(float v) { return new FloatValue(v); }
        static Value ofBoolean(boolean v) { return new BooleanValue(v); }
        static Value ofString(String v) { return new StringValue(v); }
        static Value ofText(Text v) { return new TextValue(v); }
        /** 텍스트 또는 없음(null) — 커스텀 이름 같은 옵션 텍스트 칸. */
        static Value ofOptionalText(Text v) { return new OptionalTextValue(v); }
        /** 자세 번호: 0 서 있음, 1 겉날개 비행, 2 잠, 3 수영, 4 회전 공격, 5 웅크림, 6 긴 점프, 7 죽음(1.21.1 기준 이름 순서). */
        static Value ofPose(int pose) { return new PoseValue(pose); }
    }

    public record ByteValue(byte value) implements Value {}
    public record IntValue(int value) implements Value {}
    public record LongValue(long value) implements Value {}
    public record FloatValue(float value) implements Value {}
    public record BooleanValue(boolean value) implements Value {}
    public record StringValue(String value) implements Value {
        public StringValue { Objects.requireNonNull(value, "value"); }
    }
    public record TextValue(Text value) implements Value {
        public TextValue { Objects.requireNonNull(value, "value"); }
    }
    public record OptionalTextValue(Text value) implements Value {}
    public record PoseValue(int pose) implements Value {}
    /** 아직 읽을 수 없는 직렬화 종류. serializer 는 이 서버 버전의 번호(진단용). */
    public record Unsupported(int serializer) implements Value {}

    public ClientboundSetEntityDataPacket {
        entries = List.copyOf(entries);
    }

    public static ClientboundSetEntityDataPacket of(int entityId, Entry... entries) {
        return new ClientboundSetEntityDataPacket(entityId, List.of(entries));
    }

    public static Entry entry(int index, Value value) { return new Entry(index, value); }

    /** 읽지 못한 값이 있어 다시 보낼 수 없는지. */
    public boolean hasUnsupported() {
        for (Entry e : entries) if (e.value() instanceof Unsupported) return true;
        return false;
    }

    @Override
    public PacketType<ClientboundSetEntityDataPacket> type() { return PacketTypes.Clientbound.SET_ENTITY_DATA; }
}
