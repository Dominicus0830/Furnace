// 읽을 때마다 원본에서 영속 데이터를 다시 읽는 읽기 전용 보기(Paper PaperPersistentDataContainerView 에 해당): ItemStack·OfflinePlayer 용.
package org.domi.furnace.compat.paper;

import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.persistence.PersistentDataView;

public final class PdcViews {

    private PdcViews() {}

    /** source 가 줄 때마다(없으면 null = 빈 데이터) 읽는 읽기 전용 보기. */
    public static io.papermc.paper.persistence.PersistentDataContainerView live(Supplier<PersistentDataView> source) { return new Live(source); }

    /** Furnace 몸(ItemPdc·LivePdc)을 플러그인이 받는 CraftPersistentDataContainer 로 감싼다(T-264 P2c-4, 생성자는 패키지 전용). */
    public static org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer craft(PersistentDataContainer body) { return CraftAccess.newPdc(body); }

    /** 감싼 Furnace 몸. */
    public static PersistentDataContainer body(org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer c) { return CraftAccess.pdcBody(c); }

    /** Paper 의 익명 PaperPersistentDataContainerView(CraftItemStack$3·CraftOfflinePlayer$1)와 같은 상위. */
    private static final class Live extends io.papermc.paper.persistence.PaperPersistentDataContainerView {

        private final Supplier<PersistentDataView> source;
        private final PersistentDataAdapterContext context = CraftAccess.newPdcContext();

        Live(Supplier<PersistentDataView> source) { this.source = source; }

        private ItemPdc now() {
            ItemPdc p = new ItemPdc(source.get());
            p.useContext(context);
            return p;
        }

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
        public PersistentDataAdapterContext getAdapterContext() { return context; }

        @Override
        public byte[] serializeToBytes() throws java.io.IOException { return now().serializeToBytes(); }
    }
}
