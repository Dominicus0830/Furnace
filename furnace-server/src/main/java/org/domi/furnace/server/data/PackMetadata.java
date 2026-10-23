// pack.mcmeta(바닐라 PackMetadataSection·FeatureFlagsMetadataSection·ResourceFilterSection·OverlayMetadataSection).
package org.domi.furnace.server.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;

public record PackMetadata(Text description, int packFormat, Optional<FormatRange> supportedFormats, Set<String> features,
                           List<FilterBlock> filter, List<Overlay> overlays) {

    /** 포함 범위 [min, max]. */
    public record FormatRange(int min, int max) {
        public boolean contains(int v) { return v >= min && v <= max; }

        @Override public String toString() { return "[" + min + ", " + max + "]"; }

        /** 정수 · [min, max] · {min_inclusive, max_inclusive}. */
        static FormatRange parse(JsonElement e) {
            if (e.isJsonPrimitive()) { int v = e.getAsInt(); return new FormatRange(v, v); }
            if (e.isJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                if (a.size() != 2) throw new IllegalArgumentException("형식 범위 배열은 두 값이어야 한다");
                return of(a.get(0).getAsInt(), a.get(1).getAsInt());
            }
            JsonObject o = e.getAsJsonObject();
            return of(o.get("min_inclusive").getAsInt(), o.get("max_inclusive").getAsInt());
        }

        static FormatRange of(int min, int max) {
            if (min > max) throw new IllegalArgumentException("min_inclusive must be less than or equal to max_inclusive");
            return new FormatRange(min, max);
        }
    }

    /** filter.block 한 줄 — namespace·path 정규식(없으면 모두). 바닐라처럼 find(부분 일치)로 본다. */
    public record FilterBlock(Predicate<String> namespace, Predicate<String> path) {}

    public record Overlay(FormatRange formats, String directory) {}

    private static final Pattern OVERLAY_DIR = Pattern.compile("[-_a-zA-Z0-9.]+");

    /** 메타데이터 없음(pack 섹션 없음)이면 null. */
    public static PackMetadata read(PackResources.IoSupplier<InputStream> in) throws IOException {
        if (in == null) return null;
        JsonObject root;
        try (InputStream s = in.get()) {
            root = JsonParser.parseReader(new InputStreamReader(s, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        return parse(root);
    }

    public static PackMetadata parse(JsonObject root) {
        if (!root.has("pack")) return null;
        JsonObject pack = root.getAsJsonObject("pack");
        Text description = JsonTextSerializer.json().deserialize(pack.get("description").toString());
        int format = pack.get("pack_format").getAsInt();
        Optional<FormatRange> supported = Optional.empty();
        if (pack.has("supported_formats")) {
            try {
                supported = Optional.of(FormatRange.parse(pack.get("supported_formats")));
            } catch (RuntimeException ignored) {
                // lenientOptionalField — 잘못되면 없는 것으로 본다
            }
        }
        Set<String> features = new LinkedHashSet<>();
        if (root.has("features")) {
            for (JsonElement f : root.getAsJsonObject("features").getAsJsonArray("enabled")) features.add(ResourceId.parse(f.getAsString()).toString());
        }
        List<FilterBlock> filter = new ArrayList<>();
        if (root.has("filter")) {
            for (JsonElement b : root.getAsJsonObject("filter").getAsJsonArray("block")) {
                JsonObject o = b.getAsJsonObject();
                filter.add(new FilterBlock(pattern(o, "namespace"), pattern(o, "path")));
            }
        }
        List<Overlay> overlays = new ArrayList<>();
        if (root.has("overlays")) {
            for (JsonElement e : root.getAsJsonObject("overlays").getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                String dir = o.get("directory").getAsString();
                if (!OVERLAY_DIR.matcher(dir).matches()) throw new IllegalArgumentException(dir + " is not accepted directory name");
                overlays.add(new Overlay(FormatRange.parse(o.get("formats")), dir));
            }
        }
        return new PackMetadata(description, format, supported, features, List.copyOf(filter), List.copyOf(overlays));
    }

    private static Predicate<String> pattern(JsonObject o, String key) {
        return o.has(key) ? Pattern.compile(o.get(key).getAsString()).asPredicate() : s -> true;
    }

    /** 바닐라 Pack.getDeclaredPackVersions — supported_formats 가 pack_format 을 포함하지 않으면 pack_format 하나로. */
    public FormatRange declaredRange(String packId, org.slf4j.Logger log) {
        if (supportedFormats.isEmpty()) return new FormatRange(packFormat, packFormat);
        FormatRange r = supportedFormats.get();
        if (!r.contains(packFormat)) {
            log.warn("Pack {} declared support for versions {} but declared main format is {}, defaulting to {}", packId, r, packFormat, packFormat);
            return new FormatRange(packFormat, packFormat);
        }
        return r;
    }

    /** 현재 형식에 맞는 오버레이 디렉터리(적힌 순서). */
    public List<String> activeOverlays(int currentFormat) {
        List<String> out = new ArrayList<>();
        for (Overlay o : overlays) if (o.formats().contains(currentFormat)) out.add(o.directory());
        return out;
    }

    /** filter 의 namespace 규칙 중 하나라도 맞는가. */
    public boolean filtersNamespace(String ns) {
        for (FilterBlock b : filter) if (b.namespace().test(ns)) return true;
        return false;
    }

    /** filter 의 path 규칙 중 하나라도 맞는가. */
    public boolean filtersPath(String path) {
        for (FilterBlock b : filter) if (b.path().test(path)) return true;
        return false;
    }
}
