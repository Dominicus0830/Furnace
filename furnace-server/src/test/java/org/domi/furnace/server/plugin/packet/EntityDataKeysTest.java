// 이름 있는 메타데이터 칸(EntityDataKeys)·값 종류 31개(EntityDataType)가 서버 엔티티 정의와 맞는지, 와이어 왕복·서버 직렬화와 같은 바이트인지.
package org.domi.furnace.server.plugin.packet;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.entity.data.EntityDataKey;
import org.domi.furnace.api.entity.data.EntityDataKeys;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.entity.data.EntityFlags;
import org.domi.furnace.api.entity.data.VillagerData;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.math.Quaternionf;
import org.domi.furnace.api.math.Rotations;
import org.domi.furnace.api.math.Vector3f;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.particle.Particle;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Direction;
import org.domi.furnace.api.world.GlobalPos;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.FishingHook;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityDataKeysTest {

    private static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    private static final ItemRegistry ITEMS = ItemRegistry.load("items/1.21.1.txt");
    private static Protocol767 codec;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        codec = new Protocol767(() -> BLOCKS, () -> ITEMS);
    }

    // ------------------------------------------------------------------ 표가 서버 엔티티와 맞는가

    /** 엔티티 종류마다 서버 엔티티를 만들어 칸 배치(번호 순 값 종류)를 읽는다. 플레이어는 SynchedData 밖이라 손으로. */
    private static Map<String, String> serverLayouts(Map<String, Entity> instances) {
        Map<String, String> out = new TreeMap<>();
        var level = new BlockPlacementTest.TestLevel();
        for (String type : ItemContext.regs().names("minecraft:entity_type").stream().map(Object::toString).toList()) {
            Entity e = switch (type) {
                case "minecraft:player" -> null;
                case "minecraft:fishing_bobber" -> new FishingHook(level, level.nextEntityId());
                default -> Entities.create(level, type);
            };
            if (type.equals("minecraft:player")) {
                out.put(type, "byte int optional_component boolean boolean boolean pose int byte float particles boolean int int optional_block_pos"
                        + " float int byte byte compound_tag compound_tag");   // Entity 0~7, LivingEntity 8~14, Player 15~20(1.21.1)
                continue;
            }
            assertNotNull(e, "서버가 만들 수 없는 엔티티 종류: " + type);
            instances.put(type, e);
            var d = e.entityData();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(EntityData767.serializer(d.serializer(i)).name());
            }
            out.put(type, sb.toString());
        }
        return out;
    }

    @Test
    void layoutResourceMatchesServerEntities() throws Exception {
        Map<String, String> expected = serverLayouts(new LinkedHashMap<>());
        StringBuilder file = new StringBuilder("# 엔티티 종류별 메타데이터 칸 배치(1.21.1, 번호 순 값 종류). 서버 엔티티 정의에서 만든다 — EntityDataKeysTest 가 대조한다.\n");
        expected.forEach((t, l) -> file.append(t).append(' ').append(l).append('\n'));
        boolean ok = true;
        for (var e : expected.entrySet()) {
            var l = EntityData767.layout(e.getKey());
            String got = l == null ? null : String.join(" ", java.util.Arrays.stream(l).map(EntityDataType::name).toList());
            if (!e.getValue().equals(got)) ok = false;
        }
        if (!ok) {
            Path p = Path.of("build/entity-data-layouts-1.21.1.txt");
            Files.writeString(p, file, StandardCharsets.UTF_8);
            fail("resources/entity_data/layouts-1.21.1.txt(바닐라 대조 완료) 가 서버 엔티티와 다르다 — 먼저 서버 엔티티의 defineSynchedData 를 바닐라와 대조할 것. 서버가 맞다고 확인된 경우에만 새 표로 바꾼다: " + p.toAbsolutePath());
        }
    }

    @Test
    void everyKeyMatchesItsServerOwner() throws Exception {
        Map<String, Entity> instances = new LinkedHashMap<>();
        serverLayouts(instances);
        Set<String> checked = new HashSet<>();
        for (EntityDataKey<?> key : EntityDataKeys.all()) {
            List<String> owners = EntityDataOwners767.OWNERS.get(key.id());
            assertNotNull(owners, "서버 주인 표에 없는 칸: " + key);
            OptionalInt idx = EntityData767.index(key);
            assertTrue(idx.isPresent(), "칸 번호 표에 없는 칸: " + key);
            assertEquals(idx, key.index(), "API index() 가 서버 표를 쓴다");
            int n = 0;
            for (String ownerName : owners) {
                if (ownerName.endsWith(".Player")) continue;
                Class<?> owner = Class.forName(ownerName);
                for (var inst : instances.entrySet()) {
                    if (!owner.isInstance(inst.getValue())) continue;
                    var d = inst.getValue().entityData();
                    assertTrue(idx.getAsInt() < d.size(), key + " — " + inst.getKey() + " 에 칸이 모자람");
                    assertSame(key.type(), EntityData767.serializer(d.serializer(idx.getAsInt())), key + " 값 종류 — " + inst.getKey());
                    assertTrue(EntityData767.has(inst.getKey(), key), "배치 표로도 있다: " + key + " " + inst.getKey());
                    n++;
                }
            }
            if (key.owner().equals("Player")) {
                assertTrue(EntityData767.has("minecraft:player", key), key.toString());
                n++;
            }
            assertTrue(n > 0, "대조할 서버 엔티티가 없는 칸: " + key);
            checked.add(key.id());
        }
        assertEquals(EntityDataOwners767.OWNERS.keySet(), checked, "주인 표와 API 칸 목록이 같다");
        assertEquals(EntityDataIndex767.INDEX.size(), EntityDataKeys.all().size());
        assertEquals(207, EntityDataKeys.all().size());
        assertEquals(31, EntityDataType.all().size());
        for (EntityDataType<?> t : EntityDataType.all()) assertSame(t, EntityData767.serializer(EntityData767.serializerId(t)));
    }

    @Test
    void wrongEntityKindIsDetected() {
        assertTrue(EntityData767.has("minecraft:text_display", EntityDataKeys.TextDisplay.TEXT));
        assertTrue(EntityData767.has("text_display", EntityDataKeys.Display.SCALE));
        assertFalse(EntityData767.has("minecraft:zombie", EntityDataKeys.TextDisplay.TEXT), "좀비 23번 칸은 없다");
        assertFalse(EntityData767.has("minecraft:item_display", EntityDataKeys.TextDisplay.TEXT), "같은 23번이지만 값 종류가 아이템이다");
        assertTrue(EntityData767.has("minecraft:zombie", EntityDataKeys.Entity.SHARED_FLAGS));
        assertTrue(EntityData767.has("minecraft:villager", EntityDataKeys.AgeableMob.BABY));
        assertTrue(EntityData767.has("minecraft:wandering_trader", EntityDataKeys.AbstractVillager.UNHAPPY_COUNTER));
        assertFalse(EntityData767.has("minecraft:nope", EntityDataKeys.Entity.SHARED_FLAGS));
        assertSame(EntityDataKeys.Display.SCALE, EntityDataKeys.byId("Display.scale"));
    }

    // ------------------------------------------------------------------ 와이어

    private static ClientboundSetEntityDataPacket roundTrip(ClientboundSetEntityDataPacket p) {
        ByteBuf b1 = Unpooled.buffer();
        int id = codec.encode(p, b1);
        assertSame(PacketTypes.Clientbound.SET_ENTITY_DATA, codec.clientboundType(id));
        byte[] bytes = ByteBufUtil.getBytes(b1);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        var back = (ClientboundSetEntityDataPacket) codec.decode(PacketTypes.Clientbound.SET_ENTITY_DATA, id, in);
        assertEquals(0, in.readableBytes());
        ByteBuf b2 = Unpooled.buffer();
        codec.encode(back, b2);
        assertArrayEquals(bytes, ByteBufUtil.getBytes(b2), "왕복 뒤 같은 바이트");
        return back;
    }

    /** 값 종류 31개 전부 예시 하나씩. */
    private static Map<EntityDataType<?>, Object> samples() {
        Map<EntityDataType<?>, Object> m = new LinkedHashMap<>();
        NbtCompound tag = NbtCompound.parse("{id:\"minecraft:parrot\",Variant:2}");
        m.put(EntityDataType.BYTE, (byte) 0x42);
        m.put(EntityDataType.INT, -7);
        m.put(EntityDataType.LONG, 1L << 40);
        m.put(EntityDataType.FLOAT, 2.5f);
        m.put(EntityDataType.STRING, "brown");
        m.put(EntityDataType.COMPONENT, Text.text("상점", NamedTextColor.GOLD));
        m.put(EntityDataType.OPTIONAL_COMPONENT, Optional.of(Text.text("이름")));
        m.put(EntityDataType.ITEM_STACK, ItemStack.of("minecraft:diamond_sword"));
        m.put(EntityDataType.BOOLEAN, true);
        m.put(EntityDataType.ROTATIONS, new Rotations(10, -20, 30.5f));
        m.put(EntityDataType.BLOCK_POS, new BlockPos(-5, -60, 300));
        m.put(EntityDataType.OPTIONAL_BLOCK_POS, Optional.of(new BlockPos(1, 2, 3)));
        m.put(EntityDataType.DIRECTION, Direction.WEST);
        m.put(EntityDataType.OPTIONAL_UUID, Optional.of(new UUID(1, 2)));
        m.put(EntityDataType.BLOCK_STATE, BlockData.parse("minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"));
        m.put(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.of(BlockData.parse("minecraft:grass_block[snowy=false]")));
        m.put(EntityDataType.COMPOUND_TAG, tag);
        m.put(EntityDataType.PARTICLE, Particle.dust(0xFF8000, 1.5f));
        m.put(EntityDataType.PARTICLES, List.of(Particle.entityEffect(0xFF00FF00), Particle.of("minecraft:flame"),
                Particle.block("minecraft:falling_dust", BlockData.of("minecraft:sand")), Particle.item(ItemStack.of("minecraft:apple")),
                new Particle.Shriek(5), new Particle.SculkCharge(0.5f), Particle.dustTransition(0xFF0000, 0x0000FF, 2f),
                Particle.Vibration.toBlock(new BlockPos(1, 2, 3), 20), Particle.Vibration.toEntity(42, 1.5f, 10)));
        m.put(EntityDataType.VILLAGER_DATA, new VillagerData("minecraft:taiga", "minecraft:librarian", 3));
        m.put(EntityDataType.OPTIONAL_UNSIGNED_INT, OptionalInt.of(77));
        m.put(EntityDataType.POSE, Pose.SITTING);
        m.put(EntityDataType.CAT_VARIANT, "minecraft:calico");
        m.put(EntityDataType.WOLF_VARIANT, "minecraft:snowy");
        m.put(EntityDataType.FROG_VARIANT, "minecraft:cold");
        m.put(EntityDataType.OPTIONAL_GLOBAL_POS, Optional.of(new GlobalPos("minecraft:the_nether", new BlockPos(8, 64, -8))));
        m.put(EntityDataType.PAINTING_VARIANT, "minecraft:kebab");
        m.put(EntityDataType.SNIFFER_STATE, "digging");
        m.put(EntityDataType.ARMADILLO_STATE, "scared");
        m.put(EntityDataType.VECTOR3, new Vector3f(2, 0.5f, -1));
        m.put(EntityDataType.QUATERNION, Quaternionf.rotationY(90));
        return m;
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everyValueKindRoundTrips() {
        var m = samples();
        assertEquals(EntityDataType.all().size(), m.size(), "값 종류 전부");
        List<ClientboundSetEntityDataPacket.Entry> es = new ArrayList<>();
        int i = 0;
        for (var e : m.entrySet()) es.add(ClientboundSetEntityDataPacket.entry(i++, Value.of((EntityDataType) e.getKey(), e.getValue())));
        var back = roundTrip(new ClientboundSetEntityDataPacket(5, es));
        assertFalse(back.hasUnsupported());
        i = 0;
        for (var e : m.entrySet()) {
            var v = back.entries().get(i++).value();
            assertSame(e.getKey(), v.dataType());
            assertEquals(e.getValue(), v.asObject(), "값 " + e.getKey());
        }
        // 비어 있는 옵션들
        roundTrip(ClientboundSetEntityDataPacket.of(5,
                ClientboundSetEntityDataPacket.entry(0, Value.of(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.empty())),
                ClientboundSetEntityDataPacket.entry(1, Value.of(EntityDataType.OPTIONAL_UNSIGNED_INT, OptionalInt.empty())),
                ClientboundSetEntityDataPacket.entry(2, Value.of(EntityDataType.OPTIONAL_UUID, Optional.empty())),
                ClientboundSetEntityDataPacket.entry(3, Value.of(EntityDataType.ITEM_STACK, ItemStack.EMPTY)),
                ClientboundSetEntityDataPacket.entry(4, Value.of(EntityDataType.OPTIONAL_GLOBAL_POS, Optional.empty())),
                ClientboundSetEntityDataPacket.entry(5, Value.of(EntityDataType.PARTICLES, List.of()))));
    }

    /** 같은 값을 서버 자신의 SetEntityData(실제 접속자에게 가는 경로)가 쓴 바이트와 같게 쓰는가. */
    @Test
    void writesSameBytesAsServerSerializer() {
        int sword = ITEMS.idOf("minecraft:diamond_sword");
        int stone = BLOCKS.defaultStateId("minecraft:stone");
        var server = new SetEntityData(9, List.of(
                new SetEntityData.Item(12, SetEntityData.SERIALIZER_VECTOR3, new float[] {2, 2, 2}),
                new SetEntityData.Item(13, SetEntityData.SERIALIZER_QUATERNION, new float[] {0, 0, 0, 1}),
                new SetEntityData.Item(23, SetEntityData.SERIALIZER_ITEM, new org.domi.furnace.server.entity.ItemStack(sword, 1)),
                new SetEntityData.Item(9, SetEntityData.SERIALIZER_BLOCK_STATE, stone),
                new SetEntityData.Item(16, SetEntityData.SERIALIZER_OPTIONAL_BLOCK_STATE, null),
                new SetEntityData.Item(18, SetEntityData.SERIALIZER_VILLAGER_DATA, new int[] {5, 9, 2}),
                new SetEntityData.Item(10, SetEntityData.SERIALIZER_PARTICLE, new org.domi.furnace.server.net.protocol.play.ParticleOptions.Color(-1)),
                new SetEntityData.Item(19, SetEntityData.SERIALIZER_FROG_VARIANT, 2),
                new SetEntityData.Item(8, SetEntityData.SERIALIZER_PAINTING_VARIANT, RegistrySet.current().indexOf("minecraft:painting_variant", "minecraft:kebab")),
                new SetEntityData.Item(17, SetEntityData.SERIALIZER_SNIFFER_STATE, 5),
                new SetEntityData.Item(20, SetEntityData.SERIALIZER_OPTIONAL_INT, 4),
                new SetEntityData.Item(16, SetEntityData.SERIALIZER_DIRECTION, 1),
                new SetEntityData.Item(22, SetEntityData.SERIALIZER_WOLF_VARIANT, org.domi.furnace.server.entity.Wolf.paleIndex())));
        ByteBuf sb = Unpooled.buffer();
        server.write(sb);
        byte[] serverBytes = ByteBufUtil.getBytes(sb);

        var api = ClientboundSetEntityDataPacket.of(9,
                ClientboundSetEntityDataPacket.entry(12, Value.of(EntityDataType.VECTOR3, new Vector3f(2, 2, 2))),
                ClientboundSetEntityDataPacket.entry(13, Value.of(EntityDataType.QUATERNION, Quaternionf.IDENTITY)),
                ClientboundSetEntityDataPacket.entry(23, Value.of(EntityDataType.ITEM_STACK, ItemStack.of("minecraft:diamond_sword"))),
                ClientboundSetEntityDataPacket.entry(9, Value.of(EntityDataType.BLOCK_STATE, BlockData.of("minecraft:stone"))),
                ClientboundSetEntityDataPacket.entry(16, Value.of(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.empty())),
                ClientboundSetEntityDataPacket.entry(18, Value.of(EntityDataType.VILLAGER_DATA, new VillagerData("swamp", "librarian", 2))),
                ClientboundSetEntityDataPacket.entry(10, Value.of(EntityDataType.PARTICLE, Particle.entityEffect(-1))),
                ClientboundSetEntityDataPacket.entry(19, Value.of(EntityDataType.FROG_VARIANT, "minecraft:cold")),
                ClientboundSetEntityDataPacket.entry(8, Value.of(EntityDataType.PAINTING_VARIANT, "minecraft:kebab")),
                ClientboundSetEntityDataPacket.entry(17, Value.of(EntityDataType.SNIFFER_STATE, "digging")),
                ClientboundSetEntityDataPacket.entry(20, Value.of(EntityDataType.OPTIONAL_UNSIGNED_INT, OptionalInt.of(4))),
                ClientboundSetEntityDataPacket.entry(16, Value.of(EntityDataType.DIRECTION, Direction.UP)),
                ClientboundSetEntityDataPacket.entry(22, Value.of(EntityDataType.WOLF_VARIANT, "minecraft:pale")));
        ByteBuf ab = Unpooled.buffer();
        assertEquals(SetEntityData.ID, codec.encode(api, ab));
        assertArrayEquals(serverBytes, ByteBufUtil.getBytes(ab));

        var decoded = (ClientboundSetEntityDataPacket) codec.decode(PacketTypes.Clientbound.SET_ENTITY_DATA, SetEntityData.ID, Unpooled.wrappedBuffer(serverBytes));
        assertEquals(api, decoded, "서버가 쓴 메타데이터를 같은 이름 값으로 읽는다");
    }

    // ------------------------------------------------------------------ 이름으로 읽고 쓰기

    @Test
    void getWithWithoutByKey() {
        var p = ClientboundSetEntityDataPacket.builder(3)
                .set(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.of(EntityFlags.ON_FIRE))
                .set(EntityDataKeys.TextDisplay.TEXT, Text.text("a"))
                .set(EntityDataKeys.Display.SCALE, new Vector3f(2, 2, 2))
                .set(EntityDataKeys.TextDisplay.TEXT, Text.text("b"))   // 같은 칸은 바꾼다
                .build();
        assertEquals(3, p.entries().size());
        assertEquals(Optional.of(Text.text("b")), p.get(EntityDataKeys.TextDisplay.TEXT));
        assertEquals(Optional.of(new Vector3f(2, 2, 2)), p.get(EntityDataKeys.Display.SCALE));
        assertEquals(Optional.empty(), p.get(EntityDataKeys.Entity.CUSTOM_NAME));
        assertEquals(Optional.empty(), p.get(EntityDataKeys.ItemDisplay.ITEM_STACK), "같은 23번이지만 값 종류가 다르면 없다");

        byte f = p.get(EntityDataKeys.Entity.SHARED_FLAGS).orElseThrow();
        var glow = p.with(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.set(f, EntityFlags.GLOWING, true));
        assertTrue(EntityFlags.has(glow.get(EntityDataKeys.Entity.SHARED_FLAGS).orElseThrow(), EntityFlags.GLOWING));
        assertTrue(EntityFlags.has(glow.get(EntityDataKeys.Entity.SHARED_FLAGS).orElseThrow(), EntityFlags.ON_FIRE));
        assertEquals(3, glow.entries().size(), "바꾸기는 칸을 늘리지 않는다");
        assertEquals(4, glow.with(EntityDataKeys.Entity.SILENT, true).entries().size());
        assertEquals(2, glow.without(EntityDataKeys.Display.SCALE).entries().size());
        roundTrip(glow);

        assertThrows(IllegalArgumentException.class, () -> Value.of(EntityDataType.PARTICLES, (List) List.of("x")), "값 종류 검사");
        assertThrows(NullPointerException.class, () -> p.with(EntityDataKeys.Entity.CUSTOM_NAME, null), "없음은 Optional.empty()");
        assertThrows(IllegalArgumentException.class, () -> codec.encode(ClientboundSetEntityDataPacket.of(1,
                ClientboundSetEntityDataPacket.entry(0, Value.of(EntityDataType.CAT_VARIANT, "minecraft:nope"))), Unpooled.buffer()));
        assertThrows(IllegalArgumentException.class, () -> Particle.of("minecraft:dust"), "옵션이 필요한 입자");
    }

    // ------------------------------------------------------------------ 독립 검토(2026-11-12) 반영

    /** 바닐라 1.21.1 바이트 고정: wolf_variant 는 holder(WOLF_VARIANT, 직접 코덱) — 등록된 종류 = 번호 + 1, 0 = 직접 정의(그림과 같음). */
    @Test
    void wolfVariantIsHolderIdPlusOneLikeVanilla() {
        assertEquals(3, RegistrySet.current().indexOf("minecraft:wolf_variant", "minecraft:pale"));
        // 서버 본체(SetEntityData: 실제 접속자에게 가는 경로)
        ByteBuf sb = Unpooled.buffer();
        new SetEntityData(1, List.of(new SetEntityData.Item(22, SetEntityData.SERIALIZER_WOLF_VARIANT, 3))).write(sb);
        assertArrayEquals(new byte[] {1, 22, 23, 4, (byte) 0xFF}, ByteBufUtil.getBytes(sb), "pale → 17 04(칸 22, 직렬화 23, 값 3+1)");
        // API 코덱도 같은 바이트, 읽으면 같은 이름
        ByteBuf ab = Unpooled.buffer();
        codec.encode(ClientboundSetEntityDataPacket.of(1, ClientboundSetEntityDataPacket.entry(22, Value.of(EntityDataType.WOLF_VARIANT, "minecraft:pale"))), ab);
        byte[] bytes = ByteBufUtil.getBytes(ab);
        assertArrayEquals(new byte[] {1, 22, 23, 4, (byte) 0xFF}, bytes);
        var back = (ClientboundSetEntityDataPacket) codec.decode(PacketTypes.Clientbound.SET_ENTITY_DATA, SetEntityData.ID, Unpooled.wrappedBuffer(bytes));
        assertEquals(Optional.of("minecraft:pale"), back.get(EntityDataKeys.Wolf.VARIANT));
        // ashen(0) 은 1 로 — 0 은 직접 정의라 클라이언트가 뒤 바이트를 잘못 읽는다
        ByteBuf ash = Unpooled.buffer();
        codec.encode(ClientboundSetEntityDataPacket.of(1, ClientboundSetEntityDataPacket.entry(22, Value.of(EntityDataType.WOLF_VARIANT, "minecraft:ashen"))), ash);
        assertArrayEquals(new byte[] {1, 22, 23, 1, (byte) 0xFF}, ByteBufUtil.getBytes(ash));
    }

    /** 칸이 있는지는 주인 부류로 정한다 — 번호·값 종류가 우연히 맞는 다른 부류의 칸은 없다. */
    @Test
    void hasChecksOwnerHierarchy() {
        assertFalse(EntityData767.has("minecraft:interaction", EntityDataKeys.LivingEntity.HEALTH), "interaction 9번은 height(float)");
        assertFalse(EntityData767.has("minecraft:allay", EntityDataKeys.Zombie.BABY), "allay 16번은 dancing(boolean)");
        assertFalse(EntityData767.has("minecraft:block_display", EntityDataKeys.Mob.MOB_FLAGS), "block_display 15번은 billboard(byte)");
        assertFalse(EntityData767.has("minecraft:arrow", EntityDataKeys.LivingEntity.LIVING_ENTITY_FLAGS), "arrow 8번은 AbstractArrow.flags");
        assertTrue(EntityData767.has("minecraft:minecart", EntityDataKeys.VehicleEntity.HURT));
        assertTrue(EntityData767.has("minecraft:chest_boat", EntityDataKeys.VehicleEntity.DAMAGE));
        assertTrue(EntityData767.has("minecraft:player", EntityDataKeys.Player.MODE_CUSTOMISATION));
    }

    /** 주인 부류 표(바닐라 계층)와 배치 표·키 표가 서로 맞는가: 종류마다 칸 하나에 키 하나, 번호·값 종류 일치. */
    @Test
    void ownersTableAgreesWithLayoutsAndKeys() {
        Map<String, List<EntityDataKey<?>>> byOwner = new LinkedHashMap<>();
        for (EntityDataKey<?> k : EntityDataKeys.all()) byOwner.computeIfAbsent(k.owner(), x -> new ArrayList<>()).add(k);
        Set<String> usedOwners = new HashSet<>();
        int types = 0;
        for (String type : ItemContext.regs().names("minecraft:entity_type").stream().map(Object::toString).toList()) {
            var l = EntityData767.layout(type);
            var owners = EntityData767.owners(type);
            assertEquals(l == null, owners == null, type + ": 배치 표와 주인 표 둘 다 있거나 둘 다 없어야 한다");
            if (l == null) continue;
            types++;
            EntityDataKey<?>[] slots = new EntityDataKey<?>[l.length];
            for (String o : owners) {
                assertTrue(byOwner.containsKey(o), type + ": 키가 없는 주인 부류 " + o);
                usedOwners.add(o);
                for (EntityDataKey<?> k : byOwner.get(o)) {
                    int i = k.index().orElseThrow();
                    assertTrue(i < l.length, type + ": " + k + " 번호가 배치 밖");
                    assertSame(l[i], k.type(), type + ": " + k + " 값 종류");
                    assertNull(slots[i], type + ": 칸 " + i + " 에 키가 둘 — " + slots[i] + ", " + k);
                    slots[i] = k;
                    assertTrue(EntityData767.has(type, k));
                }
            }
            for (int i = 0; i < l.length; i++) assertNotNull(slots[i], type + ": 칸 " + i + " 에 키가 없다");
            for (EntityDataKey<?> k : EntityDataKeys.all()) {
                int i = k.index().orElseThrow();
                if (i >= l.length || slots[i] != k) assertFalse(EntityData767.has(type, k), type + " 에 " + k);
            }
        }
        assertEquals(130, types, "엔티티 종류 130개(플레이어 포함)");
        assertEquals(byOwner.keySet(), usedOwners, "쓰이지 않는 주인 부류가 없다");
    }

    /** 값 하나를 못 읽어도 패킷은 남는다 — 그 칸을 Unsupported 로 두고 멈춘다(리스너가 관찰·취소할 수 있게). */
    @Test
    void unreadableValueLeavesObservablePacket() {
        for (int[] bad : new int[][] {{19, 22, 999}, {8, 26, 0}, {22, 23, 0}, {19, 22, -5}}) {   // 범위 밖 고양이, 직접 정의 그림·늑대, 음수
            ByteBuf b = Unpooled.buffer();
            org.domi.furnace.server.net.io.Types.writeVarInt(b, 7);
            b.writeByte(0); org.domi.furnace.server.net.io.Types.writeVarInt(b, 0); b.writeByte(0x40);
            b.writeByte(bad[0]); org.domi.furnace.server.net.io.Types.writeVarInt(b, bad[1]); org.domi.furnace.server.net.io.Types.writeVarInt(b, bad[2]);
            b.writeByte(3); org.domi.furnace.server.net.io.Types.writeVarInt(b, 8); b.writeBoolean(true);
            b.writeByte(0xFF);
            var p = assertDoesNotThrow(() -> (ClientboundSetEntityDataPacket) codec.decode(PacketTypes.Clientbound.SET_ENTITY_DATA, SetEntityData.ID, b));
            assertTrue(p.hasUnsupported());
            assertEquals(2, p.entries().size(), "못 읽은 칸에서 멈춘다");
            assertEquals(new ClientboundSetEntityDataPacket.Unsupported(bad[1]), p.entries().get(1).value());
            assertEquals(Optional.of((byte) 0x40), p.get(EntityDataKeys.Entity.SHARED_FLAGS));
        }
    }

    /** 칸 번호 표는 한 번만 걸린다 — 플러그인 하나가 바꿔 모든 플러그인의 칸 번호를 틀어지게 할 수 없다. */
    @Test
    void indexResolverCannotBeReplaced() {
        assertEquals(OptionalInt.of(12), EntityDataKeys.Display.SCALE.index());
        assertThrows(IllegalStateException.class, () -> EntityDataKey.installIndexResolver(k -> OptionalInt.of(0)));
        assertEquals(OptionalInt.of(12), EntityDataKeys.Display.SCALE.index(), "거부된 뒤에도 그대로");
    }
}
