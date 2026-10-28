// 바닐라 DispenserBlock.DISPENSER_REGISTRY + DispenseItemBehavior.bootstrap: 아이템별 발사 동작. 없으면 기본(던지기).
// 등록 순서도 바닐라 그대로(아이템 생성자의 ArmorItem/Elytra/Shield/Minecart 등록 → bootstrap 이 덮어쓴다).
// 보류(기본 던지기로 둔다): 몹 양동이 6종(물고기·아홀로틀·올챙이 — 몹 양동이 체계 없음), 양탄자 16색(라마 장식 없음).
package org.domi.furnace.server.block.dispenser;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockItems;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockPlacement;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.ItemUse;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.types.BaseFireBlock;
import org.domi.furnace.server.block.types.BeehiveBlock;
import org.domi.furnace.server.block.types.BucketPickup;
import org.domi.furnace.server.block.types.CampfireBlock;
import org.domi.furnace.server.block.types.CandleBlock;
import org.domi.furnace.server.block.types.CandleCakeBlock;
import org.domi.furnace.server.block.types.RespawnAnchorBlock;
import org.domi.furnace.server.block.types.TntBlock;
import org.domi.furnace.server.block.types.WitherSkullBlock;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity;
import org.domi.furnace.server.entity.AbstractMinecart;
import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.ChestBoat;
import org.domi.furnace.server.entity.DispenserEquip;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;

public final class DispenseBehaviors {

    private DispenseBehaviors() {}

    public static final DefaultDispenseItemBehavior DEFAULT = new DefaultDispenseItemBehavior();

    private static final Map<String, DispenseItemBehavior> REGISTRY = new HashMap<>();

    private static final Prop LIT = B.prop("lit"), HONEY_LEVEL = B.prop("honey_level"), SHAPE = B.prop("shape"), ROTATION = B.prop("rotation");

    /** DispenserBlock.getDispenseMethod: 등록된 동작, 없으면 기본. */
    public static DispenseItemBehavior get(ItemStack stack) {
        String n = ItemTools.itemName(stack);
        DispenseItemBehavior b = REGISTRY.get(n);
        if (b != null) return b;
        if (n.endsWith("_spawn_egg")) return SPAWN_EGG;   // SpawnEggItem.eggs() 전부
        return DEFAULT;
    }

    private static void register(String item, DispenseItemBehavior b) { REGISTRY.put("minecraft:" + item, b); }

    private static boolean isEmptyBlock(BlockLevel level, Pos p) { return B.isAir(level.getBlockState(p)); }

    /** ItemStack.hurtAndBreak(amount, level, null, …): 내구성 확률로 줄인 뒤 깎고, 다 닳으면 하나 줄인다. */
    static ItemStack hurtAndBreak(BlockLevel level, ItemStack stack, int amount) {
        if (!ItemTools.isDamageable(stack)) return stack;
        if (amount > 0) {
            int unbreaking = ItemTools.enchantmentLevel(stack, "minecraft:unbreaking");
            if (unbreaking > 0) {
                boolean armor = org.domi.furnace.server.item.ItemContext.isInTag("minecraft:item", "minecraft:enchantable/armor", stack.itemId());
                float chance = armor ? (2.0F + 2.0F * (unbreaking - 1)) / (10.0F + 5.0F * (unbreaking - 1)) : (float) unbreaking / (2.0F + (unbreaking - 1));
                int removed = 0;
                for (int j = 0; j < amount; j++) if (level.random().nextFloat() < chance) removed++;
                amount -= removed;
            }
        }
        if (amount == 0) return stack;
        int max = ItemTools.maxDamage(stack);
        int dmg = Math.clamp(ItemTools.damage(stack) + amount, 0, max);
        ItemStack damaged = ItemTools.withDamage(stack, dmg);
        return dmg >= max ? damaged.withCount(damaged.count() - 1) : damaged;
    }

    // ------------------------------------------------------------------ 생성 알·갑옷 거치대

