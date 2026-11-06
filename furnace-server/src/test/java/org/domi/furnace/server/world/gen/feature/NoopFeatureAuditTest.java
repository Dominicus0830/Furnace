// configured_feature 전체를 스캔해 BiomeFeatureData.parseConfigured가 NoopFeature로 폴백하는 루트 타입이
// 알려진 허용목록(KNOWN_UNIMPLEMENTED_TYPES) 밖으로 새로 생기지 않는지 감시한다.
// (worldgen-gap-survey task에서 조사한 17종 미구현 타입 — coral_tree/huge_red_mushroom/monster_room 등)
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.junit.jupiter.api.Test;

class NoopFeatureAuditTest {

    private static final String DIR = "worldgen/1.21.1/worldgen/configured_feature";

    // survey(tasks/worldgen-gap-survey) 확인된 미구현 루트 타입. 이 목록 밖의 새 폴백은 fail.
    // 2026-11-02: bonus_chest·void_start_platform 이식으로 루트 타입 미구현은 없다(빈 목록).
    private static final Set<String> KNOWN_UNIMPLEMENTED_TYPES = Set.of();

    @Test
    void allConfiguredFeatureFiles_fallbackTypesAreAllowlisted() throws Exception {
        List<String> ids = listConfiguredFeatureIds();
        assertFalse(ids.isEmpty(), "configured_feature 디렉토리에서 파일을 찾지 못함: " + DIR);

        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        Map<String, String> newFallbacks = new TreeMap<>(); // id -> type, 리포트용(허용목록 밖)
        Map<String, String> knownFallbacks = new TreeMap<>(); // id -> type, 리포트용(허용목록 안)

        for (String id : ids) {
            JsonObject json = data.configuredFeature(id);
            String type = json.get("type").getAsString();
            if (BiomeFeatureData.HANDLED_CONFIGURED_FEATURE_TYPES.contains(type)) continue;
            if (KNOWN_UNIMPLEMENTED_TYPES.contains(type)) {
                knownFallbacks.put(id, type);
            } else {
                newFallbacks.put(id, type);
            }
        }

        System.out.println("NoopFeature 폴백 목록 (허용목록 안, " + knownFallbacks.size() + "건):");
        knownFallbacks.forEach((id, type) -> System.out.println("  " + id + " (" + type + ")"));
        System.out.println("NoopFeature 폴백 목록 (허용목록 밖, " + newFallbacks.size() + "건):");
        newFallbacks.forEach((id, type) -> System.out.println("  " + id + " (" + type + ")"));

        assertTrue(newFallbacks.isEmpty(),
            "허용목록 밖의 새 NoopFeature 폴백 발견 — parseConfigured에 구현을 추가하거나, 의도된 것이면 " +
            "NoopFeatureAuditTest.KNOWN_UNIMPLEMENTED_TYPES에 타입을 추가할 것: " + newFallbacks);
    }

    /** 구현이 들어간 타입은 허용목록에서 빼야 한다 — 남아 있으면 목록이 "아직 미구현"이라는 거짓 정보가 된다. */
    @Test
    void allowlist_hasNoImplementedTypes() {
        Set<String> stale = new java.util.TreeSet<>(KNOWN_UNIMPLEMENTED_TYPES);
        stale.retainAll(BiomeFeatureData.HANDLED_CONFIGURED_FEATURE_TYPES);
        assertTrue(stale.isEmpty(), "구현됐는데 허용목록에 남은 타입: " + stale);
    }

    /** configured_feature/**.json 전체를 minecraft:<상대경로(확장자 제거)> id로 나열한다. */
    private static List<String> listConfiguredFeatureIds() throws Exception {
        URL dirUrl = NoopFeatureAuditTest.class.getClassLoader().getResource(DIR);
        assertNotNull(dirUrl, "classpath에서 " + DIR + " 를 찾지 못함");
        URI uri = dirUrl.toURI();
        List<String> ids = new ArrayList<>();
        if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                collectIds(fs.getPath(DIR), ids);
            }
        } else {
            collectIds(Path.of(uri), ids);
        }
        return ids;
    }

    private static void collectIds(Path root, List<String> ids) throws Exception {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                Path rel = root.relativize(p);
                String relStr = rel.toString().replace('\\', '/').replace(".json", "");
                ids.add("minecraft:" + relStr);
            });
        }
    }
}
