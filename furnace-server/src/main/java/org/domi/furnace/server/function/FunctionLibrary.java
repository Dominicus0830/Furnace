// 바닐라 ServerFunctionLibrary(alf) — data/<ns>/function/**.mcfunction 과 data/<ns>/tags/function/*.json.
// 불변 스냅샷이다: /reload 는 새 라이브러리를 만들어 통째로 바꾸고, 실행 중인 컨텍스트는 옛 함수 객체를 쥔 채 끝난다.
package org.domi.furnace.server.function;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FunctionLibrary {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    public static final String REGISTRY = "minecraft:function";
    private static final String DIR = "function";
    private static final String EXT = ".mcfunction";

    public static final FunctionLibrary EMPTY = new FunctionLibrary(Map.of(), Map.of());

    private final Map<ResourceId, CommandFunction> functions;
    private final Map<ResourceId, List<CommandFunction>> tags;

    public FunctionLibrary(Map<ResourceId, CommandFunction> functions, Map<ResourceId, List<CommandFunction>> tags) {
        this.functions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        this.tags = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    public Optional<CommandFunction> function(ResourceId id) { return Optional.ofNullable(functions.get(id)); }

    public Map<ResourceId, CommandFunction> functions() { return functions; }

    /** 바닐라 getTag: 없는 태그는 빈 목록(null 이 아니다). */
    public Collection<CommandFunction> tag(ResourceId id) { return tags.getOrDefault(id, List.of()); }

    public Iterable<ResourceId> tagIds() { return tags.keySet(); }

    /**
     * 읽고 파싱한다(어느 스레드든 — 컴파일 소스는 서버 상태를 읽지 않는다). 파싱에 실패한 함수는 로그를 남기고 그 함수만 뺀다.
     * 태그는 성공한 함수만 원소로 본다(바닐라도 함수 맵을 다 만든 뒤 태그를 푼다).
     */
    public static FunctionLibrary load(ResourceManager rm, CommandDispatcher<CommandSource> dispatcher, CommandSource compilationContext) {
        Map<ResourceId, CommandFunction> loaded = new LinkedHashMap<>();
        String prefix = DIR + "/";
        for (var e : rm.listResources(DIR, id -> id.path().endsWith(EXT)).entrySet()) {
            ResourceId file = e.getKey();
            ResourceId id = file.withPath(file.path().substring(prefix.length(), file.path().length() - EXT.length()));
            try {
                List<String> lines;
                try (InputStream in = e.getValue().open().get();
                     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    lines = r.lines().toList();
                }
                loaded.put(id, CommandFunction.fromLines(id, dispatcher, compilationContext, lines));
            } catch (Exception ex) {
                log.error("Failed to load function {}", id, ex);
            }
        }
        return new FunctionLibrary(loaded, TagLoader.loadNamed(rm, REGISTRY, loaded));
    }
}
