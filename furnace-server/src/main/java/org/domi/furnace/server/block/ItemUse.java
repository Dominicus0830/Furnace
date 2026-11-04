// 아이템 사용(바닐라 ServerPlayerGameMode.useItem → ItemStack.use → Item.use)과 블록 아이템이 아닌 아이템의 블록 쓰기(Item.useOn).
// Item.use: 양동이(뜨기·붓기)·유리병·물 위에 놓는 블록 아이템(연잎·개구리알)·갑옷/겉날개 장착(Equipable).
// Item.useOn: 부싯돌·화염구·뼛가루·괭이·삽·도끼·밀랍·가위·물병(진흙)·디버그 막대·나침반(자석석)·엔더의 눈.
// 먹기·마시기·활처럼 오래 쓰는 아이템과 엔티티를 만드는 아이템은 엔티티 체계에서.
package org.domi.furnace.server.block;

import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.types.BaseFireBlock;
import org.domi.furnace.server.block.types.BonemealableBlock;
import org.domi.furnace.server.block.types.BucketPickup;
import org.domi.furnace.server.block.types.CampfireBlock;
import org.domi.furnace.server.block.types.CandleBlock;
import org.domi.furnace.server.block.types.CandleCakeBlock;
import org.domi.furnace.server.block.types.EndPortalFrameBlock;
import org.domi.furnace.server.block.types.GrowingPlantBlock;
import org.domi.furnace.server.block.types.WeatheringCopper;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public final class ItemUse {

    private ItemUse() {}

    /** InteractionResultHolder<ItemStack>. */
    public record Holder(InteractionResult result, ItemStack stack) {
        static Holder pass(ItemStack s) { return new Holder(InteractionResult.PASS, s); }

        static Holder fail(ItemStack s) { return new Holder(InteractionResult.FAIL, s); }

        static Holder success(ItemStack s) { return new Holder(InteractionResult.sidedSuccess(false), s); }
    }

    private static final Prop LIT = B.prop("lit"), AGE = B.prop("age");
    private static final int GRASS_BLOCK = B.id("grass_block"), DIRT = B.id("dirt"), COARSE_DIRT = B.id("coarse_dirt"),
            PODZOL = B.id("podzol"), MYCELIUM = B.id("mycelium"), ROOTED_DIRT = B.id("rooted_dirt"), DIRT_PATH = B.id("dirt_path"),
            FARMLAND = B.id("farmland"), WATER = B.id("water"), SEAGRASS = B.id("seagrass"), END_PORTAL = B.id("end_portal"),
            END_PORTAL_FRAME = B.id("end_portal_frame"), LODESTONE = B.id("lodestone"), MUD = B.id("mud");

    /** AxeItem.STRIPPABLES. */
    private static final Map<Integer, Integer> STRIPPABLES = new java.util.HashMap<>();

    static {
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove"}) {
            STRIPPABLES.put(B.id(wood + "_wood"), B.id("stripped_" + wood + "_wood"));
            STRIPPABLES.put(B.id(wood + "_log"), B.id("stripped_" + wood + "_log"));
        }
        for (String n : new String[] {"warped_stem", "warped_hyphae", "crimson_stem", "crimson_hyphae", "bamboo_block"}) {
            STRIPPABLES.put(B.id(n), B.id("stripped_" + n));
        }
    }

    /** HoneycombItem.WAXABLES — 구리 사슬 블록 → waxed_ 붙인 블록. */
    private static final String[] WAXABLE = {"copper_block", "exposed_copper", "weathered_copper", "oxidized_copper",
            "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper", "cut_copper_slab",
            "exposed_cut_copper_slab", "weathered_cut_copper_slab", "oxidized_cut_copper_slab", "cut_copper_stairs",
            "exposed_cut_copper_stairs", "weathered_cut_copper_stairs", "oxidized_cut_copper_stairs", "chiseled_copper",
            "exposed_chiseled_copper", "weathered_chiseled_copper", "oxidized_chiseled_copper", "copper_door", "exposed_copper_door",
            "weathered_copper_door", "oxidized_copper_door", "copper_trapdoor", "exposed_copper_trapdoor", "weathered_copper_trapdoor",
            "oxidized_copper_trapdoor", "copper_grate", "exposed_copper_grate", "weathered_copper_grate", "oxidized_copper_grate",
            "copper_bulb", "exposed_copper_bulb", "weathered_copper_bulb", "oxidized_copper_bulb"};
    private static final Map<Integer, Integer> WAX_ON = new java.util.HashMap<>(), WAX_OFF = new java.util.HashMap<>();

    static {
        for (String n : WAXABLE) {
            WAX_ON.put(B.id(n), B.id("waxed_" + n));
            WAX_OFF.put(B.id("waxed_" + n), B.id(n));
        }
    }

    private static String name(ItemStack s) { return ItemTools.itemName(s); }

    private static ItemStack stack(String item) { return new ItemStack(ItemContext.id("minecraft:item", item), 1); }

    // ------------------------------------------------------------------ ServerPlayerGameMode.useItem

    /** useItem(player, level, stack, hand): 관전자·재사용 대기면 PASS, 아니면 Item.use 후 바뀐 스택을 손에. */
    public static InteractionResult useItem(BlockLevel level, BlockActor player, int hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        ItemStack stack = player.itemInHand(hand);
        if (player.isOnCooldown(stack.itemId())) return InteractionResult.PASS;
        Holder h = use(level, player, hand, stack);
        ItemStack out = h.stack();
        if (out == stack) return h.result();   // 같은 스택·개수·내구도(오래 쓰는 아이템은 아직 없다)
        player.setItemInHand(hand, out.isEmpty() ? ItemStack.EMPTY : out);
        return h.result();
    }

    /** Item.use(level, player, hand) — 아이템 종류별. */
    static Holder use(BlockLevel level, BlockActor player, int hand, ItemStack stack) {
        String n = name(stack);
        switch (n) {
            case "minecraft:bucket": return bucketUse(level, player, hand, stack, Fluids.EMPTY);
            case "minecraft:water_bucket": return bucketUse(level, player, hand, stack, Fluids.WATER_SOURCE);
            case "minecraft:lava_bucket": return bucketUse(level, player, hand, stack, Fluids.LAVA_SOURCE);
            case "minecraft:cod_bucket", "minecraft:salmon_bucket", "minecraft:pufferfish_bucket", "minecraft:tropical_fish_bucket",
                    "minecraft:axolotl_bucket", "minecraft:tadpole_bucket": return bucketUse(level, player, hand, stack, Fluids.WATER_SOURCE);
            case "minecraft:ender_eye": return enderEyeUse(level, player, hand, stack);
            case "minecraft:carrot_on_a_stick": return foodOnAStickUse(player, hand, stack, false);
            case "minecraft:warped_fungus_on_a_stick": return foodOnAStickUse(player, hand, stack, true);
            case "minecraft:glass_bottle": return glassBottleUse(level, player, stack);
            case "minecraft:map": return emptyMapUse(level, player, stack);
            case "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl", "minecraft:experience_bottle", "minecraft:splash_potion",
                    "minecraft:lingering_potion", "minecraft:wind_charge": return throwItem(level, player, stack, n);
            case "minecraft:firework_rocket": return fireworkUse(level, player, stack);
            case "minecraft:goat_horn": return goatHornUse(level, player, hand, stack);
            case "minecraft:writable_book", "minecraft:written_book": {   // WritableBookItem/WrittenBookItem.use: openItemGui(서버는 서명된 책만 open_book) + 사용 통계, CONSUME
                if (n.equals("minecraft:written_book")) player.openBook(hand);   // 미구현: resolveBookComponents(선택자·점수 컴포넌트 해석)
                player.awardStat("minecraft:used:" + n);
                return Holder.success(stack);
            }
            default: break;
        }
        if (n.endsWith("_boat") || n.endsWith("_raft")) return boatUse(level, player, stack, n);
        BlockItems.Entry e = BlockItems.entry(stack.itemId());
        if (e != null && e.kind() == BlockItems.Kind.PLACE_ON_WATER) return placeOnWaterUse(level, player, hand, stack, e);
        int slot = equipSlot(n);
        if (slot >= 0) return equip(level, player, hand, stack, slot, n);
        // TODO(엔티티·먹기): 음식·물약·우유(오래 쓰기), 활·쇠뇌·삼지창·방패, 낚싯대, 망원경, 책, 광산 수레.
        return Holder.pass(stack);
    }

    // ------------------------------------------------------------------ 염소 뿔(GoatHornItem.use)

    /**
     * 악기가 있으면 사용을 시작하고(use_duration 틱), 소리(기록 분류, 음량 range/16, 플레이어 본인 제외)를 낸 뒤 instrument_play 게임 이벤트,
     * 재사용 대기 use_duration, 사용 통계. 결과 CONSUME. 악기를 모르면 FAIL.
     */
    private static Holder goatHornUse(BlockLevel level, BlockActor player, int hand, ItemStack stack) {
        var inst = org.domi.furnace.server.entity.ItemEffects.instrumentOf(stack);
        if (inst == null) return Holder.fail(stack);
        player.startUsingItem(hand, inst.useDuration());
        // InstrumentItem.play: level.playSound(player, player, …, RECORDS) — 엔티티에 붙는 소리, 분류 이름은 "record"
        if (level instanceof ChunkLevel cl) cl.playSoundOn(player, player, inst.sound(), "record", inst.range() / 16.0F, 1.0F);
        else level.playSound(player, player.x(), player.y(), player.z(), inst.sound(), "record", inst.range() / 16.0F, 1.0F);
        if (level instanceof ChunkLevel cl) {
            cl.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.INSTRUMENT_PLAY,
                    new org.domi.furnace.server.entity.Vec3(player.x(), player.y(), player.z()),
                    org.domi.furnace.server.world.gameevent.GameEventContext.of(player.handle()));
        }
        player.addCooldown(stack.itemId(), inst.useDuration());
        player.awardStat("minecraft:used:minecraft:goat_horn");
        return Holder.success(stack);
    }

    // ------------------------------------------------------------------ 던지는 아이템(SnowballItem·EggItem·EnderpearlItem·ExperienceBottleItem·ThrowablePotionItem·WindChargeItem)

    /** 던지기 소리: 소리, 소리 분류(알·투척 물약은 플레이어, 나머지는 중립). */
    private static void throwSound(ChunkLevel level, BlockActor player, String sound, String source) {
        level.playSound(null, player.x(), player.y(), player.z(), sound, source, 0.5F, 0.4F / (level.random().nextFloat() * 0.4F + 0.8F));
    }

    /**
     * 던지는 아이템 use: 소리 → (재사용 대기) → 엔티티를 시선 방향으로 쏘기 → 사용 통계 → 하나 소모(크리에이티브는 안 줄어든다).
     * 순서는 아이템마다 바닐라 그대로(엔더 진주는 소리 뒤 대기, 바람 구슬은 엔티티 뒤 소리).
     */
    private static Holder throwItem(BlockLevel level, BlockActor player, ItemStack stack, String n) {
        if (!(level instanceof ChunkLevel cl) || !(player.handle() instanceof org.domi.furnace.server.entity.Targetable owner)) return Holder.pass(stack);
        org.domi.furnace.server.entity.Projectile proj;
        float zOffset = 0.0F, power = 1.5F;
        switch (n) {
            case "minecraft:snowball" -> {
                throwSound(cl, player, "entity.snowball.throw", "neutral");
                proj = new org.domi.furnace.server.entity.Snowball(cl, cl.nextEntityId());
            }
            case "minecraft:egg" -> {
                throwSound(cl, player, "entity.egg.throw", "players");
                proj = new org.domi.furnace.server.entity.ThrownEgg(cl, cl.nextEntityId());
            }
            case "minecraft:ender_pearl" -> {
                throwSound(cl, player, "entity.ender_pearl.throw", "neutral");
                player.addCooldown(stack.itemId(), 20);
                proj = new org.domi.furnace.server.entity.ThrownEnderpearl(cl, cl.nextEntityId());
            }
            case "minecraft:experience_bottle" -> {
                throwSound(cl, player, "entity.experience_bottle.throw", "neutral");
                proj = new org.domi.furnace.server.entity.ThrownExperienceBottle(cl, cl.nextEntityId());
                zOffset = -20.0F;
                power = 0.7F;
            }
            case "minecraft:splash_potion", "minecraft:lingering_potion" -> {
                if (n.equals("minecraft:splash_potion")) throwSound(cl, player, "entity.splash_potion.throw", "players");
                else throwSound(cl, player, "entity.lingering_potion.throw", "neutral");
                proj = new org.domi.furnace.server.entity.ThrownPotion(cl, cl.nextEntityId());
                zOffset = -20.0F;
                power = 0.5F;
            }
            default -> {   // wind_charge
                var wind = new org.domi.furnace.server.entity.WindCharge(cl, cl.nextEntityId());
                wind.setOwner(owner);
                wind.setPos(player.x(), owner.eyeY(), player.z());
                proj = wind;
            }
        }
        if (proj instanceof org.domi.furnace.server.entity.ThrowableProjectile t) {
            t.startAt(owner);
            t.setItem(stack);
        }
        proj.shootFromRotation(player.xRot(), player.yRot(), zOffset, power, 1.0F, player.knownMovement(), player.onGround());
        cl.addFreshEntity(proj);
        if (n.equals("minecraft:wind_charge")) {
            throwSound(cl, player, "entity.wind_charge.throw", "neutral");
            player.addCooldown(stack.itemId(), 10);
        }
        player.awardStat("minecraft:used:" + n);
        return Holder.success(player.isCreative() ? stack : stack.withCount(stack.count() - 1));
    }

    // ------------------------------------------------------------------ 배(BoatItem)

    /**
     * BoatItem.use: 유체까지 맞추는 시선이 빗나가면 넘긴다. 시선 5 블록 앞까지 넓힌 상자 안 맞출 수 있는 엔티티의 몸이 눈을 품으면 넘긴다.
     * 블록에 맞았으면 그 지점에 배(종류·상자 여부는 아이템 이름)를 플레이어 방향으로 놓아, 블록과 겹치면 실패, 아니면 넣고 하나 쓴다.
     * TODO(엔티티 충돌): noCollision 의 엔티티(다른 배) 부분, EntityType.createDefaultStackConfig(이름·엔티티 데이터 컴포넌트).
     */
    private static Holder boatUse(BlockLevel level, BlockActor player, ItemStack stack, String n) {
        if (!(level instanceof ChunkLevel cl)) return Holder.pass(stack);
        Clip.Result hit = Clip.playerPOV(level, player, Clip.FluidMode.ANY);
        if (hit.miss()) return Holder.pass(stack);
        double[] v = Clip.viewVector(player.xRot(), player.yRot());
        var body = player.handle() instanceof org.domi.furnace.server.entity.Player owner ? owner.boundingBox()
                : new org.domi.furnace.server.entity.AABB(player.x() - 0.3, player.y(), player.z() - 0.3, player.x() + 0.3, player.y() + 1.8, player.z() + 0.3);
        var box = body.expandTowards(v[0] * 5.0, v[1] * 5.0, v[2] * 5.0).inflate(1.0);
        double ex = player.x(), ey = player.y() + player.eyeHeight(), ez = player.z();
        for (var e : cl.getEntities(null, box, x -> !x.isSpectator() && x.isPickable())) {
            if (e.boundingBox().contains(ex, ey, ez)) return Holder.pass(stack);   // getPickRadius 0
        }
        String m = n.substring("minecraft:".length());
        boolean chest = m.endsWith("_chest_boat") || m.endsWith("_chest_raft");
        String type = m.endsWith("_raft") ? "bamboo" : m.substring(0, m.length() - (chest ? "_chest_boat".length() : "_boat".length()));
        org.domi.furnace.server.entity.Boat boat = chest ? new org.domi.furnace.server.entity.ChestBoat(cl, cl.nextEntityId())
                : new org.domi.furnace.server.entity.Boat(cl, cl.nextEntityId());
        boat.setPos(hit.x(), hit.y(), hit.z());
        boat.xo = hit.x();
        boat.yo = hit.y();
        boat.zo = hit.z();
        boat.setVariant(org.domi.furnace.server.entity.Boat.Type.byName(type));
        boat.setYRot(player.yRot());
        if (!cl.noCollision(boat.boundingBox())) return Holder.fail(stack);
        cl.addFreshEntity(boat);
        level.gameEvent("minecraft:entity_place", new Pos((int) Math.floor(hit.x()), (int) Math.floor(hit.y()), (int) Math.floor(hit.z())), player, -1);
        ItemStack out = player.isCreative() ? stack : stack.withCount(stack.count() - 1);
        player.awardStat("minecraft:used:" + n);
        return Holder.success(out);
    }

    // ------------------------------------------------------------------ 양동이(BucketItem)

    /** BucketItem.use: 빈 양동이는 원천 유체까지 시선을 맞춰 뜨고, 찬 양동이는 블록에 맞춰 붓는다. */
    private static Holder bucketUse(BlockLevel level, BlockActor player, int hand, ItemStack stack, int content) {
        boolean empty = Fluids.isEmpty(content);
        Clip.Result hit = Clip.playerPOV(level, player, empty ? Clip.FluidMode.SOURCE_ONLY : Clip.FluidMode.NONE);
        if (hit.miss()) return Holder.pass(stack);
        Pos pos = hit.pos();
        int dir = hit.face();
        Pos rel = pos.relative(dir);
        if (!player.mayInteract(level, pos) || !mayUseItemAt(level, player, rel, dir, stack)) return Holder.fail(stack);
        if (empty) {
            int state = level.getBlockState(pos);
            if (BucketPickup.is(state)) {
                String filled = BucketPickup.pickupBlock(player, level, pos, state);
                if (filled != null) {
                    player.awardStat("minecraft:used:minecraft:bucket");
                    level.playSound(player, player.x(), player.y(), player.z(), BucketPickup.pickupSound(state), "players", 1.0F, 1.0F);
                    level.gameEvent("minecraft:fluid_pickup", pos, player, -1);
                    ItemStack filledStack = stack(filled);
                    ItemStack result = player.createFilledResult(stack, filledStack);
                    player.trigger("minecraft:filled_bucket", pos, filledStack);
                    return Holder.success(result);
                }
            }
            return Holder.fail(stack);
        }
        int state = level.getBlockState(pos);
        Pos target = B.behavior(state).isLiquidContainer(state) && Fluids.isWater(content) ? pos : rel;
        String mob = mobBucketType(name(stack));
        if (emptyContents(level, player, content, target, hit, mobBucketSound(mob))) {
            if (mob != null) checkExtraContent(level, player, stack, target, mob);
            player.trigger("minecraft:placed_block", target, stack);
            player.awardStat("minecraft:used:" + name(stack));
            ItemStack emptyBucket = player.instabuild() ? stack : stack("minecraft:bucket");
            return Holder.success(player.createFilledResult(stack, emptyBucket));
        }
        return Holder.fail(stack);
    }

    /** BucketItem.emptyContents: 비었거나 유체로 바뀔 수 있거나 담을 수 있으면 붓고, 아니면 맞은 면 앞 칸으로 한 번 더. */
    private static boolean emptyContents(BlockLevel level, BlockActor player, int content, Pos pos, Clip.Result hit, String mobSound) {
        int state = level.getBlockState(pos);
        BlockBehavior b = B.behavior(state);
        boolean replaceable = b.canBeReplaced(state, Fluids.type(content));
        boolean ok = B.isAir(state) || replaceable || b.isLiquidContainer(state) && b.canPlaceLiquid(player, level, pos, state, Fluids.type(content));
        if (!ok) return hit != null && emptyContents(level, player, content, hit.pos().relative(hit.face()), null, mobSound);
        if (level.ultraWarm() && Fluids.isWater(content)) {
            RandomSource r = level.random();
            level.playSound(player, pos, "minecraft:block.fire.extinguish", "blocks", 0.5F, 2.6F + (r.nextFloat() - r.nextFloat()) * 0.8F);
            return true;   // 연기 파티클은 클라이언트 쪽
        }
        if (b.isLiquidContainer(state) && Fluids.isWater(content)) {
            b.placeLiquid(level, pos, state, content);
            playEmptySound(level, player, content, pos, mobSound);
            return true;
        }
        if (replaceable && !B.isLiquid(state)) level.destroyBlock(pos, true);
        if (!level.setBlock(pos, Fluids.createLegacyBlock(content), 11) && !Fluids.isSource(level.getFluidState(pos))) return false;
        playEmptySound(level, player, content, pos, mobSound);
        return true;
    }

    /** MobBucketItem.type: 몹 양동이 아이템이 쏟아 내는 몹 종류(아니면 null). */
    private static String mobBucketType(String item) {
        return switch (item) {
            case "minecraft:cod_bucket" -> "minecraft:cod";
            case "minecraft:salmon_bucket" -> "minecraft:salmon";
            case "minecraft:pufferfish_bucket" -> "minecraft:pufferfish";
            case "minecraft:tropical_fish_bucket" -> "minecraft:tropical_fish";
            case "minecraft:axolotl_bucket" -> "minecraft:axolotl";
            case "minecraft:tadpole_bucket" -> "minecraft:tadpole";
            default -> null;
        };
    }

    /** MobBucketItem.emptySound: 물고기 4종은 empty_fish, 아홀로틀·올챙이는 각자. */
    private static String mobBucketSound(String mobType) {
        if (mobType == null) return null;
        return switch (mobType) {
            case "minecraft:axolotl" -> "minecraft:item.bucket.empty_axolotl";
            case "minecraft:tadpole" -> "minecraft:item.bucket.empty_tadpole";
            default -> "minecraft:item.bucket.empty_fish";
        };
    }

    /** MobBucketItem.checkExtraContent: 서버 레벨이면 그 칸에 몹을 되살리고, 늘 entity_place 게임 이벤트. */
    private static void checkExtraContent(BlockLevel level, BlockActor player, ItemStack stack, Pos pos, String mobType) {
        if (level instanceof ChunkLevel cl) cl.spawnMobFromBucket(mobType, stack, pos);
        level.gameEvent("minecraft:entity_place", pos, player, -1);
    }

    /** 발사기용 몹 양동이: 물을 붓고(소리만 몹 양동이 것) 몹을 되살린다. 붓지 못하면 false. */
    public static boolean emptyMobBucketContents(BlockLevel level, ItemStack stack, Pos pos) {
        String mob = mobBucketType(name(stack));
        if (mob == null || !emptyContents(level, null, Fluids.WATER_SOURCE, pos, null, mobBucketSound(mob))) return false;
        checkExtraContent(level, null, stack, pos, mob);
        return true;
    }

    /** MobBucketItem.playEmptySound 는 소리만 neutral 로 내고 fluid_place 이벤트는 내지 않는다(BucketItem 의 것을 통째로 바꾼다). */
    private static void playEmptySound(BlockLevel level, BlockActor player, int content, Pos pos, String mobSound) {
        if (mobSound != null) {
            level.playSound(player, pos, mobSound, "neutral", 1.0F, 1.0F);
            return;
        }
        level.playSound(player, pos, Fluids.isLava(content) ? "minecraft:item.bucket.empty_lava" : "minecraft:item.bucket.empty", "blocks", 1.0F, 1.0F);
        level.gameEvent("minecraft:fluid_place", pos, player, -1);
    }

    /** Player.mayUseItemAt(pos, face, stack): 지을 수 있거나, 모험 모드면 can_place_on 이 붙일 칸에 맞아야. */
    static boolean mayUseItemAt(BlockLevel level, BlockActor player, Pos pos, int face, ItemStack stack) {
        return player.mayBuild() || BlockPlacement.canPlaceOnInAdventure(stack, level, pos.relative(Dir.opposite(face)));
    }

    // ------------------------------------------------------------------ 유리병·물 위에 놓기·장착

    /** BottleItem.use: 원천 물에 시선을 맞추면 물병(드래곤의 숨결 구름은 엔티티 체계에서). */
    private static Holder glassBottleUse(BlockLevel level, BlockActor player, ItemStack stack) {
        // TODO(엔티티): 2칸 안 드래곤의 숨결 구름(AreaEffectCloud)이 있으면 그 구름을 줄이고 드래곤의 숨결.
        Clip.Result hit = Clip.playerPOV(level, player, Clip.FluidMode.SOURCE_ONLY);
        if (hit.miss()) return Holder.pass(stack);
        Pos pos = hit.pos();
        if (!player.mayInteract(level, pos)) return Holder.pass(stack);
        if (Fluids.isWater(level.getFluidState(pos))) {
            level.playSound(player, player.x(), player.y(), player.z(), "minecraft:item.bottle.fill", "neutral", 1.0F, 1.0F);
            level.gameEvent("minecraft:fluid_pickup", pos, player, -1);
            player.awardStat("minecraft:used:minecraft:glass_bottle");
            return Holder.success(player.createFilledResult(stack, waterPotion()));
        }
        return Holder.pass(stack);
    }

    /**
     * MapItem.useOn: 깃발 블록(#banners) 위에서만 — 지도에 그 깃발 표식을 넣거나 뺀다. 지도 데이터가 있는데 바뀌지 않으면 FAIL, 아니면 서버에서 CONSUME.
     * 깃발이 아니면 PASS(아이템 사용 use 로 넘어간다).
     */
    private static InteractionResult filledMapOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        String block = org.domi.furnace.server.block.B.BLOCKS.nameOfState(level.getBlockState(hit.pos()));
        if (!block.endsWith("_banner")) return InteractionResult.PASS;   // BlockTags.BANNERS: <색>_banner, <색>_wall_banner
        org.domi.furnace.server.map.MapData data = org.domi.furnace.server.map.MapStore.of(stack);
        if (data != null) {
            var pos = hit.pos();
            var be = level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.BannerBlockEntity b ? b : null;
            var banner = be == null ? null : new org.domi.furnace.server.map.MapData.Banner(pos.x(), pos.y(), pos.z(), be.baseColor(), be.customName);
            if (!data.toggleBanner(pos.x(), pos.z(), banner)) return InteractionResult.FAIL;
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * EmptyMapItem.use: 하나 줄이고(크리에이티브는 그대로) 사용 통계·소리 → 서 있는 곳 중심(배율 0, 위치 추적)의 새 지도를 만든다.
     * 다 썼으면 손에 지도, 아니면 인벤토리(안 들어가면 바닥)로.
     */
    private static Holder emptyMapUse(BlockLevel level, BlockActor player, ItemStack stack) {
        if (!(level instanceof ChunkLevel cl)) return Holder.pass(stack);
        ItemStack rest = player.instabuild() ? stack : stack.withCount(stack.count() - 1);
        player.awardStat("minecraft:used:minecraft:map");
        cl.playSoundOn(null, player, "minecraft:ui.cartography_table.take_result", "players", 1.0F, 1.0F);
        ItemStack map = org.domi.furnace.server.map.MapItems.create(cl.dimension(), (int) Math.floor(player.x()), (int) Math.floor(player.z()),
                (byte) 0, true, false);
        if (rest.isEmpty()) return Holder.success(map);
        if (!player.addToInventory(map)) player.drop(map);
        return Holder.success(rest);
    }

    /** 발사기용: BucketItem.emptyContents(null, level, pos, null) — 붓는 생물·맞은 면 없이. */
    public static boolean emptyBucketContents(BlockLevel level, int content, Pos pos) { return emptyContents(level, null, content, pos, null, null); }

    /** HoneycombItem.getWaxed(state): 밀랍 칠한 블록(속성 그대로), 없으면 -1. */
    public static int waxed(int state) {
        Integer w = WAX_ON.get(B.block(state));
        return w == null ? -1 : B.withPropertiesOf(w, state);
    }

    /** PotionContents.createItemStack(Items.POTION, Potions.WATER). */
    public static ItemStack waterPotion() {
        return new ItemStack(ItemContext.id("minecraft:item", "minecraft:potion"), 1,
                ComponentPatch.of(Map.of("minecraft:potion_contents", Map.of("potion", "minecraft:water"))));
    }

    /** PlaceOnWaterBlockItem.use: 원천 유체에 시선을 맞춘 칸 바로 위에 BlockItem.useOn. */
    private static Holder placeOnWaterUse(BlockLevel level, BlockActor player, int hand, ItemStack stack, BlockItems.Entry e) {
        Clip.Result hit = Clip.playerPOV(level, player, Clip.FluidMode.SOURCE_ONLY);
        Clip.Result above = hit.withPos(hit.pos().above());
        InteractionResult r = BlockPlacement.place(e, new PlaceContext(level, player, hand, stack, above.hit()));
        return new Holder(r, player.itemInHand(hand));
    }

    /** 장착 칸(창 칸 5 머리 · 6 가슴 · 7 다리 · 8 발, 9 = 몸통 — 플레이어는 못 쓴다). Equipable 이 아니면 -1. */
    private static int equipSlot(String n) {
        if (n.equals("minecraft:elytra")) return 6;
        if (n.equals("minecraft:wolf_armor") || n.endsWith("_horse_armor")) return 9;
        if (n.endsWith("_helmet")) return 5;
        if (n.endsWith("_chestplate")) return 6;
        if (n.endsWith("_leggings")) return 7;
        if (n.endsWith("_boots")) return 8;
        return -1;
    }

    /** Equipable.getEquipSound — 갑옷 재질별. */
    public static String equipSound(String n) {
        if (n.equals("minecraft:elytra")) return "minecraft:item.armor.equip_elytra";
        String m = n.substring("minecraft:".length());
        if (m.startsWith("leather_")) return "minecraft:item.armor.equip_leather";
        if (m.startsWith("chainmail_")) return "minecraft:item.armor.equip_chain";
        if (m.startsWith("iron_")) return "minecraft:item.armor.equip_iron";
        if (m.startsWith("golden_")) return "minecraft:item.armor.equip_gold";
        if (m.startsWith("diamond_")) return "minecraft:item.armor.equip_diamond";
        if (m.startsWith("turtle_")) return "minecraft:item.armor.equip_turtle";
        if (m.startsWith("netherite_")) return "minecraft:item.armor.equip_netherite";
        if (m.startsWith("wolf_")) return "minecraft:item.armor.equip_wolf";
        return "minecraft:item.armor.equip_generic";
    }

    /**
     * Equipable.swapWithEquipmentSlot: 플레이어가 쓸 수 없는 칸이면 주 손 칸으로(바닐라 getEquipmentSlotForItem). 지금 칸이 귀속 저주가 아니거나
     * 크리에이티브이고 같은 스택이 아니면 바꾼다. 바뀐 칸이 아이템의 장착 칸이면 장착 소리(엔티티 난수 씨앗).
     */
    private static Holder equip(BlockLevel level, BlockActor player, int hand, ItemStack stack, int slot, String n) {
        boolean mainHandSlot = slot == 9;
        ItemStack current = mainHandSlot ? player.itemInHand(BlockActor.MAIN_HAND) : player.equipment(slot);
        boolean bound = ItemTools.enchantmentLevel(current, "minecraft:binding_curse") > 0;
        if ((bound && !player.isCreative()) || current.equals(stack)) return Holder.fail(stack);
        player.awardStat("minecraft:used:" + n);
        ItemStack toHand = current.isEmpty() ? (player.isCreative() ? stack : ItemStack.EMPTY) : current;
        if (mainHandSlot) {
            player.setItemInHand(BlockActor.MAIN_HAND, stack);
        } else {
            player.setEquipment(slot, stack);
            // LivingEntity.onEquipItem: 같은 아이템·컴포넌트가 아니면 장착 소리(모두에게)
            if (current.isEmpty() || current.itemId() != stack.itemId() || !current.patch().equals(stack.patch())) {
                level.playSound(null, player.x(), player.y(), player.z(), equipSound(n), "players", 1.0F, 1.0F);
                player.nextEntityLong();   // playSeededSound 의 씨앗(엔티티 난수)
            }
        }
        return Holder.success(toHand);
    }

    // ------------------------------------------------------------------ Item.useOn(블록 아이템이 아닌 것)

    /** UseOnContext 를 받아 아이템 종류별 useOn. 해당 없으면 PASS. */
    public static InteractionResult useOn(BlockLevel level, BlockActor player, int hand, Hit hit) {
        ItemStack stack = player.itemInHand(hand);
        String n = name(stack);
        switch (n) {
            case "minecraft:flint_and_steel": return flintAndSteel(level, player, hand, hit, stack);
            case "minecraft:fire_charge": return fireCharge(level, player, hand, hit, stack);
            case "minecraft:bone_meal": return boneMeal(level, player, hand, hit, stack);
            case "minecraft:honeycomb": return honeycomb(level, player, hand, hit, stack);
            case "minecraft:shears": return shears(level, player, hand, hit, stack);
            case "minecraft:potion": return potionOn(level, player, hand, hit, stack);
            case "minecraft:debug_stick": return debugStickOn(level, player, hand, hit, stack);
            case "minecraft:filled_map": return filledMapOn(level, player, hand, hit, stack);
            case "minecraft:compass": return compassOn(level, player, hand, hit, stack);
            case "minecraft:ender_eye": return enderEyeOn(level, player, hand, hit, stack);
            case "minecraft:armor_stand": return armorStandOn(level, player, hand, hit, stack);
            case "minecraft:end_crystal": return endCrystalOn(level, player, hand, hit, stack);
            case "minecraft:firework_rocket": return fireworkOn(level, player, hand, hit, stack);
            case "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting": return hangingOn(level, player, hand, hit, stack, n);
            case "minecraft:lead": return leadOn(level, player, hit);
            case "minecraft:brush": return level instanceof ChunkLevel cl ? org.domi.furnace.server.entity.Brushing.useOn(cl, player, hand) : InteractionResult.PASS;
            default: break;
        }
        if (n.endsWith("_hoe")) return hoe(level, player, hand, hit, stack);
        if (n.endsWith("_shovel")) return shovel(level, player, hand, hit, stack);
        if (n.endsWith("_axe")) return axe(level, player, hand, hit, stack);
        if (n.endsWith("_spawn_egg")) return spawnEgg(level, player, hand, hit, stack, n);
        switch (n) {
            case "minecraft:minecart": return minecartOn(level, player, hand, hit, stack, org.domi.furnace.server.entity.AbstractMinecart.Type.RIDEABLE);
            case "minecraft:chest_minecart": return minecartOn(level, player, hand, hit, stack, org.domi.furnace.server.entity.AbstractMinecart.Type.CHEST);
            case "minecraft:furnace_minecart": return minecartOn(level, player, hand, hit, stack, org.domi.furnace.server.entity.AbstractMinecart.Type.FURNACE);
            case "minecraft:tnt_minecart": return minecartOn(level, player, hand, hit, stack, org.domi.furnace.server.entity.AbstractMinecart.Type.TNT);
            case "minecraft:hopper_minecart": return minecartOn(level, player, hand, hit, stack, org.domi.furnace.server.entity.AbstractMinecart.Type.HOPPER);
            case "minecraft:command_block_minecart": return minecartOn(level, player, hand, hit, stack, org.domi.furnace.server.entity.AbstractMinecart.Type.COMMAND_BLOCK);
            default: break;
        }
        return InteractionResult.PASS;
    }

    /** LeadItem.useOn: 울타리면 끌던 몹을 묶는다. 아니면 넘긴다(몹에게 쓰는 것은 엔티티 상호작용). */
    private static InteractionResult leadOn(BlockLevel level, BlockActor player, Hit hit) {
        Pos pos = hit.pos();
        if (!B.isTag(level.getBlockState(pos), "minecraft:fences")) return InteractionResult.PASS;
        if (level instanceof ChunkLevel cl && player.handle() instanceof org.domi.furnace.server.entity.Player p) {
            if (org.domi.furnace.server.entity.LeashFenceKnotEntity.bindPlayerMobs(p, cl, pos, player)) {
                cl.gameEvent("minecraft:block_attach", pos, player, -1);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    /**
     * MinecartItem.useOn: 레일(#rails)이 아니면 실패. 레일 칸 가운데, 바닥 + 0.0625(경사면 + 0.5)에 그 종류 수레를 놓고 하나 쓴다.
     * TODO(아이템 컴포넌트): createDefaultStackConfig(이름·엔티티 데이터). TODO(디스펜서): 발사기로 놓기.
     */
    private static InteractionResult minecartOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack,
            org.domi.furnace.server.entity.AbstractMinecart.Type type) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        if (!B.isTag(state, "minecraft:rails")) return InteractionResult.FAIL;
        if (level instanceof ChunkLevel cl) {
            Prop shape = B.prop("shape");
            double d = shape.has(state) && shape.valueName(state).startsWith("ascending_") ? 0.5 : 0.0;
            var cart = org.domi.furnace.server.entity.AbstractMinecart.create(cl, pos.x() + 0.5, pos.y() + 0.0625 + d, pos.z() + 0.5, type);
            cl.addFreshEntity(cart);
            cl.gameEvent("minecraft:entity_place", pos, player, -1);
        }
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * SpawnEggItem.useOn: 맞은 칸의 충돌 모양이 비었으면 그 칸, 아니면 맞은 면 앞 칸에 몹을 만든다(바닥에 맞춰 내림).
     * 생성기면 종류만 바꾼다. TODO(ENTITY_DATA 컴포넌트): 알의 entity_data 로 종류·태그를 덮어쓴다.
     */
    private static InteractionResult spawnEgg(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack, String itemName) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        String type = "minecraft:" + itemName.substring("minecraft:".length(), itemName.length() - "_spawn_egg".length());
        if (B.is(state, "minecraft:spawner")) {
            // 생성기면 다음 스폰 데이터의 종류만 바꾼다(shrink 는 크리에이티브에서도).
            if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SpawnerBlockEntity sp) {
                sp.setEntityId(type, level);
                level.sendBlockUpdated(pos);
                level.gameEvent("minecraft:block_change", pos, player, -1);
                player.setItemInHand(hand, stack.withCount(stack.count() - 1));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        if (B.is(state, "minecraft:trial_spawner")) {
            // 시련 생성기도 Spawner 라 알을 쓰면 다음 스폰 데이터의 종류만 바꾼다
            if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.TrialSpawnerBlockEntity ts) {
                ts.setEntityId(type, level.random(), level);
                level.sendBlockUpdated(pos);
                level.gameEvent("minecraft:block_change", pos, player, -1);
                player.setItemInHand(hand, stack.withCount(stack.count() - 1));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        Pos target = B.INFO.collisionShape(state).isEmpty() ? pos : pos.relative(hit.face());
        if (level.spawnMobFromEgg(type, target, true, !pos.equals(target) && hit.face() == Dir.UP)) {
            player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            level.gameEvent("minecraft:entity_place", pos, player, -1);
        }
        return InteractionResult.sidedSuccess(false);
    }

    private static float wrapDegrees(float d) {
        float f = d % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    /** 상자와 겹치는 엔티티(플레이어 포함)가 있는가: level.getEntities(null, box).isEmpty() 의 반대. */
    private static boolean anyEntityIn(ChunkLevel level, org.domi.furnace.server.entity.AABB box) {
        if (!level.getEntities(null, box, e -> true).isEmpty()) return true;
        boolean[] found = {false};
        level.forEachPlayerIn(box, p -> found[0] = true);
        return found[0];
    }

    /** ArmorStandItem.useOn: 아랫면이 아니면, 놓을 칸에 충돌·엔티티가 없을 때 45° 단위로 돌려 거치대를 놓고 머리·몸 자세를 조금 흩는다. */
    private static InteractionResult armorStandOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        if (hit.face() == Dir.DOWN) return InteractionResult.FAIL;
        if (!(level instanceof ChunkLevel cl)) return InteractionResult.PASS;
        Pos pos = new PlaceContext(level, player, hand, stack, hit).clickedPos();
        double bx = pos.x() + 0.5, by = pos.y(), bz = pos.z() + 0.5;
        org.domi.furnace.server.entity.AABB box = new org.domi.furnace.server.entity.AABB(bx - 0.25, by, bz - 0.25, bx + 0.25, by + 1.975, bz + 0.25);
        if (!cl.noCollision(box) || anyEntityIn(cl, box)) return InteractionResult.FAIL;
        var stand = new org.domi.furnace.server.entity.ArmorStand(cl, cl.nextEntityId());
        float yaw = (float) Math.floor((wrapDegrees(player.yRot() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
        stand.moveTo(bx, by, bz, yaw, 0.0F);
        RandomSource r = cl.random();
        float[] head = stand.pose(0).clone();
        float f = r.nextFloat() * 5.0F, g = r.nextFloat() * 20.0F - 10.0F;
        stand.setPose(0, new float[] {head[0] + f, head[1] + g, head[2]});
        float[] body = stand.pose(1).clone();
        stand.setPose(1, new float[] {body[0], body[1] + (r.nextFloat() * 10.0F - 5.0F), body[2]});
        cl.addFreshEntity(stand);
        cl.playSound(null, bx, by, bz, "minecraft:entity.armor_stand.place", "blocks", 0.75F, 0.8F);
        cl.gameEvent("minecraft:entity_place", pos, player, -1);
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return InteractionResult.sidedSuccess(false);
    }

    /** HangingEntityItem.useOn: 맞은 면 앞 칸에 액자·그림을 만들어, 붙을 수 있으면 놓는다(안 되면 CONSUME). */
    private static InteractionResult hangingOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack, String itemName) {
        int face = hit.face();
        Pos pos = hit.pos().relative(face);
        if (level.isOutsideBuildHeight(pos.y()) || !mayUseItemAt(level, player, pos, face, stack)) return InteractionResult.FAIL;
        if (!(level instanceof ChunkLevel cl)) return InteractionResult.PASS;
        org.domi.furnace.server.entity.HangingEntity entity;
        if (itemName.equals("minecraft:painting")) {
            entity = org.domi.furnace.server.entity.Painting.create(cl, pos, face);
            if (entity == null) return InteractionResult.FAIL;
        } else {
            entity = new org.domi.furnace.server.entity.ItemFrame(cl, cl.nextEntityId(), pos, face, itemName.equals("minecraft:glow_item_frame"));
        }
        if (!entity.survives()) return InteractionResult.CONSUME;
        entity.playPlacementSound();
        cl.gameEvent("minecraft:entity_place", pos, player, -1);
        cl.addFreshEntity(entity);
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return InteractionResult.sidedSuccess(false);
    }

    /** FireworkRocketItem.use: 겉날개 비행 중이면 몸에 붙은 로켓을 넣고 하나 쓴다(크리에이티브는 그대로), 아니면 넘긴다. */
    private static Holder fireworkUse(BlockLevel level, BlockActor player, ItemStack stack) {
        if (!(player.handle() instanceof org.domi.furnace.server.entity.Player p) || !p.isFallFlying()) return Holder.pass(stack);
        if (!(level instanceof ChunkLevel cl)) return Holder.pass(stack);
        var rocket = new org.domi.furnace.server.entity.FireworkRocketEntity(cl, cl.nextEntityId(), stack, p);
        cl.addFreshEntity(rocket);
        ItemStack out = player.isCreative() ? stack : stack.withCount(stack.count() - 1);
        player.awardStat("minecraft:used:minecraft:firework_rocket");
        return Holder.success(out);
    }

    /** FireworkRocketItem.useOn: 맞은 지점에서 면 쪽으로 0.15 띄워 로켓을 쏘아 올린다. */
    private static InteractionResult fireworkOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        if (!(level instanceof ChunkLevel cl)) return InteractionResult.PASS;
        int f = hit.face();
        var rocket = new org.domi.furnace.server.entity.FireworkRocketEntity(cl, cl.nextEntityId(),
                hit.x() + Dir.DX[f] * 0.15, hit.y() + Dir.DY[f] * 0.15, hit.z() + Dir.DZ[f] * 0.15, stack);
        if (player.handle() instanceof org.domi.furnace.server.entity.Targetable t) rocket.setOwner(t);
        cl.addFreshEntity(rocket);
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return InteractionResult.sidedSuccess(false);
    }

    /** EndCrystalItem.useOn: 흑요석·기반암 위의 빈 칸(위 2칸에 엔티티 없음)에 받침 없는 수정을 놓는다. */
    private static InteractionResult endCrystalOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        int state = level.getBlockState(hit.pos());
        if (!B.is(state, "minecraft:obsidian") && !B.is(state, "minecraft:bedrock")) return InteractionResult.FAIL;
        Pos above = hit.pos().above();
        if (!level.isEmptyBlock(above)) return InteractionResult.FAIL;
        if (!(level instanceof ChunkLevel cl)) return InteractionResult.PASS;
        double x = above.x(), y = above.y(), z = above.z();
        if (anyEntityIn(cl, new org.domi.furnace.server.entity.AABB(x, y, z, x + 1.0, y + 2.0, z + 1.0))) return InteractionResult.FAIL;
        var crystal = new org.domi.furnace.server.entity.EndCrystal(cl, cl.nextEntityId());
        crystal.moveTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
        crystal.setShowBottom(false);
        cl.addFreshEntity(crystal);
        cl.gameEvent("minecraft:entity_place", above, player, -1);
        if (cl.dragonFight() != null) cl.dragonFight().tryRespawn();
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return InteractionResult.sidedSuccess(false);
    }

    /** FlintAndSteelItem.useOn: 켤 수 있는 모닥불·양초면 켜고, 아니면 맞은 면 앞에 불. 내구도 1. */
    private static InteractionResult flintAndSteel(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        if (!CampfireBlock.canLight(state) && !CandleBlock.canLight(state) && !CandleCakeBlock.canLight(state)) {
            Pos fire = pos.relative(hit.face());
            if (!BaseFireBlock.canBePlacedAt(level, fire, player.direction())) return InteractionResult.FAIL;
            level.playSound(player, fire, "minecraft:item.flintandsteel.use", "blocks", 1.0F, level.random().nextFloat() * 0.4F + 0.8F);
            level.setBlock(fire, BaseFireBlock.getState(level, fire), 11);
            level.gameEvent("minecraft:block_place", pos, player, -1);
            player.trigger("minecraft:placed_block", fire, stack);
            BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
            return InteractionResult.sidedSuccess(false);
        }
        level.playSound(player, pos, "minecraft:item.flintandsteel.use", "blocks", 1.0F, level.random().nextFloat() * 0.4F + 0.8F);
        level.setBlock(pos, LIT.with(state, true), 11);
        level.gameEvent("minecraft:block_change", pos, player, -1);
        BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
        return InteractionResult.sidedSuccess(false);
    }

    /** FireChargeItem.useOn: 부싯돌과 같지만 소리는 모두에게, 하나 쓴다(크리에이티브는 useItemOn 이 되돌린다). */
    private static InteractionResult fireCharge(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        boolean done = false;
        if (!CampfireBlock.canLight(state) && !CandleBlock.canLight(state) && !CandleCakeBlock.canLight(state)) {
            pos = pos.relative(hit.face());
            if (BaseFireBlock.canBePlacedAt(level, pos, player.direction())) {
                fireChargeSound(level, pos);
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                level.gameEvent("minecraft:block_place", pos, player, -1);
                done = true;
            }
        } else {
            fireChargeSound(level, pos);
            level.setBlockAndUpdate(pos, LIT.with(state, true));
            level.gameEvent("minecraft:block_change", pos, player, -1);
            done = true;
        }
        if (!done) return InteractionResult.FAIL;
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return InteractionResult.sidedSuccess(false);
    }

    private static void fireChargeSound(BlockLevel level, Pos pos) {
        RandomSource r = level.random();
        level.playSound(null, pos, "minecraft:item.firecharge.use", "blocks", 1.0F, (r.nextFloat() - r.nextFloat()) * 0.2F + 1.0F);
    }

    /** BoneMealItem.useOn: 자라는 블록이면 키우고, 아니면 단단한 면 앞의 물에 해초·산호. */
    private static InteractionResult boneMeal(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        Pos rel = pos.relative(hit.face());
        if (growCrop(level, player, hand, stack, pos)) {
            level.levelEvent(null, 1505, pos, 15);   // ITEM_INTERACT_FINISH 게임 이벤트는 엔티티 체계에서
            return InteractionResult.sidedSuccess(false);
        }
        int state = level.getBlockState(pos);
        if (B.isFaceSturdy(state, level, pos, hit.face()) && growWaterPlant(level, player, hand, stack, rel, hit.face())) {
            level.levelEvent(null, 1505, rel, 15);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /** BoneMealItem.growCrop: BonemealableBlock 이 받으면 성공 판정 후 자라게, 하나 쓴다. */
    public static boolean growCrop(BlockLevel level, BlockActor player, int hand, ItemStack stack, Pos pos) {
        int state = level.getBlockState(pos);
        if (B.behavior(state) instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, pos, state)) {
            if (bm.isBonemealSuccess(level, level.random(), pos, state)) bm.performBonemeal(level, level.random(), pos, state);
            if (player != null) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            return true;
        }
        return false;
    }

    /** BoneMealItem.growWaterPlant: 물(양 8)에 128 번 무작위로 걸어가 해초·산호(산호 바이옴)를 놓는다. */
    public static boolean growWaterPlant(BlockLevel level, BlockActor player, int hand, ItemStack stack, Pos pos, int face) {
        if (!B.is(level.getBlockState(pos), WATER) || Fluids.amount(level.getFluidState(pos)) != 8) return false;
        RandomSource r = level.random();
        Prop facing = B.prop("facing");
        outer:
        for (int attempt = 0; attempt < 128; attempt++) {
            Pos p = pos;
            int s = B.defaultState(SEAGRASS);
            for (int k = 0; k < attempt / 16; k++) {
                p = p.offset(r.nextInt(3) - 1, (r.nextInt(3) - 1) * r.nextInt(3) / 2, r.nextInt(3) - 1);
                if (B.isCollisionShapeFullBlock(level.getBlockState(p))) continue outer;
            }
            if (level.biomeIs(p, "minecraft:produces_corals_from_bonemeal")) {
                if (attempt == 0 && face >= 0 && Dir.horizontal(face)) {
                    s = randomOf("minecraft:wall_corals", r, s);
                    if (facing.has(s)) s = facing.with(s, Dir.NAME[face]);
                } else if (r.nextInt(4) == 0) {
                    s = randomOf("minecraft:underwater_bonemeals", r, s);
                }
            }
            if (B.isTag(s, "minecraft:wall_corals") && facing.has(s)) {
                for (int k = 0; !B.canSurvive(s, level, p) && k < 4; k++) s = facing.with(s, Dir.NAME[Dir.HORIZONTAL[r.nextInt(4)]]);
            }
            if (B.canSurvive(s, level, p)) {
                int cur = level.getBlockState(p);
                if (B.is(cur, WATER) && Fluids.amount(level.getFluidState(p)) == 8) {
                    level.setBlock(p, s, 3);
                } else if (B.is(cur, SEAGRASS) && r.nextInt(10) == 0) {
                    ((BonemealableBlock) B.behavior(cur)).performBonemeal(level, r, p, cur);
                }
            }
        }
        if (player != null) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return true;
    }

    /** BuiltInRegistries.BLOCK.getRandomElementOf(tag, random): 태그 목록에서 nextInt(크기), 없으면 기본. */
    private static int randomOf(String tag, RandomSource r, int fallback) {
        java.util.List<String> list = org.domi.furnace.server.world.gen.feature.BlockTags.get().blocksOf(tag);
        if (list.isEmpty()) return fallback;
        return B.defaultState(list.get(r.nextInt(list.size())));
    }

    /** HoeItem.useOn: 잔디·흙·흙길 → 경작지(위가 공기이고 아래 면이 아니면), 거친 흙 → 흙, 뿌리 흙 → 흙 + 매달린 뿌리. */
    private static InteractionResult hoe(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int b = B.block(level.getBlockState(pos));
        boolean airAbove = hit.face() != Dir.DOWN && B.isAir(level.getBlockState(pos.above()));
        int to;
        boolean roots = false;
        if (b == GRASS_BLOCK || b == DIRT_PATH || b == DIRT) {
            if (!airAbove) return InteractionResult.PASS;
            to = B.defaultState(FARMLAND);
        } else if (b == COARSE_DIRT) {
            if (!airAbove) return InteractionResult.PASS;
            to = B.defaultState(DIRT);
        } else if (b == ROOTED_DIRT) {
            to = B.defaultState(DIRT);
            roots = true;
        } else {
            return InteractionResult.PASS;
        }
        level.playSound(player, pos, "minecraft:item.hoe.till", "blocks", 1.0F, 1.0F);
        level.setBlock(pos, to, 11);
        level.gameEvent("minecraft:block_change", pos, player, to);
        if (roots) level.popResourceFromFace(pos, hit.face(), stack("minecraft:hanging_roots"));
        BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
        return InteractionResult.sidedSuccess(false);
    }

    /** ShovelItem.useOn: 위가 공기인 흙 종류 → 흙길, 켜진 모닥불은 끈다. 아래 면은 PASS. */
    private static InteractionResult shovel(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        if (hit.face() == Dir.DOWN) return InteractionResult.PASS;
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        int b = B.block(state);
        boolean flattenable = b == GRASS_BLOCK || b == DIRT || b == PODZOL || b == COARSE_DIRT || b == MYCELIUM || b == ROOTED_DIRT;
        int to = -1;
        if (flattenable && B.isAir(level.getBlockState(pos.above()))) {
            level.playSound(player, pos, "minecraft:item.shovel.flatten", "blocks", 1.0F, 1.0F);
            to = B.defaultState(DIRT_PATH);
        } else if (B.behavior(state) instanceof CampfireBlock && LIT.is(state)) {
            level.levelEvent(null, 1009, pos, 0);
            CampfireBlock.dowse(player, level, pos, state);
            to = LIT.with(state, false);
        }
        if (to < 0) return InteractionResult.PASS;
        level.setBlock(pos, to, 11);
        level.gameEvent("minecraft:block_change", pos, player, to);
        BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
        return InteractionResult.sidedSuccess(false);
    }

    /** AxeItem.useOn: 껍질 벗기기 → 녹 긁기(3005) → 밀랍 벗기기(3004). 왼손에 방패를 들고 주 손이면(웅크리지 않으면) PASS. */
    private static InteractionResult axe(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        if (hand == BlockActor.MAIN_HAND && name(player.itemInHand(BlockActor.OFF_HAND)).equals("minecraft:shield") && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        int to;
        Integer stripped = STRIPPABLES.get(B.block(state));
        if (stripped != null) {
            level.playSound(player, pos, "minecraft:item.axe.strip", "blocks", 1.0F, 1.0F);
            to = B.withPropertiesOf(stripped, state);
        } else if ((to = WeatheringCopper.previous(state)) >= 0) {
            level.playSound(player, pos, "minecraft:item.axe.scrape", "blocks", 1.0F, 1.0F);
            level.levelEvent(player, 3005, pos, 0);
        } else if (WAX_OFF.containsKey(B.block(state))) {
            to = B.withPropertiesOf(WAX_OFF.get(B.block(state)), state);
            level.playSound(player, pos, "minecraft:item.axe.wax_off", "blocks", 1.0F, 1.0F);
            level.levelEvent(player, 3004, pos, 0);
        } else {
            return InteractionResult.PASS;
        }
        player.trigger("minecraft:item_used_on_block", pos, stack);
        level.setBlock(pos, to, 11);
        level.gameEvent("minecraft:block_change", pos, player, to);
        BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
        return InteractionResult.sidedSuccess(false);
    }

    /** HoneycombItem.useOn: 구리 블록에 밀랍(3003). TODO(블록 엔티티): 표지판 밀랍은 SignApplicator. */
    private static InteractionResult honeycomb(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        Integer waxed = WAX_ON.get(B.block(state));
        if (waxed == null) return InteractionResult.PASS;
        int to = B.withPropertiesOf(waxed, state);
        player.trigger("minecraft:item_used_on_block", pos, stack);
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        level.setBlock(pos, to, 11);
        level.gameEvent("minecraft:block_change", pos, player, to);
        level.levelEvent(player, 3003, pos, 0);
        return InteractionResult.sidedSuccess(false);
    }

    /** ShearsItem.useOn: 덜 자란 덩굴 끝(켈프·눈물 덩굴 등)을 최대 나이로 — 더 자라지 않는다. */
    private static InteractionResult shears(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        if (B.behavior(state) instanceof GrowingPlantBlock.GrowingPlantHead head && !head.isMaxAge(state)) {
            player.trigger("minecraft:item_used_on_block", pos, stack);
            level.playSound(player, pos, "minecraft:block.growing_plant.crop", "blocks", 1.0F, 1.0F);
            int to = AGE.withInt(state, 25);
            level.setBlockAndUpdate(pos, to);
            level.gameEvent("minecraft:block_change", pos, player, to);
            BlockBreaking.hurtAndBreak(level, player, hand, stack, 1);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /** PotionItem.useOn: 물병을 #convertable_to_mud 에 쓰면(아래 면 제외) 진흙, 손엔 유리병. */
    private static InteractionResult potionOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        Object contents = stack.patch().entries().get("minecraft:potion_contents");
        boolean water = contents instanceof Map<?, ?> m && "minecraft:water".equals(m.get("potion")) && m.size() == 1;
        if (hit.face() == Dir.DOWN || !B.isTag(state, "minecraft:convertable_to_mud") || !water) return InteractionResult.PASS;
        level.playSound(null, pos, "minecraft:entity.generic.splash", "blocks", 1.0F, 1.0F);
        player.setItemInHand(hand, player.createFilledResult(stack, stack("minecraft:glass_bottle")));
        player.awardStat("minecraft:used:minecraft:potion");
        // 물 튀김 파티클(sendParticles 5번 — x·z 에 레벨 난수를 쓴다, x 먼저)
        for (int i = 0; i < 5; i++) {
            double px = pos.x() + level.random().nextDouble();
            double pz = pos.z() + level.random().nextDouble();
            level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("splash"), px, pos.y() + 1, pz, 1, 0.0, 0.0, 0.0, 1.0);
        }
        level.playSound(null, pos, "minecraft:item.bottle.empty", "blocks", 1.0F, 1.0F);
        level.gameEvent("minecraft:fluid_place", pos, null, -1);
        level.setBlockAndUpdate(pos, B.defaultState(MUD));
        return InteractionResult.sidedSuccess(false);
    }

    /** DebugStickItem.useOn: 고른 속성 값을 다음(웅크리면 앞) 값으로. */
    private static InteractionResult debugStickOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        if (player != null && !debugStickInteraction(level, player, hand, level.getBlockState(hit.pos()), hit.pos(), true, stack)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * DebugStickItem.handleInteraction(player, state, level, pos, update, stack): 관리자 블록을 쓸 수 있어야 한다.
     * update 면 고른 속성(없으면 첫 속성)의 값을 돌려 플래그 18 로 놓고, 아니면 다음 속성을 골라 debug_stick_state 에 적는다.
     */
    public static boolean debugStickInteraction(BlockLevel level, BlockActor player, int hand, int state, Pos pos, boolean update, ItemStack stack) {
        if (!player.canUseGameMasterBlocks()) return false;
        Blocks blocks = Blocks.get();
        int block = blocks.blockOf(state);
        String blockName = blocks.name(block);
        Prop[] props = blocks.propsOf(block);
        if (props.length == 0) {
            level.sendActionBar(player, Text.translatable("item.minecraft.debug_stick.empty", blockName));
            return false;
        }
        Object raw = stack.patch().entries().get("minecraft:debug_stick_state");
        if (raw == null && !hasDefaultDebugState(stack)) return false;
        Map<?, ?> map = raw instanceof Map<?, ?> m ? m : Map.of();
        Prop chosen = null;
        if (map.get(blockName) instanceof String pn) for (Prop p : props) if (p.name().equals(pn)) chosen = p;
        boolean back = player.isSecondaryUseActive();
        if (update) {
            if (chosen == null) chosen = props[0];
            String[] values = chosen.values(state);
            int idx = chosen.get(state);
            int next = back ? (idx - 1 + values.length) % values.length : (idx + 1) % values.length;
            int s = chosen.with(state, values[next]);
            level.setBlock(pos, s, 18);
            level.sendActionBar(player, Text.translatable("item.minecraft.debug_stick.update", chosen.name(), values[next]));
        } else {
            int idx = -1;
            for (int i = 0; i < props.length; i++) if (props[i] == chosen) idx = i;
            Prop p = idx < 0 ? props[0] : props[back ? (idx - 1 + props.length) % props.length : (idx + 1) % props.length];
            java.util.Map<String, Object> newMap = new java.util.LinkedHashMap<>();
            for (var e : map.entrySet()) newMap.put(String.valueOf(e.getKey()), e.getValue());
            newMap.put(blockName, p.name());
            java.util.Map<String, Object> entries = new java.util.LinkedHashMap<>(stack.patch().entries());
            entries.put("minecraft:debug_stick_state", newMap);
            player.setItemInHand(hand, new ItemStack(stack.itemId(), stack.count(), ComponentPatch.of(entries)));
            level.sendActionBar(player, Text.translatable("item.minecraft.debug_stick.select", p.name(), p.valueName(state)));
        }
        return true;
    }

    /** 디버그 막대는 기본 컴포넌트로 빈 debug_stick_state 를 가진다(패치에 없으면 기본값). */
    private static boolean hasDefaultDebugState(ItemStack stack) { return name(stack).equals("minecraft:debug_stick"); }

    /** CompassItem.useOn: 자석석이면 추적 나침반 — 하나뿐이고 무한 재료가 아니면 그 스택을, 아니면 새 나침반을 인벤토리로. */
    private static InteractionResult compassOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        if (!B.is(level.getBlockState(pos), LODESTONE)) return InteractionResult.PASS;
        level.playSound(null, pos, "minecraft:item.lodestone_compass.lock", "players", 1.0F, 1.0F);
        Map<String, Object> tracker = Map.of("target", Map.of("dimension", "minecraft:overworld", "pos", new int[] {pos.x(), pos.y(), pos.z()}));
        boolean single = !player.instabuild() && stack.count() == 1;
        if (single) {
            player.setItemInHand(hand, withComponent(stack, "minecraft:lodestone_tracker", tracker));
        } else {
            ItemStack compass = withComponent(new ItemStack(stack.itemId(), 1, stack.patch()), "minecraft:lodestone_tracker", tracker);
            if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            if (!player.addToInventory(compass)) player.drop(compass);
        }
        return InteractionResult.sidedSuccess(false);
    }

    private static ItemStack withComponent(ItemStack s, String key, Object raw) {
        java.util.Map<String, Object> entries = new java.util.LinkedHashMap<>(s.patch().entries());
        entries.put(key, raw);
        return new ItemStack(s.itemId(), s.count(), ComponentPatch.of(entries));
    }

    /**
     * EnderEyeItem.use: 시선이 끝없는 틀을 맞히면 넘기고(useOn 이 채운다). 아니면 반경 100(청크 단위)의 #eye_of_ender_located(스트롱홀드)를
     * 찾아 있으면 눈 엔티티를 몸 가운데 높이에서 띄워 그 위치로 보내고(projectile_shoot, used_ender_eye, 소리 피치는 레벨 난수), 하나 쓰고 팔을 휘두른다.
     * 없으면 소모 없이 CONSUME. TODO(사용 중 표시): startUsingItem 의 생물 플래그는 플레이어에 아직 없다.
     */
    /**
     * FoodOnAStickItem.use(ctz): 탄 탈것이 그 막대의 종류(당근 낚싯대 → 돼지, 뒤틀린 균류 막대 → 스트라이더)이고 가속할 수 있으면(boost)
     * 내구도를 깎는다(당근 7, 뒤틀린 균류 1) — 다 닳으면 낚싯대로 바뀐다. 아니면 사용 통계만 올리고 PASS.
     * 차이: 낚싯대로 바뀔 때 부러진 막대의 다른 컴포넌트는 옮기지 않는다(transmuteCopy 는 옮김).
     */
    private static Holder foodOnAStickUse(BlockActor player, int hand, ItemStack stack, boolean warped) {
        if (player.handle() instanceof org.domi.furnace.server.entity.Player pl) {
            var v = pl.vehicle();
            boolean boosted = warped ? v instanceof org.domi.furnace.server.entity.Strider s && s.boost()
                    : v instanceof org.domi.furnace.server.entity.Pig pig && pig.boost();
            if (boosted) {
                BlockBreaking.hurtAndBreak(v.level(), player, hand, stack, warped ? 1 : 7);
                ItemStack now = player.itemInHand(hand);
                if (now.isEmpty()) now = new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "fishing_rod"), 1);
                return Holder.success(now);
            }
        }
        player.awardStat(warped ? "minecraft:used:minecraft:warped_fungus_on_a_stick" : "minecraft:used:minecraft:carrot_on_a_stick");
        return Holder.pass(stack);
    }

    private static Holder enderEyeUse(BlockLevel level, BlockActor player, int hand, ItemStack stack) {
        Clip.Result hit = Clip.playerPOV(level, player, Clip.FluidMode.NONE);
        if (!hit.miss() && B.is(level.getBlockState(hit.pos()), END_PORTAL_FRAME)) return Holder.pass(stack);
        if (!(level instanceof ChunkLevel cl)) return new Holder(InteractionResult.CONSUME, stack);
        int[] found = locateEyeTarget(cl, player);
        if (found == null) return new Holder(InteractionResult.CONSUME, stack);
        double midY = player.handle() instanceof org.domi.furnace.server.entity.Player pl ? pl.y() + (pl.boundingBox().maxY() - pl.boundingBox().minY()) * 0.5 : player.y() + 0.9;
        var eye = new org.domi.furnace.server.entity.EyeOfEnder(cl, cl.nextEntityId(), player.x(), midY, player.z());
        eye.setItem(stack);
        Pos target = new Pos(found[0], found[1], found[2]);
        eye.signalTo(target);
        cl.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.PROJECTILE_SHOOT, new org.domi.furnace.server.entity.Vec3(eye.x(), eye.y(), eye.z()),
                org.domi.furnace.server.world.gameevent.GameEventContext.of(player));
        cl.addFreshEntity(eye);
        player.usedEnderEye(target);
        float pitch = 0.33F + cl.random().nextFloat() * (0.5F - 0.33F);   // Mth.lerp(random.nextFloat(), 0.33F, 0.5F)
        cl.playSound(null, player.x(), player.y(), player.z(), "minecraft:entity.ender_eye.launch", "neutral", 1.0F, pitch);
        ItemStack rest = player.instabuild() ? stack : stack.withCount(stack.count() - 1);
        player.awardStat("minecraft:used:" + name(stack));
        player.swing(hand);
        return new Holder(InteractionResult.SUCCESS, rest);
    }

    /** findNearestMapStructure(EYE_OF_ENDER_LOCATED, player.blockPosition(), 100, false) — 오버월드의 지도 지원이 있을 때만(가정: 다른 차원엔 스트롱홀드 없음). */
    private static int[] locateEyeTarget(ChunkLevel cl, BlockActor player) {
        var maps = cl.explorerMaps();
        if (maps == null || !"minecraft:overworld".equals(cl.dimension())) return null;
        return maps.findNearestStructurePos("minecraft:eye_of_ender_located", (int) Math.floor(player.x()), (int) Math.floor(player.y()),
                (int) Math.floor(player.z()), 100);
    }

    /** EnderEyeItem.useOn: 눈이 없는 엔드 차원문 틀에 끼우고, 무늬가 다 차면 가운데 3×3 을 엔드 차원문으로(1038 은 전역). */
    private static InteractionResult enderEyeOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        if (!B.is(state, END_PORTAL_FRAME) || EndPortalFrameBlock.hasEye(state)) return InteractionResult.PASS;
        int s = EndPortalFrameBlock.eyeProp().with(state, true);
        // TODO(엔티티): Block.pushEntitiesUp(state, s, level, pos)
        level.setBlock(pos, s, 2);
        level.updateNeighbourForOutputSignal(pos, END_PORTAL_FRAME);
        player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        level.levelEvent(null, 1503, pos, 0);
        Pos found = EndPortalFrameBlock.findPortalShape(level, pos);
        if (found != null) {
            Pos corner = found.offset(-3, 0, -3);
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) level.setBlock(corner.offset(i, 0, j), B.defaultState(END_PORTAL), 2);
            level.globalLevelEvent(1038, corner.offset(1, 0, 1), 0);
        }
        return InteractionResult.CONSUME;
    }
}
