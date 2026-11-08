// 플러그인이 보는 엔티티 — 만든 시점(소유 region 스레드)의 필드를 복사한 스냅샷 핸들. 서버 Entity 객체는 직접 들고 있지 않는다.
// 변경·재조회는 uuid 로 소유 region 에서 다시 찾거나(refresh·teleport·remove·체력), 엔티티에 붙은 작업 대기열(EntityTasks —
// 엔티티를 따라간다)로 그 엔티티를 가진 region 스레드에서 한다(영속 데이터·칸·거래). 대기열은 엔티티가 사라지면 엔티티 참조를 놓는다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.entity.EntityScheduler;
import org.domi.furnace.api.entity.MerchantRecipe;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.builtin.DataCommands;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.entity.ContainerEntity;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityTasks;
import org.domi.furnace.server.entity.InventoryCarrier;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.EntityContainer;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;

public class EntityHandle implements org.domi.furnace.api.entity.Entity {

    final PluginHost host;
    private final UUID uuid;
    private final String type;
    private final Location location;
    private final Text customName;
    private final boolean valid;
    private final EntityTasks tasks;
    private final PersistentDataView persistentData;
    /** 칸 종류·크기(칸이 없는 엔티티는 null). */
    private final String containerType;
    private final int containerSize;
    /** 원인 사슬(만든 순간 — 불변이라 참조만 복사). */
    private final org.domi.furnace.api.event.cause.CauseChain origin;
    private final org.domi.furnace.api.entity.Pose pose;
    private final boolean noPhysics;
    /** 스냅샷 순번(서버 전체에서 늘어난다 — 값을 읽기 전에 매긴다). */
    private static final java.util.concurrent.atomic.AtomicLong STAMPS = new java.util.concurrent.atomic.AtomicLong();
    private final long stamp = STAMPS.incrementAndGet();

    EntityHandle(PluginHost host, Entity e) {
        this.host = host;
        this.uuid = e.uuid();
        this.type = e.type().name();
        this.location = new Location(e.dimensionKey(), e.x(), e.y(), e.z(), e.yRot(), e.xRot());
        this.customName = e.hasCustomName() ? e.getCustomName() : null;
        this.valid = !e.isRemoved();
        this.tasks = e.apiTasks();
        this.persistentData = NbtPersistentData.snapshot(e.persistentData(false));
        this.origin = e.origin();
        this.pose = org.domi.furnace.api.entity.Pose.byId(e.getPose());
        this.noPhysics = e.noPhysics;
        if (e instanceof ContainerEntity ce) {
            containerType = type;
            containerSize = ce.chestVehicleItems().length;
        } else if (e instanceof InventoryCarrier ic) {
            containerType = type;
            containerSize = ic.getInventory().size();
        } else {
            containerType = null;
            containerSize = 0;
        }
    }

    /** 소유 region 스레드에서 만든다. */
    public static EntityHandle of(PluginHost host, Entity e) {
        if (e instanceof org.domi.furnace.server.entity.Villager v) return new VillagerHandle(host, v);
        if (e instanceof org.domi.furnace.server.trade.Merchant && e instanceof org.domi.furnace.server.entity.Mob m) return new MerchantHandle(host, m);
        if (e instanceof org.domi.furnace.server.entity.Mob m) return new MobHandle(host, m);
        return e instanceof LivingEntity le ? new Living(host, le) : new EntityHandle(host, e);
    }

    @Override
    public UUID uniqueId() { return uuid; }

    @Override
    public long snapshotStamp() { return stamp; }

    @Override
    public org.domi.furnace.api.event.cause.CauseChain origin() { return origin; }

    @Override
    public String type() { return type; }

    @Override
    public Location location() { return location; }

    @Override
    public Text customName() { return customName; }

    @Override
    public boolean isValid() { return valid; }

    private World serverWorld() {
        WorldHandle w = host.worldHandle(location.world());
        return w == null ? null : w.handle();
    }

    private ChunkPos chunk() { return ChunkPos.ofBlock(location.x(), location.z()); }

    @Override
    public TaskScheduler scheduler() {
        ChunkPos cp = chunk();
        return host.profiled().wrap(serverWorld().chunkScheduler(cp.x(), cp.z()));
    }

