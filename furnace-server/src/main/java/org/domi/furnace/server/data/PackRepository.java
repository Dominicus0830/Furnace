// 팩 저장소(바닐라 PackRepository + ServerPacksSource + FolderRepositorySource).
//  - vanilla: 첫 실행 때 풀어 둔 바닐라 데이터(내장 메타데이터: 설명 dataPack.vanilla.description, 형식 48, 기능 vanilla), 아래쪽.
//  - 기능 팩: 바닐라 데이터의 data/minecraft/datapacks/<이름>(bundle·trade_rebalance), id = 이름, 위쪽, 새로 보여도 자동으로 켜지지 않음.
//  - 월드 팩: <월드>/datapacks/ 의 폴더(pack.mcmeta 있음)·.zip, id = "file/<파일 이름>", 위쪽.
//  - 플러그인 팩(Furnace): 플러그인이 데이터 팩 찾기 훅으로 알린 jar 안 폴더, id = "plugin/<namespace>/<id>", 위쪽, 월드 팩처럼 자동으로 켜짐.
package org.domi.furnace.server.data;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.zip.ZipFile;
import org.domi.furnace.api.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PackRepository {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 1.21.1 데이터 팩 형식(version.json pack_version.data). */
    public static final int DATA_PACK_FORMAT = 48;
    public static final String VANILLA = "vanilla";

    private final Path vanillaRoot;   // null 이면 vanilla 팩 없음(테스트)
    private final Path worldPacks;    // null 이면 월드 팩 없음
    /** 찾을 때마다 부르는 플러그인 팩 목록(없으면 null). */
    private volatile java.util.function.Supplier<List<JarPack>> pluginPacks;

    /** jar(zip) 안 폴더 하나로 된 팩. prefix 는 "" 이거나 '/' 로 끝난다. */
    public record JarPack(String id, Text title, Path jar, String prefix) {}
    private Map<String, Pack> available = Map.of();
    private List<Pack> selected = List.of();

    public PackRepository(Path vanillaRoot, Path worldPacks) {
        this.vanillaRoot = vanillaRoot;
        this.worldPacks = worldPacks;
    }

    /** 플러그인 팩 공급자를 단다(다음 reload 부터 반영). */
    public void pluginPacks(java.util.function.Supplier<List<JarPack>> supplier) { this.pluginPacks = supplier; }

    /** 다시 찾는다(선택은 id 로 유지). */
    public synchronized void reload() {
        List<String> keep = selected.stream().map(Pack::id).toList();
        available = discover();
        selected = rebuildSelected(keep);
    }

    public synchronized void setSelected(Collection<String> ids) { selected = rebuildSelected(ids); }

    public synchronized boolean addPack(String id) {
        Pack p = available.get(id);
        if (p == null || selected.contains(p)) return false;
        List<Pack> l = new ArrayList<>(selected);
        l.add(p);
        selected = List.copyOf(l);
        return true;
    }

    public synchronized boolean removePack(String id) {
        Pack p = available.get(id);
        if (p == null || !selected.contains(p)) return false;
        List<Pack> l = new ArrayList<>(selected);
        l.remove(p);
        selected = List.copyOf(l);
        return true;
    }

    public synchronized Collection<String> availableIds() { return available.keySet(); }

    public synchronized Collection<Pack> available() { return available.values(); }

    public synchronized List<Pack> selected() { return selected; }

    public synchronized List<String> selectedIds() { return selected.stream().map(Pack::id).toList(); }

    public synchronized Pack pack(String id) { return available.get(id); }

    public synchronized boolean isAvailable(String id) { return available.containsKey(id); }

    /** 켜진 팩들이 요구하는 기능 플래그의 합. */
    public synchronized Set<String> requestedFeatures() {
        Set<String> s = new LinkedHashSet<>();
        for (Pack p : selected) s.addAll(p.requestedFeatures());
        return s;
    }

    /** 켜진 팩들의 메타데이터(아래→위). */
    public synchronized List<PackMetadata> selectedMetadata() { return selected.stream().map(Pack::metadata).toList(); }

    /** 켜진 팩들로 리소스 매니저를 연다(호출자가 닫는다). */
    public synchronized ResourceManager openResourceManager() { return new ResourceManager(openSelected(), selectedMetadata()); }

    /** 켜진 팩들을 아래→위 순서로 연다(호출자가 닫는다). */
    public synchronized List<PackResources> openSelected() { return selected.stream().map(Pack::open).toList(); }

    private List<Pack> rebuildSelected(Collection<String> ids) {
        List<Pack> l = new ArrayList<>();
        for (String id : ids) {
            Pack p = available.get(id);
            if (p != null && !l.contains(p)) l.add(p);
        }
        for (Pack p : available.values()) if (p.required() && !l.contains(p)) Pack.insert(l, p, false);
        return List.copyOf(l);
    }

    // ------------------------------------------------------------------ 찾기

    private Map<String, Pack> discover() {
        Map<String, Pack> out = new TreeMap<>();
        if (vanillaRoot != null) {
            Pack v = vanillaPack();
            if (v != null) out.put(v.id(), v);
            Path features = vanillaRoot.resolve("data/minecraft/datapacks");
            discoverFolder(features, name -> name, Pack.Source.FEATURE, out);
        }
        if (worldPacks != null) {
            try {
                Files.createDirectories(worldPacks);
            } catch (IOException e) {
                log.warn("Failed to list packs in {}", worldPacks, e);
            }
            discoverFolder(worldPacks, name -> "file/" + name, Pack.Source.WORLD, out);
        }
        var plugins = pluginPacks;
        if (plugins != null) {
            for (JarPack jp : plugins.get()) {
                Pack pack = create(jp.id(), jp.title(), Pack.Source.PLUGIN, overlays -> openZip(jp.id(), jp.jar(), jp.prefix(), overlays));
                if (pack != null && out.putIfAbsent(jp.id(), pack) != null) log.warn("Duplicate pack id {}, ignoring", jp.id());
            }
        }
        return out;
    }

    private Pack vanillaPack() {
        PackMetadata meta = new PackMetadata(Text.translatable("dataPack.vanilla.description"), DATA_PACK_FORMAT,
            java.util.Optional.empty(), Set.of(FeatureFlags.VANILLA), List.of(), List.of());
        Path root = vanillaRoot;
        return new Pack(VANILLA, Text.translatable("dataPack.vanilla.name"), Pack.Source.BUILT_IN, meta,
            Pack.Compatibility.COMPATIBLE, false, Pack.Position.BOTTOM, false, overlays -> new PackResources.PathPack(VANILLA, root));
    }

    /** 폴더 안의 팩들(바닐라 FolderRepositorySource.discoverPacks). */
    private void discoverFolder(Path dir, Function<String, String> idOf, Pack.Source source, Map<String, Pack> out) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
            for (Path p : s) {
                String fileName = p.getFileName().toString();
                Function<List<String>, PackResources> opener;
                if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("pack.mcmeta"))) {
                    String id = idOf.apply(fileName);
                    opener = overlays -> openFolder(id, p, overlays);
                } else if (Files.isRegularFile(p) && fileName.endsWith(".zip")) {
                    String id = idOf.apply(fileName);
                    opener = overlays -> openZip(id, p, "", overlays);
                } else {
                    log.info("Found non-pack entry '{}', ignoring", p);
                    continue;
                }
                String id = idOf.apply(fileName);
                // 기능 팩은 이름 그대로가 제목(바닐라 ServerPacksSource.getPackTitle), 월드 팩은 파일 이름.
                Pack pack = create(id, Text.text(fileName), source, opener);
                if (pack != null) out.put(id, pack);
            }
        } catch (IOException e) {
            log.warn("Failed to list packs in {}", dir, e);
        }
    }

    private static PackResources openFolder(String id, Path root, List<String> overlays) {
        PackResources main = new PackResources.PathPack(id, root);
        if (overlays.isEmpty()) return main;
        List<PackResources> o = new ArrayList<>();
        for (String d : overlays) o.add(new PackResources.PathPack(id, root.resolve(d)));
        return new PackResources.Composite(main, o);
    }

    private static PackResources openZip(String id, Path file, String prefix, List<String> overlays) {
        try {
            ZipFile zip = new ZipFile(file.toFile());
            PackResources main = new PackResources.ZipPack(id, zip, prefix);
            if (overlays.isEmpty()) return main;
            List<PackResources> o = new ArrayList<>();
            for (String d : overlays) o.add(new PackResources.ZipPack(id, zip, prefix + d + "/"));
            return new PackResources.Composite(main, o);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("팩 " + id + " 을(를) 열 수 없다", e);
        }
    }

    /** 바닐라 Pack.readPackMetadata — 메타데이터가 없거나 읽지 못하면 null(경고). */
    private static Pack create(String id, Text title, Pack.Source source, Function<List<String>, PackResources> opener) {
        try (PackResources r = opener.apply(List.of())) {
            PackMetadata meta = PackMetadata.read(r.getRootResource("pack.mcmeta"));
            if (meta == null) {
                log.warn("Missing metadata in pack {}", id);
                return null;
            }
            PackMetadata.FormatRange range = meta.declaredRange(id, log);
            Pack.Compatibility compat = Pack.Compatibility.of(range, DATA_PACK_FORMAT);
            return new Pack(id, title, source, meta, compat, false, Pack.Position.TOP, false, opener);
        } catch (Exception e) {
            log.warn("Failed to read pack {} metadata", id, e);
            return null;
        }
    }
}
