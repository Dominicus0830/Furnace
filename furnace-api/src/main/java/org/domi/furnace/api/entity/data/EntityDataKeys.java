// 엔티티 메타데이터 칸 상수 — 바닐라 1.21.1 의 SynchedEntityData 정의 전부(210칸). 부류 이름·칸 이름은 Mojang 이름을 따른다
// (클래스 이름, 필드 이름에서 DATA_·_ID 를 뺀 것). 칸 번호는 API 에 없다 — 서버가 버전마다 정한다. 주석의 "1.21.1 칸 n" 은 참고용이다.
//
// 칸은 그 부류와 하위 부류에만 있다: 예) Display.SCALE 은 block_display·item_display·text_display, LivingEntity.HEALTH 는 살아 있는 모든 엔티티.
// 예)
//   packet.with(EntityDataKeys.Entity.SHARED_FLAGS, (byte) 0x40)                       // 발광
//   virtualEntity.set(EntityDataKeys.TextDisplay.TEXT, Text.text("상점"))
//   virtualEntity.set(EntityDataKeys.Display.SCALE, new Vector3f(2, 2, 2))
//
// 이 파일은 서버 엔티티 정의에서 만든 표다(서버 시험 EntityDataKeysTest 가 칸·값 종류를 서버 엔티티와 하나하나 대조한다).
package org.domi.furnace.api.entity.data;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.math.Quaternionf;
import org.domi.furnace.api.math.Rotations;
import org.domi.furnace.api.math.Vector3f;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.particle.Particle;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Direction;

public final class EntityDataKeys {

    private EntityDataKeys() {}

    private static final Map<String, EntityDataKey<?>> BY_ID = Collections.synchronizedMap(new LinkedHashMap<>());
    private static volatile boolean loaded;

    private static <T> EntityDataKey<T> k(String owner, String name, EntityDataType<T> type) {
        EntityDataKey<T> key = new EntityDataKey<>(owner, name, type);
        if (BY_ID.putIfAbsent(key.id(), key) != null) throw new IllegalStateException("겹치는 칸: " + key.id());
        return key;
    }

