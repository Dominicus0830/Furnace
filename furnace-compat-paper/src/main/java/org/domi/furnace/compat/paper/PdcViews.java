// 읽을 때마다 원본에서 영속 데이터를 다시 읽는 읽기 전용 보기(Paper PaperPersistentDataContainerView 에 해당): ItemStack·OfflinePlayer 용.
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.persistence.PersistentDataView;
import io.papermc.paper.persistence.PersistentDataContainerView;

public final class PdcViews {

    private PdcViews() {}

    /** source 가 줄 때마다(없으면 null = 빈 데이터) 읽는 읽기 전용 보기. */
    public static PersistentDataContainerView live(Supplier<PersistentDataView> source) { return new Live(source); }

    private record Live(Supplier<PersistentDataView> source) implements PersistentDataContainerView {

        private ItemPdc now() { return new ItemPdc(source.get()); }

        @Override
        public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) { return now().has(key, type); }

        @Override
        public boolean has(NamespacedKey key) { return now().has(key); }

        @Override
        public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) { return now().get(key, type); }

        @Override
        public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) { return now().getOrDefault(key, type, defaultValue); }

        @Override
        public Set<NamespacedKey> getKeys() { return now().getKeys(); }

        @Override
        public boolean isEmpty() { return now().isEmpty(); }

        @Override
        public void copyTo(PersistentDataContainer other, boolean replace) { now().copyTo(other, replace); }

        @Override
        public PersistentDataAdapterContext getAdapterContext() { return new ItemPdc(null); }

        @Override
        public byte[] serializeToBytes() throws java.io.IOException { return now().serializeToBytes(); }
    }
}
