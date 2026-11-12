package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundSoundPacket;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/** H4 묶음 C: Bukkit World·Player 의 소리·입자·바이옴이 Paper 와 같은 사람에게 같은 패킷을 보낸다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitEffectsTest {

    record Seen(String player, Object packet) {}

    FurnaceTestServer server;
    TestPlayer ta, tb;
    Player a, b;
    BlockPos at;
    final List<Seen> sounds = new CopyOnWriteArrayList<>();
    final List<Seen> particles = new CopyOnWriteArrayList<>();
    final List<Seen> entitySounds = new CopyOnWriteArrayList<>();
    final List<Seen> stops = new CopyOnWriteArrayList<>();

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        ta = server.join("Alice");
        tb = server.join("Bob");
        // 접속 직후에는 위치가 아직 스폰 자리로 옮겨지지 않았을 수 있다 — 두 번 읽어 같아질 때까지 기다린다
        var loc = ta.player().location();
        for (int i = 0; i < 50; i++) {
            ta.sync();
            tb.sync();
            server.tick(2);
            var now = ta.player().location();
            if (now.equals(loc) && i > 0) break;
            loc = now;
        }
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        a = Compat.player(ta.player());
        b = Compat.player(tb.player());
        // 둘을 한 칸 떨어뜨려 세운다(거리 시험이 접속 위치에 기대지 않게)
        Location next = new Location(a.getWorld(), at.x() + 1.5, loc.y(), at.z() + 0.5);
        assertTrue(server.onRegion(at, () -> b.teleport(next)));
        for (int i = 0; i < 3; i++) {
            tb.sync();
            server.tick(2);
        }
        assertEquals(next.getX(), tb.player().location().x(), 1e-6);
        var packets = server.internals().plugins().packetsImpl();
        listen(packets, PacketTypes.Clientbound.SOUND, sounds);
        listen(packets, PacketTypes.Clientbound.LEVEL_PARTICLES, particles);
        listen(packets, PacketTypes.Clientbound.SOUND_ENTITY, entitySounds);
        listen(packets, PacketTypes.Clientbound.STOP_SOUND, stops);
    }

    private <P extends org.domi.furnace.api.packet.Packet> void listen(org.domi.furnace.server.plugin.packet.PacketServiceImpl packets, PacketType<P> type, List<Seen> into) {
        packets.listenInternal(type, EventPriority.MONITOR, e -> into.add(new Seen(e.player().name(), e.packet())));
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void clear() {
        sync();
        sounds.clear();
        particles.clear();
        entitySounds.clear();
        stops.clear();
    }

    private void sync() {
        server.tick(1);
        ta.sync();
        tb.sync();
    }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    private static List<String> who(List<Seen> seen) { return seen.stream().map(Seen::player).sorted().toList(); }

    private Location here(double dx) { return new Location(a.getWorld(), at.x() + 0.5 + dx, at.y() + 1, at.z() + 0.5); }

    @Test
    void worldSoundReachesPlayersInRangeWithTheGivenSeed() {
        World w = a.getWorld();
        w.playSound(here(0), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1F, 1F, 42L);
        sync();
        assertEquals(List.of("Alice", "Bob"), who(sounds));
        ClientboundSoundPacket p = (ClientboundSoundPacket) sounds.get(0).packet();
        assertEquals("minecraft:entity.player.levelup", p.sound());
        assertEquals(org.domi.furnace.api.world.SoundCategory.PLAYERS, p.category());
        assertEquals(42L, p.seed());

        sounds.clear();
        w.playSound(here(20), "minecraft:block.note_block.harp", 1F, 1F);   // 16칸 밖
        sync();
        assertEquals(List.of(), who(sounds));
        w.playSound(here(20), "minecraft:block.note_block.harp", 2F, 1F);   // 크기 2 → 32칸
        sync();
        assertEquals(List.of("Alice", "Bob"), who(sounds));
        assertEquals(org.domi.furnace.api.world.SoundCategory.MASTER, ((ClientboundSoundPacket) sounds.get(0).packet()).category());

        sounds.clear();
        w.playSound((Location) null, Sound.ENTITY_PIG_AMBIENT, 1F, 1F);   // Paper: null 은 조용히
        w.playSound(here(0), (Sound) null, 1F, 1F);
        sync();
        assertEquals(List.of(), who(sounds));
        assertThrows(IllegalArgumentException.class, () -> w.playSound(here(0), "Not A Key", 1F, 1F));
    }

    @Test
    void playerSoundGoesOnlyToThatPlayer() {
        a.playSound(here(100), "custom:thing", SoundCategory.RECORDS, 0.5F, 3F);   // 거리와 관계없이, 높이는 2 로 죈다
        sync();
        assertEquals(List.of("Alice"), who(sounds));
        ClientboundSoundPacket p = (ClientboundSoundPacket) sounds.get(0).packet();
        assertEquals("custom:thing", p.sound());
        assertEquals(org.domi.furnace.api.world.SoundCategory.RECORDS, p.category());
        assertEquals(2F, p.pitch());
        assertEquals(0.5F, p.volume());
    }

    @Test
    void entitySoundGoesToTrackersAndSelf() {
        a.getWorld().playSound(b, Sound.ENTITY_PLAYER_HURT, 1F, 1F);
        sync();
        assertEquals(List.of("Alice", "Bob"), who(entitySounds));
        entitySounds.clear();
        a.playSound(b, Sound.ENTITY_PLAYER_HURT, 1F, 1F);
        sync();
        assertEquals(List.of("Alice"), who(entitySounds));
    }

    @Test
    void stopSoundSendsOnePacket() {
        a.stopSound(Sound.MUSIC_DISC_CAT);
        a.stopSound(SoundCategory.MUSIC);
        a.stopAllSounds();
        sync();
        assertEquals(List.of("Alice", "Alice", "Alice"), who(stops));
        assertThrows(NullPointerException.class, () -> a.stopSound((SoundCategory) null));
    }

    @Test
    void particlesFollowPaperReceiversAndDataRules() {
        World w = a.getWorld();
        w.spawnParticle(Particle.FLAME, here(0), 5);
        sync();
        assertEquals(List.of("Alice", "Bob"), who(particles));

        particles.clear();
        a.spawnParticle(Particle.DUST, here(0), 1, new Particle.DustOptions(Color.RED, 1F));
        w.spawnParticle(Particle.HEART, List.of(b), null, at.x(), at.y(), at.z(), 1, 0, 0, 0, 0, null);
        sync();
        assertEquals(List.of("Alice", "Bob"), who(particles));

        particles.clear();
        w.spawnParticle(Particle.FLAME, here(600), 1);   // force 기본 true 라도 512칸 밖은 보내지 않는다
        sync();
        assertEquals(List.of(), who(particles));
        assertThrows(IllegalArgumentException.class, () -> w.spawnParticle(Particle.DUST, here(0), 1));
        assertThrows(IllegalArgumentException.class, () -> a.spawnParticle(Particle.FLAME, here(0), 1, Color.RED));
    }

    @Test
    void biomeReadAndWriteOnTheOwnerThread() {
        World w = a.getWorld();
        Biome before = owner(() -> w.getBiome(at.x(), at.y(), at.z()));
        assertNotNull(before);
        assertNotEquals(Biome.CUSTOM, before);
        assertEquals(before, owner(() -> w.getBlockAt(at.x(), at.y(), at.z()).getBiome()));
        assertNotNull(owner(() -> w.getBiome(at.x(), 10_000, at.z())));   // 높이 밖도 읽힌다(끝 칸 — LevelChunk.getNoiseBiome)
        Biome to = before == Biome.DESERT ? Biome.JUNGLE : Biome.DESERT;
        owner(() -> { w.setBiome(at.x(), at.y(), at.z(), to); return null; });
        assertEquals(to, owner(() -> w.getBiome(at.x(), at.y(), at.z())));
        assertEquals(to, owner(() -> w.getBiome(at.x() ^ 1, at.y() ^ 1, at.z() ^ 1)), "같은 4×4×4 칸");
        assertThrows(IllegalArgumentException.class, () -> owner(() -> { w.setBiome(at.x(), at.y(), at.z(), Biome.CUSTOM); return null; }));
        owner(() -> { w.setBiome(at.x(), at.y(), at.z(), before); return null; });
    }
}
