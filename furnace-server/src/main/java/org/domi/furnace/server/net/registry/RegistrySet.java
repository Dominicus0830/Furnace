// configuration 단계에서 보낼 동기화 레지스트리별 엔트리 id 목록을 리소스에서 로드/조회
package org.domi.furnace.server.net.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegistrySet {

    // 삽입 순서 유지 — 레지스트리 순서/엔트리 순서가 곧 클라가 받는 인덱스가 된다.
    private final Map<String, List<String>> registries;

    private RegistrySet(Map<String, List<String>> registries) {
        this.registries = registries;
    }

    public static RegistrySet load(String resourcePath) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        ClassLoader cl = RegistrySet.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("레지스트리 리소스 없음: " + resourcePath);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int sp = line.indexOf(' ');
                    if (sp < 0) continue;
                    String registry = line.substring(0, sp);
                    String entry = line.substring(sp + 1).strip();
                    map.computeIfAbsent(registry, k -> new ArrayList<>()).add(entry);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new RegistrySet(map);
    }

    /** 모든 레지스트리 키(삽입 순서). */
    public List<String> registryKeys() {
        return new ArrayList<>(registries.keySet());
    }

    /** 주어진 레지스트리의 엔트리 id 목록(없으면 빈 목록). */
    public List<String> entries(String registry) {
        return registries.getOrDefault(registry, List.of());
    }

    /** registry 안 entry의 0-기반 인덱스(없으면 -1). */
    public int indexOf(String registry, String entry) {
        return entries(registry).indexOf(entry);
    }


}
