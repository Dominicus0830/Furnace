// 바닐라 StoredUserList 이식 — 키(UUID 문자열·IP)→항목 HashMap 을 그대로 써서 저장 순서까지 바닐라와 같다.
// Gson pretty-print 배열로 저장하고, 값이 바뀔 때마다 즉시 파일에 쓴다. 여러 스레드(네트워크·명령)에서 쓰이므로 동기화한다.
package org.domi.furnace.server.players;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {

    private static final Logger log = LoggerFactory.getLogger(StoredUserList.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, V> map = new HashMap<>();

    protected StoredUserList(Path file) {
        this.file = file;
    }

    public Path getFile() { return file; }

    public synchronized void add(V entry) {
        map.put(getKeyForUser(entry.getUser()), entry);
        try {
            save();
        } catch (IOException e) {
            log.warn("Could not save the list after adding a user.", e);
        }
    }

    public synchronized V get(K user) {
        removeExpired();
        return map.get(getKeyForUser(user));
    }

    public synchronized void remove(K user) {
        map.remove(getKeyForUser(user));
        try {
            save();
        } catch (IOException e) {
            log.warn("Could not save the list after removing a user.", e);
        }
    }

    public void remove(StoredUserEntry<K> entry) { remove(entry.getUser()); }

    public synchronized String[] getUserList() { return map.keySet().toArray(new String[0]); }

    public synchronized boolean isEmpty() { return map.isEmpty(); }

    protected String getKeyForUser(K user) { return user.toString(); }

    protected synchronized boolean contains(K user) { return map.containsKey(getKeyForUser(user)); }

    private void removeExpired() {
        List<K> expired = new ArrayList<>();
        for (V v : map.values()) if (v.hasExpired()) expired.add(v.getUser());
        for (K k : expired) map.remove(getKeyForUser(k));
    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject json);

    /** 항목 사본(순서 = HashMap 순회). */
    public synchronized Collection<V> getEntries() { return List.copyOf(map.values()); }

    public synchronized void save() throws IOException {
        JsonArray arr = new JsonArray();
        for (V v : map.values()) {
            JsonObject o = new JsonObject();
            v.serialize(o);
            arr.add(o);
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(arr, GSON.newJsonWriter(w));
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void load() throws IOException {
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            map.clear();
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    throw new JsonSyntaxException("Expected entry to be a JsonObject, was " + typeName(el));
                }
                StoredUserEntry<K> e = createEntry(el.getAsJsonObject());
                if (e.getUser() != null) map.put(getKeyForUser(e.getUser()), (V) e);
            }
        }
    }

    /** GsonHelper.getType. */
    static String typeName(JsonElement el) {
        String s = String.valueOf(el);
        if (s.length() > 10) s = s.substring(0, 10) + "...";
        if (el == null) return "null (missing)";
        if (el.isJsonNull()) return "null (json)";
        if (el.isJsonArray()) return "an array (" + s + ")";
        if (el.isJsonObject()) return "an object (" + s + ")";
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isNumber()) return "a number (" + s + ")";
            if (p.isBoolean()) return "a boolean (" + s + ")";
        }
        return s;
    }
}
