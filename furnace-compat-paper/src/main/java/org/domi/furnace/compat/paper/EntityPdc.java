// 엔티티의 Bukkit PersistentDataContainer — 호출마다 그 엔티티를 가진 region 스레드에서 지금 값을 읽고 쓴다(editPersistentDataNow).
// 소유 스레드가 아니면 읽기는 ThreadOwnershipException, 쓰기(set·remove)는 소유 스레드로 예약된다.
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;

final class EntityPdc implements PersistentDataContainer {

    private final org.domi.furnace.api.entity.Entity entity;

    EntityPdc(org.domi.furnace.api.entity.Entity entity) { this.entity = entity; }

    /** 소유 스레드에서 지금 값 위에 op 를 돌린다(바뀐 것은 곧바로 적용). 엔티티가 사라졌으면 IllegalStateException. */
    private <T> T access(Function<ItemPdc, T> op) {
        AtomicReference<T> out = new AtomicReference<>();
        boolean alive = entity.editPersistentDataNow(c -> {
            ItemPdc p = new ItemPdc(c);
            out.set(op.apply(p));
            if (p.dirty()) p.applyTo(c);
        });
        if (!alive) throw new IllegalStateException("엔티티가 사라졌다: " + entity.uniqueId());
        return out.get();
    }

    /** 결과가 필요 없는 쓰기: 소유 스레드가 아니면 소유 스레드로 예약한다. */
    private void write(Function<ItemPdc, Object> op, String what) {
        try {
            access(op);
        } catch (ThreadOwnershipException notOwner) {
            Owned.write(entity.editPersistentData(c -> {
                ItemPdc p = new ItemPdc(c);
                op.apply(p);
                if (p.dirty()) p.applyTo(c);
            }), what);
        }
    }

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        // 값 변환은 호출 스레드에서 미리 해 둔다(사용자 정의 종류의 코드가 소유 스레드에서 돌지 않게)
        P prim = type.toPrimitive(java.util.Objects.requireNonNull(value, "value"), this);
        write(p -> {
            p.set(key, new PersistentDataType<P, P>() {
                @Override public Class<P> getPrimitiveType() { return type.getPrimitiveType(); }
                @Override public Class<P> getComplexType() { return type.getPrimitiveType(); }
                @Override public P toPrimitive(P complex, PersistentDataAdapterContext ctx) { return complex; }
                @Override public P fromPrimitive(P primitive, PersistentDataAdapterContext ctx) { return primitive; }
            }, prim);
            return null;
        }, "PersistentDataContainer#set(" + key + ")");
    }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) { return access(p -> p.has(key, type)); }

    @Override
    public boolean has(NamespacedKey key) { return access(p -> p.has(key)); }

    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) { return access(p -> p.get(key, type)); }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) { return access(p -> p.getOrDefault(key, type, defaultValue)); }

    @Override
    public Set<NamespacedKey> getKeys() { return access(ItemPdc::getKeys); }

    @Override
    public void remove(NamespacedKey key) {
        write(p -> {
            p.remove(key);
            return null;
        }, "PersistentDataContainer#remove(" + key + ")");
    }

    @Override
    public boolean isEmpty() { return access(ItemPdc::isEmpty); }

    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        access(p -> {
            p.copyTo(other, replace);
            return null;
        });
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() { return new ItemPdc(null); }
}
