// API EntityState: 서버 엔티티를 직접 잡고 소유 region 스레드에서만 다룬다(MobAiImpl 과 같은 얇은 포장 — 필요할 때마다 만든다).
// 호출마다 소유 스레드인지 확인한다. 옛 동기 작업의 빌림 구간이면 그 엔티티의 region 을 빌린다(RegionBorrow.claimEntity).
// 쓰기는 바닐라 setter 그대로라 데이터 동기화 값이 바뀌면 추적기가 다음 갱신에 보는 플레이어에게 보낸다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.MobEffect;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ArmorStand;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;

class EntityStateImpl implements org.domi.furnace.api.entity.EntityState {

    final PluginHost host;
    final Entity e;

    EntityStateImpl(PluginHost host, Entity e) {
        this.host = host;
        this.e = e;
    }

    /** 종류에 맞는 손잡이. */
    static EntityStateImpl of(PluginHost host, Entity e) {
        if (e instanceof ArmorStand a) return new Stand(host, a);
        if (e instanceof LivingEntity le) return new Living(host, le);
        return new EntityStateImpl(host, e);
    }

    final void check() {
        if (!org.domi.furnace.server.world.RegionBorrow.claimEntity(e) && !e.level().isOwnerThread()) {
            throw new ThreadOwnershipException("EntityState(" + e.uuid() + ") 는 그 엔티티를 가진 region 스레드에서만 쓸 수 있다 (현재 "
                    + Thread.currentThread().getName() + ") — Entity.editState 를 쓴다");
        }
    }

    /** 쓰기: 사라진 엔티티는 건드리지 않는다. */
    final boolean writable() {
        check();
        return !e.isRemoved();
    }

    @Override
    public UUID uniqueId() { return e.uuid(); }

    @Override
    public String type() { return Causes.typeKey(e); }

    @Override
    public boolean isValid() {
        check();
        return !e.isRemoved();
    }

    @Override
    public Text customName() {
        check();
        return e.hasCustomName() ? e.getCustomName() : null;
    }

    @Override
    public void setCustomName(Text name) {
        if (writable()) e.setCustomName(name);
    }

    @Override
    public org.domi.furnace.api.world.Aabb boundingBox() {
        check();
        return ApiBoxes.of(e.boundingBox());
    }

    @Override
    public boolean isCustomNameVisible() {
        check();
        return e.isCustomNameVisible();
    }

    @Override
    public void setCustomNameVisible(boolean visible) {
        if (writable()) e.setCustomNameVisible(visible);
    }

    @Override
    public boolean isSilent() {
        check();
        return e.isSilent();
    }

    @Override
    public void setSilent(boolean silent) {
        if (writable()) e.setSilent(silent);
    }

    @Override
    public boolean isNoGravity() {
        check();
        return e.isNoGravity();
    }

    @Override
    public void setNoGravity(boolean noGravity) {
        if (writable()) e.setNoGravity(noGravity);
    }

    @Override
    public boolean isInvulnerable() {
        check();
        return e.isInvulnerable();
    }

    @Override
    public void setInvulnerable(boolean invulnerable) {
        if (writable()) e.setInvulnerable(invulnerable);
    }

    @Override
    public boolean hasGlowingTag() {
        check();
        return e.hasGlowingTag();
    }

    @Override
    public void setGlowingTag(boolean glowing) {
        if (writable()) e.setGlowingTag(glowing);
    }

    @Override
    public boolean isInvisible() {
        check();
        return e.isInvisible();
    }

    @Override
    public boolean noPhysics() {
        check();
        return e.noPhysics;
    }

    @Override
    public void setNoPhysics(boolean noPhysics) {
        if (writable()) e.noPhysics = noPhysics;
    }

    // ------------------------------------------------------------------ 탑승

    private org.domi.furnace.api.entity.Entity handle(Rider r) {
        return r instanceof Player p ? host.player(p) : EntityHandle.of(host, (Entity) r);
    }

    @Override
    public List<org.domi.furnace.api.entity.Entity> passengers() {
        check();
        List<org.domi.furnace.api.entity.Entity> out = new ArrayList<>();
        for (Rider r : e.passengers()) out.add(handle(r));
        return out;
    }

    @Override
    public Optional<org.domi.furnace.api.entity.Entity> vehicle() {
        check();
        Entity v = e.vehicle();
        return v == null ? Optional.empty() : Optional.of(handle(v));
    }

