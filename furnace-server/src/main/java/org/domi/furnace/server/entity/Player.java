// 플레이어 엔티티 상태(region 스레드 소유). 채널로 패킷 전송(eventLoop 마샬).
package org.domi.furnace.server.entity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkPos;

public final class Player implements Targetable, org.domi.furnace.server.map.MapData.Viewer {
    private Targetable lastHurtByMob, lastHurtMob;
    private int combatTicks, lastHurtByMobTimestamp, lastHurtMobTimestamp;

    /** 틱마다 1 올라가는 전투 시계(LivingEntity.tickCount 에 해당). */
    void combatTick() { combatTicks++; }

    @Override public Targetable getLastHurtByMob() { return lastHurtByMob; }

    @Override public int getLastHurtByMobTimestamp() { return lastHurtByMobTimestamp; }

    public void setLastHurtByMob(Targetable t) {
        lastHurtByMob = t;
        lastHurtByMobTimestamp = combatTicks;
    }

    @Override public Targetable getLastHurtMob() { return lastHurtMob; }

    @Override public int getLastHurtMobTimestamp() { return lastHurtMobTimestamp; }

    public void setLastHurtMob(Targetable t) {
        lastHurtMob = t;
        lastHurtMobTimestamp = combatTicks;
    }


    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final Channel channel;

    private volatile double x, y, z; // volatile — region 외 스레드(청크 전송 완료 콜백)서 chunkPos 읽음
    private float yaw, pitch;
    private boolean onGround;

    private ChunkPos lastCenter;                 // 마지막으로 SetCenterChunk 보낸 청크(null=미전송)
    private final Set<ChunkPos> loadedChunks = new HashSet<>();
    /** Entity.random — 엔티티 자신의 난수(RandomSource.create()). */
    private final org.domi.furnace.server.block.LegacyRandomSource random =
            new org.domi.furnace.server.block.LegacyRandomSource(java.util.concurrent.ThreadLocalRandom.current().nextLong());
    private final Set<ChunkPos> pendingChunks = new LinkedHashSet<>(); // 시야 진입했으나 아직 전송 안 한 청크(순서 보존). region 스레드 전용.
    private final Map<Integer, double[]> tracked = new HashMap<>(); // 관찰중 엔티티 → 마지막 전송 {x,y,z}
    private final Map<Integer, Integer> missingTicks = new HashMap<>(); // 추적중인데 후보서 사라진 틱 수(grace despawn)
    // 이 플레이어의 탭 목록에 올려둔 엔티티 → uuid. 시야 밖으로 나가 엔티티가 디스폰돼도 접속 중이면 탭엔 남기고,
    // 후보에서 완전히 사라졌을 때(퇴장 등)만 PlayerInfoRemove로 뺀다. region 스레드 전용.
    private final Map<Integer, UUID> listed = new HashMap<>();

    private volatile double[] pending;            // {x,y,z,yaw,pitch,onGround} IO→region 발행
    private volatile boolean disconnected;
    // 유효 view distance = min(서버 view-distance, 클라 요청). ClientInformation(config 0x00/play 0x0A)서 갱신.
    // 게임중 클라가 렌더거리 바꾸면 0x0A로 다시 옴 → 갱신. onEnterPlay서 초기 설정.
    private volatile int viewDistance = 10;
    // 열어둔 체스트 세션(M4c-3 슬라이스 B) — netty 스레드가 쓰고 region 스레드(breakBlock 강제닫기)가 읽음.
    // 메뉴: 인벤토리 창(containerId 0)은 항상 있고, 열린 창(상자 등)이 있으면 containerMenu 가 그것이다(바닐라 containerMenu/inventoryMenu).
    private volatile org.domi.furnace.server.menu.InventoryMenu inventoryMenu;
    private volatile org.domi.furnace.server.menu.AbstractContainerMenu containerMenu;
    private int containerCounter;

    /** 블록 부수기 상태(ServerPlayerGameMode) — 플레이어가 속한 region 스레드에서만. */
    private final org.domi.furnace.server.block.BlockBreaking breaking = new org.domi.furnace.server.block.BlockBreaking();

    /** 인벤토리를 가진 쪽(연결)이 내주는 통로. 인벤토리는 연결 스레드 것이라 region 은 사본을 읽고, 바꿀 때는 이 통로로 맡긴다. */
    public interface InventoryHost {
        /** 창 칸(0 결과, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손)의 지금 스택. */
        ItemStack slot(int windowSlot);

