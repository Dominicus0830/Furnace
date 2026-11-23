package net.minecraft.world.entity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * 엔티티 종류 shim(Mojang 이름): 키로 구별되는 값 객체. 패킷 shim 의 필드 타입으로 쓰이고, 패킷 라이브러리가
 * {@code byString}·{@code getKey} 로 Bukkit {@link org.bukkit.entity.EntityType} 과 잇는다. 상수는 바닐라 130개 전부(종류 표에서).
 */
@SuppressWarnings({"unchecked", "rawtypes"})   // 상수: of 가 EntityType<?> 를 준다
public class EntityType<T> {

    private static final Map<ResourceLocation, EntityType<?>> BY_KEY = new ConcurrentHashMap<>();

    private final ResourceLocation key;

    /** 바닐라 기본 레지스트리(ENTITY_TYPE)의 기본 키. 키 없이 만들어진 객체(패킷 라이브러리가 필드를 빈 값으로 채울 때)가 이 값이 된다. */
    private static final ResourceLocation DEFAULT_KEY = ResourceLocation.parse("minecraft:pig");

    private EntityType(ResourceLocation key) { this.key = key == null ? DEFAULT_KEY : key; }

    /** 바닐라 상수 PLAYER(종류 표에 없다 — 플레이어는 EntityType#create 로 만들지 않는다). */
    public static final EntityType<net.minecraft.world.entity.player.Player> PLAYER = (EntityType) of(ResourceLocation.withDefaultNamespace("player"));

