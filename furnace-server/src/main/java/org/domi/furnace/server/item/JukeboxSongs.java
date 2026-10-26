// 바닐라 JukeboxSong 레지스트리(jukebox_song): 음반별 길이(초)·비교기 출력. 데이터 팩에서 읽는다(리로드 때 다시 설치).
package org.domi.furnace.server.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.entity.ItemStack;

public final class JukeboxSongs {

    private JukeboxSongs() {}

    /** length 는 초. id 는 jukebox_song 레지스트리 번호(직접 정의한 노래는 -1). */
    public record Song(int id, float lengthInSeconds, int comparatorOutput) {

        /** JukeboxSong.lengthInTicks = ceil(초 × 20). */
        public int lengthInTicks() { return (int) Math.ceil(lengthInSeconds * 20.0F); }

        /** JukeboxSong.hasFinished(ticksSinceStarted): 길이 + 20틱이 지났다. */
        public boolean hasFinished(long ticks) { return ticks >= lengthInTicks() + 20; }
    }

    private static volatile Map<String, Song> songs = Map.of();

    /** 데이터 팩의 jukebox_song/*.json 을 읽어 설치한다. */
    public static void install(ResourceManager rm) {
        Map<String, Song> out = new HashMap<>();
        for (var e : rm.listResources("jukebox_song", id -> id.path().endsWith(".json")).entrySet()) {
            ResourceId file = e.getKey();
            String path = file.path().substring("jukebox_song/".length(), file.path().length() - ".json".length());
            String name = file.namespace() + ":" + path;
            try (InputStream in = e.getValue().open().get()) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                out.put(name, new Song(registryId(name), o.get("length_in_seconds").getAsFloat(), o.get("comparator_output").getAsInt()));
            } catch (Exception ex) {
                // 읽지 못한 노래는 없는 것으로(길이를 모르면 수동 정지만)
            }
        }
        songs = Map.copyOf(out);
    }

    /** 시험·도구용: 직접 설치. */
    public static void put(String name, float lengthInSeconds, int comparatorOutput) {
        Map<String, Song> m = new HashMap<>(songs);
        m.put(name, new Song(registryId(name), lengthInSeconds, comparatorOutput));
        songs = Map.copyOf(m);
    }

    private static int registryId(String name) {
        try {
            Integer id = ItemContext.id("minecraft:jukebox_song", name);
            return id == null ? -1 : id;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** JukeboxSong.fromStack: jukebox_playable 컴포넌트의 노래(키면 레지스트리에서, 직접 정의면 그 값). 없으면 null. */
    public static Song of(ItemStack stack) {
        if (!(ItemTools.component(stack, "minecraft:jukebox_playable") instanceof Map<?, ?> m)) return null;
        Object s = m.get("song");
        if (s instanceof String name) return songs.get(name.indexOf(':') < 0 ? "minecraft:" + name : name);
        if (s instanceof Map<?, ?> d && d.get("length_in_seconds") instanceof Number len && d.get("comparator_output") instanceof Number c) {
            return new Song(-1, len.floatValue(), c.intValue());
        }
        return null;
    }
}
