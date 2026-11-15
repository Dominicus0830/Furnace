// 살아 있는 Bukkit PersistentDataContainer(엔티티·블록 엔티티) — 호출마다 주인 region 스레드에서 지금 값을 읽고 쓴다.
// 소유 스레드가 아니면 읽기는 ThreadOwnershipException, 쓰기(set·remove)는 소유 스레드로 예약된다.
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;

abstract class LivePdc implements PersistentDataContainer {

    /** 소유 스레드에서 곧바로 edit 를 돌린다. 주인(엔티티·블록 엔티티)이 없으면 false. 소유 스레드가 아니면 ThreadOwnershipException. */
    protected abstract boolean editNow(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit);

    /** edit 를 소유 스레드로 예약한다. */
    protected abstract CompletableFuture<Boolean> editLater(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit);

    /** 주인이 없을 때 op 의 결과(write 면 쓰기 시도). */
    protected abstract <T> T whenGone(Function<ItemPdc, T> op, boolean write);

    /** 소유 스레드에서 지금 값 위에 op 를 돌린다(바뀐 것은 곧바로 적용). 주인이 없으면 {@link #whenGone}. */
    private <T> T access(Function<ItemPdc, T> op) { return access(op, false); }

    private <T> T access(Function<ItemPdc, T> op, boolean write) {
        AtomicReference<T> out = new AtomicReference<>();
        boolean alive = editNow(c -> {
            ItemPdc p = new ItemPdc(c);
            out.set(op.apply(p));
            if (p.dirty()) p.applyTo(c);
        });
        if (!alive) return whenGone(op, write);
        return out.get();
    }

    /** 결과가 필요 없는 쓰기: 소유 스레드가 아니면 소유 스레드로 예약한다. */
    private void write(Function<ItemPdc, Object> op, String what) {
        try {
            access(op, true);
        } catch (ThreadOwnershipException notOwner) {
            Owned.write(editLater(c -> {
                ItemPdc p = new ItemPdc(c);
                op.apply(p);
                if (p.dirty()) p.applyTo(c);
            }), what);
        }
    }

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        java.util.Objects.requireNonNull(key, "key");
        // 값 변환·종류 검사는 호출 스레드에서 미리 해 둔다(사용자 정의 종류의 코드가 소유 스레드에서 돌지 않게)
        ItemPdc.Prepared prepared = ItemPdc.prepare(type, value, this);
        write(p -> {
            p.put(key.toString(), prepared);
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
    public byte[] serializeToBytes() throws java.io.IOException {
        try {
            return access(p -> {
                try {
                    return p.serializeToBytes();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public void readFromBytes(byte[] bytes, boolean clear) throws java.io.IOException {
        ItemPdc parsed;
        try {
            parsed = ItemPdc.parse(bytes); // 꼴이 틀리면 여기서(호출 스레드) IOException
        } catch (java.io.IOException bad) {
            if (clear) {
                write(p -> {
                    p.clearAll();
                    return null;
                }, "PersistentDataContainer#readFromBytes");   // Paper 는 파싱 전에 비운다
            }
            throw bad;
        }
        write(p -> {
            p.mergeFrom(parsed, clear);
            return null;
        }, "PersistentDataContainer#readFromBytes");
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() { return new ItemPdc(null); }
}