    @Override
    public EntityScheduler entityScheduler() { return new EntitySchedulerImpl(tasks, host.profiled()); }

    /** 엔티티를 따라가는 대기열(Internals 가 살아 있는 엔티티를 찾을 때). */
    EntityTasks tasks() { return tasks; }

    /** 핸들 시점 위치의 region 에서 uuid 로 찾아 task 를 돈다. 그 region 에 없으면 null 로 완료. */
    <T> CompletableFuture<T> onOwner(java.util.function.BiFunction<Region, Entity, T> task) {
        World w = serverWorld();
        if (w == null) return RegionFuture.wrap(CompletableFuture.completedFuture(null));
        CompletableFuture<T> f = w.submitAtOwnedChunk(chunk(), r -> {
            Entity cur = r.level().entities().get(uuid);
            return cur == null || cur.isRemoved() ? null : task.apply(r, cur);
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(null));
    }

    /**
     * 엔티티를 따라가는 대기열로 그 엔티티를 가진 region 스레드에서 다음 틱에 task 를 돈다. 엔티티가 이미 사라졌거나 그 전에 사라지면
     * gone 이 null 이 아니면 그 값으로, null 이면 IllegalStateException 으로 완료.
     */
    <T> CompletableFuture<T> onEntity(Function<Entity, T> task, T gone, boolean failWhenGone) {
        RegionFuture<T> f = new RegionFuture<>();
        Runnable retired = () -> {
            if (failWhenGone) f.completeExceptionally(new IllegalStateException("엔티티가 사라졌다: " + uuid));
            else f.complete(gone);
        };
        EntityTasks.Entry entry = tasks.schedule(() -> {
            if (!(tasks.owner() instanceof Entity e) || e.isRemoved()) {
                retired.run();
                return;
            }
            try {
                f.complete(task.apply(e));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        }, retired, 0, 0);
        if (entry == null) retired.run();
        return f;
    }

    @Override
    public CompletableFuture<? extends org.domi.furnace.api.entity.Entity> refresh() {
        return onOwner((r, cur) -> of(host, cur));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Location to) {
        if (!to.world().equals(location.world())) return RegionFuture.wrap(CompletableFuture.completedFuture(false));
        World w = serverWorld();
        return onOwner((r, cur) -> {
            // 목적지 청크가 같은 region 것일 때만 — 다른 region 으로 넘기는 이동은 다음 단계(핸드오프 필요)
            ChunkPos tcp = ChunkPos.ofBlock(to.x(), to.z());
            if (w.regionizer().ownerOf(tcp.x(), tcp.z()) != r) return false;
            cur.moveTo(to.x(), to.y(), to.z(), to.yaw(), to.pitch());
            return true;
        }).thenApply(b -> b != null && b);
    }

    @Override
    public CompletableFuture<Boolean> remove() {
        return onOwner((r, cur) -> {
            cur.discard();
            return true;
        }).thenApply(b -> b != null && b);
    }

    // ------------------------------------------------------------------ 자세·물리

    @Override
    public org.domi.furnace.api.entity.Pose pose() { return pose; }

    @Override
    public CompletableFuture<Boolean> setPose(org.domi.furnace.api.entity.Pose p) {
        int id = p == null ? org.domi.furnace.api.entity.Pose.STANDING.id() : p.id();
        return onEntity(e -> {
            e.setPose(id);
            return true;
        }, false, false);
    }

    @Override
    public boolean noPhysics() { return noPhysics; }

    @Override
    public CompletableFuture<Boolean> setNoPhysics(boolean value) {
        return onEntity(e -> {
            e.noPhysics = value;
            return true;
        }, false, false);
    }

    // ------------------------------------------------------------------ 영속 데이터·칸

    @Override
    public PersistentDataView persistentData() { return persistentData; }

    @Override
    public CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit) {
        return onEntity(e -> {
            var tag = e.persistentData(true);
            edit.accept(new NbtPersistentData(tag, false));
            return true;
        }, false, false);
    }

    /** 소유 스레드에서 곧바로: 이 스레드가 엔티티를 가진 region 을 돌리고 있어야 한다(아니면 ThreadOwnershipException). 사라졌으면 false. */
    @Override
    public boolean editPersistentDataNow(Consumer<PersistentDataContainer> edit) {
        if (!(tasks.owner() instanceof Entity e) || e.isRemoved()) return false;
        World w = host.core().world(e.dimensionKey());
        ChunkPos cp = ChunkPos.ofBlock(e.x(), e.z());
        // 전역 빌림 구간이면 그 엔티티의 region 을 빌린다 — 기다리는 사이 경계를 넘었으면 옮겨 간 region 을(위치는 빌린 뒤 다시 읽는다)
        if (org.domi.furnace.server.world.RegionBorrow.claimEntity(e)) cp = ChunkPos.ofBlock(e.x(), e.z());
        Region r = w == null ? null : w.regionizer().ownerOf(cp.x(), cp.z());
        if (r == null || !r.isOwnerThread() || r.level() == null || r.level().entities().get(e.uuid()) != e) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("엔티티 " + type() + " 를 가진 region 스레드가 아니다(현재 "
                    + Thread.currentThread().getName() + ") — editPersistentData 나 entityScheduler() 작업 안에서 부른다");
        }
        edit.accept(new NbtPersistentData(e.persistentData(true), false));
        return true;
    }

