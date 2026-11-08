package org.bukkit.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.Plugin;

/**
 * 메타데이터 저장소 바탕: 대상+열쇠를 문자열 하나로 바꾸고({@link #disambiguate}) 그 아래 플러그인마다 값 하나를 둔다.
 * 플러그인은 약한 열쇠라 플러그인 객체가 사라지면 그 값도 사라진다. 여러 스레드에서 써도 된다.
 */
public abstract class MetadataStoreBase<T> {

    private final Map<String, Map<Plugin, MetadataValue>> metadataMap = new ConcurrentHashMap<>();

    public synchronized void setMetadata(T subject, String metadataKey, MetadataValue newMetadataValue) {
        if (newMetadataValue == null) throw new IllegalArgumentException("Value cannot be null");
        Plugin owner = newMetadataValue.getOwningPlugin();
        if (owner == null) throw new IllegalArgumentException("Plugin cannot be null");
        Map<Plugin, MetadataValue> entry = metadataMap.computeIfAbsent(disambiguate(subject, metadataKey), k -> new WeakHashMap<>(1));
        synchronized (entry) {
            entry.put(owner, newMetadataValue);
        }
    }

    public List<MetadataValue> getMetadata(T subject, String metadataKey) {
        Map<Plugin, MetadataValue> entry = metadataMap.get(disambiguate(subject, metadataKey));
        if (entry == null) return Collections.emptyList();
        synchronized (entry) {
            return Collections.unmodifiableList(new ArrayList<>(entry.values()));
        }
    }

    public boolean hasMetadata(T subject, String metadataKey) { return metadataMap.containsKey(disambiguate(subject, metadataKey)); }

    public void removeMetadata(T subject, String metadataKey, Plugin owningPlugin) {
        if (owningPlugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        String key = disambiguate(subject, metadataKey);
        Map<Plugin, MetadataValue> entry = metadataMap.get(key);
        if (entry == null) return;
        synchronized (entry) {
            entry.remove(owningPlugin);
            if (entry.isEmpty()) metadataMap.remove(key);
        }
    }

    public void invalidateAll(Plugin owningPlugin) {
        if (owningPlugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        for (Map<Plugin, MetadataValue> values : metadataMap.values()) {
            MetadataValue v;
            synchronized (values) {
                v = values.get(owningPlugin);
            }
            if (v != null) v.invalidate();
        }
    }

    /** 그 플러그인이 붙인 값을 모두 뗀다(Paper 는 /reload 때 부른다). */
    public void removeAll(Plugin owningPlugin) {
        if (owningPlugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        for (Iterator<Map<Plugin, MetadataValue>> it = metadataMap.values().iterator(); it.hasNext(); ) {
            Map<Plugin, MetadataValue> values = it.next();
            synchronized (values) {
                values.remove(owningPlugin);
                if (values.isEmpty()) it.remove();
            }
        }
    }

    /** 대상과 열쇠를 저장소 안에서 겹치지 않는 문자열 하나로. */
    protected abstract String disambiguate(T subject, String metadataKey);
}