        /** 손에 든 핫바 칸(0..8). */
        int selected();

        /** 그 칸이 아직 expect 이면 now 로 바꾼다(연결 스레드에서). */
        void replace(int windowSlot, ItemStack expect, ItemStack now);

        /** 권한 단계(관리자 블록 판정). */
        int permissionLevel();

        /** 파괴 직전 허가(플러그인 BlockBreakEvent). */
        default boolean allowBreak(int x, int y, int z, int state) { return true; }

        /** Level.mayInteract 의 스폰 보호 부분(블록 x, z). */
        default boolean mayInteract(int x, int z) { return true; }

        /** 그 창 칸이 바뀌었다(inventory_changed 발전 과제 — broadcastChanges 가 부른다). */
        default void inventoryChanged(int windowSlot) {}

        /** 열린 창이 expect 이면 서버가 닫는다(ServerPlayer.closeContainer — 상자가 사라졌을 때 등). 어느 스레드에서든. */
        default void closeContainer(org.domi.furnace.server.menu.AbstractContainerMenu expect) {}

        /** 블록에 매이지 않은 열린 창이 아직 유효한가(AbstractContainerMenu.stillValid). */
        default boolean menuStillValid(org.domi.furnace.server.menu.AbstractContainerMenu menu) { return true; }

        /**
         * 블록에 매인 열린 창(상자·제작대)의 유효성 검사 — 그 블록을 가진 region 스레드에서 불린다. 유효하지 않으면(블록이 사라졌거나 멀어졌거나
         * 청크가 내려갔으면) 서버가 닫는다.
         */
        default void validateBlockMenu(org.domi.furnace.server.world.Region region, org.domi.furnace.server.menu.AbstractContainerMenu menu) {}
    }

    private volatile InventoryHost inventoryHost;

    public org.domi.furnace.server.block.BlockBreaking breaking() { return breaking; }

    public org.domi.furnace.server.block.LegacyRandomSource random() { return random; }

    /** 플레이어 인벤토리(연결이 만든 것을 넘겨받는다 — 테스트용 기본값도 있다). */
    private volatile PlayerInventory inventory = new PlayerInventory();

    public PlayerInventory inventory() { return inventory; }

    public void inventory(PlayerInventory inv) { this.inventory = inv; }

    public InventoryHost inventoryHost() { return inventoryHost; }

    public void inventoryHost(InventoryHost h) { this.inventoryHost = h; }