    @Override
    public CompletableFuture<Optional<Inventory>> container() {
        if (containerType == null) return RegionFuture.wrap(CompletableFuture.completedFuture(Optional.empty()));
        OwnedInventory.Runner runner = new OwnedInventory.Runner() {
            @Override
            public <T> CompletableFuture<T> run(Function<OwnedInventory.Slots, T> op) {
                return onEntity(e -> op.apply(slotsOf(e)), null, true);
            }
        };
        return RegionFuture.wrap(CompletableFuture.completedFuture(Optional.of(new OwnedInventory(host, containerType, containerSize, runner))));
    }

    // ------------------------------------------------------------------ NBT(/data entity 경로)

    @Override
    public CompletableFuture<org.domi.furnace.api.nbt.NbtCompound> readNbt() {
        return onEntity(e -> ApiNbt.toApi(DataCommands.entityData(e)), null, true);
    }

    @Override
    public CompletableFuture<Boolean> editNbt(Consumer<org.domi.furnace.api.nbt.NbtCompound> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        return onEntity(e -> editEntityNbt(e, edit), null, true);
    }

    /** 주인 스레드에서: 읽고 고치고 바뀌었으면 싣는다. */
    static boolean editEntityNbt(org.domi.furnace.server.entity.Rider e, Consumer<org.domi.furnace.api.nbt.NbtCompound> edit) {
        var nbt = ApiNbt.toApi(DataCommands.entityData(e));
        var before = nbt.copy();
        edit.accept(nbt);
        if (nbt.equals(before)) return false;
        DataCommands.setEntityData(e, ApiNbt.toServer(nbt));
        return true;
    }

    /** 소유 스레드에서: 그 엔티티의 살아 있는 칸. */
    private static OwnedInventory.Slots slotsOf(Entity e) {
        if (e instanceof ContainerEntity ce) {
            EntityContainer c = new EntityContainer(ce);
            return new OwnedInventory.Slots() {
                public int size() { return c.size(); }

                public org.domi.furnace.server.entity.ItemStack get(int slot) { return c.get(slot); }

                public void set(int slot, org.domi.furnace.server.entity.ItemStack s) { c.set(slot, s); }
            };
        }
        if (e instanceof InventoryCarrier ic) {
            var inv = ic.getInventory();
            return new OwnedInventory.Slots() {
                public int size() { return inv.size(); }

                public org.domi.furnace.server.entity.ItemStack get(int slot) { return inv.get(slot); }

                public void set(int slot, org.domi.furnace.server.entity.ItemStack s) { inv.set(slot, s); }
            };
        }
        throw new IllegalStateException("칸이 없는 엔티티: " + e.type().name());
    }

    static class Living extends EntityHandle implements org.domi.furnace.api.entity.LivingEntity {

        private final float health, maxHealth;

        Living(PluginHost host, LivingEntity e) {
            super(host, e);
            this.health = e.health();
            this.maxHealth = e.maxHealth();
        }

        @Override
        public float health() { return health; }

        @Override
        public float maxHealth() { return maxHealth; }

