package org.bukkit.craftbukkit.inventory;

import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.compat.paper.ItemMetaSerialization;

/**
 * ItemMeta 를 되읽는 쪽(CraftBukkit 과 같은 이름 — 별칭 "ItemMeta"). Furnace 의 ItemMeta 구현은 {@code @DelegateDeserialization} 으로
 * 이 클래스를 가리키고, ConfigurationSerialization 은 {@code ==: ItemMeta} 를 이 클래스의 {@link #deserialize} 로 되읽는다.
 */
@SerializableAs("ItemMeta")
public final class SerializableMeta implements ConfigurationSerializable {

    private SerializableMeta() {}

    /** {@code meta-type} 이 있는 맵에서 ItemMeta 를 만든다. 없으면 NoSuchElementException, 모르는 종류면 IllegalArgumentException. */
    public static ItemMeta deserialize(Map<String, Object> map) throws Throwable {
        if (map == null) throw new IllegalArgumentException("Cannot deserialize null map");
        return ItemMetaSerialization.deserialize(map);
    }

    @Override
    public Map<String, Object> serialize() { throw new AssertionError(); }
}