    public Player(int entityId, UUID uuid, String name, Channel channel) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.name = name;
        this.channel = channel;
    }

    public void applyMove(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.moveDx = x - this.x; this.moveDy = y - this.y; this.moveDz = z - this.z;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public void applyRotation(float yaw, float pitch, boolean onGround) {
        this.yaw = yaw; this.pitch = pitch; this.onGround = onGround;
    }

    public ChunkPos chunkPos() {
        return ChunkPos.ofBlock(x, z);
    }

    /** IO 스레드: 들어온 위치를 발행(단일 writer). */
    public void publishPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.pending = new double[]{x, y, z, yaw, pitch, onGround ? 1 : 0};
    }

    /** region 스레드: 발행된 위치를 권위 필드에 적용(단일 reader). 변경 있으면 true. */
    public boolean drainPending() {
        double[] p = this.pending;
        if (p == null) return false;
        this.pending = null;
        applyMove(p[0], p[1], p[2], (float) p[3], (float) p[4], p[5] != 0);
        return true;
    }

    public void markDisconnected() { this.disconnected = true; }
    public boolean isDisconnected() { return disconnected; }
    public int viewDistance() { return viewDistance; }
    public void viewDistance(int vd) { this.viewDistance = Math.max(Config.MIN_VIEW_DISTANCE, vd); }

    /** 패킷을 이 플레이어 채널로 전송(IO eventLoop 마샬). region 스레드서 호출 안전. */
    public void send(Packet packet) {
        channel.eventLoop().execute(() -> {
            ByteBuf buf = channel.alloc().buffer();
            Types.writeVarInt(buf, packet.id());
            packet.write(buf);
            channel.write(buf);
            // 보낼 때마다 flush(시스템 호출) 하지 않고, 같은 순간에 쌓인 패킷은 한 번에 내보낸다(플래그는 이벤트 루프 스레드에서만 만진다).
            if (!flushPending) {
                flushPending = true;
                channel.eventLoop().execute(() -> {
                    flushPending = false;
                    channel.flush();
                });
            }
        });
    }

    private boolean flushPending;

    private volatile org.domi.furnace.api.text.Text kickReason;

    /** 서버가 끊은 사유(퇴장 로그용), 없으면 null. */
    public org.domi.furnace.api.text.Text kickReason() { return kickReason; }

    /** 사유 패킷을 보낸 뒤 연결을 닫는다(중복 로그인 킥 등). 어느 스레드에서든 안전. */
    public void kick(Packet packet) {
        if (packet instanceof org.domi.furnace.server.net.protocol.play.Disconnect d) {
            kickReason = d.reason();
            org.domi.furnace.api.text.Text r = localize(d.reason());
            if (r != d.reason()) packet = new org.domi.furnace.server.net.protocol.play.Disconnect(r);
        }
        final Packet out = packet;
        channel.eventLoop().execute(() -> {
            ByteBuf buf = channel.alloc().buffer();
            Types.writeVarInt(buf, out.id());
            out.write(buf);
            channel.writeAndFlush(buf).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        });
    }

    public int entityId() { return entityId; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }

    // 세션 서버가 준 프로필 속성(textures 등) — 탭 목록 add_player 로 다른 클라에 전달돼 스킨이 보인다.
    private volatile java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties = java.util.List.of();

    public java.util.List<org.domi.furnace.server.players.GameProfile.Property> profileProperties() { return profileProperties; }

    // 생존 상태(게임 모드·체력·허기·경험치·사망). 스레드 안전.
    private final PlayerVitals vitals = new PlayerVitals(this);

    public PlayerVitals vitals() { return vitals; }

    private final org.domi.furnace.server.recipe.RecipeBook recipeBook = new org.domi.furnace.server.recipe.RecipeBook();

    private volatile org.domi.furnace.server.advancement.PlayerAdvancements advancements;

    /** 발전 과제 진행(바닐라 ServerPlayer.getAdvancements). 데이터 팩 없이 뜬 서버면 null. */
    public org.domi.furnace.server.advancement.PlayerAdvancements advancements() { return advancements; }

    public void advancements(org.domi.furnace.server.advancement.PlayerAdvancements a) { this.advancements = a; }

    /** 레시피 북(바닐라 ServerRecipeBook). */
    public org.domi.furnace.server.recipe.RecipeBook recipeBook() { return recipeBook; }

    /** 바닐라 ServerPlayer.awardRecipes — 새로 배운 것만 ADD 로 알린다. @return 새로 배운 수 */
    public int awardRecipes(java.util.Collection<org.domi.furnace.server.data.ResourceId> ids,
                            org.domi.furnace.server.recipe.RecipeManager recipes) {
        var added = recipeBook.addRecipes(ids, recipes);
        // 바닐라 ServerRecipeBook.addRecipes: 레시피마다 recipe_unlocked 를 ADD 패킷보다 먼저
        var adv = advancements;
        if (adv != null) for (var id : added) adv.trigger("minecraft:recipe_unlocked", id);
        if (!added.isEmpty()) send(recipeBook.packet(org.domi.furnace.server.net.protocol.play.RecipeBookUpdate.ADD, added));
        return added.size();
    }

    /** 바닐라 ServerPlayer.resetRecipes — 바닐라처럼 잊은 게 없어도 REMOVE 를 보낸다. @return 잊은 수 */
    public int resetRecipes(java.util.Collection<org.domi.furnace.server.data.ResourceId> ids) {
        var removed = recipeBook.removeRecipes(ids);
        send(recipeBook.packet(org.domi.furnace.server.net.protocol.play.RecipeBookUpdate.REMOVE, removed));
        return removed.size();
    }

    public org.domi.furnace.server.world.GameMode gameMode() { return vitals.gameMode(); }

    /** ServerGamePacketListenerImpl.dropSpamTickCount: 크리에이티브 버리기 한도(버릴 때 +20, 매 틱 -1, 200 이상이면 막음). */
    private final java.util.concurrent.atomic.AtomicInteger dropSpam = new java.util.concurrent.atomic.AtomicInteger();

    /** 크리에이티브 버리기를 허용하면 +20 하고 참. */
    public boolean tryCreativeDrop() {
        while (true) {
            int v = dropSpam.get();
            if (v >= 200) return false;
            if (dropSpam.compareAndSet(v, v + 20)) return true;
        }
    }

    public void tickDropSpam() { dropSpam.updateAndGet(v -> v > 0 ? v - 1 : 0); }

    /** hasInfiniteMaterials: 크리에이티브. */
    public boolean hasInfiniteMaterials() { return gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE; }

    /** LivingEntity.take(entity, count): 그 엔티티를 보는 플레이어들에게 줍는 동작(take_item_entity). 제거되기 전에 부른다. */
    public void take(Entity e, int count) {
        if (!e.isRemoved() && e.tracker != null) {
            e.tracker.broadcast(new org.domi.furnace.server.net.protocol.play.TakeItemEntity(e.id(), entityId, count));
        }
    }

    /** 경험치 구슬 줍기 쿨다운(바닐라 takeXpDelay — 줍으면 2, 틱마다 감소). */
    public int takeXpDelay;

    /** 마지막으로 받아들인 이동 패킷의 변위(서버가 아는 deltaMovement 의 근사 — 밀려나기 계산에 쓴다). */
    private volatile double moveDx, moveDy, moveDz;

    // ------------------------------------------------------------------ Targetable(몹이 겨냥하는 대상으로서의 플레이어)

    @Override public int id() { return entityId; }

    @Override public double eyeY() { return y + (sneaking ? 1.27 : 1.62); }

    @Override public float bbWidth() { return 0.6F; }

    @Override public float bbHeight() { return sneaking ? 1.5F : 1.8F; }

    @Override
    public AABB boundingBox() { return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + bbHeight(), z + 0.3); }

    @Override
    public org.domi.furnace.server.block.Pos blockPosition() { return new org.domi.furnace.server.block.Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }

    @Override public boolean isAlive() { return !vitals.isDeadOrDying(); }

    /** Player.canBeSeenAsEnemy: 무적 능력(크리에이티브·관전자)이 없고 살아 있다. */
    @Override
    public boolean canBeSeenAsEnemy() {
        var gm = gameMode();
        return isAlive() && gm != org.domi.furnace.server.world.GameMode.CREATIVE && gm != org.domi.furnace.server.world.GameMode.SPECTATOR;
    }

    /** getVisibilityPercent: 웅크리면 0.8. TODO(투명·머리 아이템): 투명이면 방어구 가림 정도, 몹 머리 아이템(해골 등)에 따른 보정. */
    @Override
    public double visibilityPercent(Entity looker) { return sneaking ? 0.8 : 1.0; }

    @Override public boolean isPlayer() { return true; }

    @Override
    public boolean hurtByMob(Entity.Damage source, float amount, Mob attacker) {
        return vitals.hurt(org.domi.furnace.server.entity.damage.DamageType.byKey(source.type()), amount, attacker, shieldSource(source.type(), attacker, null));
    }

    @Override
    public void addDeltaMovement(double dx, double dy, double dz) {
        send(new org.domi.furnace.server.net.protocol.play.SetEntityMotion(entityId, moveDx + dx, moveDy + dy, moveDz + dz));
    }

    /** LivingEntity.knockback: 밀려나는 속도를 그 플레이어에게 보낸다(set_entity_motion). */
    @Override
    public void knockback(double strength, double dx, double dz) {
        if (strength <= 0.0) return;
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = len < 1.0E-5 ? 0.0 : dx / len * strength, nz = len < 1.0E-5 ? 0.0 : dz / len * strength;
        send(new org.domi.furnace.server.net.protocol.play.SetEntityMotion(entityId, moveDx / 2.0 - nx,
                onGround ? Math.min(0.4, moveDy / 2.0 + strength) : moveDy, moveDz / 2.0 - nz));
    }

    /**
     * Player.hurtArmor → doHurtEquipment: 방어구(ArmorItem)마다 max(1, 피해/4) 내구도를 깎고 다 닳으면 부서진다(entity_event 49..52).
     * TODO(내구성 인챈트·불 저항 아이템).
     */
    public void hurtArmor(float amount) {
        if (amount <= 0.0F) return;   // Inventory.hurtArmor: 피해 0(방패로 막음)이면 그대로
        amount /= 4.0F;
        if (amount < 1.0F) amount = 1.0F;
        int n = (int) amount;
        for (int i = 0; i < 4; i++) {
            int ws = 5 + i;
            ItemStack stack = inventory.get(ws);
            String name = stack.isEmpty() ? "" : org.domi.furnace.server.item.ItemTools.itemName(stack);
            boolean armorItem = name.endsWith("_helmet") || name.endsWith("_chestplate") || name.endsWith("_leggings") || name.endsWith("_boots");
            if (!armorItem || !org.domi.furnace.server.item.ItemTools.isDamageable(stack)) continue;
            int nd = org.domi.furnace.server.item.ItemTools.damage(stack) + n;
            if (nd >= org.domi.furnace.server.item.ItemTools.maxDamage(stack)) {
                inventory.set(ws, ItemStack.EMPTY);
                var ev = new org.domi.furnace.server.net.protocol.play.EntityEvent(entityId, (byte) (49 + i));
                send(ev);
                vitals.broadcastToTrackers(ev);
            } else {
                inventory.set(ws, org.domi.furnace.server.item.ItemTools.withDamage(stack, nd));
            }
        }
    }

    /** 방어구 칸(머리 5·가슴 6·다리 7·발 8)의 armor 속성 합 — 플레이어 기본 방어 0. */
    public float armorValue() {
        float sum = 0;
        String[] slots = {"head", "chest", "legs", "feet"};
        for (int i = 0; i < 4; i++) sum += org.domi.furnace.server.item.ItemTools.attributeBonus(inventory.get(5 + i), "minecraft:generic.armor", slots[i]);
        return Math.clamp(sum, 0.0F, 30.0F);
    }

    public float armorToughness() {
        float sum = 0;
        String[] slots = {"head", "chest", "legs", "feet"};
        for (int i = 0; i < 4; i++) sum += org.domi.furnace.server.item.ItemTools.attributeBonus(inventory.get(5 + i), "minecraft:generic.armor_toughness", slots[i]);
        return Math.clamp(sum, 0.0F, 20.0F);
    }

    /** 먹기·마시기 중인 손(없으면 -1)과 남은 틱 — region 스레드 전용. */
    public int usingHand = -1, usingTicks;

    private byte sentLivingFlags;
    private boolean usedOffhand;

    /** 사용 중 비트(1)·손 비트(2) — LivingEntity.DATA_LIVING_ENTITY_FLAGS(8). 손 비트는 바닐라처럼 사용이 끝나도 남는다. */
    public byte livingFlags() {
        if (usingHand >= 0) usedOffhand = usingHand == 1;
        return (byte) ((usingHand >= 0 ? 1 : 0) | (usedOffhand ? 2 : 0));
    }

    /** 바뀌었으면 엔티티 데이터(사용 중 플래그)를 본인과 보는 사람에게(ServerEntity.sendDirtyEntityData). */
    public void syncLivingFlags() {
        byte f = livingFlags();
        if (f == sentLivingFlags) return;
        sentLivingFlags = f;
        var pkt = new org.domi.furnace.server.net.protocol.play.SetEntityData(entityId, java.util.List.of(
                new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(8, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_BYTE, f)));
        send(pkt);
        vitals.broadcastToTrackers(pkt);
    }

    /** 방패를 들고 막는 중인가(LivingEntity.isBlocking). */
    public boolean isBlocking() { return Shields.isBlocking(this); }

    /** 직접 원인 엔티티(투사체)가 따로 있는 피해: 막기 방향·관통은 그 엔티티로 본다. */
    public boolean hurtByVia(Entity.Damage source, float amount, Mob attacker, Entity direct) {
        return vitals.hurt(org.domi.furnace.server.entity.damage.DamageType.byKey(source.type()), amount, attacker, shieldSource(source.type(), attacker, direct));
    }

    /** 피해 원인 위치: 직접 원인 엔티티(없으면 때린 몹) 위치. */
    public Shields.Source shieldSource(String type, Mob attacker, Entity direct) {
        Entity d = direct != null ? direct : attacker;
        if (d == null) return Shields.Source.none(type);
        boolean piercing = d instanceof AbstractArrow a && a.getPierceLevel() > 0;
        boolean living = d instanceof LivingEntity && !org.domi.furnace.server.entity.damage.DamageType.keyHasTag(type, "is_projectile");
        return new Shields.Source(type, true, d.x(), d.z(), d instanceof LivingEntity le ? le : null, living, piercing, d.level());
    }

    /** 쇠뇌 장전 소리(시작·중간)를 이미 냈는가 — 바닐라는 CrossbowItem 객체 필드라 전역이지만 여기선 플레이어별. */
    public boolean crossbowStartSound, crossbowMidSound;

    /** 던져 둔 낚시찌(Player.fishing). */
    public FishingHook fishing;

    private volatile org.domi.furnace.server.stats.PlayerStats stats;

    public org.domi.furnace.server.stats.PlayerStats stats() { return stats; }

    public void stats(org.domi.furnace.server.stats.PlayerStats s) { this.stats = s; }

    /** Player.awardStat(통계, n): 이름은 "minecraft:커스텀" 또는 "minecraft:종류:값". 통계가 아직 붙지 않은 플레이어(시험)는 무시. */
    public void awardStat(String stat, int amount) {
        var s = stats;
        if (s != null) s.increment(stat, amount);
    }

    /** 바닐라 Player.getDisplayName: 이름 + 삽입 텍스트 + "/tell 이름 " 제안 클릭. */
    public org.domi.furnace.api.text.Text displayName() {
        return org.domi.furnace.api.text.Text.text(name).insertion(name).clickEvent(new org.domi.furnace.api.text.ClickEvent(org.domi.furnace.api.text.ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " "));
    }

    // 리스폰할 때마다 증가 — 관찰자가 값이 바뀐 것을 보고 엔티티를 지웠다 다시 스폰한다(바닐라: 리스폰 = 새 ServerPlayer).
    private volatile int spawnGeneration;

    public int spawnGeneration() { return spawnGeneration; }

    public void bumpSpawnGeneration() { spawnGeneration++; }

    // 달리기·웅크리기(serverbound player_command). netty 가 쓰고 region 이 읽는다.
    private volatile boolean sprinting, sneaking;

    public boolean sprinting() { return sprinting; }
    public void sprinting(boolean v) { sprinting = v; }
    public boolean sneaking() { return sneaking; }
    public void sneaking(boolean v) { sneaking = v; }

    /**
     * 수락된 위치 패킷 하나(낙하·허기 소모 판정용). netty 가 넣고 소유 region 이 순서대로 꺼낸다.
     * teleport=true 는 서버가 위치를 정한 것(스폰·리스폰·텔레포트) — 이동량 없이 기준점만 옮긴다.
     */
    public record MoveSample(double x, double y, double z, boolean onGround, boolean teleport) {}

    private final java.util.concurrent.ConcurrentLinkedQueue<MoveSample> moves = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void pushMove(double x, double y, double z, boolean onGround) {
        if (moves.size() < 256) moves.add(new MoveSample(x, y, z, onGround, false));
    }

    public void pushTeleport(double x, double y, double z) {
        moves.add(new MoveSample(x, y, z, false, true));
    }

    // region 전용: 마지막으로 처리한 이동 샘플(낙하·소모 계산의 기준점). null = 아직 없음.
    private MoveSample moveAnchor;

    public MoveSample moveAnchor() { return moveAnchor; }

    public void moveAnchor(MoveSample m) { moveAnchor = m; }

    public MoveSample pollMove() { return moves.poll(); }

    public void clearMoves() { moves.clear(); }

    // 클라이언트 언어(client_information, 소문자 en_us 형식).
    private volatile String locale = "en_us";

    public String locale() { return locale; }

    public void locale(String l) {
        String v = l == null || l.isEmpty() ? "en_us" : l;
        this.javaLocale = java.util.Locale.forLanguageTag(v.replace('_', '-'));
        this.locale = v;
    }

    private volatile java.util.Locale javaLocale = java.util.Locale.US;

    /** 클라이언트 언어(Java Locale). */
    public java.util.Locale javaLocale() { return javaLocale; }

    /**
     * 플러그인 번역 저장소(GlobalRenderer)가 아는 키를 이 플레이어 언어로 펼친다(Paper 의 GlobalTranslator 렌더링).
     * 저장소가 없거나 바뀐 곳이 없으면 같은 인스턴스 — 인코딩 캐시가 그대로 쓰인다.
     */
    public org.domi.furnace.api.text.Text localize(org.domi.furnace.api.text.Text t) {
        return org.domi.furnace.api.text.translation.GlobalRenderer.render(t, javaLocale);
    }

    // 플러그인 API 손잡이(처음 요청할 때 만든다).
    private volatile Object apiHandle;

    public Object apiHandle() { return apiHandle; }

    public void apiHandle(Object h) { this.apiHandle = h; }

    // 실제 접속 주소(Velocity 포워딩이면 프록시가 알려 준 주소).
    private volatile java.net.SocketAddress remoteAddress;

    public void remoteAddress(java.net.SocketAddress a) { this.remoteAddress = a; }

    public java.net.SocketAddress remoteAddress() {
        java.net.SocketAddress a = remoteAddress;
        return a != null ? a : channel.remoteAddress();
    }

    /** 바닐라 ServerPlayer.getIpAddress — 포트 없는 IP 문자열. */
    public String ipAddress() {
        java.net.SocketAddress a = remoteAddress();
        if (a instanceof java.net.InetSocketAddress isa && isa.getAddress() != null) return isa.getAddress().getHostAddress();
        return "<unknown>";
    }

    // 채팅 상태(서명 세션·last seen·시그니처 캐시·채팅 표시 설정). 연결이 만들 때 정한다.
    private volatile org.domi.furnace.server.chat.PlayerChatState chat;

    public org.domi.furnace.server.chat.PlayerChatState chat() {
        org.domi.furnace.server.chat.PlayerChatState c = chat;
        if (c == null) {
            synchronized (this) {
                if (chat == null) chat = new org.domi.furnace.server.chat.PlayerChatState(uuid, () -> false);
                c = chat;
            }
        }
        return c;
    }

    public void chat(org.domi.furnace.server.chat.PlayerChatState c) { this.chat = c; }

    /** 바닐라 ServerPlayer.sendSystemMessage — 채팅 숨김이면 액션바(overlay)만 받는다. */
    public void sendSystemMessage(org.domi.furnace.api.text.Text message, boolean overlay) {
        if (chat().acceptsSystemMessages(overlay)) send(new org.domi.furnace.server.net.protocol.play.SystemChat(localize(message), overlay));
    }

    // 클라이언트 설정 allowServerListings — 거부하면 상태 응답 sample 에 익명으로 나온다.
    private volatile boolean allowsListing = true;

    public boolean allowsListing() { return allowsListing; }

    public void allowsListing(boolean v) { this.allowsListing = v; }

    public void profileProperties(java.util.List<org.domi.furnace.server.players.GameProfile.Property> props) {
        this.profileProperties = java.util.List.copyOf(props);
    }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean onGround() { return onGround; }

    // ---- 지도(MapData.Viewer)
    @Override public boolean removed() { return disconnected; }

    /** 지금은 오버월드 하나뿐(차원은 로드맵 뒤쪽). */
    @Override public String dimension() { return "minecraft:overworld"; }

    /** Inventory.contains(predicate): 칸 전체(주·핫바·갑옷·왼손 — 제작 칸 제외). */
    @Override public boolean holdsMap(java.util.function.Predicate<ItemStack> predicate) {
        for (int slot = 5; slot < PlayerInventory.SIZE; slot++) {
            ItemStack s = inventory.get(slot);
            if (!s.isEmpty() && predicate.test(s)) return true;
        }
        return false;
    }

    private volatile java.util.function.Consumer<double[]> teleporter;

    public void teleporter(java.util.function.Consumer<double[]> t) { this.teleporter = t; }

    /** 서버가 플레이어를 (x,y,z)로 옮긴다 — 연결의 IO 스레드가 보정 텔레포트를 보내고 이동 대기열을 비운다. */
    public void teleportTo(double x, double y, double z) {
        var t = teleporter;
        if (t != null) t.accept(new double[] {x, y, z});
    }

    /** 마지막으로 받은 이동 패킷의 이동량(블록/틱) — 던지기 속도에 더해진다(바닐라 getKnownMovement). */
    public double[] knownMovement() { return new double[] {moveDx, moveDy, moveDz}; }

    // ------------------------------------------------------------------ 아이템 재사용 대기(ItemCooldowns)

    private final java.util.concurrent.ConcurrentHashMap<Integer, int[]> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();   // 아이템 → {시작, 끝} 틱
    private volatile int cooldownTick;

    public boolean isOnCooldown(int itemId) {
        int[] c = cooldowns.get(itemId);
        return c != null && c[1] > cooldownTick;
    }

    /** ItemCooldowns.addCooldown: 그 아이템을 ticks 동안 못 쓰게 하고 클라이언트에 알린다. */
    public void addCooldown(int itemId, int ticks) {
        cooldowns.put(itemId, new int[] {cooldownTick, cooldownTick + ticks});
        send(new org.domi.furnace.server.net.protocol.play.Cooldown(itemId, ticks));
    }

    /** ItemCooldowns.tick: 틱마다 한 칸 가고, 끝난 것은 지우며 끝났다고 알린다. */
    public void tickCooldowns() {
        int now = ++cooldownTick;
        if (cooldowns.isEmpty()) return;
        for (var it = cooldowns.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue()[1] <= now) {
                it.remove();
                send(new org.domi.furnace.server.net.protocol.play.Cooldown(e.getKey(), 0));
            }
        }
    }
    private ChunkPos queuedCenter;               // 시야 청크를 대기열에 올린 마지막 중심(null=아직 안 함)
    public ChunkPos lastCenter() { return lastCenter; }
    public ChunkPos queuedCenter() { return queuedCenter; }
    public void queuedCenter(ChunkPos c) { this.queuedCenter = c; }
    public void lastCenter(ChunkPos c) { this.lastCenter = c; }
    public Set<ChunkPos> loadedChunks() { return loadedChunks; }
    public Set<ChunkPos> pendingChunks() { return pendingChunks; }

    /** 요청했지만 아직 끝나지 않은(생성·직렬화 중인) 청크 전송 수 — 완료 콜백은 다른 스레드서 줄인다. */
    private final java.util.concurrent.atomic.AtomicInteger chunkSendsInFlight = new java.util.concurrent.atomic.AtomicInteger();

    public java.util.concurrent.atomic.AtomicInteger chunkSendsInFlight() { return chunkSendsInFlight; }
    public Map<Integer, double[]> tracked() { return tracked; }
    public Map<Integer, Integer> missingTicks() { return missingTicks; }
    public Map<Integer, UUID> listed() { return listed; }

    private volatile int latencyMs;
    /** 탭 목록에 표시되는 핑(ms). KeepAlive 왕복으로 갱신. */
    public int latencyMs() { return latencyMs; }
    /** 바닐라와 같은 평활화: (이전*3 + 새값)/4. 첫 측정은 그대로. */
    public void recordLatency(int sampleMs) {
        int old = latencyMs;
        latencyMs = old == 0 ? sampleMs : (old * 3 + sampleMs) / 4;
    }

    private final EnderChestInventory enderChest = new EnderChestInventory();

    /** 플레이어의 엔더 상자 27칸(어느 엔더 상자를 열든 같다). */
    public EnderChestInventory enderChest() { return enderChest; }

    public org.domi.furnace.server.menu.InventoryMenu inventoryMenu() { return inventoryMenu; }

    /** 지금 열린 창(없으면 인벤토리 창). 메뉴가 만들어지기 전(시험용 Player)에는 null. */
    public org.domi.furnace.server.menu.AbstractContainerMenu containerMenu() { return containerMenu; }

    /** 접속 때 인벤토리 창을 만들어 건다. */
    public void initMenus(org.domi.furnace.server.menu.InventoryMenu menu) {
        this.inventoryMenu = menu;
        this.containerMenu = menu;
    }

    public void setContainerMenu(org.domi.furnace.server.menu.AbstractContainerMenu menu) { this.containerMenu = menu; }

    /** 열린 창이 있는가(인벤토리 창이 아닌). */
    public boolean hasOpenContainer() { return containerMenu != null && containerMenu != inventoryMenu; }

    /** ServerPlayer.nextContainerCounter: 1..100 순환(0 은 인벤토리 창). */
    public int nextContainerCounter() {
        containerCounter = containerCounter % 100 + 1;
        return containerCounter;
    }

    /** 메뉴 상태(remote·stateId·커서)를 지키는 모니터 — 연결 스레드(클릭)와 region 스레드(틱 동기·줍기)가 함께 쓴다. */
    public Object menuLock() { return inventory; }
}
