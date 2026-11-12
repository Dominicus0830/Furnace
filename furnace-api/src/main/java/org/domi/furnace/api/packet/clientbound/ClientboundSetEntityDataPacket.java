// 엔티티 메타데이터(set_entity_data): (칸, 값) 목록. 엔티티의 이름표·투명·발광·자세·디스플레이 엔티티 모양 같은 겉모습을 정한다.
//
// 칸은 이름 있는 키로 다룬다(EntityDataKeys — 칸 번호는 버전마다 밀리므로 서버가 실행 중인 버전에 맞춘다):
//   ClientboundSetEntityDataPacket.builder(id).set(EntityDataKeys.TextDisplay.TEXT, Text.text("상점")).set(EntityDataKeys.Display.SCALE, new Vector3f(2, 2, 2)).build()
//   e.packet().get(EntityDataKeys.Entity.SHARED_FLAGS)   // 가로챈 패킷에서 읽기
//   e.setPacket(e.packet().with(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.of(EntityFlags.GLOWING)))
// 숫자 칸(entry(int, Value))도 남아 있다 — 그 번호는 마인크래프트 버전이 바뀌면 달라진다.
//
// 값 종류 31개(EntityDataType) 를 모두 읽고 쓴다. 이 서버 버전이 모르는 직렬화 번호를 만나면 거기서 멈추고 Unsupported 로 남긴다
// — 그런 패킷은 다시 보낼 수 없고(원본 그대로 전달), 관찰·취소는 된다.
package org.domi.furnace.api.packet.clientbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.entity.data.EntityDataKey;
import org.domi.furnace.api.entity.data.EntityDataType;
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

    /**
     * 값 하나. 종류(dataType)와 자바 값(asObject)을 가진다. 만들 때는 {@link #of(EntityDataType, Object)} 를 쓴다.
     * 예전 9종 레코드(ByteValue …)는 그대로 쓸 수 있고, 나머지 종류는 {@link Typed} 다.
     */
    public sealed interface Value {
        /** 값 종류. Unsupported 면 null. */
        EntityDataType<?> dataType();

        /** 자바 값(EntityDataType 이 정한 꼴: Optional·List·Pose …). Unsupported 면 null. */
        Object asObject();

        /** 종류에 맞는 값을 만든다(맞지 않으면 IllegalArgumentException). */
        @SuppressWarnings("unchecked")
        static <T> Value of(EntityDataType<T> type, T value) {
            Object v = type.check(value);
            if (type == EntityDataType.BYTE) return new ByteValue((Byte) v);
            if (type == EntityDataType.INT) return new IntValue((Integer) v);
            if (type == EntityDataType.LONG) return new LongValue((Long) v);
            if (type == EntityDataType.FLOAT) return new FloatValue((Float) v);
            if (type == EntityDataType.BOOLEAN) return new BooleanValue((Boolean) v);
            if (type == EntityDataType.STRING) return new StringValue((String) v);
            if (type == EntityDataType.COMPONENT) return new TextValue((Text) v);
            if (type == EntityDataType.OPTIONAL_COMPONENT) return new OptionalTextValue(((Optional<Text>) v).orElse(null));
            if (type == EntityDataType.POSE) return new PoseValue(((Pose) v).id());
            return new Typed<>(type, (T) v);
        }

        static Value ofByte(int v) { return new ByteValue((byte) v); }
        static Value ofInt(int v) { return new IntValue(v); }
        static Value ofLong(long v) { return new LongValue(v); }
        static Value ofFloat(float v) { return new FloatValue(v); }
        static Value ofBoolean(boolean v) { return new BooleanValue(v); }
        static Value ofString(String v) { return new StringValue(v); }
        static Value ofText(Text v) { return new TextValue(v); }
        /** 텍스트 또는 없음(null) — 커스텀 이름 같은 옵션 텍스트 칸. */
        static Value ofOptionalText(Text v) { return new OptionalTextValue(v); }
        /** 자세 번호(Pose.id()): 0 서 있음, 1 겉날개 비행, 2 잠, 3 수영, 4 회전 공격, 5 웅크림, 6 긴 점프, 7 죽음 …(1.21.1 순서). */
        static Value ofPose(int pose) { return new PoseValue(pose); }
    }

    public record ByteValue(byte value) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.BYTE; }
        @Override public Object asObject() { return value; }
    }
    public record IntValue(int value) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.INT; }
        @Override public Object asObject() { return value; }
    }
    public record LongValue(long value) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.LONG; }
        @Override public Object asObject() { return value; }
    }
    public record FloatValue(float value) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.FLOAT; }
        @Override public Object asObject() { return value; }
    }
    public record BooleanValue(boolean value) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.BOOLEAN; }
        @Override public Object asObject() { return value; }
    }
    public record StringValue(String value) implements Value {
        public StringValue { Objects.requireNonNull(value, "value"); }
        @Override public EntityDataType<?> dataType() { return EntityDataType.STRING; }
        @Override public Object asObject() { return value; }
    }
    public record TextValue(Text value) implements Value {
        public TextValue { Objects.requireNonNull(value, "value"); }
        @Override public EntityDataType<?> dataType() { return EntityDataType.COMPONENT; }
        @Override public Object asObject() { return value; }
    }
    /** value 가 null 이면 "없음". */
    public record OptionalTextValue(Text value) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.OPTIONAL_COMPONENT; }
        @Override public Object asObject() { return Optional.ofNullable(value); }
    }
    public record PoseValue(int pose) implements Value {
        @Override public EntityDataType<?> dataType() { return EntityDataType.POSE; }
        @Override public Object asObject() { return Pose.byId(pose); }
    }

    /** 위 9종 밖의 값(아이템·블록 상태·벡터·쿼터니언·입자·NBT …). Value.of 로 만든다. */
    public record Typed<T>(EntityDataType<T> type, T value) implements Value {
        public Typed {
            Objects.requireNonNull(type, "type");
            value = type.check(value);
        }
        @Override public EntityDataType<?> dataType() { return type; }
        @Override public Object asObject() { return value; }
    }

    /** 이 서버 버전이 모르는 직렬화 종류. serializer 는 이 서버 버전의 번호(진단용). */
    public record Unsupported(int serializer) implements Value {
        @Override public EntityDataType<?> dataType() { return null; }
        @Override public Object asObject() { return null; }
    }

    public ClientboundSetEntityDataPacket {
        entries = List.copyOf(entries);
    }

    public static ClientboundSetEntityDataPacket of(int entityId, Entry... entries) {
        return new ClientboundSetEntityDataPacket(entityId, List.of(entries));
    }

    public static Entry entry(int index, Value value) { return new Entry(index, value); }

    /** 이름 있는 칸의 항목. 칸 번호는 실행 중인 서버 버전에서 정한다. */
    public static <T> Entry entry(EntityDataKey<T> key, T value) { return new Entry(index(key), Value.of(key.type(), value)); }

    public static Builder builder(int entityId) { return new Builder(entityId); }

    /** 이 칸의 값(없으면 empty). 같은 칸이 여러 번 있으면 마지막 것. 칸 번호는 같아도 값 종류가 다르면 empty(다른 엔티티 부류의 칸). */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(EntityDataKey<T> key) {
        int i = index(key);
        Object found = null;
        for (Entry e : entries) if (e.index() == i && e.value().dataType() == key.type()) found = e.value().asObject();
        return Optional.ofNullable((T) found);
    }

    /** 이 칸을 value 로 바꾼(없으면 끝에 더한) 새 패킷. */
    public <T> ClientboundSetEntityDataPacket with(EntityDataKey<T> key, T value) {
        Entry n = entry(key, value);
        List<Entry> out = new ArrayList<>(entries.size() + 1);
        boolean replaced = false;
        for (Entry e : entries) {
            if (e.index() == n.index()) {
                if (!replaced) out.add(n);
                replaced = true;
            } else {
                out.add(e);
            }
        }
        if (!replaced) out.add(n);
        return new ClientboundSetEntityDataPacket(entityId, out);
    }

    /** 이 칸을 뺀 새 패킷(없으면 그대로). */
    public ClientboundSetEntityDataPacket without(EntityDataKey<?> key) {
        int i = index(key);
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) if (e.index() != i) out.add(e);
        return out.size() == entries.size() ? this : new ClientboundSetEntityDataPacket(entityId, out);
    }

    /** 읽지 못한 값이 있어 다시 보낼 수 없는지. */
    public boolean hasUnsupported() {
        for (Entry e : entries) if (e.value() instanceof Unsupported) return true;
        return false;
    }

    private static int index(EntityDataKey<?> key) {
        OptionalInt i = key.index();
        if (i.isEmpty()) throw new IllegalArgumentException("이 서버 버전에 없는 메타데이터 칸: " + key);
        return i.getAsInt();
    }

    @Override
    public PacketType<ClientboundSetEntityDataPacket> type() { return PacketTypes.Clientbound.SET_ENTITY_DATA; }

    /** 여러 칸을 이름으로 채우는 빌더. 같은 칸을 다시 넣으면 앞의 값을 바꾼다. */
    public static final class Builder {
        private final int entityId;
        private final java.util.LinkedHashMap<Integer, Entry> entries = new java.util.LinkedHashMap<>();

        private Builder(int entityId) { this.entityId = entityId; }

        public <T> Builder set(EntityDataKey<T> key, T value) {
            Entry e = entry(key, value);
            entries.put(e.index(), e);
            return this;
        }

        /** 숫자 칸(버전마다 바뀜). */
        public Builder set(int index, Value value) {
            entries.put(index, new Entry(index, value));
            return this;
        }

        public ClientboundSetEntityDataPacket build() { return new ClientboundSetEntityDataPacket(entityId, List.copyOf(entries.values())); }
    }
}
