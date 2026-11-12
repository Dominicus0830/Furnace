// 엔티티 속성 API(E6): API 속성 표가 서버 표와 같은지, 핸들(만든 시점 읽기 + 소유 스레드 쓰기)·지금 값 손잡이·Bukkit 의미(중복 수정자 예외,
// 범위 자르기, transient 저장 제외, registerAttribute)·플레이어(/attribute 와 같은 맵, 최근 값 스냅샷).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.LivingEntity;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.attribute.Attribute;
import org.domi.furnace.api.entity.attribute.AttributeInstance;
import org.domi.furnace.api.entity.attribute.AttributeModifier;
import org.domi.furnace.api.item.AttributeModifiers.Operation;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Pig;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityAttributeApiTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private <T> T onRegion(java.util.function.Supplier<T> task) throws Exception {
        return core.world().submitAtChunk(C00, r -> task.get()).get(30, TimeUnit.SECONDS);
    }

    /** 자동 틱이 없으니 region 을 직접 틱해 엔티티·플레이어 작업 대기열을 돌리고 결과를 기다린다. */
    private <T> T run(java.util.concurrent.CompletableFuture<T> f) throws Exception {
        core.world().submitAtChunk(C00, r -> {
            for (int i = 0; i < 2; i++) r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        return f.get(10, TimeUnit.SECONDS);
    }

    private record Spawned(LivingEntity handle, Pig raw) {}

    private Spawned spawnPig() throws Exception {
        return onRegion(() -> {
            var level = core.world().regionizer().ownerOf(0, 0).level();
            Pig pig = new Pig(level, level.nextEntityId());
            pig.moveTo(6.5, -60, 6.5, 0, 0);
            level.addFreshEntity(pig);
            return new Spawned((LivingEntity) EntityHandle.of(host, pig), pig);
        });
    }

    /** 새 핸들(최신 스냅샷)로 다시 얻는다. */
    private LivingEntity fresh(Spawned s) throws Exception {
        return onRegion(() -> (LivingEntity) EntityHandle.of(host, s.raw()));
    }

    private static Throwable cause(Throwable t) {
        while ((t instanceof ExecutionException || t instanceof CompletionException) && t.getCause() != null) t = t.getCause();
        return t;
    }

    // ---------------------------------------------------------------- 표·이름

    @Test
    void apiTableMatchesServerRegistry() {
        var all = Attribute.values();
        int n = 0;
        for (int id = 0; ; id++) {
            org.domi.furnace.server.attribute.Attribute s;
            try {
                s = org.domi.furnace.server.attribute.Attribute.byId(id);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            Attribute a = all.get(id);
            assertEquals(s.name(), a.key(), "순서·키 " + id);
            assertEquals(s.defaultValue(), a.defaultValue(), 0.0, a.key());
            assertEquals(s.sanitize(-1e300), a.minValue(), 0.0, a.key() + " min");
            assertEquals(s.sanitize(1e300), a.maxValue(), 0.0, a.key() + " max");
            n++;
        }
        assertEquals(n, all.size(), "API 표가 서버 표와 같은 개수");
    }

    @Test
    void namesResolveAcrossVersionsAndLegacy() {
        assertSame(Attribute.MOVEMENT_SPEED, Attribute.of("minecraft:generic.movement_speed"));
        assertSame(Attribute.MOVEMENT_SPEED, Attribute.of("generic.movement_speed"));
        assertSame(Attribute.MOVEMENT_SPEED, Attribute.of("minecraft:movement_speed"), "1.21.2 이후 이름");
        assertSame(Attribute.MOVEMENT_SPEED, Attribute.of("movement_speed"));
        assertSame(Attribute.MOVEMENT_SPEED, Attribute.of("generic.movementSpeed"), "옛 Bukkit 이름");
        assertSame(Attribute.MAX_HEALTH, Attribute.of("Max Health"));
        assertSame(Attribute.JUMP_STRENGTH, Attribute.of("horse.jump_strength"));
        assertSame(Attribute.BLOCK_BREAK_SPEED, Attribute.of("minecraft:block_break_speed"));
        assertSame(Attribute.SPAWN_REINFORCEMENTS, Attribute.of("zombie.spawn_reinforcements"));
        assertTrue(Attribute.find("other:movement_speed").isEmpty());
        assertTrue(Attribute.find("minecraft:nope").isEmpty());
        assertTrue(Attribute.find(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Attribute.of("nope"));
        assertEquals(1024.0, Attribute.MAX_HEALTH.sanitize(5000), 0.0);
        assertEquals(1.0, Attribute.MAX_HEALTH.sanitize(Double.NaN), 0.0, "NaN 은 최소로");
    }

    @Test
    void modifierRecordNormalizesAndValidatesId() {
        assertEquals("minecraft:boost", new AttributeModifier("boost", 1, Operation.ADD_VALUE).id());
        assertEquals("my_plugin:boost/a", new AttributeModifier("my_plugin:boost/a", 1, Operation.ADD_VALUE).id());
        assertThrows(IllegalArgumentException.class, () -> new AttributeModifier("", 1, Operation.ADD_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new AttributeModifier("Boost", 1, Operation.ADD_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new AttributeModifier("a:", 1, Operation.ADD_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new AttributeModifier(":a", 1, Operation.ADD_VALUE));
        var entry = new AttributeModifier("x:y", 0.5, Operation.ADD_MULTIPLIED_BASE).toEntry(Attribute.ATTACK_DAMAGE, "mainhand");
        assertEquals("minecraft:generic.attack_damage", entry.attribute());
        assertEquals(new AttributeModifier("x:y", 0.5, Operation.ADD_MULTIPLIED_BASE), AttributeModifier.of(entry));
    }

    // ---------------------------------------------------------------- 핸들(몹)

    @Test
    void handleReadsSnapshotAndMissingAttributeIsEmpty() throws Exception {
        Spawned s = spawnPig();
        var speed = s.handle().attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        double rawBase = onRegion(() -> s.raw().attributes().baseValue(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED));
        assertEquals(rawBase, speed.baseValue(), 0.0);
        assertEquals(rawBase, speed.value(), 0.0);
        assertEquals(0.7, speed.defaultValue(), 0.0, "Bukkit getDefaultValue 는 속성 기본값");
        assertTrue(speed.modifiers().isEmpty());
        assertEquals(Attribute.MOVEMENT_SPEED, speed.attribute());
        assertTrue(s.handle().attribute(Attribute.ATTACK_DAMAGE).isEmpty(), "돼지는 공격력 속성이 없다");
        assertTrue(s.handle().attributes().contains(Attribute.MAX_HEALTH));
        assertFalse(s.handle().attributes().contains(Attribute.ATTACK_DAMAGE));
        assertEquals(5.0, s.handle().attributeValue(Attribute.ATTACK_DAMAGE, 5.0), 0.0);
        assertEquals(s.handle().maxHealth(), s.handle().attributeValue(Attribute.MAX_HEALTH, -1), 0.0);
    }

    @Test
    void setBaseValueGoesToOwnerThread_clampsValueKeepsBase_andTrimsHealth() throws Exception {
        Spawned s = spawnPig();
        var hp = s.handle().attribute(Attribute.MAX_HEALTH).orElseThrow();
        run(hp.setBaseValue(5000));
        assertEquals(5000, onRegion(() -> s.raw().attributes().get(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH).baseValue()), 0.0, "기본값은 넣은 그대로");
        assertEquals(1024.0, onRegion(() -> s.raw().attributeValue(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH)), 0.0, "값은 범위로 잘린다");
        // 기존 핸들은 만든 시점 값, 새 핸들은 바뀐 값
        assertEquals(10.0, hp.baseValue(), 0.0);
        var again = fresh(s).attribute(Attribute.MAX_HEALTH).orElseThrow();
        assertEquals(5000, again.baseValue(), 0.0);
        assertEquals(1024.0, again.value(), 0.0);
        // 최대 체력을 줄이면 체력도 따라 잘린다(바닐라 onAttributeUpdated)
        onRegion(() -> {
            s.raw().setHealth(1024f);
            return null;
        });
        String thread = run(again.<String>edit(i -> {
            i.setBaseValue(4);
            return Thread.currentThread().getName();
        }));
        assertNotEquals(Thread.currentThread().getName(), thread);
        onRegion(() -> {
            s.raw().tick();   // 몹 틱 끝의 refreshDirtyAttributes — 틱하는 플레이어가 없으니 직접 한 번
            return null;
        });
        assertEquals(4f, onRegion(s.raw()::health), 0f);
    }

    @Test
    void modifiersFollowTheVanillaFormula() throws Exception {
        Spawned s = spawnPig();
        var speed = s.handle().attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        double base = speed.baseValue();
        run(speed.addModifier(new AttributeModifier("t:add", 0.05, Operation.ADD_VALUE)));
        run(speed.addModifier(new AttributeModifier("t:base", 0.5, Operation.ADD_MULTIPLIED_BASE)));
        run(speed.addModifier(new AttributeModifier("t:total", 0.1, Operation.ADD_MULTIPLIED_TOTAL)));
        double expected = (base + 0.05 + (base + 0.05) * 0.5) * 1.1;
        var now = fresh(s).attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        assertEquals(expected, now.value(), 1e-12);
        assertEquals(base, now.baseValue(), 0.0);
        assertEquals(List.of("t:add", "t:base", "t:total"), now.modifiers().stream().map(AttributeModifier::id).toList(), "단 순서");
        assertEquals(Operation.ADD_MULTIPLIED_BASE, now.modifier("t:base").orElseThrow().operation());
        assertTrue(now.modifier("t:none").isEmpty());
        assertTrue(run(now.removeModifier("t:base")));
        assertFalse(run(now.removeModifier("t:base")), "없으면 false");
        assertEquals((base + 0.05) * 1.1, fresh(s).attribute(Attribute.MOVEMENT_SPEED).orElseThrow().value(), 1e-12);
        // /attribute 명령과 같은 상태
        assertEquals(0.05, onRegion(() -> s.raw().attributes().get(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED).getModifier("t:add").amount()), 0.0);
    }

    @Test
    void addModifierRejectsDuplicateId_setModifierReplaces() throws Exception {
        Spawned s = spawnPig();
        var speed = s.handle().attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        var m = new AttributeModifier("t:dup", 0.1, Operation.ADD_VALUE);
        run(speed.addModifier(m));
        var ex = assertThrows(Exception.class, () -> run(speed.addModifier(new AttributeModifier("t:dup", 0.2, Operation.ADD_VALUE))));
        assertInstanceOf(IllegalArgumentException.class, cause(ex));
        assertEquals("Modifier is already applied on this attribute!", cause(ex).getMessage());
        var ex2 = assertThrows(Exception.class, () -> run(speed.addTransientModifier(m)));
        assertInstanceOf(IllegalArgumentException.class, cause(ex2));
        assertEquals(0.1, fresh(s).attribute(Attribute.MOVEMENT_SPEED).orElseThrow().modifier("t:dup").orElseThrow().amount(), 0.0, "실패한 add 는 바꾸지 않는다");
        run(speed.setModifier(new AttributeModifier("t:dup", 0.3, Operation.ADD_VALUE)));
        assertEquals(0.3, fresh(s).attribute(Attribute.MOVEMENT_SPEED).orElseThrow().modifier("t:dup").orElseThrow().amount(), 0.0);
    }

    private static NbtCompound entry(NbtList list, String attr) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof NbtCompound c && attr.equals(c.tags().get("id"))) return c;
        return null;
    }

    @Test
    void permanentModifiersAreSavedTransientAreNot() throws Exception {
        Spawned s = spawnPig();
        var speed = s.handle().attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        run(speed.addModifier(new AttributeModifier("t:keep", 0.1, Operation.ADD_VALUE)));
        run(speed.addTransientModifier(new AttributeModifier("t:temp", 0.2, Operation.ADD_VALUE)));
        NbtList saved = onRegion(() -> s.raw().attributes().save());
        var e = entry(saved, "minecraft:generic.movement_speed");
        var mods = (NbtList) e.tags().get("modifiers");
        assertEquals(1, mods.size(), "transient 는 저장하지 않는다");
        assertEquals("t:keep", ((NbtCompound) mods.get(0)).tags().get("id"));
        // 값에는 둘 다 든다
        assertEquals(onRegion(() -> s.raw().attributes().baseValue(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED)) + 0.3,
                fresh(s).attribute(Attribute.MOVEMENT_SPEED).orElseThrow().value(), 1e-12);
        // 같은 id 를 permanent 로 바꾸면 저장 대상이 된다, 떼면 transient 표시도 사라진다
        run(speed.setModifier(new AttributeModifier("t:temp", 0.2, Operation.ADD_VALUE)));
        assertEquals(2, ((NbtList) entry(onRegion(() -> s.raw().attributes().save()), "minecraft:generic.movement_speed").tags().get("modifiers")).size());
        run(speed.removeModifier("t:temp"));
        run(speed.addModifier(new AttributeModifier("t:temp", 0.2, Operation.ADD_VALUE)));
        assertEquals(2, ((NbtList) entry(onRegion(() -> s.raw().attributes().save()), "minecraft:generic.movement_speed").tags().get("modifiers")).size());
    }

    // ---------------------------------------------------------------- 지금 값 손잡이

    @Test
    void liveInstanceNeedsTheOwnerThreadAndSeesCurrentValues() throws Exception {
        Spawned s = spawnPig();
        var speed = s.handle().attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        // edit 안에서는 읽은 뒤 쓰기가 이어진다
        double after = run(speed.<Double>edit(i -> {
            double b = i.baseValue();
            i.setBaseValue(b * 2);
            i.addModifier(new AttributeModifier("t:live", 0.1, Operation.ADD_VALUE));
            assertTrue(i.hasModifier("t:live"));
            assertEquals(Attribute.MOVEMENT_SPEED.defaultValue(), i.defaultValue(), 0.0);
            return i.value();
        }));
        assertEquals(onRegion(() -> s.raw().attributeValue(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED)), after, 0.0);
        // 손잡이를 빼돌려도 쓸 수 없다
        AttributeInstance leaked = run(speed.<AttributeInstance>edit(i -> i));
        assertThrows(ThreadOwnershipException.class, leaked::value);
        assertThrows(ThreadOwnershipException.class, () -> leaked.setBaseValue(1));
    }

    @Test
    void stateNowGivesLiveAttributesAndRegisterAddsMissing() throws Exception {
        Spawned s = spawnPig();
        assertTrue(onRegion(() -> ((LivingEntityState) s.handle().stateNow().orElseThrow()).attribute(Attribute.ATTACK_DAMAGE).isEmpty()));
        assertTrue(run(s.handle().registerAttribute(Attribute.ATTACK_DAMAGE)));
        assertFalse(run(s.handle().registerAttribute(Attribute.ATTACK_DAMAGE)), "이미 있으면 false");
        assertEquals(2.0, onRegion(() -> ((LivingEntityState) s.handle().stateNow().orElseThrow()).attribute(Attribute.ATTACK_DAMAGE).orElseThrow().value()), 0.0, "기본값으로");
        var dmg = fresh(s).attribute(Attribute.ATTACK_DAMAGE).orElseThrow();
        assertEquals(2.0, dmg.baseValue(), 0.0);
        run(dmg.setBaseValue(6));
        assertEquals(6.0, onRegion(() -> s.raw().attributeValue(org.domi.furnace.server.attribute.Attribute.ATTACK_DAMAGE)), 0.0);
        // editState 로 한꺼번에
        boolean added = run(s.handle().<Boolean>editState(st -> ((LivingEntityState) st).registerAttribute(Attribute.LUCK)));
        assertTrue(added);
        assertTrue(onRegion(() -> ((LivingEntityState) s.handle().stateNow().orElseThrow()).attributes().contains(Attribute.LUCK)));
        // 소유 스레드가 아니면 막는다
        var leaked = (LivingEntityState) onRegion(() -> s.handle().stateNow().orElseThrow());
        assertThrows(ThreadOwnershipException.class, () -> leaked.attribute(Attribute.MAX_HEALTH));
    }

    // ---------------------------------------------------------------- 플레이어

    @Test
    void playerAttributesUseTheAttributeMapAndPublishRecentSnapshot() throws Exception {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Steve", new EmbeddedChannel());
        core.world().playerOnline(p);
        var ph = host.player(p);
        core.world().submitAtChunk(C00, r -> {
            r.players().put(p.entityId(), p);
            p.ownerRegion = r;
            p.syncAttributes();
            return null;
        }).get(10, TimeUnit.SECONDS);

        var speed = ph.attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        assertEquals(0.1f, speed.baseValue(), 1e-9, "플레이어 이동 속도 기본값 0.1");
        assertEquals(0.7, speed.defaultValue(), 0.0);
        assertTrue(ph.attribute(Attribute.ATTACK_DAMAGE).isPresent());
        assertTrue(ph.attribute(Attribute.SPAWN_REINFORCEMENTS).isEmpty());
        assertEquals(1.0, ph.attribute(Attribute.ATTACK_DAMAGE).orElseThrow().baseValue(), 0.0);

        // 소유 스레드로 넘어가 맵에 닿는다 — 그 값이 syncAttributes 뒤 스냅샷에 올라온다
        run(speed.setBaseValue(0.2));
        run(ph.attribute(Attribute.MAX_HEALTH).orElseThrow().addModifier(new AttributeModifier("t:hp", 10, Operation.ADD_VALUE)));
        onRegion(() -> {
            p.syncAttributes();
            return null;
        });
        var after = ph.attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
        assertEquals(0.2, after.baseValue(), 0.0);
        assertEquals(30.0, ph.attribute(Attribute.MAX_HEALTH).orElseThrow().value(), 0.0);
        assertEquals(onRegion(() -> p.attributes().value(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH)), 30.0, 0.0);
        assertEquals(0.1f, speed.baseValue(), 1e-9, "먼저 얻은 핸들은 그 시점 값");

        // 장비·효과 수정자도 modifiers 에 보인다(Bukkit 과 같다) — 여기서는 사용자 수정자 제거까지 확인
        assertTrue(run(ph.attribute(Attribute.MAX_HEALTH).orElseThrow().removeModifier("t:hp")));
        onRegion(() -> {
            p.syncAttributes();
            return null;
        });
        assertEquals(20.0, ph.attribute(Attribute.MAX_HEALTH).orElseThrow().value(), 0.0);
        // 플레이어도 저장 목록은 transient 를 뺀다
        run(ph.attribute(Attribute.LUCK).orElseThrow().addTransientModifier(new AttributeModifier("t:luck", 3, Operation.ADD_VALUE)));
        var luck = entry(onRegion(() -> p.attributes().save()), "minecraft:generic.luck");
        assertNull(luck.tags().get("modifiers"), "transient 는 저장되지 않는다");
        assertEquals(3.0, onRegion(() -> org.domi.furnace.server.entity.PlayerAttributes.value(p, org.domi.furnace.server.attribute.Attribute.LUCK)), 0.0, "계산에는 든다");
        // 손잡이 밖 사용은 막힌다
        AttributeInstance leaked = run(ph.attribute(Attribute.LUCK).orElseThrow().<AttributeInstance>edit(i -> i));
        assertThrows(ThreadOwnershipException.class, leaked::value);
        // 접속이 끊기면 쓰기는 IllegalStateException
        p.markDisconnected();
        var ex = assertThrows(Exception.class, () -> ph.attribute(Attribute.LUCK).orElseThrow().setBaseValue(1).get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, cause(ex));
    }
}
