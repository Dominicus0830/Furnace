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
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class RegistrySet {

    // 삽입 순서 유지 — 레지스트리 순서/엔트리 순서가 곧 클라가 받는 인덱스가 된다.
    private final Map<String, List<String>> registries;
    // 플러그인이 더한 항목의 전송 데이터(hasData=true 로 보낼 NBT). 바닐라 항목은 클라가 알려진 팩에서 채우므로 여기 없다.
    private final Map<String, Map<String, NbtCompound>> data;

    private RegistrySet(Map<String, List<String>> registries) {
        this(registries, Map.of());
    }

    private RegistrySet(Map<String, List<String>> registries, Map<String, Map<String, NbtCompound>> data) {
        this.registries = registries;
        this.data = data;
    }

    /** 레지스트리 하나에 더하는 항목 하나. data 는 클라이언트가 받는 NBT 본문. */
    public record Extra(String registry, String entry, NbtCompound data) {}

    /**
     * 이 세트 뒤에 extras 를 레지스트리별로 같은 순서로 덧붙인 새 세트. 바닐라 항목의 인덱스는 그대로라서 기존 id(월드젠 표 등)는 유지된다.
     * 이미 있는 항목이거나 알 수 없는 레지스트리면 IllegalArgumentException.
     */
    public RegistrySet withExtras(List<Extra> extras) {
        if (extras.isEmpty()) return this;
        Map<String, List<String>> map = new LinkedHashMap<>();
        registries.forEach((k, v) -> map.put(k, new ArrayList<>(v)));
        Map<String, Map<String, NbtCompound>> extraData = new LinkedHashMap<>();
        data.forEach((k, v) -> extraData.put(k, new LinkedHashMap<>(v)));
        for (Extra e : extras) {
            List<String> list = map.get(e.registry());
            if (list == null) throw new IllegalArgumentException("알 수 없는 레지스트리: " + e.registry());
            if (list.contains(e.entry())) throw new IllegalArgumentException("이미 있는 항목: " + e.registry() + " " + e.entry());
            list.add(e.entry());
            extraData.computeIfAbsent(e.registry(), k -> new LinkedHashMap<>()).put(e.entry(), e.data());
        }
        return new RegistrySet(map, extraData);
    }

    /** 플러그인이 더한 항목의 전송 데이터(바닐라 항목이거나 없으면 null). */
    public NbtCompound data(String registry, String entry) {
        Map<String, NbtCompound> m = data.get(registry);
        return m == null ? null : m.get(entry);
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
