// 바닐라 StructureTemplateManager(구조물 블록이 쓰는 템플릿 저장소): 이름 → 템플릿. 월드 폴더의 generated/<네임스페이스>/structures/<경로>.nbt 를 먼저 찾고,
// 없으면 내장 템플릿(minecraft 이름공간, 1.21.1 jar 자료)을 쓴다. 구조물 블록 저장은 메모리 템플릿을 갈아 끼우고 .nbt 로 쓴다.
// 차이: 데이터 팩의 structure/*.nbt 와 .snbt 는 읽지 않는다(Furnace 에 데이터 팩 템플릿 경로가 아직 없다). 옛 DataVersion 파일은 데이터 픽서를 거치지 않는다.
package org.domi.furnace.server.world.gen.structure.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TemplateManager {

    private static final Logger log = LoggerFactory.getLogger(TemplateManager.class);
    private static final String EXT = ".nbt";
    private static final Map<Path, TemplateManager> BY_DIR = new ConcurrentHashMap<>();

    /** 월드 폴더(차원이 공유하는 루트)당 하나. */
    public static TemplateManager forWorld(Path worldDir, BlockRegistry registry) {
        Path key = worldDir.toAbsolutePath().normalize();
        TemplateManager m = BY_DIR.computeIfAbsent(key, k -> new TemplateManager(k.resolve("generated"), registry));
        return m;
    }

    private final Path generatedDir;
    private final BlockRegistry registry;
    private final BlockStateTable table;
    /** 바닐라 structureRepository: 없음도 기억한다(Optional.empty). */
    private final Map<ResourceId, Optional<StructureTemplate>> repository = new ConcurrentHashMap<>();

    public TemplateManager(Path generatedDir, BlockRegistry registry) {
        this.generatedDir = generatedDir.toAbsolutePath().normalize();
        this.registry = registry;
        this.table = BlockStateTable.of(registry);
    }

    public BlockRegistry registry() { return registry; }

    public BlockStateTable table() { return table; }

    /** get: 저장소(캐시) → 월드 generated → 내장 순으로. */
    public Optional<StructureTemplate> get(ResourceId id) {
        return repository.computeIfAbsent(id, this::tryLoad);
    }

    /** getOrCreate: 없으면 빈 템플릿을 만들어 저장소에 둔다. */
    public StructureTemplate getOrCreate(ResourceId id) {
        Optional<StructureTemplate> t = get(id);
        if (t.isPresent()) return t.get();
        StructureTemplate created = StructureTemplate.empty();
        repository.put(id, Optional.of(created));
        return created;
    }

    /** 템플릿을 메모리 저장소에 둔다(구조물 블록 저장이 fillFromWorld 결과를 갈아 끼운다). */
    public void put(ResourceId id, StructureTemplate t) { repository.put(id, Optional.of(t)); }

    /** remove: 캐시에서만 뺀다(파일은 그대로 — 다음 get 이 다시 읽는다). */
    public void remove(ResourceId id) { repository.remove(id); }

    private Optional<StructureTemplate> tryLoad(ResourceId id) {
        Optional<StructureTemplate> t = loadFromGenerated(id);
        return t.isPresent() ? t : loadBuiltin(id);
    }

    private Optional<StructureTemplate> loadFromGenerated(ResourceId id) {
        Path file;
        try {
            file = pathOf(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) return Optional.empty();
        try (InputStream in = Files.newInputStream(file)) {
            return Optional.of(StructureTemplate.fromNbt(registry, NbtIo.readGzip(in)));
        } catch (IOException | RuntimeException e) {
            log.error("Couldn't load structure {}", id, e);
            return Optional.empty();
        }
    }

    private Optional<StructureTemplate> loadBuiltin(ResourceId id) {
        if (!"minecraft".equals(id.namespace())) return Optional.empty();
        String res = "worldgen/1.21.1/structure/" + id.path() + EXT;
        try (InputStream in = TemplateManager.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) return Optional.empty();
            return Optional.of(StructureTemplate.fromNbt(registry, NbtIo.readGzip(in)));
        } catch (IOException | RuntimeException e) {
            log.error("Couldn't load structure {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * 바닐라 save: 저장소의 템플릿을 generated 에 .nbt 로 쓴다. 저장소에 없으면 false. 경로 오류·쓰기 실패도 false.
     * 쓰기 실패는 .nbt 를 덮어쓰지 않도록 임시 파일에 쓴 뒤 옮긴다.
     */
    public boolean save(ResourceId id) {
        Optional<StructureTemplate> t = repository.get(id);
        if (t == null || t.isEmpty()) return false;
        Path file;
        try {
            file = pathOf(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
        NbtCompound root = t.get().toNbt(table);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                NbtIo.writeGzip(out, root, "");
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("Failed to save structure {}", id, e);
            return false;
        }
    }

    /** 바닐라 createAndValidatePathToGeneratedStructure: "//" 가 있거나 generated 밖으로 나가면 예외. */
    public Path pathOf(ResourceId id) {
        if (id.path().contains("//")) throw new IllegalArgumentException("Invalid resource path: " + id);
        Path file = generatedDir.resolve(id.namespace()).resolve("structures").resolve(id.path() + EXT).normalize();
        if (!file.startsWith(generatedDir)) throw new IllegalArgumentException("Invalid resource path: " + file);
        return file;
    }

    /** 바닐라 listGenerated + 내장 목록 — 구조물 블록 불러오기 화면과 /place template 제안이 쓴다. */
    public List<ResourceId> listGenerated() {
        TreeSet<ResourceId> out = new TreeSet<>();
        if (!Files.isDirectory(generatedDir)) return List.of();
        try (Stream<Path> nsDirs = Files.list(generatedDir)) {
            for (Path ns : (Iterable<Path>) nsDirs.filter(Files::isDirectory)::iterator) {
                Path root = ns.resolve("structures");
                if (!Files.isDirectory(root)) continue;
                try (Stream<Path> walk = Files.walk(root)) {
                    for (Path f : (Iterable<Path>) walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(EXT))::iterator) {
                        String rel = root.relativize(f).toString().replace('\\', '/');
                        ResourceId id = ResourceId.tryBuild(ns.getFileName().toString(), rel.substring(0, rel.length() - EXT.length()));
                        if (id != null) out.add(id);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Invalid location while listing generated structures", e);
        }
        return new ArrayList<>(out);
    }
}
