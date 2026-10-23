// ops/whitelist/banned-*.json·usercache.json 이 바닐라와 같은 JSON 형식·순서로 저장되고 다시 읽히는지.
package org.domi.furnace.server.players;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoredUserListTest {

    private static final GameProfile ALEX = new GameProfile(UUID.fromString("6ab43178-89fd-4905-97f6-0f67d9d76fd9"), "Alex");
    private static final GameProfile STEVE = new GameProfile(UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"), "Steve");

    @Test
    void emptyListsWriteBrackets(@TempDir Path dir) throws Exception {
        UserWhiteList w = new UserWhiteList(dir.resolve("whitelist.json"));
        w.save();
        assertEquals("[]", Files.readString(w.getFile()));
    }

    @Test
    void opsFormat(@TempDir Path dir) throws Exception {
        ServerOpList ops = new ServerOpList(dir.resolve("ops.json"));
        ops.add(new ServerOpListEntry(ALEX, 4, false));
        assertEquals("""
                [
                  {
                    "uuid": "6ab43178-89fd-4905-97f6-0f67d9d76fd9",
                    "name": "Alex",
                    "level": 4,
                    "bypassesPlayerLimit": false
                  }
                ]""", Files.readString(ops.getFile()));
        ServerOpList again = new ServerOpList(ops.getFile());
        again.load();
        assertTrue(again.isListed(ALEX));
        assertEquals(4, again.get(ALEX).getLevel());
        assertFalse(again.isListed(STEVE));
    }

    @Test
    void orderFollowsJavaHashMapOfKeys(@TempDir Path dir) throws Exception {
        UserWhiteList w = new UserWhiteList(dir.resolve("whitelist.json"));
        w.add(new UserWhiteListEntry(ALEX));
        w.add(new UserWhiteListEntry(STEVE));
        java.util.HashMap<String, String> ref = new java.util.HashMap<>();
        ref.put(ALEX.id().toString(), "Alex");
        ref.put(STEVE.id().toString(), "Steve");
        assertArrayEquals(ref.values().toArray(new String[0]), w.getUserList());
    }

    @Test
    void banEntryFormatAndExpiry(@TempDir Path dir) throws Exception {
        UserBanList bans = new UserBanList(dir.resolve("banned-players.json"));
        Date created = new Date(1_700_000_000_000L);
        bans.add(new UserBanListEntry(ALEX, created, "Server", null, "griefing"));
        bans.add(new UserBanListEntry(STEVE, created, null, new Date(System.currentTimeMillis() - 1000), null));
        String json = Files.readString(bans.getFile());
        assertTrue(json.contains("\"created\": \"" + BanListEntry.formatDate(created) + "\""), json);
        assertTrue(json.contains("\"expires\": \"forever\""));
        assertTrue(json.contains("\"reason\": \"Banned by an operator.\""));
        assertTrue(json.contains("\"source\": \"(Unknown)\""));
        assertNotNull(bans.get(ALEX));
        assertNull(bans.get(STEVE), "기한 지난 밴은 조회 시 제거");
        UserBanList again = new UserBanList(bans.getFile());
        again.load();
        assertEquals("griefing", again.get(ALEX).getReason());
        assertEquals(created, again.get(ALEX).getCreated());
    }

    @Test
    void ipFromAddress() {
        assertEquals("127.0.0.1", IpBanList.getIpFromAddress(new java.net.InetSocketAddress("127.0.0.1", 25565)));
    }

    @Test
    void invalidEntriesSkipped(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("whitelist.json");
        Files.writeString(f, "[{\"uuid\":\"nope\",\"name\":\"x\"},{\"name\":\"y\"},{\"uuid\":\"" + ALEX.id() + "\",\"name\":\"Alex\"}]");
        UserWhiteList w = new UserWhiteList(f);
        w.load();
        assertArrayEquals(new String[]{"Alex"}, w.getUserList());
    }

    @Test
    void profileCache_offlineLookupAndMruSave(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("usercache.json");
        GameProfileCache c = new GameProfileCache(ProfileLookup.NONE, f);
        GameProfile p = c.get("Notch").orElseThrow();
        assertEquals(GameProfile.offlineUuid("notch"), p.id(), "바닐라는 소문자 이름으로 조회한다");
        c.add(ALEX);
        c.add(STEVE);
        c.get(ALEX.id());
        c.save();
        String json = Files.readString(f);
        assertFalse(json.contains("\n"), "압축 JSON");
        assertTrue(json.indexOf("Alex") < json.indexOf("Steve"), "최근 사용 순");
        GameProfileCache again = new GameProfileCache(ProfileLookup.NONE, f);
        assertEquals(ALEX, again.get(ALEX.id()).orElseThrow());
        c.setUsesAuthentication(true);
        assertTrue(c.get("Unknown_Name").isEmpty(), "온라인 모드는 오프라인 프로필을 만들지 않는다");
    }
}
