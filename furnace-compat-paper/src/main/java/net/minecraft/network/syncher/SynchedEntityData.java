package net.minecraft.network.syncher;

import java.util.ArrayList;
import java.util.List;

/**
 * 엔티티 메타데이터(Mojang 이름 shim). 바닐라 1.21.1 과 같은 모양: 칸 번호로 색인한 {@link DataItem} 배열 하나와 더러움 표시.
 * ProtocolLib 은 생성자 {@code (SyncedDataHolder, DataItem[])}, 배열 필드, {@link DataItem} 의 필드 순서
 * (accessor, value, initialValue, dirty)를 반사로 쓴다. 서버 엔티티의 칸 표는 Furnace API 의 {@code EntityDataKeys} 가 가진다.
 */
public class SynchedEntityData {

    private final SyncedDataHolder entity;
    private final DataItem<?>[] itemsById;
    private boolean isDirty;

    public SynchedEntityData(SyncedDataHolder entity, DataItem<?>[] itemsById) {
        this.entity = entity;
        this.itemsById = itemsById;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(EntityDataAccessor<T> accessor) {
        return (T) itemsById[accessor.id()].value;
    }

    public <T> void set(EntityDataAccessor<T> accessor, T value) {
        set(accessor, value, false);
    }

    public <T> void set(EntityDataAccessor<T> accessor, T value, boolean force) {
        DataItem<T> item = item(accessor);
        if (force || !java.util.Objects.equals(value, item.value)) {
            // 바닐라 순서: 값 → onSyncedDataUpdated → 더러움. shim 엔티티는 쓰기를 기록하고 서버 재생으로 칸을 다시 채운다
            net.minecraft.world.entity.Entity shim = entity instanceof net.minecraft.world.entity.Entity e ? e : null;
            if (shim != null) shim.furnaceBeforeDataSet();
            item.value = value;
            if (shim != null) shim.furnaceDataWritten(accessor, value);
            else if (entity != null) entity.onSyncedDataUpdated(accessor);
            item.dirty = true;
            isDirty = true;
        }
    }

    /** 호환 모듈 전용: 서버가 다시 채운 칸 하나(값·더러움). 처음 값(initialValue)은 종류마다 정해져 그대로다. */
    @SuppressWarnings("unchecked")
    public void furnaceAssign(int id, Object value, boolean dirty) {
        DataItem<Object> item = (DataItem<Object>) itemsById[id];
        // 값이 같으면 기존 객체를 둔다(바닐라 set 도 같을 때 바꾸지 않는다) — 플러그인이 꺼낸 가변 값(JOML 등)이 다음 조작에서 다른 객체로 바뀌지 않게
        if (!java.util.Objects.equals(item.value, value)) item.value = value;
        item.setDirty(dirty);
        boolean any = false;
        for (DataItem<?> i : itemsById) any |= i != null && i.isDirty();
        isDirty = any;
    }

    /** 호환 모듈 전용: 칸 하나. */
    public DataItem<?> furnaceItem(int id) { return itemsById[id]; }

    @SuppressWarnings("unchecked")
    private <T> DataItem<T> item(EntityDataAccessor<T> accessor) {
        return (DataItem<T>) itemsById[accessor.id()];
    }

    public boolean isDirty() {
        return isDirty;
    }

    /** 바뀐 칸만 꺼내며 더러움 표시를 지운다(바닐라 packDirty). */
    public List<DataValue<?>> packDirty() {
        if (!isDirty) {
            return null;
        }
        isDirty = false;
        List<DataValue<?>> out = new ArrayList<>();
        for (DataItem<?> item : itemsById) {
            if (item != null && item.dirty) {
                item.dirty = false;
                out.add(item.value());
            }
        }
        return out;
    }

    /** 처음 값과 다른 칸 전부(바닐라 getNonDefaultValues). */
    public List<DataValue<?>> getNonDefaultValues() {
        List<DataValue<?>> out = null;
        for (DataItem<?> item : itemsById) {
            if (item != null && !item.isSetToDefault()) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(item.value());
            }
        }
        return out;
    }

    /** 패킷에 실리는 칸 값 하나: 칸 번호, 값 종류, 값(바닐라와 같은 레코드). */
    public record DataValue<T>(int id, EntityDataSerializer<T> serializer, T value) {

        public static <T> DataValue<T> create(EntityDataAccessor<T> accessor, T value) {
            return new DataValue<>(accessor.id(), accessor.serializer(), accessor.serializer().copy(value));
        }
    }

    /** 엔티티가 가진 칸 하나. 필드 선언 순서가 ProtocolLib 의 읽기 색인과 맞아야 한다. */
    public static class DataItem<T> {
        final EntityDataAccessor<T> accessor;
        T value;
        private final T initialValue;
        private boolean dirty;

        public DataItem(EntityDataAccessor<T> accessor, T value) {
            this.accessor = accessor;
            this.initialValue = value;
            this.value = value;
        }

        public EntityDataAccessor<T> getAccessor() {
            return accessor;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isSetToDefault() {
            return java.util.Objects.equals(initialValue, value);
        }

        public DataValue<T> value() {
            return DataValue.create(accessor, value);
        }
    }
}
