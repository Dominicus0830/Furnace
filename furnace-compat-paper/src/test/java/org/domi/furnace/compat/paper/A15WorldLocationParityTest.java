package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A15-01 후속(헤드리스 서버): 월드가 있는 Location 을 설정에 저장하고 되읽기, OfflinePlayer·접속 중 Player 의 직렬화,
 * 월드를 쓰는 Location 편의 메서드(isWorldLoaded·isChunkLoaded·getNearby*). PR #247 검토 R3 에서 빠져 있다고 짚은 시험이다.
 * 기대값은 Paper 1.21.1 Location·CraftOfflinePlayer·CraftPlayer 본문에서 옮겼다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A15WorldLocationParityTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    World world;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Nora");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
        world = player.getWorld();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    /** Paper 와 같은 꼴: 월드는 이름으로 저장되고(world 키), 되읽으면 서버의 같은 월드를 가리킨다. */
    @Test
    void locationWithWorldRoundTripsThroughYaml() throws Exception {
        Location home = new Location(world, 12.5, 64, -3.25, 90f, -15f);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("home", home);
        String yaml = cfg.saveToString();
        assertTrue(yaml.contains("world: " + world.getName()), () -> "world 키에 월드 이름. 실제:\n" + yaml);
        assertTrue(yaml.matches("(?s).*'?=='?: org\\.bukkit\\.Location.*"), yaml);
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(yaml);
        Location read = back.getLocation("home");
        assertEquals(home, read);
        assertSame(world, read.getWorld(), "같은 월드 객체(서버가 월드마다 객체 하나를 쓴다)");
        assertTrue(back.isLocation("home"));
        // 직렬화한 맵: 월드 이름 문자열, 좌표 double, 시선 float(Paper 키 순서와 상관없이 값만 본다)
        Map<String, Object> map = home.serialize();
        assertEquals(world.getName(), map.get("world"));
        assertEquals(12.5, map.get("x"));
        assertEquals(90f, map.get("yaw"));
        assertEquals(-15f, map.get("pitch"));
        assertEquals(home, Location.deserialize(map));
    }

    /** Paper: 모르는 월드 이름이면 deserialize 가 IllegalArgumentException("unknown world"), 설정 읽기는 SEVERE 로그를 남기고 null. */
    @Test
    void unknownWorldFailsTheWayPaperDoes() throws Exception {
        Map<String, Object> map = new Location(world, 1, 2, 3).serialize();
        map.put("world", "no_such_world");
        assertEquals("unknown world", assertThrows(IllegalArgumentException.class, () -> Location.deserialize(map)).getMessage());
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("""
                spot:
                  ==: org.bukkit.Location
                  world: no_such_world
                  x: 1.0
                  y: 2.0
                  z: 3.0
                  yaw: 0.0
                  pitch: 0.0
                """);
        assertNull(cfg.getLocation("spot"), "되읽지 못하면 null");
        // 월드 키가 없으면 월드 없는 Location
        cfg.loadFromString("""
                spot:
                  ==: org.bukkit.Location
                  x: 1.0
                  y: 2.0
                  z: 3.0
                """);
        assertNull(cfg.getLocation("spot").getWorld());
    }

    @Test
    void worldAwareConveniences() {
        Location here = new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5);
        assertTrue(here.isWorldLoaded());
        assertTrue(owner(here::isChunkLoaded), "플레이어가 선 청크는 로드돼 있다");
        assertFalse(new Location(world, 1_000_000, 64, 1_000_000).isChunkLoaded(), "먼 청크는 로드되지 않았다");
        assertFalse(new Location(null, 0, 0, 0).isWorldLoaded());
        assertEquals(world.getName(), new Location(world, 1, 2, 3, 4f, 5f).asString().split(",")[0].substring("world=".length()));
        // 같은 월드 사이의 거리
        assertEquals(5.0, new Location(world, 0, 0, 0).distance(new Location(world, 3, 4, 0)), 1e-9);
        // 월드 있는 두 Location 의 덧셈은 된다
        assertEquals(4.0, new Location(world, 1, 0, 0).add(new Location(world, 3, 0, 0)).getX());
        // toLocation(World)
        assertSame(world, new Location(null, 1, 2, 3).toLocation(world).getWorld());
        assertSame(world, new Vector(1, 2, 3).toLocation(world).getWorld());
        assertSame(world, new Vector(1, 2, 3).toLocation(world, 4f, 5f).getWorld());
    }

    @Test
    void nearbyQueriesFilterByType() {
        Location here = player.getLocation();
        Collection<Player> players = owner(() -> here.getNearbyPlayers(5));
        assertEquals(List.of(player), List.copyOf(players));
        assertEquals(1, owner(() -> here.getNearbyPlayers(5, 5, 5)).size());
        assertEquals(1, owner(() -> here.getNearbyPlayers(5, 5)).size());
        assertEquals(1, owner(() -> here.getNearbyPlayers(5, p -> p.getName().equals("Nora"))).size());
        assertEquals(0, owner(() -> here.getNearbyPlayers(5, p -> false)).size(), "조건이 거르면 비어 있다");
        assertEquals(0, owner(() -> new Location(world, here.getX() + 100, here.getY(), here.getZ()).getNearbyPlayers(5)).size(), "멀면 없다");
        assertEquals(1, owner(() -> here.getNearbyEntitiesByType(Player.class, 5)).size());
        assertEquals(1, owner(() -> here.getNearbyEntitiesByType(null, 5)).size(), "null 종류는 Entity 전체");
        assertEquals(1, owner(() -> here.getNearbyLivingEntities(5)).size(), "플레이어는 LivingEntity 다");
        Collection<Entity> all = owner(() -> here.getNearbyEntities(5, 5, 5));
        assertTrue(all.contains(player));
        // 월드 쪽 같은 메서드도 같은 결과
        assertEquals(1, owner(() -> world.getNearbyPlayers(here, 5)).size());
        assertEquals(1, owner(() -> world.getNearbyLivingEntities(here, 5, 5, 5)).size());
        assertEquals(0, owner(() -> world.<LivingEntity>getNearbyEntitiesByType(LivingEntity.class, here, 5, 5, 5, e -> false)).size());
    }

    /** CraftOfflinePlayer: 별칭 Player, 키 UUID. 접속 중 CraftPlayer 는 name 키로 저장되고 같은 별칭으로 되읽힌다(DelegateDeserialization). */
    @Test
    void offlinePlayersRoundTripThroughYaml() throws Exception {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        OfflinePlayer stranger = Bukkit.getOfflinePlayer(id);
        assertTrue(ConfigurationSerialization.getClassByAlias("Player") != null, "별칭 Player 가 등록돼 있다");
        assertEquals(Map.of("UUID", id.toString()), stranger.serialize());

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("owner", stranger);
        String yaml = cfg.saveToString();
        assertTrue(yaml.matches("(?s).*'?=='?: Player\n.*"), () -> "별칭 Player. 실제:\n" + yaml);
        assertTrue(yaml.contains("UUID: " + id), yaml);
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(yaml);
        OfflinePlayer read = back.getOfflinePlayer("owner");
        assertEquals(id, read.getUniqueId());
        assertEquals(stranger, read);
        assertTrue(back.isOfflinePlayer("owner"));
        assertFalse(back.isOfflinePlayer("nothing"));

        // 접속 중 플레이어를 넣으면 name 키, 되읽으면 OfflinePlayer(접속 중이면 같은 CraftPlayer)
        assertInstanceOf(CraftPlayer.class, player);
        assertEquals(Map.of("name", "Nora"), player.serialize());
        cfg.set("live", player);
        String liveYaml = cfg.saveToString();
        assertTrue(liveYaml.contains("name: Nora"), liveYaml);
        back.loadFromString(liveYaml);
        OfflinePlayer live = back.getOfflinePlayer("live");
        assertEquals(player.getUniqueId(), live.getUniqueId());
        assertEquals("Nora", live.getName());
        // 옛 형식: Paper 가 쓴 name 키 맵을 이름으로 찾는다
        back.loadFromString("""
                p:
                  ==: Player
                  name: Nora
                """);
        assertEquals(player.getUniqueId(), back.getOfflinePlayer("p").getUniqueId());
        // 목록 안에서도 되읽힌다
        cfg.set("members", List.of(stranger, player));
        back.loadFromString(cfg.saveToString());
        List<?> members = back.getList("members");
        assertEquals(2, members.size());
        assertEquals(id, ((OfflinePlayer) members.get(0)).getUniqueId());
        assertEquals(player.getUniqueId(), ((OfflinePlayer) members.get(1)).getUniqueId());
    }

    @Test
    void boundingBoxOfLocationsAndBlocks() {
        Location a = new Location(world, 1, 2, 3);
        Location b = new Location(world, 4, 0, 6);
        assertEquals(new BoundingBox(1, 0, 3, 4, 2, 6), BoundingBox.of(a, b));
        assertEquals("Locations from different worlds!", assertThrows(IllegalArgumentException.class, () -> BoundingBox.of(a, new Location(null, 0, 0, 0))).getMessage());
        assertEquals(new BoundingBox(1, 2, 3, 5, 3, 7), BoundingBox.of(world.getBlockAt(1, 2, 3), world.getBlockAt(4, 2, 6)));
        assertEquals(new BoundingBox(1, 2, 3, 2, 3, 4), BoundingBox.of(world.getBlockAt(1, 2, 3)));
        assertEquals(new BoundingBox(-1, 0, 1, 3, 4, 5), BoundingBox.of(new Location(world, 1, 2, 3), 2, 2, 2));
    }
}