    /** API 핸들 → 지금 살아 있는 서버 승객(사라졌으면 null). 이 엔티티와 같은 region 에 있어야 한다. */
    private Rider rider(org.domi.furnace.api.entity.Entity h) {
        java.util.Objects.requireNonNull(h, "passenger");
        if (h instanceof PlayerHandle ph) {
            Player p = ph.handle();
            World w = host.core().world(p.dimensionKey());
            ChunkPos cp = ChunkPos.ofBlock(p.x(), p.z());
            Region r = w == null ? null : w.regionizer().ownerOf(cp.x(), cp.z());
            if (r == null || !r.isOwnerThread()) throw new ThreadOwnershipException("승객 " + p.name() + " 은 이 엔티티와 같은 region 에 있어야 한다");
            return p;
        }
        if (h instanceof EntityHandle eh) {
            if (!(eh.tasks().owner() instanceof Entity pe) || pe.isRemoved()) return null;
            if (!org.domi.furnace.server.world.RegionBorrow.claimEntity(pe) && !pe.level().isOwnerThread()) {
                throw new ThreadOwnershipException("승객 " + pe.uuid() + " 은 이 엔티티와 같은 region 에 있어야 한다");
            }
            return pe;
        }
        throw new IllegalArgumentException("Furnace 가 만든 엔티티 핸들이 아니다: " + h.getClass().getName());
    }

    @Override
    public boolean addPassenger(org.domi.furnace.api.entity.Entity passenger) {
        if (!writable()) return false;
        Rider r = rider(passenger);
        if (r == null || r == e) return false;
        return r.startRiding(e, true);
    }

    @Override
    public boolean removePassenger(org.domi.furnace.api.entity.Entity passenger) {
        if (!writable()) return false;
        Rider r = rider(passenger);
        if (r == null || r.vehicle() != e) return false;
        r.stopRiding();
        return true;
    }

    @Override
    public boolean ejectPassengers() {
        if (!writable() || !e.isVehicle()) return false;
        e.ejectPassengers();
        return true;
    }

    @Override
    public boolean leaveVehicle() {
        if (!writable() || e.vehicle() == null) return false;
        e.stopRiding();
        return true;
    }

    /** 살아 있는 엔티티: 상태 효과·밀림. */
    static class Living extends EntityStateImpl implements org.domi.furnace.api.entity.LivingEntityState {

        final LivingEntity le;

        Living(PluginHost host, LivingEntity le) {
            super(host, le);
            this.le = le;
        }

        private static org.domi.furnace.server.effect.MobEffect effectType(String key) {
            String k = key.indexOf(':') < 0 ? "minecraft:" + key : key;
            var t = org.domi.furnace.server.effect.MobEffect.byName(k);
            if (t == null) throw new IllegalArgumentException("모르는 효과: " + key);
            return t;
        }

        static MobEffect toApi(MobEffectInstance i) {
            return new MobEffect(i.effect().name(), i.duration(), i.amplifier(), i.ambient(), i.visible(), i.showIcon());
        }

        @Override
        public List<MobEffect> effects() {
            check();
            List<MobEffect> out = new ArrayList<>();
            for (var i : le.getActiveEffects()) out.add(toApi(i));
            return out;
        }

        @Override
        public Optional<MobEffect> effect(String type) {
            check();
            var i = le.getEffect(effectType(type));
            return i == null ? Optional.empty() : Optional.of(toApi(i));
        }

        @Override
        public boolean addEffect(MobEffect effect) {
            java.util.Objects.requireNonNull(effect, "effect");
            var t = effectType(effect.type());
            if (!writable()) return false;
            return le.addEffect(new MobEffectInstance(t, effect.duration(), effect.amplifier(), effect.ambient(), effect.particles(), effect.icon()));
        }

        @Override
        public boolean removeEffect(String type) {
            var t = effectType(type);
            if (!writable()) return false;
            return le.removeEffect(t);
        }

        @Override
        public int clearEffects() {
            if (!writable()) return 0;
            return le.removeAllEffects();
        }

        @Override
        public boolean isCollidable() {
            check();
            return le.collides;
        }

        @Override
        public void setCollidable(boolean collidable) {
            if (writable()) le.collides = collidable;
        }
    }

    /** 갑옷 거치대. */
    static final class Stand extends Living implements org.domi.furnace.api.entity.ArmorStandState {

        private final ArmorStand a;

        Stand(PluginHost host, ArmorStand a) {
            super(host, a);
            this.a = a;
        }

        @Override
        public boolean isSmall() {
            check();
            return a.isSmall();
        }

        @Override
        public void setSmall(boolean small) {
            if (writable()) a.setSmall(small);
        }

        @Override
        public boolean isMarker() {
            check();
            return a.isMarker();
        }

        @Override
        public void setMarker(boolean marker) {
            if (writable()) a.setMarker(marker);
        }

        @Override
        public boolean hasArms() {
            check();
            return a.showArms();
        }

        @Override
        public void setArms(boolean arms) {
            if (writable()) a.setShowArms(arms);
        }

        @Override
        public boolean hasBasePlate() {
            check();
            return !a.noBasePlate();
        }

        @Override
        public void setBasePlate(boolean basePlate) {
            if (writable()) a.setNoBasePlate(!basePlate);
        }

        @Override
        public void setInvisible(boolean invisible) {
            if (writable()) a.setInvisible(invisible);
        }
    }
}
