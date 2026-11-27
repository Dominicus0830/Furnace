package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.entity.EntityState;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.MobEffect;
import org.domi.furnace.api.entity.attribute.Attribute;
import org.domi.furnace.api.entity.attribute.AttributeInstance;
import org.domi.furnace.api.internal.ChunkInternals.Call;
import org.domi.furnace.api.internal.ChunkInternals.DetachedEntityData;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 월드 밖 엔티티의 상태 손잡이 호출 기록(EntityOp.StateCall): 세션이 재생하고 결과를 돌려주며, 거절은 세션을 망가뜨리지 않는다(설계 검토 필수 3·6). */
class DetachedStateCallTest {

    static final ChunkInternalsImpl BRIDGE = new ChunkInternalsImpl(null);

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    static Call call(Class<?> c, String name, Object... args) throws Exception {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return new Call(m, java.util.Arrays.asList(args));
        }
        throw new NoSuchMethodException(name);
    }

    static EntityOp.StateCall op(Call... path) { return new EntityOp.StateCall(List.of(path)); }

    @Test
    void aWriteIsReplayedAndTheResultIsReturned() throws Exception {
        DetachedEntityData s = BRIDGE.openDetachedEntityData("minecraft:cow", List.of());
        Text name = Text.text("Moo");
        s.apply(op(call(EntityState.class, "setCustomName", name)));
        assertEquals(Text.text("Moo"), s.read(List.of(call(EntityState.class, "customName"))));
        s.apply(op(call(LivingEntityState.class, "addEffect", new MobEffect("minecraft:speed", 200, 1, false, true, true))));
        assertEquals(Boolean.TRUE, s.lastResult(), "값을 돌려주는 쓰기의 결과");
        assertEquals(1, ((List<?>) s.read(List.of(call(LivingEntityState.class, "effects")))).size());
        s.apply(op(call(LivingEntityState.class, "clearEffects")));
        assertEquals(1, s.lastResult());
    }

    @Test
    void anAttributePathWritesThroughTheAttributeObject() throws Exception {
        DetachedEntityData s = BRIDGE.openDetachedEntityData("minecraft:cow", List.of());
        var path = List.of(call(LivingEntityState.class, "attribute", Attribute.MAX_HEALTH), call(AttributeInstance.class, "setBaseValue", 25.0));
        s.apply(new EntityOp.StateCall(path));
        assertEquals(25.0, s.read(List.of(call(LivingEntityState.class, "attribute", Attribute.MAX_HEALTH), call(AttributeInstance.class, "baseValue"))));
        assertTrue(s.stateTypes().contains(LivingEntityState.class));
    }

    @Test
    void aRejectedCallDoesNotPoisonTheSessionAndNothingChanged() throws Exception {
        DetachedEntityData s = BRIDGE.openDetachedEntityData("minecraft:cow", List.of());
        assertThrows(IllegalArgumentException.class, () -> s.apply(op(call(LivingEntityState.class, "addEffect", new MobEffect("minecraft:nope", 1, 0, false, true, true)))));
        s.apply(op(call(EntityState.class, "setSilent", true)));
        assertEquals(Boolean.TRUE, s.read(List.of(call(EntityState.class, "isSilent"))), "거절 뒤에도 세션이 쓰인다");
    }

    @Test
    void onlyApiInterfaceMethodsCanBeCalled() throws Exception {
        DetachedEntityData s = BRIDGE.openDetachedEntityData("minecraft:cow", List.of());
        var ex = assertThrows(IllegalArgumentException.class, () -> s.apply(op(new Call(Object.class.getMethod("hashCode"), List.of()))));
        assertTrue(ex.getMessage().contains("org.domi.furnace.api.entity"), ex.getMessage());
        // 그 종류에 없는 상태(갑옷 거치대 메서드를 소에게)
        assertThrows(IllegalArgumentException.class, () -> s.apply(op(call(org.domi.furnace.api.entity.ArmorStandState.class, "setSmall", true))));
    }

    @Test
    void argumentsAreCopiedWhenTheCallIsMade() throws Exception {
        List<Object> mutable = new java.util.ArrayList<>(List.of("a"));
        Call c = new Call(EntityState.class.getMethod("isSilent"), mutable);
        mutable.add("b");
        assertEquals(1, c.args().size());
        assertThrows(UnsupportedOperationException.class, () -> c.args().add("c"));
        Object[] arr = {"x"};
        Call a = new Call(EntityState.class.getMethod("isSilent"), List.<Object>of((Object) arr));
        arr[0] = "y";
        assertEquals("x", ((Object[]) a.args().get(0))[0]);
    }

    @Test
    void theUuidIsTheOneOfTheEntityMadeWithTheSameSeed() {
        var seed = new EntityOp.EntityRandom(42L);
        UUID_EQ(BRIDGE.openDetachedEntityData("minecraft:cow", List.of(seed)), BRIDGE.openDetachedEntityData("minecraft:cow", List.of(seed)));
        DetachedEntityData s = BRIDGE.openDetachedEntityData("minecraft:cow", List.of(seed));
        var forced = java.util.UUID.randomUUID();
        s.apply(new EntityOp.SetUuid(forced));
        assertEquals(forced, s.entityUuid());
    }

    private static void UUID_EQ(DetachedEntityData a, DetachedEntityData b) {
        assertNotNull(a.entityUuid());
        assertEquals(a.entityUuid(), b.entityUuid(), "같은 씨앗은 같은 UUID — 넣을 때 정해지는 UUID 를 미리 알 수 있다");
    }

    @Test
    void aWorldlessEntityGivesAClearErrorAtTheServerEntrance() {
        var worldless = (org.domi.furnace.api.entity.Entity) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {org.domi.furnace.api.entity.Entity.class, org.domi.furnace.api.internal.ChunkInternals.WorldlessEntity.class},
                (p, m, a) -> m.getName().equals("type") ? "minecraft:pig" : null);
        var e = ForeignEntity.reject(worldless, "not an entity of this server");
        assertInstanceOf(UnsupportedOperationException.class, e);
        assertTrue(e.getMessage().contains("not in a world yet"), e.getMessage());
        var other = (org.domi.furnace.api.entity.Entity) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {org.domi.furnace.api.entity.Entity.class}, (p, m, a) -> m.getName().equals("toString") ? "other" : null);
        assertInstanceOf(IllegalArgumentException.class, ForeignEntity.reject(other, "x"));
        assertFalse(Optional.empty().isPresent());
    }
}