        @Override
        public CompletableFuture<Void> updateHealth(float h) {
            return onOwner((r, cur) -> {
                if (!(cur instanceof LivingEntity le)) return Boolean.FALSE;
                if (h <= 0f) le.kill();
                else le.setHealth(h);
                return Boolean.TRUE;
            }).thenAccept(ok -> {
                if (ok == null || !ok) throw new IllegalStateException("엔티티를 소유 region 에서 찾지 못했다: " + uniqueId());
            });
        }
    }

    /** AI 가 있는 몹. AI 는 소유 스레드에서 MobAi 로 다룬다. */
    static class MobHandle extends Living implements org.domi.furnace.api.entity.Mob {

        MobHandle(PluginHost host, org.domi.furnace.server.entity.Mob e) { super(host, e); }

        @Override
        public <T> CompletableFuture<T> editAi(Function<? super org.domi.furnace.api.entity.ai.MobAi, ? extends T> action) {
            return onEntity(e -> e instanceof org.domi.furnace.server.entity.Mob m ? (T) action.apply(new MobAiImpl(host, m)) : null, null, true);
        }

        @Override
        public Optional<org.domi.furnace.api.entity.ai.MobAi> aiNow() {
            // 엔티티를 따라가는 대기열이 지금의 몹을 쥐고 있다 — 스폰 이벤트처럼 아직 레벨 목록에 오르기 전에도 찾는다
            if (!(tasks().owner() instanceof org.domi.furnace.server.entity.Mob m)) return Optional.empty();
            if (!org.domi.furnace.server.world.RegionBorrow.claimEntity(m) && !m.level().isOwnerThread()) {
                throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("aiNow() 는 그 몹을 가진 region 스레드에서만 부를 수 있다 (현재 "
                        + Thread.currentThread().getName() + ") — editAi 를 쓴다");
            }
            return m.isRemoved() ? Optional.empty() : Optional.of(new MobAiImpl(host, m));
        }
    }

    /** 주민·떠돌이 상인. */
    static class MerchantHandle extends MobHandle implements org.domi.furnace.api.entity.Merchant {

        private final boolean trading;

        MerchantHandle(PluginHost host, org.domi.furnace.server.entity.Mob e) {
            super(host, e);
            this.trading = ((org.domi.furnace.server.trade.Merchant) e).tradingPlayer() != null;
        }

        @Override
        public boolean isTrading() { return trading; }

        private org.domi.furnace.server.world.ItemRegistry items() { return host.core().world().itemRegistry(); }

        @Override
        public CompletableFuture<List<MerchantRecipe>> trades() {
            return onEntity(e -> {
                List<MerchantRecipe> out = new ArrayList<>();
                for (MerchantOffer o : ((org.domi.furnace.server.trade.Merchant) e).offers()) out.add(toApi(o));
                return out;
            }, null, false);
        }

        @Override
        public CompletableFuture<Boolean> setTrades(List<MerchantRecipe> recipes) {
            MerchantOffers offers = new MerchantOffers();
            try {
                for (MerchantRecipe r : recipes) offers.add(toServer(r));
            } catch (IllegalArgumentException ex) {
                return RegionFuture.wrap(CompletableFuture.failedFuture(ex));
            }
            return onEntity(e -> {
                // 바닐라 getOffers 는 비어 있으면 거래표를 만든다(난수 소비) — 통째로 바꿀 때는 그 경로를 거치지 않는다
                if (e instanceof org.domi.furnace.server.entity.Villager v) v.setOffers(offers.copy());
                else if (e instanceof org.domi.furnace.server.entity.WanderingTrader t) t.setOffers(offers.copy());
                else return false;
                return true;
            }, false, false);
        }

        private MerchantRecipe toApi(MerchantOffer o) {
            var a = ApiConvert.item(o.baseCostAStack(), items());
            var b = ApiConvert.item(o.costBStack(), items());
            return new MerchantRecipe(a, b, ApiConvert.item(o.result(), items()), o.uses(), o.maxUses(), o.xp(), o.priceMultiplier(), o.rewardExp());
        }

