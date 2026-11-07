// 바닐라 StructureTemplateManager(구조물 블록·/place template 이 쓰는 템플릿 저장소): 이름 → 템플릿. 찾는 순서는 바닐라 sources 와 같다:
//   1) 월드 폴더의 generated/<네임스페이스>/structures/<경로>.nbt  2) 켜진 데이터 팩의 data/<네임스페이스>/structure/<경로>.nbt(위 팩이 아래 팩을 덮음)
//   3) 내장 템플릿(minecraft 이름공간, 1.21.1 jar 자료 — 데이터 팩을 아직 못 읽은 시험·도구용 예비).
// 데이터 팩 리소스는 DataPackManager 가 시작·/reload 때 installDataPacks 로 걸고, 그때마다 저장소를 비운다(바닐라 onResourceManagerReload).
// 구조물 블록 저장은 메모리 템플릿을 갈아 끼우고 generated 에 .nbt 로 쓴다.
// 차이: .snbt 는 읽지 않는다(바닐라 1.21.1 운영 서버도 gameteststructures 의 .snbt 는 IDE 에서만 읽는다).
// 옛 DataVersion 파일은 바닐라 readStructure 처럼 모든 source 에서 데이터 픽서(StructureDataFixer, Q8)를 거친다. 픽서가 못 다루는 판
// (DataVersion 없음·1452 미만·3955 초과)은 경고를 남기고 그대로 읽는다.
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
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.datafix.StructureDataFixer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TemplateManager {

    private static final Logger log = LoggerFactory.getLogger(TemplateManager.class);
    private static final String EXT = ".nbt";
    private static final String DIR = "structure";
    private static final Map<Path, TemplateManager> BY_DIR = new ConcurrentHashMap<>();
    /** 켜진 데이터 팩의 리소스(없으면 null — 시험·도구). */
    private static volatile ResourceManager dataPacks;

    /**
     * 데이터 팩 리소스를 새로 건다(시작·/reload). 모든 월드의 저장소를 비워 다음 get 이 새 팩에서 다시 읽게 한다(바닐라 onResourceManagerReload).
     * 구조물 블록이 아직 파일로 쓰지 않은 메모리 템플릿도 함께 사라진다 — 바닐라도 같다.
     */
    public static void installDataPacks(ResourceManager rm) {
        dataPacks = rm;
        for (TemplateManager m : BY_DIR.values()) m.repository.clear();
    }

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

    /** get: 저장소(캐시) → 월드 generated → 데이터 팩 → 내장 순으로. */
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

    /**
     * 바닐라 구조물 NBT 로 템플릿을 정의해 메모리 저장소에 둔다(플러그인 API 의 불러오기). 옛 DataVersion 은 파일을 읽을 때와 같이 데이터 픽서를 거친다.
     * 올바른 템플릿 모양이 아니면 IllegalArgumentException. root 는 고쳐질 수 있다.
     */
    public StructureTemplate define(ResourceId id, NbtCompound root) {
        pathOf(id);   // 이름 검사(올바르지 않으면 IllegalArgumentException)
        StructureTemplate t;
        try {
            if (!root.contains("size") || !root.contains("blocks") || !(root.contains("palette") || root.contains("palettes"))) {
                throw new IllegalArgumentException("구조물 NBT 에 size·blocks·palette 가 없다");
            }
            t = read(id, root, "plugin");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("구조물 NBT 를 읽을 수 없다: " + e, e);
        }
        repository.put(id, Optional.of(t));
        return t;
    }

    /** 월드 generated/ 의 파일을 지운다(저장소 캐시는 그대로). 지웠으면 true. */
    public boolean deleteGenerated(ResourceId id) {
        Path file = pathOf(id);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete structure {}", id, e);
            return false;
        }
    }

    /** 메모리 저장소에 (있다고 기억된) 템플릿이 있는가 — 없음을 기억한 항목은 거짓. */
    public boolean hasInMemory(ResourceId id) {
        Optional<StructureTemplate> t = repository.get(id);
        return t != null && t.isPresent();
    }

    /** remove: 캐시에서만 뺀다(파일은 그대로 — 다음 get 이 다시 읽는다). */
    public void remove(ResourceId id) { repository.remove(id); }

    private Optional<StructureTemplate> tryLoad(ResourceId id) {
        Optional<StructureTemplate> t = loadFromGenerated(id);
        if (t.isEmpty()) t = loadFromDataPack(id);
        return t.isPresent() ? t : loadBuiltin(id);
    }

    /** 바닐라 loadFromResource: data/<ns>/structure/<path>.nbt. 가장 위의 팩 것을 쓴다. 읽기 실패는 로그만 남기고 없음(다음 source 로 넘어가지 않는다 — 바닐라와 같다). */
    private Optional<StructureTemplate> loadFromDataPack(ResourceId id) {
        ResourceManager rm = dataPacks;
        if (rm == null) return Optional.empty();
        Optional<ResourceManager.Resource> res = rm.getResource(id.withPath(DIR + "/" + id.path() + EXT));
        if (res.isEmpty()) return Optional.empty();
        try (InputStream in = res.get().open().get()) {
            return Optional.of(read(id, NbtIo.readGzip(in), "pack " + res.get().packId()));
        } catch (IOException | RuntimeException e) {
            log.error("Couldn't load structure {} from pack {}", id, res.get().packId(), e);
            return Optional.empty();
        }
    }

    /** 바닐라 NbtUtils.getDataVersion(tag, 500): DataVersion 이 없으면 500. */
    static final int MISSING_DATA_VERSION = 500;

    /**
     * 바닐라 readStructure: DataVersion 을 보고 데이터 픽서로 올린 뒤 읽는다. 픽서가 다루지 못하는 판이면 경고 후 그대로 읽는다
     * (구조물마다 한 번 — 저장소가 캐시한다).
     */
    private StructureTemplate read(ResourceId id, NbtCompound root, String source) {
        int v = root.containsNumber("DataVersion") ? root.getInt("DataVersion") : MISSING_DATA_VERSION;
        if (!StructureDataFixer.update(root, v)) {
            if (v > StructureDataFixer.CURRENT_VERSION) {
                log.warn("Structure {} ({}) has DataVersion {} newer than this server ({}); read as is", id, source, v, StructureDataFixer.CURRENT_VERSION);
            } else {
                log.warn("Structure {} ({}) has DataVersion {} older than {}; no data fixer for it, read as is",
                    id, source, root.containsNumber("DataVersion") ? String.valueOf(v) : "(none)", StructureDataFixer.MIN_SUPPORTED_VERSION);
            }
        }
        return StructureTemplate.fromNbt(registry, root);
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
            return Optional.of(read(id, NbtIo.readGzip(in), "world generated"));
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
            return Optional.of(read(id, NbtIo.readGzip(in), "builtin"));
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
        Path root = generatedDir.resolve(id.namespace()).resolve("structures").normalize();
        Path file = root.resolve(id.path() + EXT).normalize();
        // 바닐라: 경로가 generated/<ns>/structures 안이어야 한다("a:../../b/structures/c" 로 다른 네임스페이스 폴더에 쓰지 못하게)
        if (!file.startsWith(generatedDir) || !file.startsWith(root) || file.equals(root)) throw new IllegalArgumentException("Invalid resource path: " + file);
        return file;
    }

    /** 바닐라 listTemplates: 월드 generated 와 켜진 데이터 팩의 템플릿 이름(정렬, 중복 없음). /place template 제안이 쓴다. */
    public List<ResourceId> listTemplates() {
        TreeSet<ResourceId> out = new TreeSet<>(listGenerated());
        out.addAll(listDataPack());
        return new ArrayList<>(out);
    }

    /** 켜진 데이터 팩의 data/&lt;ns&gt;/structure/**.nbt 이름. */
    public static List<ResourceId> listDataPack() {
        ResourceManager rm = dataPacks;
        if (rm == null) return List.of();
        TreeSet<ResourceId> out = new TreeSet<>();
        String prefix = DIR + "/";
        for (ResourceId f : rm.listResources(DIR, p -> p.path().endsWith(EXT)).keySet()) {
            ResourceId id = ResourceId.tryBuild(f.namespace(), f.path().substring(prefix.length(), f.path().length() - EXT.length()));
            if (id != null) out.add(id);
        }
        return new ArrayList<>(out);
    }

    /** 월드 generated/ 의 템플릿 이름만(구조물 블록 저장 결과). */
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