    // 나머지 바닐라 상수: 종류 표(src/shimspec/kind-classes-1.21.1.txt 의 E 줄) 그대로 — 형식 인자는 바닐라 상수의 것. EntityTypeConstantsTest 가 표와 견준다
    public static final EntityType<net.minecraft.world.entity.animal.allay.Allay> ALLAY = (EntityType) of(ResourceLocation.withDefaultNamespace("allay"));
    public static final EntityType<net.minecraft.world.entity.AreaEffectCloud> AREA_EFFECT_CLOUD = (EntityType) of(ResourceLocation.withDefaultNamespace("area_effect_cloud"));
    public static final EntityType<net.minecraft.world.entity.animal.armadillo.Armadillo> ARMADILLO = (EntityType) of(ResourceLocation.withDefaultNamespace("armadillo"));
    public static final EntityType<net.minecraft.world.entity.decoration.ArmorStand> ARMOR_STAND = (EntityType) of(ResourceLocation.withDefaultNamespace("armor_stand"));
    public static final EntityType<net.minecraft.world.entity.projectile.Arrow> ARROW = (EntityType) of(ResourceLocation.withDefaultNamespace("arrow"));
    public static final EntityType<net.minecraft.world.entity.animal.axolotl.Axolotl> AXOLOTL = (EntityType) of(ResourceLocation.withDefaultNamespace("axolotl"));
    public static final EntityType<net.minecraft.world.entity.ambient.Bat> BAT = (EntityType) of(ResourceLocation.withDefaultNamespace("bat"));
    public static final EntityType<net.minecraft.world.entity.animal.Bee> BEE = (EntityType) of(ResourceLocation.withDefaultNamespace("bee"));
    public static final EntityType<net.minecraft.world.entity.monster.Blaze> BLAZE = (EntityType) of(ResourceLocation.withDefaultNamespace("blaze"));
    public static final EntityType<net.minecraft.world.entity.Display.BlockDisplay> BLOCK_DISPLAY = (EntityType) of(ResourceLocation.withDefaultNamespace("block_display"));
    public static final EntityType<net.minecraft.world.entity.vehicle.Boat> BOAT = (EntityType) of(ResourceLocation.withDefaultNamespace("boat"));
    public static final EntityType<net.minecraft.world.entity.monster.Bogged> BOGGED = (EntityType) of(ResourceLocation.withDefaultNamespace("bogged"));
    public static final EntityType<net.minecraft.world.entity.monster.breeze.Breeze> BREEZE = (EntityType) of(ResourceLocation.withDefaultNamespace("breeze"));
    public static final EntityType<net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge> BREEZE_WIND_CHARGE = (EntityType) of(ResourceLocation.withDefaultNamespace("breeze_wind_charge"));
    public static final EntityType<net.minecraft.world.entity.animal.camel.Camel> CAMEL = (EntityType) of(ResourceLocation.withDefaultNamespace("camel"));
    public static final EntityType<net.minecraft.world.entity.animal.Cat> CAT = (EntityType) of(ResourceLocation.withDefaultNamespace("cat"));
    public static final EntityType<net.minecraft.world.entity.monster.CaveSpider> CAVE_SPIDER = (EntityType) of(ResourceLocation.withDefaultNamespace("cave_spider"));
    public static final EntityType<net.minecraft.world.entity.vehicle.ChestBoat> CHEST_BOAT = (EntityType) of(ResourceLocation.withDefaultNamespace("chest_boat"));
    public static final EntityType<net.minecraft.world.entity.vehicle.MinecartChest> CHEST_MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("chest_minecart"));
    public static final EntityType<net.minecraft.world.entity.animal.Chicken> CHICKEN = (EntityType) of(ResourceLocation.withDefaultNamespace("chicken"));
    public static final EntityType<net.minecraft.world.entity.animal.Cod> COD = (EntityType) of(ResourceLocation.withDefaultNamespace("cod"));
    public static final EntityType<net.minecraft.world.entity.vehicle.MinecartCommandBlock> COMMAND_BLOCK_MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("command_block_minecart"));
    public static final EntityType<net.minecraft.world.entity.animal.Cow> COW = (EntityType) of(ResourceLocation.withDefaultNamespace("cow"));
    public static final EntityType<net.minecraft.world.entity.monster.Creeper> CREEPER = (EntityType) of(ResourceLocation.withDefaultNamespace("creeper"));
    public static final EntityType<net.minecraft.world.entity.animal.Dolphin> DOLPHIN = (EntityType) of(ResourceLocation.withDefaultNamespace("dolphin"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.Donkey> DONKEY = (EntityType) of(ResourceLocation.withDefaultNamespace("donkey"));
    public static final EntityType<net.minecraft.world.entity.projectile.DragonFireball> DRAGON_FIREBALL = (EntityType) of(ResourceLocation.withDefaultNamespace("dragon_fireball"));
    public static final EntityType<net.minecraft.world.entity.monster.Drowned> DROWNED = (EntityType) of(ResourceLocation.withDefaultNamespace("drowned"));
    public static final EntityType<net.minecraft.world.entity.projectile.ThrownEgg> EGG = (EntityType) of(ResourceLocation.withDefaultNamespace("egg"));
    public static final EntityType<net.minecraft.world.entity.monster.ElderGuardian> ELDER_GUARDIAN = (EntityType) of(ResourceLocation.withDefaultNamespace("elder_guardian"));
    public static final EntityType<net.minecraft.world.entity.boss.enderdragon.EndCrystal> END_CRYSTAL = (EntityType) of(ResourceLocation.withDefaultNamespace("end_crystal"));
    public static final EntityType<net.minecraft.world.entity.boss.enderdragon.EnderDragon> ENDER_DRAGON = (EntityType) of(ResourceLocation.withDefaultNamespace("ender_dragon"));
    public static final EntityType<net.minecraft.world.entity.projectile.ThrownEnderpearl> ENDER_PEARL = (EntityType) of(ResourceLocation.withDefaultNamespace("ender_pearl"));
    public static final EntityType<net.minecraft.world.entity.monster.EnderMan> ENDERMAN = (EntityType) of(ResourceLocation.withDefaultNamespace("enderman"));
    public static final EntityType<net.minecraft.world.entity.monster.Endermite> ENDERMITE = (EntityType) of(ResourceLocation.withDefaultNamespace("endermite"));
    public static final EntityType<net.minecraft.world.entity.monster.Evoker> EVOKER = (EntityType) of(ResourceLocation.withDefaultNamespace("evoker"));
    public static final EntityType<net.minecraft.world.entity.projectile.EvokerFangs> EVOKER_FANGS = (EntityType) of(ResourceLocation.withDefaultNamespace("evoker_fangs"));
    public static final EntityType<net.minecraft.world.entity.projectile.ThrownExperienceBottle> EXPERIENCE_BOTTLE = (EntityType) of(ResourceLocation.withDefaultNamespace("experience_bottle"));
    public static final EntityType<net.minecraft.world.entity.ExperienceOrb> EXPERIENCE_ORB = (EntityType) of(ResourceLocation.withDefaultNamespace("experience_orb"));
    public static final EntityType<net.minecraft.world.entity.projectile.EyeOfEnder> EYE_OF_ENDER = (EntityType) of(ResourceLocation.withDefaultNamespace("eye_of_ender"));
    public static final EntityType<net.minecraft.world.entity.item.FallingBlockEntity> FALLING_BLOCK = (EntityType) of(ResourceLocation.withDefaultNamespace("falling_block"));
    public static final EntityType<net.minecraft.world.entity.projectile.FireworkRocketEntity> FIREWORK_ROCKET = (EntityType) of(ResourceLocation.withDefaultNamespace("firework_rocket"));
    public static final EntityType<net.minecraft.world.entity.animal.Fox> FOX = (EntityType) of(ResourceLocation.withDefaultNamespace("fox"));
    public static final EntityType<net.minecraft.world.entity.animal.frog.Frog> FROG = (EntityType) of(ResourceLocation.withDefaultNamespace("frog"));
    public static final EntityType<net.minecraft.world.entity.vehicle.MinecartFurnace> FURNACE_MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("furnace_minecart"));
    public static final EntityType<net.minecraft.world.entity.monster.Ghast> GHAST = (EntityType) of(ResourceLocation.withDefaultNamespace("ghast"));
    public static final EntityType<net.minecraft.world.entity.monster.Giant> GIANT = (EntityType) of(ResourceLocation.withDefaultNamespace("giant"));
    public static final EntityType<net.minecraft.world.entity.decoration.GlowItemFrame> GLOW_ITEM_FRAME = (EntityType) of(ResourceLocation.withDefaultNamespace("glow_item_frame"));
    public static final EntityType<net.minecraft.world.entity.GlowSquid> GLOW_SQUID = (EntityType) of(ResourceLocation.withDefaultNamespace("glow_squid"));
    public static final EntityType<net.minecraft.world.entity.animal.goat.Goat> GOAT = (EntityType) of(ResourceLocation.withDefaultNamespace("goat"));
    public static final EntityType<net.minecraft.world.entity.monster.Guardian> GUARDIAN = (EntityType) of(ResourceLocation.withDefaultNamespace("guardian"));
    public static final EntityType<net.minecraft.world.entity.monster.hoglin.Hoglin> HOGLIN = (EntityType) of(ResourceLocation.withDefaultNamespace("hoglin"));
    public static final EntityType<net.minecraft.world.entity.vehicle.MinecartHopper> HOPPER_MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("hopper_minecart"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.Horse> HORSE = (EntityType) of(ResourceLocation.withDefaultNamespace("horse"));
    public static final EntityType<net.minecraft.world.entity.monster.Husk> HUSK = (EntityType) of(ResourceLocation.withDefaultNamespace("husk"));
    public static final EntityType<net.minecraft.world.entity.monster.Illusioner> ILLUSIONER = (EntityType) of(ResourceLocation.withDefaultNamespace("illusioner"));
    public static final EntityType<net.minecraft.world.entity.Interaction> INTERACTION = (EntityType) of(ResourceLocation.withDefaultNamespace("interaction"));
    public static final EntityType<net.minecraft.world.entity.animal.IronGolem> IRON_GOLEM = (EntityType) of(ResourceLocation.withDefaultNamespace("iron_golem"));
    public static final EntityType<net.minecraft.world.entity.item.ItemEntity> ITEM = (EntityType) of(ResourceLocation.withDefaultNamespace("item"));
    public static final EntityType<net.minecraft.world.entity.Display.ItemDisplay> ITEM_DISPLAY = (EntityType) of(ResourceLocation.withDefaultNamespace("item_display"));
    public static final EntityType<net.minecraft.world.entity.decoration.ItemFrame> ITEM_FRAME = (EntityType) of(ResourceLocation.withDefaultNamespace("item_frame"));
    public static final EntityType<net.minecraft.world.entity.OminousItemSpawner> OMINOUS_ITEM_SPAWNER = (EntityType) of(ResourceLocation.withDefaultNamespace("ominous_item_spawner"));
    public static final EntityType<net.minecraft.world.entity.projectile.LargeFireball> FIREBALL = (EntityType) of(ResourceLocation.withDefaultNamespace("fireball"));
    public static final EntityType<net.minecraft.world.entity.decoration.LeashFenceKnotEntity> LEASH_KNOT = (EntityType) of(ResourceLocation.withDefaultNamespace("leash_knot"));
    public static final EntityType<net.minecraft.world.entity.LightningBolt> LIGHTNING_BOLT = (EntityType) of(ResourceLocation.withDefaultNamespace("lightning_bolt"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.Llama> LLAMA = (EntityType) of(ResourceLocation.withDefaultNamespace("llama"));
    public static final EntityType<net.minecraft.world.entity.projectile.LlamaSpit> LLAMA_SPIT = (EntityType) of(ResourceLocation.withDefaultNamespace("llama_spit"));
    public static final EntityType<net.minecraft.world.entity.monster.MagmaCube> MAGMA_CUBE = (EntityType) of(ResourceLocation.withDefaultNamespace("magma_cube"));
    public static final EntityType<net.minecraft.world.entity.Marker> MARKER = (EntityType) of(ResourceLocation.withDefaultNamespace("marker"));
    public static final EntityType<net.minecraft.world.entity.vehicle.Minecart> MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("minecart"));
    public static final EntityType<net.minecraft.world.entity.animal.MushroomCow> MOOSHROOM = (EntityType) of(ResourceLocation.withDefaultNamespace("mooshroom"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.Mule> MULE = (EntityType) of(ResourceLocation.withDefaultNamespace("mule"));
    public static final EntityType<net.minecraft.world.entity.animal.Ocelot> OCELOT = (EntityType) of(ResourceLocation.withDefaultNamespace("ocelot"));
    public static final EntityType<net.minecraft.world.entity.decoration.Painting> PAINTING = (EntityType) of(ResourceLocation.withDefaultNamespace("painting"));
    public static final EntityType<net.minecraft.world.entity.animal.Panda> PANDA = (EntityType) of(ResourceLocation.withDefaultNamespace("panda"));
    public static final EntityType<net.minecraft.world.entity.animal.Parrot> PARROT = (EntityType) of(ResourceLocation.withDefaultNamespace("parrot"));
    public static final EntityType<net.minecraft.world.entity.monster.Phantom> PHANTOM = (EntityType) of(ResourceLocation.withDefaultNamespace("phantom"));
    public static final EntityType<net.minecraft.world.entity.animal.Pig> PIG = (EntityType) of(ResourceLocation.withDefaultNamespace("pig"));
    public static final EntityType<net.minecraft.world.entity.monster.piglin.Piglin> PIGLIN = (EntityType) of(ResourceLocation.withDefaultNamespace("piglin"));
    public static final EntityType<net.minecraft.world.entity.monster.piglin.PiglinBrute> PIGLIN_BRUTE = (EntityType) of(ResourceLocation.withDefaultNamespace("piglin_brute"));
    public static final EntityType<net.minecraft.world.entity.monster.Pillager> PILLAGER = (EntityType) of(ResourceLocation.withDefaultNamespace("pillager"));
    public static final EntityType<net.minecraft.world.entity.animal.PolarBear> POLAR_BEAR = (EntityType) of(ResourceLocation.withDefaultNamespace("polar_bear"));
    public static final EntityType<net.minecraft.world.entity.projectile.ThrownPotion> POTION = (EntityType) of(ResourceLocation.withDefaultNamespace("potion"));
    public static final EntityType<net.minecraft.world.entity.animal.Pufferfish> PUFFERFISH = (EntityType) of(ResourceLocation.withDefaultNamespace("pufferfish"));
    public static final EntityType<net.minecraft.world.entity.animal.Rabbit> RABBIT = (EntityType) of(ResourceLocation.withDefaultNamespace("rabbit"));
    public static final EntityType<net.minecraft.world.entity.monster.Ravager> RAVAGER = (EntityType) of(ResourceLocation.withDefaultNamespace("ravager"));
    public static final EntityType<net.minecraft.world.entity.animal.Salmon> SALMON = (EntityType) of(ResourceLocation.withDefaultNamespace("salmon"));
    public static final EntityType<net.minecraft.world.entity.animal.Sheep> SHEEP = (EntityType) of(ResourceLocation.withDefaultNamespace("sheep"));
    public static final EntityType<net.minecraft.world.entity.monster.Shulker> SHULKER = (EntityType) of(ResourceLocation.withDefaultNamespace("shulker"));
    public static final EntityType<net.minecraft.world.entity.projectile.ShulkerBullet> SHULKER_BULLET = (EntityType) of(ResourceLocation.withDefaultNamespace("shulker_bullet"));
    public static final EntityType<net.minecraft.world.entity.monster.Silverfish> SILVERFISH = (EntityType) of(ResourceLocation.withDefaultNamespace("silverfish"));
    public static final EntityType<net.minecraft.world.entity.monster.Skeleton> SKELETON = (EntityType) of(ResourceLocation.withDefaultNamespace("skeleton"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.SkeletonHorse> SKELETON_HORSE = (EntityType) of(ResourceLocation.withDefaultNamespace("skeleton_horse"));
    public static final EntityType<net.minecraft.world.entity.monster.Slime> SLIME = (EntityType) of(ResourceLocation.withDefaultNamespace("slime"));
    public static final EntityType<net.minecraft.world.entity.projectile.SmallFireball> SMALL_FIREBALL = (EntityType) of(ResourceLocation.withDefaultNamespace("small_fireball"));
    public static final EntityType<net.minecraft.world.entity.animal.sniffer.Sniffer> SNIFFER = (EntityType) of(ResourceLocation.withDefaultNamespace("sniffer"));
    public static final EntityType<net.minecraft.world.entity.animal.SnowGolem> SNOW_GOLEM = (EntityType) of(ResourceLocation.withDefaultNamespace("snow_golem"));
    public static final EntityType<net.minecraft.world.entity.projectile.Snowball> SNOWBALL = (EntityType) of(ResourceLocation.withDefaultNamespace("snowball"));
    public static final EntityType<net.minecraft.world.entity.vehicle.MinecartSpawner> SPAWNER_MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("spawner_minecart"));
    public static final EntityType<net.minecraft.world.entity.projectile.SpectralArrow> SPECTRAL_ARROW = (EntityType) of(ResourceLocation.withDefaultNamespace("spectral_arrow"));
    public static final EntityType<net.minecraft.world.entity.monster.Spider> SPIDER = (EntityType) of(ResourceLocation.withDefaultNamespace("spider"));
    public static final EntityType<net.minecraft.world.entity.animal.Squid> SQUID = (EntityType) of(ResourceLocation.withDefaultNamespace("squid"));
    public static final EntityType<net.minecraft.world.entity.monster.Stray> STRAY = (EntityType) of(ResourceLocation.withDefaultNamespace("stray"));
    public static final EntityType<net.minecraft.world.entity.monster.Strider> STRIDER = (EntityType) of(ResourceLocation.withDefaultNamespace("strider"));
    public static final EntityType<net.minecraft.world.entity.animal.frog.Tadpole> TADPOLE = (EntityType) of(ResourceLocation.withDefaultNamespace("tadpole"));
    public static final EntityType<net.minecraft.world.entity.Display.TextDisplay> TEXT_DISPLAY = (EntityType) of(ResourceLocation.withDefaultNamespace("text_display"));
    public static final EntityType<net.minecraft.world.entity.item.PrimedTnt> TNT = (EntityType) of(ResourceLocation.withDefaultNamespace("tnt"));
    public static final EntityType<net.minecraft.world.entity.vehicle.MinecartTNT> TNT_MINECART = (EntityType) of(ResourceLocation.withDefaultNamespace("tnt_minecart"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.TraderLlama> TRADER_LLAMA = (EntityType) of(ResourceLocation.withDefaultNamespace("trader_llama"));
    public static final EntityType<net.minecraft.world.entity.projectile.ThrownTrident> TRIDENT = (EntityType) of(ResourceLocation.withDefaultNamespace("trident"));
    public static final EntityType<net.minecraft.world.entity.animal.TropicalFish> TROPICAL_FISH = (EntityType) of(ResourceLocation.withDefaultNamespace("tropical_fish"));
    public static final EntityType<net.minecraft.world.entity.animal.Turtle> TURTLE = (EntityType) of(ResourceLocation.withDefaultNamespace("turtle"));
    public static final EntityType<net.minecraft.world.entity.monster.Vex> VEX = (EntityType) of(ResourceLocation.withDefaultNamespace("vex"));
    public static final EntityType<net.minecraft.world.entity.npc.Villager> VILLAGER = (EntityType) of(ResourceLocation.withDefaultNamespace("villager"));
    public static final EntityType<net.minecraft.world.entity.monster.Vindicator> VINDICATOR = (EntityType) of(ResourceLocation.withDefaultNamespace("vindicator"));
    public static final EntityType<net.minecraft.world.entity.npc.WanderingTrader> WANDERING_TRADER = (EntityType) of(ResourceLocation.withDefaultNamespace("wandering_trader"));
    public static final EntityType<net.minecraft.world.entity.monster.warden.Warden> WARDEN = (EntityType) of(ResourceLocation.withDefaultNamespace("warden"));
    public static final EntityType<net.minecraft.world.entity.projectile.windcharge.WindCharge> WIND_CHARGE = (EntityType) of(ResourceLocation.withDefaultNamespace("wind_charge"));
    public static final EntityType<net.minecraft.world.entity.monster.Witch> WITCH = (EntityType) of(ResourceLocation.withDefaultNamespace("witch"));
    public static final EntityType<net.minecraft.world.entity.boss.wither.WitherBoss> WITHER = (EntityType) of(ResourceLocation.withDefaultNamespace("wither"));
    public static final EntityType<net.minecraft.world.entity.monster.WitherSkeleton> WITHER_SKELETON = (EntityType) of(ResourceLocation.withDefaultNamespace("wither_skeleton"));
    public static final EntityType<net.minecraft.world.entity.projectile.WitherSkull> WITHER_SKULL = (EntityType) of(ResourceLocation.withDefaultNamespace("wither_skull"));
    public static final EntityType<net.minecraft.world.entity.animal.Wolf> WOLF = (EntityType) of(ResourceLocation.withDefaultNamespace("wolf"));
    public static final EntityType<net.minecraft.world.entity.monster.Zoglin> ZOGLIN = (EntityType) of(ResourceLocation.withDefaultNamespace("zoglin"));
    public static final EntityType<net.minecraft.world.entity.monster.Zombie> ZOMBIE = (EntityType) of(ResourceLocation.withDefaultNamespace("zombie"));
    public static final EntityType<net.minecraft.world.entity.animal.horse.ZombieHorse> ZOMBIE_HORSE = (EntityType) of(ResourceLocation.withDefaultNamespace("zombie_horse"));
    public static final EntityType<net.minecraft.world.entity.monster.ZombieVillager> ZOMBIE_VILLAGER = (EntityType) of(ResourceLocation.withDefaultNamespace("zombie_villager"));
    public static final EntityType<net.minecraft.world.entity.monster.ZombifiedPiglin> ZOMBIFIED_PIGLIN = (EntityType) of(ResourceLocation.withDefaultNamespace("zombified_piglin"));
    public static final EntityType<net.minecraft.world.entity.projectile.FishingHook> FISHING_BOBBER = (EntityType) of(ResourceLocation.withDefaultNamespace("fishing_bobber"));

    /** 바닐라 noSave 종류(저장하지 않는다): 1.21.1 바닐라 EntityType 의 noSave 넷. */
    private static final java.util.Set<String> NO_SAVE = java.util.Set.of("leash_knot", "lightning_bolt", "player", "fishing_bobber");

    /** 바닐라 canSerialize: 청크에 저장되는 종류인가(플레이어·번개·낚시찌·끈 매듭은 거짓). */
    public boolean canSerialize() { return !("minecraft".equals(key.getNamespace()) && NO_SAVE.contains(key.getPath())); }

    /** 바닐라 {@code EntityType.byString}: "zombie"·"minecraft:zombie". 모르는 종류는 빈 값. */
    public static Optional<EntityType<?>> byString(String name) {
        ResourceLocation k = name == null ? null : ResourceLocation.tryParse(name);
        if (k == null || !"minecraft".equals(k.getNamespace())) return Optional.empty();
        org.bukkit.entity.EntityType t = org.bukkit.entity.EntityType.fromKey(k.getPath());
        return t == null ? Optional.empty() : Optional.of(of(k));
    }

    /** 바닐라 {@code EntityType.by(nbt)}: "id" 의 종류(모르면 빈 값). */
    public static Optional<EntityType<?>> by(net.minecraft.nbt.CompoundTag nbt) { return byString(nbt.getString("id")); }

    /** 바닐라 {@code EntityType.create(nbt, level)}: 종류를 만들어 nbt 를 싣는다. 모르는 종류·만들 수 없는 종류는 경고를 남기고 빈 값. */
    public static Optional<Entity> create(net.minecraft.nbt.CompoundTag nbt, net.minecraft.world.level.Level level) {
        Optional<Entity> e = by(nbt).map(t -> t.create(level));
        if (e.isPresent()) e.get().load(nbt);
        else org.slf4j.LoggerFactory.getLogger(EntityType.class).warn("Skipping Entity with id {}", nbt.getString("id"));
        return e;
    }

    /**
     * 바닐라 {@code EntityType.loadEntityRecursive}(NMS shim 5e-3 — Axiom 엔티티 놓기): nbt 로 엔티티를 만들어 싣고 처리 함수를 적용한 뒤(null 이면 null),
     * "Passengers" 의 승객마다 같은 방법으로 만들어 강제로 태운다. 읽다 실패하면 경고를 남기고 null. 돌려준 묶음은 아직 월드에 없고
     * {@code ServerLevel#tryAddFreshEntityWithPassengers}·{@code addFreshEntityWithPassengers} 로 넣는다(태우기는 그때 서버가 한다).
     */
    public static Entity loadEntityRecursive(net.minecraft.nbt.CompoundTag nbt, net.minecraft.world.level.Level level, java.util.function.Function<Entity, Entity> fn) {
        Optional<Entity> made;
        try {
            made = create(nbt, level);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(EntityType.class).warn("Exception loading entity: ", e);
            return null;
        }
        Entity e = made.map(fn).orElse(null);
        if (e == null) return null;
        if (nbt.contains("Passengers", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = nbt.getList("Passengers", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Entity p = loadEntityRecursive(list.getCompound(i), level, fn);
                if (p != null) p.furnaceRideDetached(e);
            }
        }
        return e;
    }

    /** 바닐라 {@code EntityType.getKey}. */
    public static ResourceLocation getKey(EntityType<?> type) { return type.key; }

    /** 호환 모듈 전용: 키로 같은 객체를 돌려준다. */
    public static EntityType<?> of(ResourceLocation key) { return BY_KEY.computeIfAbsent(key == null ? DEFAULT_KEY : key, EntityType::new); }

    public ResourceLocation furnaceKey() { return key; }

    /**
     * 바닐라 {@code EntityType#create(Level)}(T-212): 아직 월드에 없는 새 엔티티. 플레이어·낚시찌처럼 따로 만들 수 없는 종류는 null(바닐라 공장이 null).
     * 돌려준 엔티티는 {@code load}·{@code absMoveTo}·{@code setUUID} 를 기록했다가 {@code ServerLevel#addFreshEntity} 때 서버 엔티티로 만든다
     * (Furnace 엔티티는 넣을 region 의 레벨에 묶여 만들어지기 때문). 객체는 그 종류의 바닐라 클래스다(좀비는 Zombie — 그 Bukkit 객체의 getHandle 과 같은 클래스, T-232).
     * 1.21.1 엔티티 종류는 모두 기본 기능 플래그라 {@code isEnabled} 로 거르는 종류는 없다.
     */
    public Entity create(net.minecraft.world.level.Level level) {
        org.bukkit.entity.EntityType bt = "minecraft".equals(key.getNamespace()) ? org.bukkit.entity.EntityType.fromKey(key.getPath()) : null;
        if (bt == null || !bt.isSpawnable()) return null;
        return org.domi.furnace.compat.nms.NmsEntityKinds.createFresh(this, level);   // 그 종류의 바닐라 클래스(T-232)
    }

    @Override
    public String toString() { return "entity." + key.getNamespace() + "." + key.getPath(); }
}
