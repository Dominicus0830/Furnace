// 바닐라 GameProfileCache 이식 (usercache.json). 이름(소문자)·UUID 두 색인, 항목은 한 달 뒤 만료, 저장은 최근 사용 순 최대 1000개를
// 압축 JSON 한 줄로 쓴다. 조회 실패 시 인증 서버를 쓰지 않는(오프라인) 서버면 오프라인 프로필을 만든다.
package org.domi.furnace.server.players;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GameProfileCache {

    private static final Logger log = LoggerFactory.getLogger(GameProfileCache.class);
    private static final int GAMEPROFILES_MRU_LIMIT = 1000;
    private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;

    private final Map<String, Entry> profilesByName = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> profilesByUuid = new ConcurrentHashMap<>();
    private final ProfileLookup lookup;
    private final Gson gson = new GsonBuilder().create();
    private final Path file;
    private final AtomicLong operationCount = new AtomicLong();
    private volatile boolean usesAuthentication;

    public GameProfileCache(ProfileLookup lookup, Path file) {
        this.lookup = lookup;
        this.file = file;
        List<Entry> loaded = load();
        Collections.reverse(loaded);
        loaded.forEach(this::safeAdd);
    }

    /** 온라인 모드면 참: 조회 실패 시 오프라인 프로필을 만들지 않는다. */
    public void setUsesAuthentication(boolean v) { usesAuthentication = v; }

    private void safeAdd(Entry e) {
        e.setLastAccess(nextOperation());
        profilesByName.put(e.profile.name().toLowerCase(Locale.ROOT), e);
        profilesByUuid.put(e.profile.id(), e);
    }

    private Optional<GameProfile> lookupGameProfile(String name) {
        if (!GameProfile.isValidPlayerName(name)) return createUnknownProfile(name);
        Optional<GameProfile> p = lookup.findProfileByName(name);
        return p.isPresent() ? p : createUnknownProfile(name);
    }

    private Optional<GameProfile> createUnknownProfile(String name) {
        return usesAuthentication ? Optional.empty() : Optional.of(GameProfile.offline(name));
    }

    public void add(GameProfile profile) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.MONTH, GAMEPROFILES_EXPIRATION_MONTHS);
        safeAdd(new Entry(profile, c.getTime()));
        save();
    }

    private long nextOperation() { return operationCount.incrementAndGet(); }

    public Optional<GameProfile> get(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Entry e = profilesByName.get(key);
        boolean dirty = false;
        if (e != null && new Date().getTime() >= e.expires.getTime()) {
            profilesByUuid.remove(e.profile.id());
            profilesByName.remove(e.profile.name().toLowerCase(Locale.ROOT));
            dirty = true;
            e = null;
        }
        Optional<GameProfile> result;
        if (e != null) {
            e.setLastAccess(nextOperation());
            result = Optional.of(e.profile);
        } else {
            result = lookupGameProfile(key);
            if (result.isPresent()) {
                add(result.get());
                dirty = false;
            }
        }
        if (dirty) save();
        return result;
    }

    /** 캐시에 있는 이름만 본다(없거나 만료면 비어 있음). 조회·오프라인 프로필 생성·저장을 하지 않는다. */
    public Optional<GameProfile> peek(String name) {
        Entry e = profilesByName.get(name.toLowerCase(Locale.ROOT));
        if (e == null || new Date().getTime() >= e.expires.getTime()) return Optional.empty();
        return Optional.of(e.profile);
    }

    public Optional<GameProfile> get(UUID id) {
        Entry e = profilesByUuid.get(id);
        if (e == null) return Optional.empty();
        e.setLastAccess(nextOperation());
        return Optional.of(e.profile);
    }

    private static DateFormat createDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    }

    List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray arr = gson.fromJson(r, JsonArray.class);
            if (arr == null) return out;
            DateFormat df = createDateFormat();
            for (JsonElement el : arr) readEntry(el, df).ifPresent(out::add);
        } catch (NoSuchFileException ignored) {
        } catch (JsonParseException | IOException e) {
            log.warn("Failed to load profile cache {}", file, e);
        }
        return out;
    }

    public synchronized void save() {
        JsonArray arr = new JsonArray();
        DateFormat df = createDateFormat();
        getTopMRUProfiles(GAMEPROFILES_MRU_LIMIT).forEach(e -> arr.add(writeEntry(e, df)));
        String json = gson.toJson(arr);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(json);
        } catch (IOException ignored) {
        }
    }

    private List<Entry> getTopMRUProfiles(int limit) {
        return List.copyOf(profilesByUuid.values()).stream()
                .sorted(Comparator.comparingLong(Entry::lastAccess).reversed()).limit(limit).toList();
    }

    private static JsonElement writeEntry(Entry e, DateFormat df) {
        JsonObject o = new JsonObject();
        o.addProperty("name", e.profile.name());
        o.addProperty("uuid", e.profile.id().toString());
        o.addProperty("expiresOn", df.format(e.expires));
        return o;
    }

    private static Optional<Entry> readEntry(JsonElement el, DateFormat df) {
        if (!el.isJsonObject()) return Optional.empty();
        JsonObject o = el.getAsJsonObject();
        JsonElement name = o.get("name"), uuid = o.get("uuid"), expires = o.get("expiresOn");
        if (name == null || uuid == null) return Optional.empty();
        String u = uuid.getAsString(), n = name.getAsString();
        Date d = null;
        if (expires != null) {
            try {
                d = df.parse(expires.getAsString());
            } catch (ParseException ignored) {
            }
        }
        if (n == null || u == null || d == null) return Optional.empty();
        UUID id;
        try {
            id = UUID.fromString(u);
        } catch (Throwable t) {
            return Optional.empty();
        }
        return Optional.of(new Entry(new GameProfile(id, n), d));
    }

    static final class Entry {
        final GameProfile profile;
        final Date expires;
        private volatile long lastAccess;

        Entry(GameProfile profile, Date expires) {
            this.profile = profile;
            this.expires = expires;
        }

        void setLastAccess(long v) { lastAccess = v; }

        long lastAccess() { return lastAccess; }
    }
}
