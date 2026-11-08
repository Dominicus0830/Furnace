package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Pig;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.LazyMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.HoloProbe;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * helper 5.6.14 의 BukkitHologram.spawn() 이 부르는 그대로(같은 순서·같은 값) 갑옷 거치대 + 투명 돼지 홀로그램 줄을 만드는 플러그인 jar 를
 * 올려, 서버 쪽 엔티티 상태(NBT)·메타데이터·클라이언트로 가는 패킷·클릭 이벤트 다리를 Paper 1.21.1 과 대조한다.
 */
class HologramEntitiesTest {

    private static final String YML = """
            name: Holo
            version: '1'
            main: holo.HoloPlugin
            api-version: '1.21'
            """;

    /** helper 의 BukkitHologram.spawn()·setClickCallback 을 옮긴 모양(시험용 명령으로 부른다). */
    private static final String PLUGIN = """
            package holo;
            import java.util.*;
            import org.bukkit.*;
            import org.bukkit.command.*;
            import org.bukkit.entity.*;
            import org.bukkit.event.*;
            import org.bukkit.event.entity.*;
            import org.bukkit.event.player.*;
            import org.bukkit.metadata.FixedMetadataValue;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.bukkit.potion.*;
            import org.domi.furnace.compat.paper.fixtures.HoloProbe;
            public final class HoloPlugin extends JavaPlugin implements Listener {
                static final java.lang.reflect.Method SET_CAN_TICK;
                static {
                    java.lang.reflect.Method m = null;
                    try { m = ArmorStand.class.getDeclaredMethod("setCanTick", boolean.class); } catch (Throwable t) { HoloProbe.LOG.add("setCanTick:" + t.getClass().getSimpleName()); }
                    SET_CAN_TICK = m;
                }
                @Override public void onEnable() {
                    getServer().getPluginManager().registerEvents(this, this);
                    getServer().getCommandMap().register("holo", new Command("holo") {
                        @Override public boolean execute(CommandSender s, String l, String[] a) {
                            try { run(a); } catch (Throwable t) { HoloProbe.failure = t; }
                            return true;
                        }
                    });
                }
                static Location at(String[] a) {
                    return new Location(Bukkit.getWorlds().get(0), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
                }
                void run(String[] a) throws Exception {
                    switch (a[0]) {
                        case "build" -> build(at(a), a[4]);
                        case "rename" -> ((ArmorStand) HoloProbe.stand).setCustomName(a[1]);
                        case "plain" -> HoloProbe.plainPig = at(a).getWorld().spawnEntity(at(a), EntityType.PIG);
                        case "read" -> read();
                        case "consumer" -> {
                            List<String> seen = new ArrayList<>();
                            ArmorStand as = at(a).getWorld().spawn(at(a), ArmorStand.class, st -> { st.setSmall(true); seen.add("in:" + st.getType() + ":" + st.isSmall()); });
                            seen.add("after:" + as.isSmall() + ":" + as.isValid());
                            HoloProbe.LOG.add("consumer:" + seen);
                            as.remove();
                        }
                        case "bad" -> {
                            try { at(a).getWorld().spawn(at(a), Entity.class); HoloProbe.LOG.add("bad:none"); }
                            catch (IllegalArgumentException e) { HoloProbe.LOG.add("bad:IAE:" + e.getMessage()); }
                            try { at(a).getWorld().spawn(at(a), Zombie.class); HoloProbe.LOG.add("zombie:none"); }
                            catch (RuntimeException e) { HoloProbe.LOG.add("zombie:" + e.getClass().getSimpleName()); }
                        }
                        default -> throw new IllegalArgumentException(a[0]);
                    }
                }
                void build(Location loc, String line) throws Exception {
                    if (!loc.getChunk().isLoaded()) loc.getChunk().load();
                    loc.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0).forEach(e -> {
                        if (e.getType() == EntityType.ARMOR_STAND && e.getLocation().equals(loc)) e.remove();
                    });
                    ArmorStand as = (ArmorStand) loc.getWorld().spawn(loc, ArmorStand.class);
                    as.setSmall(true);
                    as.setMarker(true);
                    as.setArms(false);
                    as.setBasePlate(false);
                    as.setGravity(false);
                    as.setVisible(false);
                    as.setCustomName(line);
                    as.setCustomNameVisible(true);
                    as.setAI(false);
                    as.setCollidable(false);
                    as.setInvulnerable(true);
                    if (SET_CAN_TICK != null) SET_CAN_TICK.invoke(as, false);
                    Pig pig = (Pig) as.getWorld().spawnEntity(as.getLocation(), EntityType.PIG);
                    HoloProbe.LOG.add("effect:" + pig.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false)));
                    pig.setCustomNameVisible(false);
                    pig.setSilent(true);
                    pig.setGravity(false);
                    pig.setMetadata("nodespawn", new FixedMetadataValue(this, true));
                    pig.setAI(false);
                    pig.setCollidable(false);
                    pig.setInvulnerable(true);
                    HoloProbe.LOG.add("ride:" + as.addPassenger(pig));
                    HoloProbe.stand = as;
                    HoloProbe.pig = pig;
                }
                void read() {
                    ArmorStand as = (ArmorStand) HoloProbe.stand;
                    Pig pig = (Pig) HoloProbe.pig;
                    HoloProbe.LOG.add("stand:" + List.of(as.isSmall(), as.isMarker(), as.hasArms(), as.hasBasePlate(), as.hasGravity(), as.isVisible(),
                            as.isCustomNameVisible(), as.hasAI(), as.isCollidable(), as.isInvulnerable()) + ":" + as.getCustomName());
                    HoloProbe.LOG.add("riding:" + as.getPassengers().equals(List.of(pig)) + ":" + pig.isInsideVehicle() + ":" + as.equals(pig.getVehicle()) + ":" + as.isEmpty());
                    PotionEffect inv = pig.getPotionEffect(PotionEffectType.INVISIBILITY);
                    HoloProbe.LOG.add("pig:" + List.of(pig.hasPotionEffect(PotionEffectType.INVISIBILITY), inv.getAmplifier() == 0, inv.isAmbient(), inv.hasParticles(),
                            inv.getDuration() > 2_000_000_000, pig.isSilent(), pig.hasGravity(), pig.hasAI(), pig.isCollidable(), pig.isInvulnerable(), pig.isCustomNameVisible()));
                    HoloProbe.LOG.add("meta:" + pig.hasMetadata("nodespawn") + ":" + pig.getMetadata("nodespawn").get(0).asBoolean()
                            + ":" + (pig.getMetadata("nodespawn").get(0).getOwningPlugin() == this) + ":" + as.hasMetadata("nodespawn"));
                }
                @EventHandler
                public void onInteract(PlayerInteractEntityEvent e) {
                    boolean holo = e.getRightClicked().equals(HoloProbe.pig);
                    HoloProbe.LOG.add("interact:" + e.getClass().getSimpleName() + ":" + e.getRightClicked().getType() + ":" + e.getHand() + ":" + holo
                            + ":" + e.getRightClicked().hasMetadata("nodespawn") + ":" + e.getPlayer().getName());
                    if (e.getRightClicked() instanceof Pig && holo) e.setCancelled(true);
                }
                @EventHandler
                public void onHit(EntityDamageByEntityEvent e) {
                    HoloProbe.LOG.add("byEntity:" + (e.getDamager() instanceof Player) + ":" + e.getEntity().getType() + ":" + e.getCause() + ":" + (e.getDamage() > 0));
                    if (e.getEntity() instanceof Pig && HoloProbe.cancelDamage) e.setCancelled(true);
                }
                @EventHandler(priority = EventPriority.MONITOR)
                public void onDamage(EntityDamageEvent e) {
                    if (e.getEntity() instanceof Pig) HoloProbe.LOG.add("damage:" + e.getClass().getSimpleName() + ":" + e.isCancelled());
                }
                @EventHandler public void onZap(PigZapEvent e) { HoloProbe.LOG.add("zap"); }
                @EventHandler public void onAt(PlayerInteractAtEntityEvent e) { HoloProbe.LOG.add("at"); }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { HoloProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("holo.jar"), YML, Map.of("holo.HoloPlugin", PLUGIN));
        FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        return server;
    }

    private static void ok() {
        if (HoloProbe.failure != null) throw new AssertionError("플러그인이 던졌다: " + HoloProbe.failure, HoloProbe.failure);
    }

    /** 접속 직후 핸들은 아직 원점에 있다 — 평지 월드 지면(y<0)에 선 위치를 볼 때까지 틱을 돌린다. */
    private static org.domi.furnace.api.world.Location settled(FurnaceTestServer server, TestPlayer tp) {
        server.tick(3);
        for (int i = 0; i < 100 && tp.player().location().y() >= 0; i++) server.tick();
        var loc = tp.player().location();
        assertTrue(loc.y() < 0, () -> "플레이어가 지면에 서지 않았다: " + loc);
        return loc;
    }

    private static BlockPos block(double x, double y, double z) { return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }

    @Test
    void helperHologramLineIsBuiltWithTheSameEntityStateAsPaper() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Nora");
            var p = settled(server, tp);
            double x = Math.floor(p.x()) + 0.5, y = Math.floor(p.y()) + 1, z = Math.floor(p.z()) + 2.5;
            BlockPos pos = block(x, y, z);

            Map<UUID, Integer> ids = new ConcurrentHashMap<>();
            List<ClientboundSetEntityDataPacket> data = new CopyOnWriteArrayList<>();
            AtomicInteger passengerPackets = new AtomicInteger();
            var packets = server.internals().plugins().packetsImpl();
            packets.listenInternal(PacketTypes.Clientbound.ADD_ENTITY, EventPriority.MONITOR, e -> {
                if (e.player().name().equals("Nora")) ids.put(e.packet().uuid(), e.packet().entityId());
            });
            packets.listenInternal(PacketTypes.Clientbound.SET_ENTITY_DATA, EventPriority.MONITOR, e -> {
                if (e.player().name().equals("Nora")) data.add(e.packet());
            });
            packets.listenInternal(PacketTypes.Clientbound.SET_PASSENGERS, EventPriority.MONITOR, e -> {
                if (e.player().name().equals("Nora")) passengerPackets.incrementAndGet();
            });

            server.execute("holo build " + x + " " + y + " " + z + " §aLine_one");
            server.tick(3);
            ok();
            assertEquals(List.of("setCanTick:NoSuchMethodException"), HoloProbe.log("setCanTick"), "Paper 전용 setCanTick 은 없다(helper 는 null 이면 건너뛴다)");
            assertEquals(List.of("effect:true", "ride:true"), HoloProbe.LOG.stream().filter(s -> s.startsWith("effect") || s.startsWith("ride")).toList());
            assertInstanceOf(ArmorStand.class, HoloProbe.stand);
            assertInstanceOf(Pig.class, HoloProbe.pig);
            assertEquals("org.bukkit.craftbukkit.entity.CraftArmorStand", HoloProbe.stand.getClass().getName());
            assertEquals("org.bukkit.craftbukkit.entity.CraftPig", HoloProbe.pig.getClass().getName());

            // ---- 서버 쪽 NBT
            NbtCompound st = nbt(server, HoloProbe.stand);
            assertTrue(st.getBoolean("Small"), st::toString);
            assertTrue(st.getBoolean("Marker"), st::toString);
            assertTrue(st.getBoolean("NoBasePlate"), st::toString);
            assertFalse(st.getBoolean("ShowArms"), st::toString);
            assertTrue(st.getBoolean("NoGravity"), st::toString);
            assertTrue(st.getBoolean("Invisible"), st::toString);
            assertTrue(st.getBoolean("CustomNameVisible"), st::toString);
            assertTrue(st.getBoolean("Invulnerable"), st::toString);
            assertTrue(st.getString("CustomName").contains("Line_one"), st::toString);
            NbtCompound pg = nbt(server, HoloProbe.pig);
            assertTrue(pg.getBoolean("NoAI"), pg::toString);
            assertTrue(pg.getBoolean("Silent"), pg::toString);
            assertTrue(pg.getBoolean("NoGravity"), pg::toString);
            assertTrue(pg.getBoolean("Invulnerable"), pg::toString);
            assertFalse(pg.getBoolean("CustomNameVisible"), pg::toString);
            var effects = pg.getList("active_effects");
            assertEquals(1, effects.size(), pg::toString);
            NbtCompound inv = effects.getCompound(0);
            assertEquals("minecraft:invisibility", inv.getString("id"));
            assertEquals(0, inv.getInt("amplifier"));
            assertTrue(inv.getInt("duration") > 2_000_000_000, "Integer.MAX_VALUE 틱(무한 -1 이 아니다): " + inv);
            assertFalse(inv.getBoolean("ambient"));
            assertEquals(0, inv.getByte("show_particles"), "입자 숨김: " + inv);

            // ---- region 스레드에서 본 상태(투명 효과가 Invisible 플래그를 켰고 밀리지 않는다)
            var standApi = ((CraftEntity) HoloProbe.stand).furnaceHandle();
            var pigApi = ((CraftEntity) HoloProbe.pig).furnaceHandle();
            assertEquals(List.of(true, false), server.onRegion(pos, () -> {
                var ps = (org.domi.furnace.api.entity.LivingEntityState) pigApi.stateNow().orElseThrow();
                return List.of(ps.isInvisible(), ps.isCollidable());
            }));
            assertEquals(List.of(pigApi.uniqueId()), server.onRegion(pos, () -> standApi.stateNow().orElseThrow().passengers().stream().map(e -> e.uniqueId()).toList()));

            // ---- 같은 상태를 Bukkit getter 로(옛 명령 = 전역 + 빌림)
            server.execute("holo read");
            server.tick();
            ok();
            assertEquals(List.of("stand:[true, true, false, false, false, false, true, false, false, true]:§aLine_one"), HoloProbe.log("stand:"));
            assertEquals(List.of("riding:true:true:true:false"), HoloProbe.log("riding:"));
            assertEquals(List.of("pig:[true, true, false, false, true, true, false, false, false, true, false]"), HoloProbe.log("pig:"));
            assertEquals(List.of("meta:true:true:true:false"), HoloProbe.log("meta:"));

            // ---- 클라이언트가 실제로 받는다: 생성·탑승·이름 바꾸기
            Integer standId = ids.get(standApi.uniqueId());
            assertNotNull(standId, "갑옷 거치대 생성 패킷을 받았다: " + ids);
            assertNotNull(ids.get(pigApi.uniqueId()), "돼지 생성 패킷");
            assertTrue(passengerPackets.get() > 0, "탑승 패킷(set_passengers)");
            data.clear();
            server.execute("holo rename §bRenamed");
            server.tick(3);
            ok();
            assertTrue(data.stream().filter(d -> d.entityId() == standId).flatMap(d -> d.entries().stream())
                    .anyMatch(en -> en.index() == 2 && en.value() instanceof ClientboundSetEntityDataPacket.OptionalTextValue t
                            && t.value() != null && t.value().plainText().contains("Renamed")),
                    "이름 칸(2) 갱신이 클라이언트로 갔다: " + data);

            // ---- 메타데이터: Paper 처럼 플러그인을 꺼도 남는다(/reload 때만 지운다)
            Plugin holo = Bukkit.getPluginManager().getPlugin("Holo");
            assertNotNull(holo);
            server.onGlobal(() -> { Bukkit.getPluginManager().disablePlugin(holo); return null; });
            Pig pig = (Pig) HoloProbe.pig;
            assertTrue(pig.hasMetadata("nodespawn"), "끄고 나도 값이 남는다(Paper 1.21.1 과 같다)");
            assertSame(holo, pig.getMetadata("nodespawn").get(0).getOwningPlugin());
            pig.removeMetadata("nodespawn", holo);
            assertFalse(pig.hasMetadata("nodespawn"));
        }
    }

    @Test
    void clickAndHitReachHelperStyleListeners() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Nora");
            var p = settled(server, tp);
            double x = Math.floor(p.x()) + 0.5, y = Math.floor(p.y()), z = Math.floor(p.z()) + 1.5;
            Map<UUID, Integer> ids = new ConcurrentHashMap<>();
            server.internals().plugins().packetsImpl().listenInternal(PacketTypes.Clientbound.ADD_ENTITY, EventPriority.MONITOR, e -> {
                if (e.player().name().equals("Nora")) ids.put(e.packet().uuid(), e.packet().entityId());
            });
            server.execute("holo build " + x + " " + y + " " + z + " Click_me");
            server.execute("holo plain " + (x + 1) + " " + y + " " + z);
            server.tick(3);
            ok();
            UUID holoPig = ((CraftEntity) HoloProbe.pig).getUniqueId();
            UUID plain = ((CraftEntity) HoloProbe.plainPig).getUniqueId();
            assertNotNull(ids.get(holoPig), "홀로그램 돼지를 클라이언트가 안다");
            assertNotNull(ids.get(plain), "평범한 돼지를 클라이언트가 안다");

            // 오른쪽 클릭(interact 패킷 INTERACT, 주 손) → PlayerInteractEntityEvent, 리스너가 취소한다
            int holoId = ids.get(holoPig);
            tp.sendPacket(0x16, b -> {
                Types.writeVarInt(b, holoId);
                Types.writeVarInt(b, 0);
                Types.writeVarInt(b, 0);
                b.writeBoolean(false);
            });
            server.tick(2);
            ok();
            assertEquals(List.of("interact:PlayerInteractEntityEvent:PIG:HAND:true:true:Nora"), HoloProbe.log("interact:"), HoloProbe.LOG::toString);

            // 평범한 돼지를 때린다(ATTACK) → EntityDamageByEntityEvent(때린 쪽 = 플레이어). 처리기 목록은 EntityDamageEvent 와 같다
            int plainId = ids.get(plain);
            HoloProbe.cancelDamage = true;
            float before = health(server, HoloProbe.plainPig);
            tp.sendPacket(0x16, b -> {
                Types.writeVarInt(b, plainId);
                Types.writeVarInt(b, 1);
                b.writeBoolean(false);
            });
            server.tick(2);
            ok();
            assertEquals(List.of("byEntity:true:PIG:ENTITY_ATTACK:true"), HoloProbe.log("byEntity:"));
            assertEquals(List.of("damage:EntityDamageByEntityEvent:true"), HoloProbe.log("damage:"), "EntityDamageEvent 리스너도 하위 이벤트를 받는다");
            assertEquals(before, health(server, HoloProbe.plainPig), "취소하면 체력이 그대로");

            HoloProbe.cancelDamage = false;
            HoloProbe.LOG.clear();
            server.tick(20);   // 무적 시간·공격 쿨다운이 지나게
            tp.sendPacket(0x16, b -> {
                Types.writeVarInt(b, plainId);
                Types.writeVarInt(b, 1);
                b.writeBoolean(false);
            });
            server.tick(2);
            ok();
            assertEquals(List.of("damage:EntityDamageByEntityEvent:false"), HoloProbe.log("damage:"));
            assertTrue(health(server, HoloProbe.plainPig) < before, "취소하지 않으면 맞는다");

            // 불리지 않는 이벤트도 리스너는 오류 없이 올라갔다
            assertTrue(HoloProbe.log("zap").isEmpty());
            assertTrue(HoloProbe.log("at").isEmpty());
        }
    }

    private static float health(FurnaceTestServer server, Object entity) { return nbt(server, entity).getFloat("Health"); }

    /** 엔티티 작업은 그 엔티티의 틱에 돈다 — 맡기고 한 틱 돌린 뒤 받는다. */
    private static NbtCompound nbt(FurnaceTestServer server, Object entity) {
        var f = ((CraftEntity) entity).furnaceHandle().readNbt();
        server.tick();
        return server.await(f);
    }

    @Test
    void spawnByClassConsumerAndBadKinds() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Nora");
            var p = settled(server, tp);
            double x = Math.floor(p.x()) + 3.5, y = Math.floor(p.y()), z = Math.floor(p.z()) + 0.5;
            server.execute("holo consumer " + x + " " + y + " " + z);
            server.execute("holo bad " + x + " " + y + " " + z);
            server.tick(2);
            ok();
            assertEquals(List.of("consumer:[in:ARMOR_STAND:true, after:true:true]"), HoloProbe.log("consumer:"), "Consumer 는 돌려주기 전에 같은 틱에 돈다");
            assertEquals(1, HoloProbe.log("bad:IAE:").size(), () -> "추상 종류는 IllegalArgumentException(Paper): " + HoloProbe.LOG);
            assertEquals(List.of("zombie:UnsupportedFeature"), HoloProbe.log("zombie:"), "아직 없는 종류는 무엇이 없는지 말한다");
        }
    }

    @Test
    void offThreadSettersAreScheduledAndGettersRefuse() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Nora");
            var p = settled(server, tp);
            double x = Math.floor(p.x()) + 0.5, y = Math.floor(p.y()) + 1, z = Math.floor(p.z()) - 2.5;
            server.execute("holo build " + x + " " + y + " " + z + " Off");
            server.tick(2);
            ok();
            ArmorStand as = (ArmorStand) HoloProbe.stand;
            // 시험 스레드는 소유 스레드가 아니다: 쓰기는 예약되고, 읽기는 던진다
            as.setSmall(false);
            as.setCustomNameVisible(false);
            assertThrows(ThreadOwnershipException.class, as::isSmall);
            server.tick(2);
            NbtCompound st = nbt(server, as);
            assertFalse(st.getBoolean("Small"), st::toString);
            assertFalse(st.getBoolean("CustomNameVisible"), st::toString);
            BlockPos pos = block(x, y, z);
            assertFalse(server.onRegion(pos, as::isSmall), "소유 스레드에서는 곧바로 읽는다");
            // 이름만은 소유 스레드 밖에서도 던지지 않고 핸들 시점(여기서는 이름을 달기 전 spawn 때) 값을 준다 — Paper 와 다른 점
            assertEquals("Off", server.onRegion(pos, as::getCustomName));
            assertNull(as.getCustomName(), "소유 스레드 밖: spawn 때 스냅숏");
        }
    }

    @Test
    void metadataValuesFollowBukkitSemantics() {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (o, m, a) -> switch (m.getName()) {
            case "getName" -> "P";
            case "hashCode" -> System.identityHashCode(o);
            case "equals" -> o == a[0];
            case "toString" -> "P";
            default -> null;
        });
        AtomicInteger calls = new AtomicInteger();
        LazyMetadataValue cached = new LazyMetadataValue(plugin, () -> calls.incrementAndGet());
        assertEquals(1, cached.asInt());
        assertEquals(1, cached.asInt());
        assertEquals(1, calls.get(), "기본은 처음 한 번만 계산한다");
        cached.invalidate();
        assertEquals(2, cached.asInt());
        LazyMetadataValue never = new LazyMetadataValue(plugin, LazyMetadataValue.CacheStrategy.NEVER_CACHE, () -> calls.incrementAndGet());
        assertEquals(3, never.asInt());
        assertEquals(4, never.asInt());

        MetadataValue fixed = new FixedMetadataValue(plugin, "12");
        assertEquals(12, fixed.asInt());
        assertEquals(12.0, fixed.asDouble());
        assertEquals("12", fixed.asString());
        assertTrue(new FixedMetadataValue(plugin, "true").asBoolean());
        assertFalse(new FixedMetadataValue(plugin, 0).asBoolean());
        assertTrue(new FixedMetadataValue(plugin, 2).asBoolean());
        assertEquals("", new FixedMetadataValue(plugin, null).asString());
        assertSame(plugin, fixed.getOwningPlugin());
        assertThrows(IllegalArgumentException.class, () -> new FixedMetadataValue(null, 1));
    }
}
