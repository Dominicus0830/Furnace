package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SkullType;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.profile.PlayerTextures;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/** T-141 E10: 머리 상태(Paper CraftSkull)·프로필(PlayerProfile·PlayerTextures)·BlockFace 완성. */
@SuppressWarnings({"removal", "deprecation"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitSkullTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    static final String SKIN = "https://textures.minecraft.net/texture/3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3";
    static final String CAPE = "https://textures.minecraft.net/texture/2340c0e03dd24a11b15a8b33c2a7e9e32abb2051b2481d0ba7defd635ca7a933";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(13, -20, 13);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    private static String decode(ProfileProperty p) { return new String(Base64.getDecoder().decode(p.getValue()), StandardCharsets.UTF_8); }

    // ------------------------------------------------------------------ BlockFace

    @Test
    void blockFaceMatchesBukkitOrderAndVectors() {
        assertEquals(19, BlockFace.values().length);
        assertEquals(BlockFace.SELF, BlockFace.values()[18], "ordinal 을 저장하는 플러그인을 위해 SELF 가 맨 끝");
        assertEquals(BlockFace.NORTH_EAST, BlockFace.values()[6]);
        assertEquals(-2, BlockFace.WEST_NORTH_WEST.getModX());
        assertEquals(-1, BlockFace.WEST_NORTH_WEST.getModZ(), "WEST + NORTH_WEST");
        assertEquals(0, BlockFace.WEST_NORTH_WEST.getModY());
        assertEquals(1, BlockFace.NORTH_EAST.getModX());
        assertEquals(-1, BlockFace.NORTH_EAST.getModZ());
        for (BlockFace f : BlockFace.values()) {
            assertEquals(f, f.getOppositeFace().getOppositeFace(), f + " 의 반대의 반대");
            assertEquals(-f.getModX(), f.getOppositeFace().getModX(), f.toString());
            assertEquals(-f.getModZ(), f.getOppositeFace().getModZ(), f.toString());
        }
        assertEquals(BlockFace.SOUTH_SOUTH_WEST, BlockFace.NORTH_NORTH_EAST.getOppositeFace());
        assertEquals(BlockFace.EAST_SOUTH_EAST, BlockFace.WEST_NORTH_WEST.getOppositeFace());
        assertEquals(BlockFace.SELF, BlockFace.SELF.getOppositeFace());
        var d = BlockFace.NORTH_EAST.getDirection();
        assertEquals(Math.sqrt(0.5), d.getX(), 1e-9);
        assertEquals(-Math.sqrt(0.5), d.getZ(), 1e-9);
    }

    @Test
    void blockGetFaceKnowsDiagonals() {
        var a = Compat.block(dim, pos);
        assertEquals(BlockFace.NORTH_EAST, a.getFace(a.getRelative(1, 0, -1)));
        assertEquals(BlockFace.EAST, a.getFace(a.getRelative(BlockFace.EAST)));
        assertEquals(BlockFace.SELF, a.getFace(a));
    }

    // ------------------------------------------------------------------ 프로필 값

    @Test
    void paperProfileValues() {
        UUID id = UUID.randomUUID();
        PlayerProfile p = Bukkit.createProfile(id, "Steve");
        assertEquals(id, p.getId());
        assertEquals(id, p.getUniqueId());
        assertEquals("Steve", p.getName());
        assertTrue(p.isComplete(), "Paper isComplete 는 UUID 와 이름(텍스처는 보지 않는다)");
        assertFalse(Bukkit.createProfile(id).isComplete());
        assertNull(Bukkit.createProfile("Alex").getId());
        assertNull(Bukkit.createProfile(id).getName());
        assertEquals("Name cannot be longer than 16 characters", assertThrows(IllegalArgumentException.class, () -> Bukkit.createProfile(id, "x".repeat(17))).getMessage());
        assertDoesNotThrow(() -> Bukkit.createProfile((UUID) null, null), "Paper 프로필은 둘 다 비어 있어도 된다");

        ProfileProperty a = new ProfileProperty("textures", "v1", "sig");
        p.setProperty(a);
        assertTrue(p.hasProperty("textures"));
        assertTrue(p.hasTextures());
        p.setProperty(new ProfileProperty("textures", "v2"));
        assertEquals(1, p.getProperties().size(), "같은 이름은 바꾼다");
        assertEquals("v2", p.getProperties().iterator().next().getValue());
        assertTrue(p.removeProperty("textures"));
        assertFalse(p.removeProperty("textures"));
        for (int i = 0; i < 16; i++) p.setProperty(new ProfileProperty("p" + i, "v"));
        assertEquals("Cannot add more than 16 properties to a profile",
                assertThrows(IllegalArgumentException.class, () -> p.setProperty(new ProfileProperty("p16", "v"))).getMessage());
        p.getProperties().remove(p.getProperties().iterator().next());
        assertEquals(15, p.getProperties().size(), "집합 보기에서 지우면 프로필에서 지워진다");
        p.clearProperties();
        assertTrue(p.getProperties().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new ProfileProperty("n".repeat(65), "v"));
        assertThrows(NullPointerException.class, () -> new ProfileProperty("n", null));
        assertFalse(new ProfileProperty("n", "v").isSigned());
        assertEquals(new ProfileProperty("n", "v", "s"), new ProfileProperty("n", "v", "s"));
        assertNotEquals(new ProfileProperty("n", "v", "s"), new ProfileProperty("n", "v"));
    }

    @Test
    void profileCloneEqualsAndToString() {
        UUID id = UUID.randomUUID();
        PlayerProfile p = Bukkit.createProfile(id, "Steve");
        p.setProperty(new ProfileProperty("textures", "v", "s"));
        PlayerProfile c = (PlayerProfile) p.clone();
        assertEquals(p, c);
        assertEquals(p.hashCode(), c.hashCode());
        c.setProperty(new ProfileProperty("textures", "other"));
        assertNotEquals(p, c);
        assertEquals("v", p.getProperties().iterator().next().getValue(), "복사본을 고쳐도 원본은 그대로");
        assertEquals("CraftPlayerProfile [uniqueId=" + id + ", name=Steve, properties={textures=[{name=textures, value=v, signature=s}]}]", p.toString());
    }

    @Test
    void bukkitProfileRequiresUuidOrName() {
        assertEquals("uniqueId is null or name is blank", assertThrows(IllegalArgumentException.class, () -> Bukkit.createPlayerProfile((String) null)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Bukkit.createPlayerProfile(" "));
        org.bukkit.profile.PlayerProfile p = Bukkit.createPlayerProfile("Notch");
        assertEquals("Notch", p.getName());
        assertNull(p.getUniqueId());
        assertFalse(p.isComplete());
        assertEquals(p, p.clone());
        assertFalse(p instanceof PlayerProfile, "Spigot 형 프로필은 Paper 형이 아니다(Paper 와 같다)");
    }

    @Test
    void profileSerializationRoundTripsThroughTheAlias() {
        UUID id = UUID.randomUUID();
        PlayerProfile p = Bukkit.createProfile(id, "Steve");
        p.setProperty(new ProfileProperty("textures", "v", "s"));
        Map<String, Object> m = p.serialize();
        assertEquals(id.toString(), m.get("uniqueId"));
        assertEquals("Steve", m.get("name"));
        assertEquals(List.of(Map.of("name", "textures", "value", "v", "signature", "s")), m.get("properties"));
        Object back = ConfigurationSerialization.deserializeObject(withType(m));
        assertEquals(p, back, "별칭 PlayerProfile 은 Paper 가 나중에 등록한 쪽");
        assertInstanceOf(PlayerProfile.class, back);

        org.bukkit.profile.PlayerProfile spigot = Bukkit.createPlayerProfile(id, "Steve");
        Object spigotBack = ConfigurationSerialization.deserializeObject(withType(spigot.serialize()), org.bukkit.craftbukkit.profile.CraftPlayerProfile.class);
        assertEquals(spigot, spigotBack);
        assertThrows(IllegalArgumentException.class, () -> org.bukkit.craftbukkit.profile.CraftPlayerProfile.deserialize(Map.of("name", 5)));
        assertEquals("uniqueId is null or name is blank", assertThrows(IllegalArgumentException.class, () -> org.bukkit.craftbukkit.profile.CraftPlayerProfile.deserialize(Map.of())).getMessage());
    }

    private static Map<String, Object> withType(Map<String, Object> m) {
        Map<String, Object> out = new java.util.LinkedHashMap<>(m);
        out.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, "PlayerProfile");
        return out;
    }

    // ------------------------------------------------------------------ 텍스처

    @Test
    void texturesBuildTheSameJsonAsPaper() throws Exception {
        PlayerProfile p = Bukkit.createProfile(UUID.randomUUID(), "Steve");
        PlayerTextures t = p.getTextures();
        assertTrue(t.isEmpty());
        assertEquals(PlayerTextures.SkinModel.CLASSIC, t.getSkinModel());
        t.setSkin(URI.create(SKIN).toURL());
        p.setTextures(t);
        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN + "\"}}}", decode(p.getProperties().iterator().next()), "모양이 CLASSIC 이면 metadata 를 쓰지 않는다");
        assertNull(p.getProperties().iterator().next().getSignature());

        PlayerProfile q = Bukkit.createProfile(UUID.randomUUID(), "Alex");
        PlayerTextures qt = q.getTextures();
        qt.setSkin(URI.create(SKIN).toURL(), PlayerTextures.SkinModel.SLIM);
        qt.setCape(URI.create(CAPE).toURL());
        q.setTextures(qt);
        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN + "\",\"metadata\":{\"model\":\"slim\"}},\"CAPE\":{\"url\":\"" + CAPE + "\"}}}",
                decode(q.getProperties().iterator().next()));
        assertEquals(PlayerTextures.SkinModel.SLIM, q.getTextures().getSkinModel());
        assertEquals(URI.create(CAPE).toURL(), q.getTextures().getCape());
        assertFalse(q.getTextures().isSigned(), "고친 텍스처는 서명이 없다");

        // 다른 프로필에서 복사
        p.setTextures(q.getTextures());
        assertEquals(PlayerTextures.SkinModel.SLIM, p.getTextures().getSkinModel());
        assertEquals(URI.create(SKIN).toURL(), p.getTextures().getSkin());
        p.setTextures(null);
        assertFalse(p.hasTextures());
        assertTrue(p.getTextures().isEmpty());
    }

    @Test
    void texturesRejectForeignHostsAndReadMojangValues() throws Exception {
        PlayerTextures t = Bukkit.createProfile("Steve").getTextures();
        assertEquals("Expected host 'textures.minecraft.net' but got 'example.com'",
                assertThrows(IllegalArgumentException.class, () -> t.setSkin(URI.create("https://example.com/texture/a").toURL())).getMessage());
        assertEquals("Expected path starting with '/texture/' but got '/skin/a'",
                assertThrows(IllegalArgumentException.class, () -> t.setCape(URI.create("https://textures.minecraft.net/skin/a").toURL())).getMessage());
        assertDoesNotThrow(() -> t.setSkin(null));

        // Mojang 이 준 값: 시각·프로필 표시가 있고 서명이 붙어 있다
        String json = "{\"timestamp\":1700000000000,\"profileId\":\"abc\",\"profileName\":\"Steve\",\"textures\":{\"SKIN\":{\"url\":\"" + SKIN + "\",\"metadata\":{\"model\":\"slim\"}}}}";
        PlayerProfile p = Bukkit.createProfile(UUID.randomUUID(), "Steve");
        p.setProperty(new ProfileProperty("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)), "c2ln"));
        PlayerTextures read = p.getTextures();
        assertEquals(URI.create(SKIN).toURL(), read.getSkin());
        assertEquals(PlayerTextures.SkinModel.SLIM, read.getSkinModel());
        assertEquals(1700000000000L, read.getTimestamp());
        assertNull(read.getCape());
        assertFalse(read.isSigned(), "서버가 Mojang 공개키를 모르면 서명은 맞다고 하지 않는다");

        // 값이 읽히지 않으면 비어 있다
        PlayerProfile bad = Bukkit.createProfile(UUID.randomUUID(), "Steve");
        bad.setProperty(new ProfileProperty("textures", "not base64 !"));
        assertTrue(bad.getTextures().isEmpty());
        assertEquals(0L, bad.getTextures().getTimestamp());
    }

    // ------------------------------------------------------------------ 채우기(오프라인 모드 시험 서버)

    @Test
    void completeFromCacheUsesOfflineUuidWhenNotOnline() {
        PlayerProfile p = Bukkit.createProfile("Alex");
        assertTrue(p.completeFromCache(false), "오프라인 모드: 이름으로 오프라인 UUID 를 만든다");
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(StandardCharsets.UTF_8)), p.getId());
        assertEquals("Alex", p.getName());
        assertFalse(p.hasTextures());
        assertTrue(p.complete(false, false));
        assertFalse(Bukkit.createProfile((UUID) null, null).complete(false, false), "이름도 UUID 도 없으면 못 채운다");
    }

    @Test
    void updateReturnsAFilledCopyAndKeepsTheOriginal() throws Exception {
        PlayerProfile p = Bukkit.createProfile("Steve");
        PlayerProfile updated = p.update().get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(updated.getId(), "오프라인 모드는 이름으로 오프라인 UUID 를 준다");
        assertNull(p.getId(), "원본은 바뀌지 않는다");
        org.bukkit.profile.PlayerProfile spigot = Bukkit.createPlayerProfile("Steve");
        org.bukkit.profile.PlayerProfile spigotUpdated = spigot.update().get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(spigotUpdated.getUniqueId());
        assertNull(spigot.getUniqueId());
    }

    // ------------------------------------------------------------------ 머리 블록

    private Skull placeHead(String block, Map<String, String> props) {
        var w = server.api().world(dim);
        w.block(pos).set(new BlockData(block, props));
        return (Skull) Compat.block(dim, pos).getState();
    }

    @Test
    void setPlayerProfileSavesTheVanillaProfileTag() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            Skull s = placeHead("minecraft:player_head", Map.of("rotation", "0"));
            assertFalse(s.hasOwner());
            assertNull(s.getPlayerProfile());
            assertNull(s.getOwnerProfile());
            assertNull(s.getOwningPlayer());
            assertNull(s.getOwner());

            UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
            PlayerProfile profile = Bukkit.createProfile(id, "Notch");
            profile.setProperty(new ProfileProperty("textures", "dGV4", "c2ln"));
            s.setPlayerProfile(profile);
            assertTrue(s.hasOwner());
            assertEquals("Notch", s.getOwner());
            assertEquals(profile, s.getPlayerProfile());
            assertEquals(id, s.getOwningPlayer().getUniqueId());
            assertTrue(s.update());

            NbtCompound saved = w.blockEntityNbtNow(pos).orElseThrow().getCompound("profile");
            assertEquals("Notch", saved.getString("name"));
            assertArrayEquals(new int[] {0x069a79f4, 0x44e94726, 0xa5befca9 | 0, 0x0e38aaf5}, saved.getIntArray("id"));
            assertEquals(1, saved.getList("properties").size());
            assertEquals("textures", saved.getList("properties").getCompound(0).getString("name"));
            assertEquals("c2ln", saved.getList("properties").getCompound(0).getString("signature"));

            Skull again = (Skull) Compat.block(dim, pos).getState();
            assertEquals(profile, again.getPlayerProfile());
            assertEquals(profile.getProperties(), again.getPlayerProfile().getProperties());
            org.bukkit.profile.PlayerProfile spigot = again.getOwnerProfile();
            assertEquals(id, spigot.getUniqueId());
            assertEquals("Notch", spigot.getName());
            assertEquals("dGV4", again.getPlayerProfile().getProperties().iterator().next().getValue());
            return null;
        });
    }

    @Test
    void clearingTheOwnerReachesTheBlockEntity() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            Skull s = placeHead("minecraft:player_head", Map.of("rotation", "0"));
            s.setPlayerProfile(Bukkit.createProfile(UUID.randomUUID(), "Steve"));
            assertTrue(s.update());
            assertTrue(w.blockEntityNbtNow(pos).orElseThrow().contains("profile"));

            Skull again = (Skull) Compat.block(dim, pos).getState();
            assertTrue(again.hasOwner());
            again.setOwnerProfile(null);
            assertFalse(again.hasOwner());
            assertTrue(again.update());
            assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("profile"), "Paper applyTo 는 플레이어 머리의 소유자를 지운다(바닐라 load 는 지우지 못한다)");
            assertFalse(((Skull) Compat.block(dim, pos).getState()).hasOwner());
            return null;
        });
    }

    @Test
    void otherHeadsNeverGetAnOwner() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            Skull s = placeHead("minecraft:zombie_head", Map.of("rotation", "0"));
            s.setPlayerProfile(Bukkit.createProfile(UUID.randomUUID(), "Steve"));
            assertTrue(s.hasOwner(), "상태 안에서는 정해진다");
            assertTrue(s.update());
            assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("profile"), "Paper applyTo: 플레이어 머리만 소유자를 적용한다");
            assertEquals(SkullType.ZOMBIE, s.getSkullType());
            return null;
        });
    }

    @Test
    void ownerProfileNeedsANameOrUuidAndTextures() throws Exception {
        Skull s = (Skull) Material.PLAYER_HEAD.createBlockData().createBlockState();
        String message = "The skull profile is missing a name or textures!";
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> s.setOwnerProfile(Bukkit.createPlayerProfile(UUID.randomUUID()))).getMessage());
        org.bukkit.profile.PlayerProfile textureOnly = Bukkit.createPlayerProfile(UUID.randomUUID());
        textureOnly.getTextures().setSkin(URI.create(SKIN).toURL());
        textureOnly.setTextures(textureOnly.getTextures());
        assertDoesNotThrow(() -> s.setOwnerProfile(textureOnly), "UUID 와 텍스처가 있으면 이름이 없어도 된다");
        // Paper 프로필(setPlayerProfile)은 검사하지 않는다
        assertDoesNotThrow(() -> s.setPlayerProfile(Bukkit.createProfile(UUID.randomUUID())));
        // 이름이 있으면 그것만으로 된다
        assertDoesNotThrow(() -> s.setOwnerProfile(Bukkit.createPlayerProfile("Notch")));
    }

    @Test
    void ownerSettersFollowPaper() {
        Skull s = (Skull) Material.PLAYER_HEAD.createBlockData().createBlockState();
        // 이름: 서버가 아는 이름만(오프라인 모드는 오프라인 UUID 프로필을 만든다)
        assertFalse(s.setOwner(null));
        assertFalse(s.setOwner("x".repeat(17)));
        assertTrue(s.setOwner("Herobrine"));
        // 바닐라 GameProfileCache.get 은 이름을 소문자로 바꿔 오프라인 프로필을 만든다(서버 사용자 캐시와 같은 값)
        assertEquals("herobrine", s.getOwner());
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:herobrine".getBytes(StandardCharsets.UTF_8)), s.getOwningPlayer().getUniqueId());

        assertThrows(NullPointerException.class, () -> s.setOwningPlayer(null));
        assertEquals("profile", assertThrows(NullPointerException.class, () -> s.setPlayerProfile(null)).getMessage());
        UUID id = UUID.randomUUID();
        s.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        assertEquals("", s.getOwner(), "이름을 모르는 플레이어는 빈 이름");
        assertEquals(id, s.getOwningPlayer().getUniqueId());

        // UUID 없이 이름만 있는 프로필: 이름으로 오프라인 플레이어를 찾는다
        s.setPlayerProfile(Bukkit.createProfile("Notch"));
        assertEquals("Notch", s.getOwningPlayer().getName());
        // 이름도 UUID 도 없는 프로필은 Paper setPlayerProfile 이 그대로 받는다(소유자 플레이어는 없다)
        s.setPlayerProfile(Bukkit.createProfile((UUID) null, null));
        assertTrue(s.hasOwner());
        assertNull(s.getOwningPlayer());
        assertNull(s.getOwner());

        // Bukkit 쪽 프로필로 정하면 텍스처만 있어도 된다
        org.bukkit.profile.PlayerProfile bp = Bukkit.createPlayerProfile("Notch");
        s.setOwnerProfile(bp);
        assertEquals("Notch", s.getPlayerProfile().getName());
        s.setOwnerProfile(null);
        assertFalse(s.hasOwner());
    }

    @Test
    void rotationOfFloorAndWallHeads() {
        server.onRegion(pos, () -> {
            Skull floor = placeHead("minecraft:player_head", Map.of("rotation", "4"));
            assertEquals(BlockFace.WEST, floor.getRotation());
            floor.setRotation(BlockFace.NORTH_NORTH_EAST);
            assertEquals(BlockFace.NORTH_NORTH_EAST, floor.getRotation());
            assertEquals("9", ((CompatBlockData) floor.getBlockData()).furnace().properties().get("rotation"));
            assertEquals("Illegal rotation UP", assertThrows(IllegalArgumentException.class, () -> floor.setRotation(BlockFace.UP)).getMessage());
            for (int i = 0; i < 16; i++) {
                Skull f = placeHead("minecraft:skeleton_skull", Map.of("rotation", Integer.toString(i)));
                assertEquals(new BlockFace[] {BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
                        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
                        BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
                        BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST}[i], f.getRotation());
            }
            Skull wall = placeHead("minecraft:player_wall_head", Map.of("facing", "south"));
            assertEquals(BlockFace.SOUTH, wall.getRotation());
            wall.setRotation(BlockFace.EAST);
            assertEquals(BlockFace.EAST, wall.getRotation());
            assertThrows(IllegalArgumentException.class, () -> wall.setRotation(BlockFace.UP));
            assertThrows(IllegalArgumentException.class, () -> wall.setRotation(BlockFace.NORTH_EAST));
            assertTrue(wall.update());
            assertEquals("east", server.api().world(dim).block(pos).data().properties().get("facing"), "update 가 방향을 블록에 쓴다");
            return null;
        });
    }

    @Test
    void skullTypesAndNoteBlockSound() {
        Map<Material, SkullType> expected = new java.util.LinkedHashMap<>();
        expected.put(Material.SKELETON_SKULL, SkullType.SKELETON);
        expected.put(Material.SKELETON_WALL_SKULL, SkullType.SKELETON);
        expected.put(Material.WITHER_SKELETON_SKULL, SkullType.WITHER);
        expected.put(Material.WITHER_SKELETON_WALL_SKULL, SkullType.WITHER);
        expected.put(Material.ZOMBIE_HEAD, SkullType.ZOMBIE);
        expected.put(Material.ZOMBIE_WALL_HEAD, SkullType.ZOMBIE);
        expected.put(Material.PIGLIN_HEAD, SkullType.PIGLIN);
        expected.put(Material.PIGLIN_WALL_HEAD, SkullType.PIGLIN);
        expected.put(Material.PLAYER_HEAD, SkullType.PLAYER);
        expected.put(Material.PLAYER_WALL_HEAD, SkullType.PLAYER);
        expected.put(Material.CREEPER_HEAD, SkullType.CREEPER);
        expected.put(Material.CREEPER_WALL_HEAD, SkullType.CREEPER);
        expected.put(Material.DRAGON_HEAD, SkullType.DRAGON);
        expected.put(Material.DRAGON_WALL_HEAD, SkullType.DRAGON);
        expected.forEach((m, t) -> assertEquals(t, ((Skull) m.createBlockData().createBlockState()).getSkullType(), m.toString()));
        Skull s = (Skull) Material.CREEPER_HEAD.createBlockData().createBlockState();
        assertEquals("Must change block type", assertThrows(UnsupportedOperationException.class, () -> s.setSkullType(SkullType.ZOMBIE)).getMessage());

        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            Skull head = placeHead("minecraft:creeper_head", Map.of("rotation", "0"));
            assertNull(head.getNoteBlockSound());
            head.setNoteBlockSound(NamespacedKey.minecraft("entity.creeper.hurt"));
            assertTrue(head.update());
            assertEquals("minecraft:entity.creeper.hurt", w.blockEntityNbtNow(pos).orElseThrow().getString("note_block_sound"));
            Skull again = (Skull) Compat.block(dim, pos).getState();
            assertEquals(NamespacedKey.minecraft("entity.creeper.hurt"), again.getNoteBlockSound());
            again.setNoteBlockSound(null);
            assertNull(again.getNoteBlockSound());
            assertTrue(again.update());
            assertEquals("minecraft:entity.creeper.hurt", w.blockEntityNbtNow(pos).orElseThrow().getString("note_block_sound"),
                    "Paper 도 지운 소리는 블록 엔티티에서 지워지지 않는다(저장 → 읽기라 없는 값은 그대로)");
            return null;
        });
    }

    @Test
    void facadeFindsProfilesWithoutBlocking() throws Exception {
        var f = Compat.furnace();
        var found = f.profileByName("Dinnerbone").orElseThrow();
        assertEquals("dinnerbone", found.name(), "바닐라 사용자 캐시는 모르는 이름을 소문자로 만든다");
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:dinnerbone".getBytes(StandardCharsets.UTF_8)), found.id());
        assertEquals(found, f.profileById(found.id()).orElseThrow(), "처음 찾은 오프라인 프로필은 캐시에 들어간다");
        assertTrue(f.profileById(UUID.randomUUID()).isEmpty());
        assertTrue(f.fetchProfile(found.id()).get().isEmpty(), "오프라인 모드는 세션 서버에 묻지 않는다");
        assertEquals(found, f.fetchProfileByName("Dinnerbone").get().orElseThrow());
        assertFalse(f.verifyProfileProperty("textures", "v", "c2ln"));
    }
}