    /** 모든 칸(부류 순서). */
    public static Collection<EntityDataKey<?>> all() {
        load();
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** "Display.scale" 같은 id 로 찾는다(없으면 null). */
    public static EntityDataKey<?> byId(String id) {
        load();
        return BY_ID.get(id);
    }

    private static void load() {
        if (loaded) return;
        synchronized (EntityDataKeys.class) {
            if (loaded) return;
            try {
                for (Class<?> c : EntityDataKeys.class.getDeclaredClasses()) MethodHandles.lookup().ensureInitialized(c);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            loaded = true;
        }
    }


    /** 바닐라 Entity. */
    public static final class Entity {
        private Entity() {}
        /** 1.21.1 칸 0. */
        public static final EntityDataKey<Byte> SHARED_FLAGS = k("Entity", "shared_flags", EntityDataType.BYTE);
        /** 1.21.1 칸 1. */
        public static final EntityDataKey<Integer> AIR_SUPPLY = k("Entity", "air_supply", EntityDataType.INT);
        /** 1.21.1 칸 2. */
        public static final EntityDataKey<Optional<Text>> CUSTOM_NAME = k("Entity", "custom_name", EntityDataType.OPTIONAL_COMPONENT);
        /** 1.21.1 칸 3. */
        public static final EntityDataKey<Boolean> CUSTOM_NAME_VISIBLE = k("Entity", "custom_name_visible", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 4. */
        public static final EntityDataKey<Boolean> SILENT = k("Entity", "silent", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 5. */
        public static final EntityDataKey<Boolean> NO_GRAVITY = k("Entity", "no_gravity", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 6. */
        public static final EntityDataKey<Pose> POSE = k("Entity", "pose", EntityDataType.POSE);
        /** 1.21.1 칸 7. */
        public static final EntityDataKey<Integer> TICKS_FROZEN = k("Entity", "ticks_frozen", EntityDataType.INT);
    }

    /** 바닐라 LivingEntity (상위: Entity). */
    public static final class LivingEntity {
        private LivingEntity() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Byte> LIVING_ENTITY_FLAGS = k("LivingEntity", "living_entity_flags", EntityDataType.BYTE);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Float> HEALTH = k("LivingEntity", "health", EntityDataType.FLOAT);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<List<Particle>> EFFECT_PARTICLES = k("LivingEntity", "effect_particles", EntityDataType.PARTICLES);
        /** 1.21.1 칸 11. */
        public static final EntityDataKey<Boolean> EFFECT_AMBIENCE = k("LivingEntity", "effect_ambience", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 12. */
        public static final EntityDataKey<Integer> ARROW_COUNT = k("LivingEntity", "arrow_count", EntityDataType.INT);
        /** 1.21.1 칸 13. */
        public static final EntityDataKey<Integer> STINGER_COUNT = k("LivingEntity", "stinger_count", EntityDataType.INT);
        /** 1.21.1 칸 14. */
        public static final EntityDataKey<Optional<BlockPos>> SLEEPING_POS = k("LivingEntity", "sleeping_pos", EntityDataType.OPTIONAL_BLOCK_POS);
    }

    /** 바닐라 Mob (상위: LivingEntity). */
    public static final class Mob {
        private Mob() {}
        /** 1.21.1 칸 15. */
        public static final EntityDataKey<Byte> MOB_FLAGS = k("Mob", "mob_flags", EntityDataType.BYTE);
    }

    /** 바닐라 AgeableMob (상위: Mob). */
    public static final class AgeableMob {
        private AgeableMob() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> BABY = k("AgeableMob", "baby", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Player (상위: LivingEntity). */
    public static final class Player {
        private Player() {}
        /** 1.21.1 칸 15. */
        public static final EntityDataKey<Float> ABSORPTION = k("Player", "absorption", EntityDataType.FLOAT);
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> SCORE = k("Player", "score", EntityDataType.INT);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> MODE_CUSTOMISATION = k("Player", "mode_customisation", EntityDataType.BYTE);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Byte> MAIN_HAND = k("Player", "main_hand", EntityDataType.BYTE);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<NbtCompound> SHOULDER_LEFT = k("Player", "shoulder_left", EntityDataType.COMPOUND_TAG);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<NbtCompound> SHOULDER_RIGHT = k("Player", "shoulder_right", EntityDataType.COMPOUND_TAG);
    }

    /** 바닐라 ArmorStand (상위: LivingEntity). */
    public static final class ArmorStand {
        private ArmorStand() {}
        /** 1.21.1 칸 15. */
        public static final EntityDataKey<Byte> CLIENT_FLAGS = k("ArmorStand", "client_flags", EntityDataType.BYTE);
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Rotations> HEAD_POSE = k("ArmorStand", "head_pose", EntityDataType.ROTATIONS);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Rotations> BODY_POSE = k("ArmorStand", "body_pose", EntityDataType.ROTATIONS);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Rotations> LEFT_ARM_POSE = k("ArmorStand", "left_arm_pose", EntityDataType.ROTATIONS);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Rotations> RIGHT_ARM_POSE = k("ArmorStand", "right_arm_pose", EntityDataType.ROTATIONS);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Rotations> LEFT_LEG_POSE = k("ArmorStand", "left_leg_pose", EntityDataType.ROTATIONS);
        /** 1.21.1 칸 21. */
        public static final EntityDataKey<Rotations> RIGHT_LEG_POSE = k("ArmorStand", "right_leg_pose", EntityDataType.ROTATIONS);
    }

    /** 바닐라 Display (상위: Entity). */
    public static final class Display {
        private Display() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Integer> TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS = k("Display", "transformation_interpolation_start_delta_ticks", EntityDataType.INT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Integer> TRANSFORMATION_INTERPOLATION_DURATION = k("Display", "transformation_interpolation_duration", EntityDataType.INT);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Integer> POS_ROT_INTERPOLATION_DURATION = k("Display", "pos_rot_interpolation_duration", EntityDataType.INT);
        /** 1.21.1 칸 11. */
        public static final EntityDataKey<Vector3f> TRANSLATION = k("Display", "translation", EntityDataType.VECTOR3);
        /** 1.21.1 칸 12. */
        public static final EntityDataKey<Vector3f> SCALE = k("Display", "scale", EntityDataType.VECTOR3);
        /** 1.21.1 칸 13. */
        public static final EntityDataKey<Quaternionf> LEFT_ROTATION = k("Display", "left_rotation", EntityDataType.QUATERNION);
        /** 1.21.1 칸 14. */
        public static final EntityDataKey<Quaternionf> RIGHT_ROTATION = k("Display", "right_rotation", EntityDataType.QUATERNION);
        /** 1.21.1 칸 15. */
        public static final EntityDataKey<Byte> BILLBOARD_RENDER_CONSTRAINTS = k("Display", "billboard_render_constraints", EntityDataType.BYTE);
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> BRIGHTNESS_OVERRIDE = k("Display", "brightness_override", EntityDataType.INT);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Float> VIEW_RANGE = k("Display", "view_range", EntityDataType.FLOAT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Float> SHADOW_RADIUS = k("Display", "shadow_radius", EntityDataType.FLOAT);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Float> SHADOW_STRENGTH = k("Display", "shadow_strength", EntityDataType.FLOAT);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Float> WIDTH = k("Display", "width", EntityDataType.FLOAT);
        /** 1.21.1 칸 21. */
        public static final EntityDataKey<Float> HEIGHT = k("Display", "height", EntityDataType.FLOAT);
        /** 1.21.1 칸 22. */
        public static final EntityDataKey<Integer> GLOW_COLOR_OVERRIDE = k("Display", "glow_color_override", EntityDataType.INT);
    }

    /** 바닐라 BlockDisplay (상위: Display). */
    public static final class BlockDisplay {
        private BlockDisplay() {}
        /** 1.21.1 칸 23. */
        public static final EntityDataKey<BlockData> BLOCK_STATE = k("BlockDisplay", "block_state", EntityDataType.BLOCK_STATE);
    }

    /** 바닐라 ItemDisplay (상위: Display). */
    public static final class ItemDisplay {
        private ItemDisplay() {}
        /** 1.21.1 칸 23. */
        public static final EntityDataKey<ItemStack> ITEM_STACK = k("ItemDisplay", "item_stack", EntityDataType.ITEM_STACK);
        /** 1.21.1 칸 24. */
        public static final EntityDataKey<Byte> ITEM_DISPLAY = k("ItemDisplay", "item_display", EntityDataType.BYTE);
    }

    /** 바닐라 TextDisplay (상위: Display). */
    public static final class TextDisplay {
        private TextDisplay() {}
        /** 1.21.1 칸 23. */
        public static final EntityDataKey<Text> TEXT = k("TextDisplay", "text", EntityDataType.COMPONENT);
        /** 1.21.1 칸 24. */
        public static final EntityDataKey<Integer> LINE_WIDTH = k("TextDisplay", "line_width", EntityDataType.INT);
        /** 1.21.1 칸 25. */
        public static final EntityDataKey<Integer> BACKGROUND_COLOR = k("TextDisplay", "background_color", EntityDataType.INT);
        /** 1.21.1 칸 26. */
        public static final EntityDataKey<Byte> TEXT_OPACITY = k("TextDisplay", "text_opacity", EntityDataType.BYTE);
        /** 1.21.1 칸 27. */
        public static final EntityDataKey<Byte> STYLE_FLAGS = k("TextDisplay", "style_flags", EntityDataType.BYTE);
    }

    /** 바닐라 Interaction. */
    public static final class Interaction {
        private Interaction() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Float> WIDTH = k("Interaction", "width", EntityDataType.FLOAT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Float> HEIGHT = k("Interaction", "height", EntityDataType.FLOAT);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Boolean> RESPONSE = k("Interaction", "response", EntityDataType.BOOLEAN);
    }

    /** 바닐라 AbstractArrow. */
    public static final class AbstractArrow {
        private AbstractArrow() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Byte> FLAGS = k("AbstractArrow", "flags", EntityDataType.BYTE);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Byte> PIERCE_LEVEL = k("AbstractArrow", "pierce_level", EntityDataType.BYTE);
    }

    /** 바닐라 AbstractChestedHorse. */
    public static final class AbstractChestedHorse {
        private AbstractChestedHorse() {}
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> CHEST = k("AbstractChestedHorse", "chest", EntityDataType.BOOLEAN);
    }

    /** 바닐라 AbstractFish. */
    public static final class AbstractFish {
        private AbstractFish() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> FROM_BUCKET = k("AbstractFish", "from_bucket", EntityDataType.BOOLEAN);
    }

    /** 바닐라 AbstractHorse. */
    public static final class AbstractHorse {
        private AbstractHorse() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> FLAGS = k("AbstractHorse", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 AbstractMinecart. */
    public static final class AbstractMinecart {
        private AbstractMinecart() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Integer> HURT = k("AbstractMinecart", "hurt", EntityDataType.INT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Integer> HURTDIR = k("AbstractMinecart", "hurtdir", EntityDataType.INT);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Float> DAMAGE = k("AbstractMinecart", "damage", EntityDataType.FLOAT);
        /** 1.21.1 칸 11. */
        public static final EntityDataKey<Integer> DISPLAY_BLOCK = k("AbstractMinecart", "display_block", EntityDataType.INT);
        /** 1.21.1 칸 12. */
        public static final EntityDataKey<Integer> DISPLAY_OFFSET = k("AbstractMinecart", "display_offset", EntityDataType.INT);
        /** 1.21.1 칸 13. */
        public static final EntityDataKey<Boolean> CUSTOM_DISPLAY = k("AbstractMinecart", "custom_display", EntityDataType.BOOLEAN);
    }

    /** 바닐라 AbstractPiglin. */
    public static final class AbstractPiglin {
        private AbstractPiglin() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> IMMUNE_TO_ZOMBIFICATION = k("AbstractPiglin", "immune_to_zombification", EntityDataType.BOOLEAN);
    }

    /** 바닐라 AbstractVillager (상위: AgeableMob). */
    public static final class AbstractVillager {
        private AbstractVillager() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> UNHAPPY_COUNTER = k("AbstractVillager", "unhappy_counter", EntityDataType.INT);
    }

    /** 바닐라 Allay. */
    public static final class Allay {
        private Allay() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> DANCING = k("Allay", "dancing", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> CAN_DUPLICATE = k("Allay", "can_duplicate", EntityDataType.BOOLEAN);
    }

    /** 바닐라 AreaEffectCloud. */
    public static final class AreaEffectCloud {
        private AreaEffectCloud() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Float> RADIUS = k("AreaEffectCloud", "radius", EntityDataType.FLOAT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Boolean> WAITING = k("AreaEffectCloud", "waiting", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Particle> PARTICLE = k("AreaEffectCloud", "particle", EntityDataType.PARTICLE);
    }

    /** 바닐라 Armadillo. */
    public static final class Armadillo {
        private Armadillo() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<String> STATE = k("Armadillo", "state", EntityDataType.ARMADILLO_STATE);
    }

    /** 바닐라 Arrow. */
    public static final class Arrow {
        private Arrow() {}
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Integer> EFFECT_COLOR = k("Arrow", "effect_color", EntityDataType.INT);
    }

    /** 바닐라 Axolotl. */
    public static final class Axolotl {
        private Axolotl() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> VARIANT = k("Axolotl", "variant", EntityDataType.INT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> PLAYING_DEAD = k("Axolotl", "playing_dead", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> FROM_BUCKET = k("Axolotl", "from_bucket", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Bat. */
    public static final class Bat {
        private Bat() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Byte> FLAGS = k("Bat", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 Bee. */
    public static final class Bee {
        private Bee() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> FLAGS = k("Bee", "flags", EntityDataType.BYTE);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> REMAINING_ANGER_TIME = k("Bee", "remaining_anger_time", EntityDataType.INT);
    }

    /** 바닐라 Blaze. */
    public static final class Blaze {
        private Blaze() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Byte> FLAGS = k("Blaze", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 Boat. */
    public static final class Boat {
        private Boat() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Integer> HURT = k("Boat", "hurt", EntityDataType.INT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Integer> HURTDIR = k("Boat", "hurtdir", EntityDataType.INT);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Float> DAMAGE = k("Boat", "damage", EntityDataType.FLOAT);
        /** 1.21.1 칸 11. */
        public static final EntityDataKey<Integer> TYPE = k("Boat", "type", EntityDataType.INT);
        /** 1.21.1 칸 12. */
        public static final EntityDataKey<Boolean> PADDLE_LEFT = k("Boat", "paddle_left", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 13. */
        public static final EntityDataKey<Boolean> PADDLE_RIGHT = k("Boat", "paddle_right", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 14. */
        public static final EntityDataKey<Integer> BUBBLE_TIME = k("Boat", "bubble_time", EntityDataType.INT);
    }

    /** 바닐라 Bogged. */
    public static final class Bogged {
        private Bogged() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> SHEARED = k("Bogged", "sheared", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Camel. */
    public static final class Camel {
        private Camel() {}
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> DASH = k("Camel", "dash", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Long> LAST_POSE_CHANGE_TICK = k("Camel", "last_pose_change_tick", EntityDataType.LONG);
    }

    /** 바닐라 Cat. */
    public static final class Cat {
        private Cat() {}
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<String> VARIANT = k("Cat", "variant", EntityDataType.CAT_VARIANT);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Boolean> IS_LYING = k("Cat", "is_lying", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 21. */
        public static final EntityDataKey<Boolean> RELAX_STATE_ONE = k("Cat", "relax_state_one", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 22. */
        public static final EntityDataKey<Integer> COLLAR_COLOR = k("Cat", "collar_color", EntityDataType.INT);
    }

    /** 바닐라 Creeper. */
    public static final class Creeper {
        private Creeper() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> SWELL_DIR = k("Creeper", "swell_dir", EntityDataType.INT);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> IS_POWERED = k("Creeper", "is_powered", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> IS_IGNITED = k("Creeper", "is_ignited", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Dolphin. */
    public static final class Dolphin {
        private Dolphin() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<BlockPos> TREASURE_POS = k("Dolphin", "treasure_pos", EntityDataType.BLOCK_POS);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> GOT_FISH = k("Dolphin", "got_fish", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> MOISTNESS_LEVEL = k("Dolphin", "moistness_level", EntityDataType.INT);
    }

    /** 바닐라 EndCrystal. */
    public static final class EndCrystal {
        private EndCrystal() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Optional<BlockPos>> BEAM_TARGET = k("EndCrystal", "beam_target", EntityDataType.OPTIONAL_BLOCK_POS);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Boolean> SHOW_BOTTOM = k("EndCrystal", "show_bottom", EntityDataType.BOOLEAN);
    }

    /** 바닐라 EnderDragon. */
    public static final class EnderDragon {
        private EnderDragon() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> PHASE = k("EnderDragon", "phase", EntityDataType.INT);
    }

    /** 바닐라 EnderMan. */
    public static final class EnderMan {
        private EnderMan() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Optional<BlockData>> CARRY_STATE = k("EnderMan", "carry_state", EntityDataType.OPTIONAL_BLOCK_STATE);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> CREEPY = k("EnderMan", "creepy", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> STARED_AT = k("EnderMan", "stared_at", EntityDataType.BOOLEAN);
    }

    /** 바닐라 EyeOfEnder. */
    public static final class EyeOfEnder {
        private EyeOfEnder() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> ITEM_STACK = k("EyeOfEnder", "item_stack", EntityDataType.ITEM_STACK);
    }

    /** 바닐라 FallingBlockEntity. */
    public static final class FallingBlockEntity {
        private FallingBlockEntity() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<BlockPos> START_POS = k("FallingBlockEntity", "start_pos", EntityDataType.BLOCK_POS);
    }

    /** 바닐라 Fireball. */
    public static final class Fireball {
        private Fireball() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> ITEM_STACK = k("Fireball", "item_stack", EntityDataType.ITEM_STACK);
    }

    /** 바닐라 FireworkRocketEntity. */
    public static final class FireworkRocketEntity {
        private FireworkRocketEntity() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> FIREWORKS_ITEM = k("FireworkRocketEntity", "fireworks_item", EntityDataType.ITEM_STACK);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<OptionalInt> ATTACHED_TO_TARGET = k("FireworkRocketEntity", "attached_to_target", EntityDataType.OPTIONAL_UNSIGNED_INT);
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Boolean> SHOT_AT_ANGLE = k("FireworkRocketEntity", "shot_at_angle", EntityDataType.BOOLEAN);
    }

    /** 바닐라 FishingHook. */
    public static final class FishingHook {
        private FishingHook() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Integer> HOOKED_ENTITY = k("FishingHook", "hooked_entity", EntityDataType.INT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Boolean> BITING = k("FishingHook", "biting", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Fox. */
    public static final class Fox {
        private Fox() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> TYPE = k("Fox", "type", EntityDataType.INT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Byte> FLAGS = k("Fox", "flags", EntityDataType.BYTE);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Optional<UUID>> TRUSTED_0 = k("Fox", "trusted_0", EntityDataType.OPTIONAL_UUID);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Optional<UUID>> TRUSTED_1 = k("Fox", "trusted_1", EntityDataType.OPTIONAL_UUID);
    }

    /** 바닐라 Frog. */
    public static final class Frog {
        private Frog() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<String> VARIANT = k("Frog", "variant", EntityDataType.FROG_VARIANT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<OptionalInt> TONGUE_TARGET = k("Frog", "tongue_target", EntityDataType.OPTIONAL_UNSIGNED_INT);
    }

    /** 바닐라 Ghast. */
    public static final class Ghast {
        private Ghast() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> IS_CHARGING = k("Ghast", "is_charging", EntityDataType.BOOLEAN);
    }

    /** 바닐라 GlowSquid. */
    public static final class GlowSquid {
        private GlowSquid() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> DARK_TICKS_REMAINING = k("GlowSquid", "dark_ticks_remaining", EntityDataType.INT);
    }

    /** 바닐라 Goat. */
    public static final class Goat {
        private Goat() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> IS_SCREAMING_GOAT = k("Goat", "is_screaming_goat", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> HAS_LEFT_HORN = k("Goat", "has_left_horn", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> HAS_RIGHT_HORN = k("Goat", "has_right_horn", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Guardian. */
    public static final class Guardian {
        private Guardian() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> MOVING = k("Guardian", "moving", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> ATTACK_TARGET = k("Guardian", "attack_target", EntityDataType.INT);
    }

    /** 바닐라 Hoglin. */
    public static final class Hoglin {
        private Hoglin() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> IMMUNE_TO_ZOMBIFICATION = k("Hoglin", "immune_to_zombification", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Horse. */
    public static final class Horse {
        private Horse() {}
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> TYPE_VARIANT = k("Horse", "type_variant", EntityDataType.INT);
    }

    /** 바닐라 IronGolem. */
    public static final class IronGolem {
        private IronGolem() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Byte> FLAGS = k("IronGolem", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 ItemEntity. */
    public static final class ItemEntity {
        private ItemEntity() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> ITEM = k("ItemEntity", "item", EntityDataType.ITEM_STACK);
    }

    /** 바닐라 ItemFrame. */
    public static final class ItemFrame {
        private ItemFrame() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> ITEM = k("ItemFrame", "item", EntityDataType.ITEM_STACK);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<Integer> ROTATION = k("ItemFrame", "rotation", EntityDataType.INT);
    }

    /** 바닐라 Llama. */
    public static final class Llama {
        private Llama() {}
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Integer> STRENGTH = k("Llama", "strength", EntityDataType.INT);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Integer> VARIANT = k("Llama", "variant", EntityDataType.INT);
    }

    /** 바닐라 MinecartCommandBlock. */
    public static final class MinecartCommandBlock {
        private MinecartCommandBlock() {}
        /** 1.21.1 칸 14. */
        public static final EntityDataKey<String> COMMAND_NAME = k("MinecartCommandBlock", "command_name", EntityDataType.STRING);
        /** 1.21.1 칸 15. */
        public static final EntityDataKey<Text> LAST_OUTPUT = k("MinecartCommandBlock", "last_output", EntityDataType.COMPONENT);
    }

    /** 바닐라 MinecartFurnace. */
    public static final class MinecartFurnace {
        private MinecartFurnace() {}
        /** 1.21.1 칸 14. */
        public static final EntityDataKey<Boolean> FUEL = k("MinecartFurnace", "fuel", EntityDataType.BOOLEAN);
    }

    /** 바닐라 MushroomCow. */
    public static final class MushroomCow {
        private MushroomCow() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<String> TYPE = k("MushroomCow", "type", EntityDataType.STRING);
    }

    /** 바닐라 Ocelot. */
    public static final class Ocelot {
        private Ocelot() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> TRUSTING = k("Ocelot", "trusting", EntityDataType.BOOLEAN);
    }

    /** 바닐라 OminousItemSpawner. */
    public static final class OminousItemSpawner {
        private OminousItemSpawner() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> ITEM = k("OminousItemSpawner", "item", EntityDataType.ITEM_STACK);
    }

    /** 바닐라 Painting. */
    public static final class Painting {
        private Painting() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<String> VARIANT = k("Painting", "variant", EntityDataType.PAINTING_VARIANT);
    }

    /** 바닐라 Panda. */
    public static final class Panda {
        private Panda() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> UNHAPPY_COUNTER = k("Panda", "unhappy_counter", EntityDataType.INT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> SNEEZE_COUNTER = k("Panda", "sneeze_counter", EntityDataType.INT);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Integer> EAT_COUNTER = k("Panda", "eat_counter", EntityDataType.INT);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Byte> MAIN_GENE = k("Panda", "main_gene", EntityDataType.BYTE);
        /** 1.21.1 칸 21. */
        public static final EntityDataKey<Byte> HIDDEN_GENE = k("Panda", "hidden_gene", EntityDataType.BYTE);
        /** 1.21.1 칸 22. */
        public static final EntityDataKey<Byte> FLAGS = k("Panda", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 Parrot. */
    public static final class Parrot {
        private Parrot() {}
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Integer> VARIANT = k("Parrot", "variant", EntityDataType.INT);
    }

    /** 바닐라 Phantom. */
    public static final class Phantom {
        private Phantom() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> SIZE = k("Phantom", "size", EntityDataType.INT);
    }

    /** 바닐라 Pig. */
    public static final class Pig {
        private Pig() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> SADDLE = k("Pig", "saddle", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> BOOST_TIME = k("Pig", "boost_time", EntityDataType.INT);
    }

    /** 바닐라 Piglin. */
    public static final class Piglin {
        private Piglin() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> BABY = k("Piglin", "baby", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> IS_CHARGING_CROSSBOW = k("Piglin", "is_charging_crossbow", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> IS_DANCING = k("Piglin", "is_dancing", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Pillager. */
    public static final class Pillager {
        private Pillager() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> IS_CHARGING_CROSSBOW = k("Pillager", "is_charging_crossbow", EntityDataType.BOOLEAN);
    }

    /** 바닐라 PolarBear. */
    public static final class PolarBear {
        private PolarBear() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> STANDING = k("PolarBear", "standing", EntityDataType.BOOLEAN);
    }

    /** 바닐라 PrimedTnt. */
    public static final class PrimedTnt {
        private PrimedTnt() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Integer> FUSE = k("PrimedTnt", "fuse", EntityDataType.INT);
        /** 1.21.1 칸 9. */
        public static final EntityDataKey<BlockData> BLOCK_STATE = k("PrimedTnt", "block_state", EntityDataType.BLOCK_STATE);
    }

    /** 바닐라 Pufferfish. */
    public static final class Pufferfish {
        private Pufferfish() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> PUFF_STATE = k("Pufferfish", "puff_state", EntityDataType.INT);
    }

    /** 바닐라 Rabbit. */
    public static final class Rabbit {
        private Rabbit() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> TYPE = k("Rabbit", "type", EntityDataType.INT);
    }

    /** 바닐라 Raider. */
    public static final class Raider {
        private Raider() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> IS_CELEBRATING = k("Raider", "is_celebrating", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Sheep. */
    public static final class Sheep {
        private Sheep() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> WOOL = k("Sheep", "wool", EntityDataType.BYTE);
    }

    /** 바닐라 Shulker. */
    public static final class Shulker {
        private Shulker() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Direction> ATTACH_FACE = k("Shulker", "attach_face", EntityDataType.DIRECTION);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> PEEK = k("Shulker", "peek", EntityDataType.BYTE);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Byte> COLOR = k("Shulker", "color", EntityDataType.BYTE);
    }

    /** 바닐라 Skeleton. */
    public static final class Skeleton {
        private Skeleton() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> STRAY_CONVERSION = k("Skeleton", "stray_conversion", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Slime. */
    public static final class Slime {
        private Slime() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> SIZE = k("Slime", "size", EntityDataType.INT);
    }

    /** 바닐라 Sniffer. */
    public static final class Sniffer {
        private Sniffer() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<String> STATE = k("Sniffer", "state", EntityDataType.SNIFFER_STATE);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> DROP_SEED_AT_TICK = k("Sniffer", "drop_seed_at_tick", EntityDataType.INT);
    }

    /** 바닐라 SnowGolem. */
    public static final class SnowGolem {
        private SnowGolem() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Byte> PUMPKIN = k("SnowGolem", "pumpkin", EntityDataType.BYTE);
    }

    /** 바닐라 SpellcasterIllager. */
    public static final class SpellcasterIllager {
        private SpellcasterIllager() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> SPELL_CASTING = k("SpellcasterIllager", "spell_casting", EntityDataType.BYTE);
    }

    /** 바닐라 Spider. */
    public static final class Spider {
        private Spider() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Byte> FLAGS = k("Spider", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 Strider. */
    public static final class Strider {
        private Strider() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> BOOST_TIME = k("Strider", "boost_time", EntityDataType.INT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> SUFFOCATING = k("Strider", "suffocating", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> SADDLE = k("Strider", "saddle", EntityDataType.BOOLEAN);
    }

    /** 바닐라 TamableAnimal. */
    public static final class TamableAnimal {
        private TamableAnimal() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Byte> FLAGS = k("TamableAnimal", "flags", EntityDataType.BYTE);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Optional<UUID>> OWNER_UUID = k("TamableAnimal", "owner_uuid", EntityDataType.OPTIONAL_UUID);
    }

    /** 바닐라 ThrowableItemProjectile. */
    public static final class ThrowableItemProjectile {
        private ThrowableItemProjectile() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<ItemStack> ITEM_STACK = k("ThrowableItemProjectile", "item_stack", EntityDataType.ITEM_STACK);
    }

    /** 바닐라 ThrownTrident. */
    public static final class ThrownTrident {
        private ThrownTrident() {}
        /** 1.21.1 칸 10. */
        public static final EntityDataKey<Byte> LOYALTY = k("ThrownTrident", "loyalty", EntityDataType.BYTE);
        /** 1.21.1 칸 11. */
        public static final EntityDataKey<Boolean> FOIL = k("ThrownTrident", "foil", EntityDataType.BOOLEAN);
    }

    /** 바닐라 TropicalFish. */
    public static final class TropicalFish {
        private TropicalFish() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> TYPE_VARIANT = k("TropicalFish", "type_variant", EntityDataType.INT);
    }

    /** 바닐라 Turtle. */
    public static final class Turtle {
        private Turtle() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<BlockPos> HOME_POS = k("Turtle", "home_pos", EntityDataType.BLOCK_POS);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> HAS_EGG = k("Turtle", "has_egg", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> LAYING_EGG = k("Turtle", "laying_egg", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<BlockPos> TRAVEL_POS = k("Turtle", "travel_pos", EntityDataType.BLOCK_POS);
        /** 1.21.1 칸 21. */
        public static final EntityDataKey<Boolean> GOING_HOME = k("Turtle", "going_home", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 22. */
        public static final EntityDataKey<Boolean> TRAVELLING = k("Turtle", "travelling", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Vex. */
    public static final class Vex {
        private Vex() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Byte> FLAGS = k("Vex", "flags", EntityDataType.BYTE);
    }

    /** 바닐라 Villager (상위: AbstractVillager). */
    public static final class Villager {
        private Villager() {}
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<VillagerData> VILLAGER_DATA = k("Villager", "villager_data", EntityDataType.VILLAGER_DATA);
    }

    /** 바닐라 Warden. */
    public static final class Warden {
        private Warden() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> CLIENT_ANGER_LEVEL = k("Warden", "client_anger_level", EntityDataType.INT);
    }

    /** 바닐라 Witch. */
    public static final class Witch {
        private Witch() {}
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Boolean> USING_ITEM = k("Witch", "using_item", EntityDataType.BOOLEAN);
    }

    /** 바닐라 WitherBoss. */
    public static final class WitherBoss {
        private WitherBoss() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Integer> TARGET_A = k("WitherBoss", "target_a", EntityDataType.INT);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> TARGET_B = k("WitherBoss", "target_b", EntityDataType.INT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Integer> TARGET_C = k("WitherBoss", "target_c", EntityDataType.INT);
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Integer> INVULNERABLE_TICKS = k("WitherBoss", "invulnerable_ticks", EntityDataType.INT);
    }

    /** 바닐라 WitherSkull. */
    public static final class WitherSkull {
        private WitherSkull() {}
        /** 1.21.1 칸 8. */
        public static final EntityDataKey<Boolean> DANGEROUS = k("WitherSkull", "dangerous", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Wolf. */
    public static final class Wolf {
        private Wolf() {}
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> INTERESTED = k("Wolf", "interested", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<Integer> COLLAR_COLOR = k("Wolf", "collar_color", EntityDataType.INT);
        /** 1.21.1 칸 21. */
        public static final EntityDataKey<Integer> REMAINING_ANGER_TIME = k("Wolf", "remaining_anger_time", EntityDataType.INT);
        /** 1.21.1 칸 22. */
        public static final EntityDataKey<String> VARIANT = k("Wolf", "variant", EntityDataType.WOLF_VARIANT);
    }

    /** 바닐라 Zoglin. */
    public static final class Zoglin {
        private Zoglin() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> BABY = k("Zoglin", "baby", EntityDataType.BOOLEAN);
    }

    /** 바닐라 Zombie. */
    public static final class Zombie {
        private Zombie() {}
        /** 1.21.1 칸 16. */
        public static final EntityDataKey<Boolean> BABY = k("Zombie", "baby", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 17. */
        public static final EntityDataKey<Integer> SPECIAL_TYPE = k("Zombie", "special_type", EntityDataType.INT);
        /** 1.21.1 칸 18. */
        public static final EntityDataKey<Boolean> DROWNED_CONVERSION = k("Zombie", "drowned_conversion", EntityDataType.BOOLEAN);
    }

    /** 바닐라 ZombieVillager. */
    public static final class ZombieVillager {
        private ZombieVillager() {}
        /** 1.21.1 칸 19. */
        public static final EntityDataKey<Boolean> CONVERTING = k("ZombieVillager", "converting", EntityDataType.BOOLEAN);
        /** 1.21.1 칸 20. */
        public static final EntityDataKey<VillagerData> VILLAGER_DATA = k("ZombieVillager", "villager_data", EntityDataType.VILLAGER_DATA);
    }
}