    /** 생성 알: 앞 칸에 몹(아래를 보지 않으면 바닥에 맞춘다) — 예외 없으면 하나 쓰고, 발사기 칸에서 ENTITY_PLACE. */
    static final DispenseItemBehavior SPAWN_EGG = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource s, ItemStack stack) {
            int dir = s.facing();
            String n = ItemTools.itemName(stack);
            String type = "minecraft:" + n.substring("minecraft:".length(), n.length() - "_spawn_egg".length());
            s.level().spawnMobFromEgg(type, s.front(), dir != Dir.UP, false);
            s.level().gameEvent("minecraft:entity_place", s.pos(), null, -1);
            return stack.withCount(stack.count() - 1);
        }
    };

    /** 갑옷 거치대: EntityType.spawn — 칸 가운데, 레벨 난수로 방향을 정한 뒤 발사 방향(toYRot)으로 덮어쓴다. */
    static final DispenseItemBehavior ARMOR_STAND = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource s, ItemStack stack) {
            if (!(s.level() instanceof ChunkLevel cl)) return stack;
            Pos p = s.front();
            var stand = new org.domi.furnace.server.entity.ArmorStand(cl, cl.nextEntityId());
            stand.moveTo(p.x() + 0.5, p.y(), p.z() + 0.5, org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(cl.random().nextFloat() * 360.0F), 0.0F);
            stand.setYRot(BlockSource.toYRot(s.facing()));
            cl.addFreshEntityWithPassengers(stand);
            return stack.withCount(stack.count() - 1);
        }
    };

    static {
        // 아이템 생성자 등록: ArmorItem(갑옷·거북 등딱지·동물 갑옷), 겉날개, 방패 — ArmorItem.DISPENSE_ITEM_BEHAVIOR
        DispenseItemBehavior armor = new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.dispenseArmor(cl, s.front(), stack) : null;
                return r != null ? r : super.execute(s, stack);
            }
        };
        for (String m : new String[] {"leather", "chainmail", "iron", "golden", "diamond", "netherite"}) {
            for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) register(m + "_" + piece, armor);
        }
        register("turtle_helmet", armor);
        register("wolf_armor", armor);
        for (String m : new String[] {"leather", "iron", "golden", "diamond"}) register(m + "_horse_armor", armor);
        register("elytra", armor);
        register("shield", armor);
        // MinecartItem 생성자
        DispenseItemBehavior cart = new MinecartDispense();
        for (String c : new String[] {"minecart", "chest_minecart", "furnace_minecart", "tnt_minecart", "hopper_minecart", "command_block_minecart"}) register(c, cart);

        // bootstrap
        register("arrow", new ProjectileDispense(ProjectileDispense::arrow));
        register("tipped_arrow", new ProjectileDispense(ProjectileDispense::arrow));
        register("spectral_arrow", new ProjectileDispense(ProjectileDispense::spectralArrow));
        register("egg", new ProjectileDispense(ProjectileDispense::egg));
        register("snowball", new ProjectileDispense(ProjectileDispense::snowball));
        register("experience_bottle", new ProjectileDispense(ProjectileDispense::experienceBottle, 6.0F * 0.5F, 1.1F * 1.25F));
        register("splash_potion", new ProjectileDispense(ProjectileDispense::potion, 6.0F * 0.5F, 1.1F * 1.25F));
        register("lingering_potion", new ProjectileDispense(ProjectileDispense::potion, 6.0F * 0.5F, 1.1F * 1.25F));
        register("firework_rocket", ProjectileDispense.FIREWORK);
        register("fire_charge", ProjectileDispense.FIRE_CHARGE);
        register("wind_charge", ProjectileDispense.WIND_CHARGE);
        // 생성 알은 get() 에서(SPAWN_EGG)
        register("armor_stand", ARMOR_STAND);
        register("saddle", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.saddle(cl, s.front(), stack) : null;
                if (r == null) return super.execute(s, stack);
                setSuccess(true);
                return r;
            }
        });
        DispenseItemBehavior horseArmor = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.horseArmor(cl, s.front(), stack) : null;
                if (r == null) return super.execute(s, stack);
                setSuccess(true);
                return r;
            }
        };
        for (String m : new String[] {"leather", "iron", "golden", "diamond"}) register(m + "_horse_armor", horseArmor);
        // 양탄자 16색: 보류(라마 장식 칸 없음) — 기본 던지기
        register("chest", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.chest(cl, s.front(), stack) : null;
                if (r == null) return super.execute(s, stack);
                setSuccess(true);
                return r;
            }
        });
        for (String t : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "cherry", "dark_oak", "mangrove"}) {
            register(t + "_boat", new BoatDispense(t, false));
            register(t + "_chest_boat", new BoatDispense(t, true));
        }
        register("bamboo_raft", new BoatDispense("bamboo", false));
        register("bamboo_chest_raft", new BoatDispense("bamboo", true));
        DispenseItemBehavior filledBucket = new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                Pos p = s.front();
                String n = ItemTools.itemName(stack);
                boolean ok = switch (n) {
                    case "minecraft:water_bucket" -> ItemUse.emptyBucketContents(s.level(), Fluids.WATER_SOURCE, p);
                    case "minecraft:lava_bucket" -> ItemUse.emptyBucketContents(s.level(), Fluids.LAVA_SOURCE, p);
                    default -> emptyPowderSnow(s.level(), p);
                };
                if (ok) return consumeWithRemainder(s, stack, B.stack("bucket", 1));
                return DEFAULT.dispense(s, stack);
            }
        };
        register("lava_bucket", filledBucket);
        register("water_bucket", filledBucket);
        register("powder_snow_bucket", filledBucket);
        // 몹 양동이(연어·대구·복어·열대어·아홀로틀·올챙이): 보류 — 기본 던지기
        register("bucket", new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                Pos p = s.front();
                int state = level.getBlockState(p);
                if (!BucketPickup.is(state)) return super.execute(s, stack);
                String filled = BucketPickup.pickupBlock(null, level, p, state);
                if (filled == null) return super.execute(s, stack);
                level.gameEvent("minecraft:fluid_pickup", p, null, -1);
                return consumeWithRemainder(s, stack, B.stack(filled, 1));
            }
        });
        register("flint_and_steel", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                setSuccess(true);
                int dir = s.facing();
                Pos p = s.front();
                int state = level.getBlockState(p);
                if (BaseFireBlock.canBePlacedAt(level, p, dir)) {
                    level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                    level.gameEvent("minecraft:block_place", p, null, -1);
                } else if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
                    level.setBlockAndUpdate(p, LIT.with(state, true));
                    level.gameEvent("minecraft:block_change", p, null, -1);
                } else if (B.behavior(state) instanceof TntBlock) {
                    TntBlock.prime(level, p);
                    level.removeBlock(p, false);
                } else {
                    setSuccess(false);
                }
                return isSuccess() ? hurtAndBreak(level, stack, 1) : stack;
            }
        });
        register("bone_meal", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                setSuccess(true);
                BlockLevel level = s.level();
                Pos p = s.front();
                if (!ItemUse.growCrop(level, null, 0, stack, p) && !ItemUse.growWaterPlant(level, null, 0, stack, p, -1)) {
                    setSuccess(false);
                    return stack;
                }
                level.levelEvent(null, 1505, p, 15);
                return stack.withCount(stack.count() - 1);
            }
        });
        register("tnt", new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                Pos p = s.front();
                level.spawnPrimedTnt(p, null, -1);
                level.playSound(null, p.x() + 0.5, p.y(), p.z() + 0.5, "minecraft:entity.tnt.primed", "blocks", 1.0F, 1.0F);
                level.gameEvent("minecraft:entity_place", p, null, -1);
                return stack.withCount(stack.count() - 1);
            }
        });
        DispenseItemBehavior head = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.dispenseArmor(cl, s.front(), stack) : null;
                setSuccess(r != null);
                return r != null ? r : stack;
            }
        };
        for (String h : new String[] {"creeper_head", "zombie_head", "dragon_head", "skeleton_skull", "piglin_head", "player_head"}) register(h, head);
        register("wither_skeleton_skull", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                int dir = s.facing();
                Pos p = s.front();
                if (isEmptyBlock(level, p) && canSpawnWither(level, p)) {
                    int rot = (int) Math.floor(BlockSource.toYRot(dir) / 22.5F + 0.5) & 15;   // RotationSegment.convertToSegment
                    level.setBlock(p, ROTATION.withInt(B.defaultState("wither_skeleton_skull"), rot), 3);
                    level.gameEvent("minecraft:block_place", p, null, -1);
                    WitherSkullBlock.checkSpawn(level, p);   // 해골 블록 엔티티는 늘 있으므로 바로
                    setSuccess(true);
                    return stack.withCount(stack.count() - 1);
                }
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.dispenseArmor(cl, p, stack) : null;
                setSuccess(r != null);
                return r != null ? r : stack;
            }
        });
        register("carved_pumpkin", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                Pos p = s.front();
                if (isEmptyBlock(level, p) && canSpawnGolem(level, p)) {
                    level.setBlock(p, B.defaultState("carved_pumpkin"), 3);
                    level.gameEvent("minecraft:block_place", p, null, -1);
                    setSuccess(true);
                    return stack.withCount(stack.count() - 1);
                }
                ItemStack r = s.level() instanceof ChunkLevel cl ? DispenserEquip.dispenseArmor(cl, p, stack) : null;
                setSuccess(r != null);
                return r != null ? r : stack;
            }
        });
        DispenseItemBehavior shulker = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                setSuccess(false);
                BlockItems.Entry e = BlockItems.entry(stack.itemId());
                if (e != null) {
                    int dir = s.facing();
                    Pos p = s.front();
                    int face = isEmptyBlock(s.level(), p.below()) ? dir : Dir.UP;
                    boolean ok = BlockPlacement.place(e, PlaceContext.directional(s.level(), p, dir, stack, face)).consumesAction();
                    setSuccess(ok);
                    if (ok) return stack.withCount(stack.count() - 1);
                }
                return stack;
            }
        };
        register("shulker_box", shulker);
        for (String c : new String[] {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
                "blue", "brown", "green", "red", "black"}) register(c + "_shulker_box", shulker);
        register("glass_bottle", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                setSuccess(false);
                BlockLevel level = s.level();
                Pos p = s.front();
                int state = level.getBlockState(p);
                if (B.isTag(state, "minecraft:beehives") && HONEY_LEVEL.has(state) && B.behavior(state) instanceof BeehiveBlock hive
                        && HONEY_LEVEL.intValue(state) >= BeehiveBlock.MAX_HONEY_LEVELS) {
                    hive.releaseBeesAndResetHoneyLevel(level, state, p, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                    setSuccess(true);
                    return takeLiquid(s, stack, B.stack("honey_bottle", 1));
                }
                if (Fluids.isWater(level.getFluidState(p))) {
                    setSuccess(true);
                    return takeLiquid(s, stack, ItemUse.waterPotion());
                }
                return super.execute(s, stack);
            }

            private ItemStack takeLiquid(BlockSource s, ItemStack stack, ItemStack filled) {
                s.level().gameEvent("minecraft:fluid_pickup", s.pos(), null, -1);
                return consumeWithRemainder(s, stack, filled);
            }
        });
        register("glowstone", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                Pos p = s.front();
                int state = level.getBlockState(p);
                setSuccess(true);
                if (!B.is(state, "respawn_anchor")) return super.execute(s, stack);
                if (RespawnAnchorBlock.charge(state) != 4) {
                    RespawnAnchorBlock.charge(null, level, p, state);
                    return stack.withCount(stack.count() - 1);
                }
                setSuccess(false);
                return stack;
            }
        });
        register("shears", new ShearsDispense());
        register("brush", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                Boolean r = s.level() instanceof ChunkLevel cl ? DispenserEquip.brush(cl, s.front()) : null;
                if (r == null || !r) {
                    setSuccess(false);
                    return stack;
                }
                return hurtAndBreak(s.level(), stack, 16);   // 성공 표시는 바꾸지 않는다(바닐라)
            }
        });
        register("honeycomb", new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                BlockLevel level = s.level();
                Pos p = s.front();
                int waxed = ItemUse.waxed(level.getBlockState(p));
                if (waxed < 0) return super.execute(s, stack);
                level.setBlockAndUpdate(p, waxed);
                level.levelEvent(null, 3003, p, 0);
                setSuccess(true);
                return stack.withCount(stack.count() - 1);
            }
        });
        register("potion", new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource s, ItemStack stack) {
                Object pc = ItemTools.component(stack, "minecraft:potion_contents");
                boolean water = pc instanceof Map<?, ?> m && "minecraft:water".equals(m.get("potion"));
                BlockLevel level = s.level();
                Pos src = s.pos(), p = s.front();
                if (!water || !B.isTag(level.getBlockState(p), "minecraft:convertable_to_mud")) return DEFAULT.dispense(s, stack);
                for (int i = 0; i < 5; i++) {
                    double px = src.x() + level.random().nextDouble();
                    double pz = src.z() + level.random().nextDouble();
                    level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("splash"), px, src.y() + 1, pz, 1, 0.0, 0.0, 0.0, 1.0);
                }
                level.playSound(null, src, "minecraft:item.bottle.empty", "blocks", 1.0F, 1.0F);
                level.gameEvent("minecraft:fluid_place", src, null, -1);
                level.setBlockAndUpdate(p, B.defaultState("mud"));
                return consumeWithRemainder(s, stack, B.stack("glass_bottle", 1));
            }
        });
    }

    /** SolidBucketItem.emptyContents(가루눈): 세계 안의 빈 칸이면 놓고 FLUID_PLACE·소리. */
    private static boolean emptyPowderSnow(BlockLevel level, Pos p) {
        if (!level.isInWorldBounds(p) || !isEmptyBlock(level, p)) return false;
        level.setBlock(p, B.defaultState("powder_snow"), 3);
        level.gameEvent("minecraft:fluid_place", p, null, -1);
        level.playSound(null, p, "minecraft:item.bucket.empty_powder_snow", "blocks", 1.0F, 1.0F);
        return true;
    }

    /** CarvedPumpkinBlock.canSpawnGolem: 눈 골렘 바탕(" ", "#", "#") 또는 철 골렘 바탕("~ ~", "###", "~#~")이 24 방향 중 하나로 맞다. */
    static boolean canSpawnGolem(BlockLevel level, Pos p) {
        return BlockPatternFind.find(level, p, new String[] {" ", "#", "#"}, st -> B.is(st, "snow_block"))
                || BlockPatternFind.find(level, p, new String[] {"~ ~", "###", "~#~"}, st -> B.is(st, "iron_block"));
    }

    /** WitherSkullBlock.canSpawnMob(위더 해골): 바닥 + 2 이상, 평화로움 아님, 위더 바탕("   ", "###", "~#~")이 맞다. */
    static boolean canSpawnWither(BlockLevel level, Pos p) {
        if (p.y() < level.minBuildHeight() + 2 || level.difficulty() == 0) return false;
        return BlockPatternFind.find(level, p, new String[] {"   ", "###", "~#~"}, st -> B.isTag(st, "minecraft:wither_summon_base_blocks"));
    }

    // ------------------------------------------------------------------ 배·수레

    /** BoatDispenseItemBehavior: 앞 칸이 물이면 1 위, 공기이고 아래가 물이면 그 높이에. 아니면 기본 던지기(소리는 늘 1000). */
    static final class BoatDispense extends DefaultDispenseItemBehavior {
        private final String type;
        private final boolean chest;

        BoatDispense(String type, boolean chest) {
            this.type = type;
            this.chest = chest;
        }

        @Override
        protected ItemStack execute(BlockSource s, ItemStack stack) {
            if (!(s.level() instanceof ChunkLevel cl)) return stack;
            int dir = s.facing();
            double off = 0.5625 + 1.375 / 2.0;   // EntityType.BOAT.getWidth()
            double x = s.centerX() + Dir.DX[dir] * off, y = s.centerY() + Dir.DY[dir] * 1.125F, z = s.centerZ() + Dir.DZ[dir] * off;
            Pos p = s.front();
            double dy;
            if (Fluids.isWater(cl.getFluidState(p))) {
                dy = 1.0;
            } else {
                if (!B.isAir(cl.getBlockState(p)) || !Fluids.isWater(cl.getFluidState(p.below()))) return DEFAULT.dispense(s, stack);
                dy = 0.0;
            }
            Boat boat = chest ? new ChestBoat(cl, cl.nextEntityId()) : new Boat(cl, cl.nextEntityId());
            boat.setPos(x, y + dy, z);
            boat.xo = x;
            boat.yo = y + dy;
            boat.zo = z;
            boat.setVariant(Boat.Type.byName(type));
            boat.setYRot(BlockSource.toYRot(dir));
            cl.addFreshEntity(boat);
            return stack.withCount(stack.count() - 1);
        }
    }

    /** MinecartItem 의 발사 동작: 앞 칸이 레일이면 0.1(경사 0.6), 공기이고 아래가 레일이면 −0.9(아래로 쏘지 않고 경사면 −0.4). */
    static final class MinecartDispense extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource s, ItemStack stack) {
            if (!(s.level() instanceof ChunkLevel cl)) return stack;
            int dir = s.facing();
            double x = s.centerX() + Dir.DX[dir] * 1.125, y = Math.floor(s.centerY()) + Dir.DY[dir], z = s.centerZ() + Dir.DZ[dir] * 1.125;
            Pos p = s.front();
            int state = cl.getBlockState(p);
            double dy;
            if (B.isTag(state, "minecraft:rails")) {
                dy = ascending(state) ? 0.6 : 0.1;
            } else {
                int below = cl.getBlockState(p.below());
                if (!B.isAir(state) || !B.isTag(below, "minecraft:rails")) return DEFAULT.dispense(s, stack);
                dy = dir != Dir.DOWN && ascending(below) ? -0.4 : -0.9;
            }
            AbstractMinecart.Type type = switch (ItemTools.itemName(stack)) {
                case "minecraft:chest_minecart" -> AbstractMinecart.Type.CHEST;
                case "minecraft:furnace_minecart" -> AbstractMinecart.Type.FURNACE;
                case "minecraft:tnt_minecart" -> AbstractMinecart.Type.TNT;
                case "minecraft:hopper_minecart" -> AbstractMinecart.Type.HOPPER;
                case "minecraft:command_block_minecart" -> AbstractMinecart.Type.COMMAND_BLOCK;
                default -> AbstractMinecart.Type.RIDEABLE;
            };
            cl.addFreshEntity(AbstractMinecart.create(cl, x, y + dy, z, type));
            return stack.withCount(stack.count() - 1);
        }

        private static boolean ascending(int state) { return SHAPE.has(state) && SHAPE.valueName(state).startsWith("ascending_"); }
    }

    // ------------------------------------------------------------------ 가위(ShearsDispenseItemBehavior)

    static final class ShearsDispense extends OptionalDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource s, ItemStack stack) {
            BlockLevel level = s.level();
            Pos p = s.front();
            setSuccess(tryShearBeehive(level, p) || level instanceof ChunkLevel cl && DispenserEquip.shear(cl, p));
            return isSuccess() ? hurtAndBreak(level, stack, 1) : stack;
        }

        private static boolean tryShearBeehive(BlockLevel level, Pos p) {
            int state = level.getBlockState(p);
            if (!B.isTag(state, "minecraft:beehives") || !HONEY_LEVEL.has(state) || !(B.behavior(state) instanceof BeehiveBlock hive)) return false;
            if (HONEY_LEVEL.intValue(state) < BeehiveBlock.MAX_HONEY_LEVELS) return false;
            level.playSound(null, p, "minecraft:block.beehive.shear", "blocks", 1.0F, 1.0F);
            BeehiveBlock.dropHoneycomb(level, p);
            hive.releaseBeesAndResetHoneyLevel(level, state, p, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
            level.gameEvent("minecraft:shear", p, null, -1);
            return true;
        }
    }
}
