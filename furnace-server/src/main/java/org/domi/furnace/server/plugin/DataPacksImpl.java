// Furnace.dataPacks(): 서버 PackRepository 보기와 켜고 끄기(Paper PaperDatapack·PaperDatapackManager 와 같은 규칙).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.domi.furnace.api.datapack.DataPacks;
import org.domi.furnace.server.data.DataPackManager;

final class DataPacksImpl implements DataPacks {

    private final PluginHost host;

    DataPacksImpl(PluginHost host) { this.host = host; }

    /** 데이터 팩이 없는 서버(시험용 코어)면 null. */
    private DataPackManager manager() { return host.core().dataPacks(); }

    private static DataPacks.Pack view(org.domi.furnace.server.data.Pack p, boolean enabled) {
        return new DataPacks.Pack(p.id(), p.title(), p.description(), p.required(), Compatibility.valueOf(p.compatibility().name()),
                java.util.Set.copyOf(p.requestedFeatures()), Source.valueOf(p.source().name()), enabled, p.chatLink(enabled));
    }

    @Override
    public void refresh() {
        var m = manager();
        if (m != null) m.repository().reload();
    }

    @Override
    public List<DataPacks.Pack> available() {
        var m = manager();
        if (m == null) return List.of();
        var repo = m.repository();
        synchronized (repo) {
            List<org.domi.furnace.server.data.Pack> selected = repo.selected();
            List<DataPacks.Pack> out = new ArrayList<>();
            for (org.domi.furnace.server.data.Pack p : repo.available()) out.add(view(p, selected.contains(p)));
            return List.copyOf(out);
        }
    }

    @Override
    public List<DataPacks.Pack> enabled() {
        var m = manager();
        if (m == null) return List.of();
        return m.repository().selected().stream().map(p -> view(p, true)).toList();
    }

    @Override
    public Optional<DataPacks.Pack> find(String id) {
        java.util.Objects.requireNonNull(id, "id");
        var m = manager();
        if (m == null) return Optional.empty();
        var repo = m.repository();
        synchronized (repo) {
            org.domi.furnace.server.data.Pack p = repo.pack(id);
            return p == null ? Optional.empty() : Optional.of(view(p, repo.selected().contains(p)));
        }
    }

    @Override
    public CompletableFuture<Void> setEnabled(String id, boolean enabled) {
        var m = manager();
        if (m == null) throw new UnsupportedOperationException("데이터 팩이 없는 서버");
        List<org.domi.furnace.server.data.Pack> list;
        var repo = m.repository();
        synchronized (repo) {
            org.domi.furnace.server.data.Pack p = repo.pack(id);
            if (p == null) throw new IllegalStateException("Cannot toggle state of pack that doesn't exist: " + id);
            list = new ArrayList<>(repo.selected());
            if (enabled == list.contains(p)) return CompletableFuture.completedFuture(null);
            if (enabled) org.domi.furnace.server.data.Pack.insert(list, p, false);   // /datapack enable 기본 자리
            else list.remove(p);
        }
        var done = m.reload(list.stream().map(org.domi.furnace.server.data.Pack::id).toList(), ForkJoinPool.commonPool());
        // /reload 처럼 실패는 경고 한 줄(Paper setEnabled 는 결과를 버린다 — future 는 그대로 실패로 끝난다)
        done.exceptionally(e -> {
            org.slf4j.LoggerFactory.getLogger("Furnace").warn("Failed to execute reload", e);
            return null;
        });
        return done;
    }
}