        private MerchantOffer toServer(MerchantRecipe r) {
            ItemCost a = cost(r.ingredient1());
            Optional<ItemCost> b = r.ingredient2().isEmpty() ? Optional.empty() : Optional.of(cost(r.ingredient2()));
            var result = ApiConvert.item(r.result(), items());
            return new MerchantOffer(a, b, result, r.uses(), r.maxUses(), r.rewardExp(), 0, 0, r.priceMultiplier(), r.xp());
        }

        /** 거래 값: 아이템 + 개수 + 컴포넌트 조건(넣은 컴포넌트가 모두 같아야 맞는다 — 바닐라 ItemCost). 지운 컴포넌트는 조건이 되지 않는다. */
        private ItemCost cost(org.domi.furnace.api.item.ItemStack s) {
            if (items().idOf(s.type()) < 0) throw new IllegalArgumentException("알 수 없는 아이템: " + s.type());
            java.util.Map<String, Object> comps = new java.util.LinkedHashMap<>();
            for (var e : s.components().entrySet()) comps.put(e.getKey(), org.domi.furnace.server.item.Snbt.parse(e.getValue()));
            return new ItemCost(s.type(), s.amount(), comps);
        }
    }
    /** 마을 주민: 직업·단계(핸들 시점)와 평판(소문). */
    static final class VillagerHandle extends MerchantHandle implements org.domi.furnace.api.entity.Villager {

        private final String profession;
        private final int level;

        VillagerHandle(PluginHost host, org.domi.furnace.server.entity.Villager v) {
            super(host, v);
            String prof = v.profession();
            this.profession = prof.indexOf(':') < 0 ? "minecraft:" + prof : prof;
            this.level = v.villagerLevel();
        }

        @Override
        public String profession() { return profession; }

        @Override
        public int professionLevel() { return level; }

        private static org.domi.furnace.server.entity.gossip.GossipContainer.GossipType kind(org.domi.furnace.api.entity.GossipKind k) {
            return org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.byId(k.key());
        }

        @Override
        public CompletableFuture<Integer> reputation(java.util.UUID target) {
            java.util.Objects.requireNonNull(target, "target");
            return onEntity(e -> ((org.domi.furnace.server.entity.Villager) e).getGossips().getReputation(target, t -> true), null, false);
        }

        @Override
        public CompletableFuture<java.util.Map<org.domi.furnace.api.entity.GossipKind, Integer>> gossip(java.util.UUID target) {
            java.util.Objects.requireNonNull(target, "target");
            return onEntity(e -> {
                java.util.Map<org.domi.furnace.api.entity.GossipKind, Integer> out = new java.util.EnumMap<>(org.domi.furnace.api.entity.GossipKind.class);
                var entries = ((org.domi.furnace.server.entity.Villager) e).getGossips().getGossipEntries().get(target);
                if (entries != null) {
                    for (var k : org.domi.furnace.api.entity.GossipKind.values()) {
                        Integer v = entries.get(kind(k));
                        if (v != null && v != 0) out.put(k, v);
                    }
                }
                return out;
            }, null, false);
        }

        @Override
        public CompletableFuture<Boolean> addGossip(java.util.UUID target, org.domi.furnace.api.entity.GossipKind kind, int value) {
            java.util.Objects.requireNonNull(target, "target");
            java.util.Objects.requireNonNull(kind, "kind");
            return onEntity(e -> {
                ((org.domi.furnace.server.entity.Villager) e).getGossips().add(target, kind(kind), value);
                return true;
            }, false, false);
        }

        @Override
        public CompletableFuture<Boolean> clearGossip(java.util.UUID target) {
            java.util.Objects.requireNonNull(target, "target");
            return onEntity(e -> {
                var g = ((org.domi.furnace.server.entity.Villager) e).getGossips();
                for (var t : org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.values()) g.remove(target, t);
                return true;
            }, false, false);
        }

        @Override
        public CompletableFuture<Boolean> applyReputationEvent(org.domi.furnace.api.entity.ReputationEvent event, java.util.UUID target) {
            java.util.Objects.requireNonNull(event, "event");
            java.util.Objects.requireNonNull(target, "target");
            var type = org.domi.furnace.server.entity.Villager.ReputationEvent.valueOf(event.name());
            return onEntity(e -> {
                ((org.domi.furnace.server.entity.Villager) e).onReputationEvent(type, target);
                return true;
            }, false, false);
        }
    }
}
