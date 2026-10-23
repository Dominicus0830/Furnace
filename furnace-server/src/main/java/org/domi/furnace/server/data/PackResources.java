// 팩 하나의 파일 접근(바닐라 PackResources) — 폴더·zip·오버레이 합성. data/<namespace>/<path> 만 다룬다.
package org.domi.furnace.server.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public interface PackResources extends AutoCloseable {

    @FunctionalInterface
    interface IoSupplier<T> { T get() throws IOException; }

    /** 팩 루트의 파일(pack.mcmeta 등), 없으면 null. */
    IoSupplier<InputStream> getRootResource(String name);

    /** data/<ns>/<path>, 없으면 null. */
    IoSupplier<InputStream> getResource(ResourceId id);

    /** data/<ns>/<dir>/ 아래 모든 파일. id 의 path 는 "<dir>/…". */
    void listResources(String namespace, String dir, BiConsumer<ResourceId, IoSupplier<InputStream>> out);

    Set<String> namespaces();

    String packId();

    @Override
    void close();

    // ------------------------------------------------------------------ 폴더

    final class PathPack implements PackResources {
        private final String id;
        private final Path root;

        public PathPack(String id, Path root) { this.id = id; this.root = root; }

        @Override
        public IoSupplier<InputStream> getRootResource(String name) {
            Path p = root.resolve(name);
            return Files.isRegularFile(p) ? () -> Files.newInputStream(p) : null;
        }

        @Override
        public IoSupplier<InputStream> getResource(ResourceId rid) {
            Path p = root.resolve("data").resolve(rid.namespace()).resolve(rid.path());
            return Files.isRegularFile(p) ? () -> Files.newInputStream(p) : null;
        }

        @Override
        public void listResources(String namespace, String dir, BiConsumer<ResourceId, IoSupplier<InputStream>> out) {
            Path nsRoot = root.resolve("data").resolve(namespace);
            Path base = nsRoot.resolve(dir);
            if (!Files.isDirectory(base)) return;
            try (Stream<Path> s = Files.find(base, Integer.MAX_VALUE, (p, a) -> a.isRegularFile())) {
                s.forEach(p -> {
                    String rel = nsRoot.relativize(p).toString().replace('\\', '/');
                    ResourceId rid = ResourceId.tryBuild(namespace, rel);
                    if (rid != null) out.accept(rid, () -> Files.newInputStream(p));
                });
            } catch (NoSuchFileException | NotDirectoryException e) {
                // 없음
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Set<String> namespaces() {
            Set<String> out = new HashSet<>();
            Path data = root.resolve("data");
            if (!Files.isDirectory(data)) return out;
            try (Stream<Path> s = Files.list(data)) {
                s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).filter(ResourceId::validNamespace).forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out;
        }

        @Override public String packId() { return id; }

        @Override public void close() {}
    }

    // ------------------------------------------------------------------ zip

    final class ZipPack implements PackResources {
        private final String id;
        private final ZipFile zip;
        private final String prefix;   // 오버레이면 "<dir>/", 아니면 ""
        /** namespace → (data/<ns>/ 뒤의 경로 → 항목), 경로순. */
        private final Map<String, TreeMap<String, ZipEntry>> index = new HashMap<>();

        public ZipPack(String id, ZipFile zip, String prefix) {
            this.id = id;
            this.zip = zip;
            this.prefix = prefix;
            String dataPrefix = prefix + "data/";
            var it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.startsWith(dataPrefix)) continue;
                String rest = n.substring(dataPrefix.length());
                int slash = rest.indexOf('/');
                if (slash <= 0) continue;
                String ns = rest.substring(0, slash);
                if (!ResourceId.validNamespace(ns)) continue;
                index.computeIfAbsent(ns, k -> new TreeMap<>()).put(rest.substring(slash + 1), e);
            }
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String name) {
            ZipEntry e = zip.getEntry(prefix + name);
            return e != null ? () -> zip.getInputStream(e) : null;
        }

        @Override
        public IoSupplier<InputStream> getResource(ResourceId rid) {
            TreeMap<String, ZipEntry> m = index.get(rid.namespace());
            ZipEntry e = m != null ? m.get(rid.path()) : null;
            return e != null ? () -> zip.getInputStream(e) : null;
        }

        @Override
        public void listResources(String namespace, String dir, BiConsumer<ResourceId, IoSupplier<InputStream>> out) {
            TreeMap<String, ZipEntry> m = index.get(namespace);
            if (m == null) return;
            String from = dir + "/";
            for (var en : m.tailMap(from, true).entrySet()) {
                if (!en.getKey().startsWith(from)) break;
                ResourceId rid = ResourceId.tryBuild(namespace, en.getKey());
                ZipEntry e = en.getValue();
                if (rid != null) out.accept(rid, () -> zip.getInputStream(e));
            }
        }

        @Override public Set<String> namespaces() { return new HashSet<>(index.keySet()); }

        @Override public String packId() { return id; }

        @Override
        public void close() {
            if (!prefix.isEmpty()) return;   // 오버레이는 주 팩이 닫는다
            try {
                zip.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ 오버레이 합성(바닐라 CompositePackResources)

    /** 주 팩 + 오버레이들. 오버레이는 뒤에 적힌 것이 우선, 모든 오버레이가 주 팩보다 우선. 루트 파일은 주 팩에서. */
    final class Composite implements PackResources {
        private final PackResources primary;
        private final List<PackResources> stack;   // 우선순위 높은 것부터

        public Composite(PackResources primary, List<PackResources> overlays) {
            this.primary = primary;
            List<PackResources> s = new ArrayList<>(overlays.size() + 1);
            for (int i = overlays.size() - 1; i >= 0; i--) s.add(overlays.get(i));
            s.add(primary);
            this.stack = List.copyOf(s);
        }

        @Override public IoSupplier<InputStream> getRootResource(String name) { return primary.getRootResource(name); }

        @Override
        public IoSupplier<InputStream> getResource(ResourceId rid) {
            for (PackResources p : stack) {
                IoSupplier<InputStream> r = p.getResource(rid);
                if (r != null) return r;
            }
            return null;
        }

        @Override
        public void listResources(String namespace, String dir, BiConsumer<ResourceId, IoSupplier<InputStream>> out) {
            Map<ResourceId, IoSupplier<InputStream>> m = new HashMap<>();
            for (PackResources p : stack) p.listResources(namespace, dir, m::putIfAbsent);
            m.forEach(out);
        }

        @Override
        public Set<String> namespaces() {
            Set<String> s = new HashSet<>();
            for (PackResources p : stack) s.addAll(p.namespaces());
            return s;
        }

        @Override public String packId() { return primary.packId(); }

        @Override public void close() { stack.forEach(PackResources::close); }
    }
}
