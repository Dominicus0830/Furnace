// 켜진 팩들 위의 리소스 조회(바닐라 MultiPackResourceManager + FallbackResourceManager, 데이터 팩 쪽만).
//  - 위(나중) 팩이 아래 팩을 덮어쓴다.
//  - 팩의 filter: 그 팩보다 아래 팩들의 리소스 중 filter 에 걸리는 것을 숨긴다(namespace 규칙 하나라도·path 규칙 하나라도 맞으면).
//  - 스택 조회(태그처럼 겹쳐 합치는 리소스)는 아래→위 순서, filter 에 걸리면 그 아래는 버린다.
package org.domi.furnace.server.data;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class ResourceManager implements AutoCloseable {

    /** 찾은 리소스 하나 — 어느 팩에서 왔는지와 열기. */
    public record Resource(String packId, PackResources.IoSupplier<InputStream> open) {}

    /** namespace 하나의 팩 줄(아래→위). pack 이 null 이면 filter 만 있는 줄. */
    private record Entry(PackResources pack, Predicate<ResourceId> filter) {
        boolean filtered(ResourceId id) { return filter != null && filter.test(id); }
    }

    private final List<PackResources> packs;
    private final Map<String, List<Entry>> byNamespace = new HashMap<>();

    /** packs: 아래→위. 닫는 책임을 넘겨받는다. */
    public ResourceManager(List<PackResources> packs, List<PackMetadata> metadata) {
        this.packs = List.copyOf(packs);
        Set<String> all = new LinkedHashSet<>();
        List<Set<String>> nss = new ArrayList<>();
        for (PackResources p : packs) {
            Set<String> s = p.namespaces();
            nss.add(s);
            all.addAll(s);
        }
        for (int i = 0; i < packs.size(); i++) {
            PackResources p = packs.get(i);
            PackMetadata meta = i < metadata.size() ? metadata.get(i) : null;
            boolean hasFilter = meta != null && !meta.filter().isEmpty();
            Predicate<ResourceId> pathFilter = hasFilter ? id -> meta.filtersPath(id.path()) : null;
            for (String ns : all) {
                boolean has = nss.get(i).contains(ns);
                boolean filters = hasFilter && meta.filtersNamespace(ns);
                if (!has && !filters) continue;
                byNamespace.computeIfAbsent(ns, k -> new ArrayList<>())
                    .add(new Entry(has ? p : null, filters ? pathFilter : null));
            }
        }
    }

    public Set<String> namespaces() { return byNamespace.keySet(); }

    /** 가장 위의 리소스. filter 에 걸리면 그 아래는 보지 않는다. */
    public Optional<Resource> getResource(ResourceId id) {
        List<Entry> es = byNamespace.get(id.namespace());
        if (es == null) return Optional.empty();
        for (int i = es.size() - 1; i >= 0; i--) {
            Entry e = es.get(i);
            if (e.pack != null) {
                var r = e.pack.getResource(id);
                if (r != null) return Optional.of(new Resource(e.pack.packId(), r));
            }
            if (e.filtered(id)) return Optional.empty();
        }
        return Optional.empty();
    }

    /** 같은 id 의 모든 리소스(아래→위). */
    public List<Resource> getResourceStack(ResourceId id) {
        List<Entry> es = byNamespace.get(id.namespace());
        if (es == null) return List.of();
        List<Resource> out = new ArrayList<>();
        for (int i = es.size() - 1; i >= 0; i--) {
            Entry e = es.get(i);
            if (e.pack != null) {
                var r = e.pack.getResource(id);
                if (r != null) out.add(new Resource(e.pack.packId(), r));
            }
            if (e.filtered(id)) break;
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** dir 아래 리소스(가장 위의 것), id 순. */
    public Map<ResourceId, Resource> listResources(String dir, Predicate<ResourceId> accept) {
        checkDir(dir);
        Map<ResourceId, Resource> out = new TreeMap<>();
        for (var nsEntry : byNamespace.entrySet()) {
            String ns = nsEntry.getKey();
            Map<ResourceId, Resource> m = new HashMap<>();
            for (Entry e : nsEntry.getValue()) {
                if (e.filter != null) m.keySet().removeIf(e.filter);
                if (e.pack != null) {
                    String packId = e.pack.packId();
                    e.pack.listResources(ns, dir, (id, s) -> { if (accept.test(id)) m.put(id, new Resource(packId, s)); });
                }
            }
            out.putAll(m);
        }
        return out;
    }

    /** dir 아래 리소스 스택(각 id 의 아래→위 목록), id 순. */
    public Map<ResourceId, List<Resource>> listResourceStacks(String dir, Predicate<ResourceId> accept) {
        checkDir(dir);
        Map<ResourceId, List<Resource>> out = new TreeMap<>();
        for (var nsEntry : byNamespace.entrySet()) {
            String ns = nsEntry.getKey();
            Map<ResourceId, List<Resource>> m = new HashMap<>();
            for (Entry e : nsEntry.getValue()) {
                if (e.filter != null) for (var en : m.entrySet()) if (e.filter.test(en.getKey())) en.getValue().clear();
                if (e.pack != null) {
                    String packId = e.pack.packId();
                    e.pack.listResources(ns, dir, (id, s) -> {
                        if (accept.test(id)) m.computeIfAbsent(id, k -> new ArrayList<>()).add(new Resource(packId, s));
                    });
                }
            }
            m.forEach((k, v) -> { if (!v.isEmpty()) out.put(k, v); });
        }
        return out;
    }

    private static void checkDir(String dir) {
        if (dir.endsWith("/")) throw new IllegalArgumentException("Trailing slash in path " + dir);
    }

    @Override
    public void close() { packs.forEach(PackResources::close); }
}
