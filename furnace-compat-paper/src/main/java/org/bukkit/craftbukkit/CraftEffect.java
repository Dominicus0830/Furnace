package org.bukkit.craftbukkit;

import org.bukkit.Axis;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;

/**
 * Paper 1.21.1 CraftEffect: 효과 데이터(재료·색·방향 등) → level_event 의 정수 데이터. 분기·예외 문구는 Paper 와 같다.
 * Furnace 의 BlockFace 는 여섯 면과 SELF 뿐이라 SMOKE 의 대각선(옛 방향) 분기는 SELF 로만 닿는다.
 */
public final class CraftEffect {

    private CraftEffect() {}

    /** Paper CraftWorld·CraftPlayer#playEffect(…, T data) 앞머리의 검사(문구 같음). */
    public static void checkData(Effect effect, Object data) {
        if (effect == null) throw new IllegalArgumentException("Effect cannot be null");
        if (data != null) {
            if (effect.getData() == null) throw new IllegalArgumentException("Effect." + effect + " does not have a valid Data");
            if (!effect.isApplicable(data)) throw new IllegalArgumentException(data.getClass().getName() + " data cannot be used for the " + effect + " effect");
        } else if (effect.getData() != null && effect != Effect.ELECTRIC_SPARK) {   // 축은 ELECTRIC_SPARK 만 생략할 수 있다
            throw new IllegalArgumentException("Wrong kind of data for the " + effect + " effect");
        }
    }

    /** level_event 패킷(전역 아님). 위치는 Location 의 블록 칸(CraftLocation.toBlockPosition). */
    public static org.domi.furnace.api.packet.GenericPacket packet(org.bukkit.Location loc, Effect effect, int data) {
        return org.domi.furnace.api.packet.GenericPacket.of(org.domi.furnace.api.packet.PacketTypes.Clientbound.LEVEL_EVENT)
                .with("type", effect.getId())
                .with("pos", new org.domi.furnace.api.world.BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                .with("data", data)
                .with("global_event", false);
    }

    public static <T> int getDataValue(Effect effect, T data) {
        int datavalue;
        switch (effect) {
            case PARTICLES_SCULK_CHARGE, TRIAL_SPAWNER_DETECT_PLAYER, BEE_GROWTH, TURTLE_EGG_PLACEMENT, SMASH_ATTACK,
                    TRIAL_SPAWNER_DETECT_PLAYER_OMINOUS, VILLAGER_PLANT_GROW -> datavalue = (Integer) data;
            case POTION_BREAK, INSTANT_POTION_BREAK -> datavalue = ((Color) data).asRGB();
            case RECORD_PLAY -> {
                if (!(data == Material.AIR || ((Material) data).isRecord())) {
                    throw new IllegalArgumentException("Invalid record type for Material " + data + "!");
                }
                datavalue = net.minecraft.world.item.Item.getId(CraftMagicNumbers.getItem((Material) data));
            }
            case SHOOT_WHITE_SMOKE -> {
                BlockFace face = (BlockFace) data;
                datavalue = switch (face) {   // Direction.get3DDataValue: DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5
                    case DOWN -> 0;
                    case UP -> 1;
                    case NORTH -> 2;
                    case SOUTH -> 3;
                    case WEST -> 4;
                    case EAST -> 5;
                    default -> throw new IllegalArgumentException(face + " isn't cartesian");
                };
            }
            case SMOKE -> datavalue = switch ((BlockFace) data) {
                case DOWN, SELF -> 0;   // SPIGOT-6318: 옛 방향(대각선·SELF)은 0
                case UP -> 1;
                case NORTH -> 2;
                case SOUTH -> 3;
                case WEST -> 4;
                case EAST -> 5;
            };
            case STEP_SOUND, PARTICLES_AND_SOUND_BRUSH_BLOCK_COMPLETE -> {
                if (effect == Effect.STEP_SOUND && data instanceof Material m) {
                    if (!m.isBlock()) throw new IllegalArgumentException("Material " + m + " is not a block!");
                    datavalue = CraftMagicNumbers.getBlock(m).defaultBlockState().furnaceStateId();
                } else {
                    datavalue = ((CraftBlockData) data).getState().furnaceStateId();
                }
            }
            case COMPOSTER_FILL_ATTEMPT, TRIAL_SPAWNER_SPAWN, TRIAL_SPAWNER_SPAWN_MOB_AT, VAULT_ACTIVATE, VAULT_DEACTIVATE,
                    TRIAL_SPAWNER_BECOME_OMINOUS, TRIAL_SPAWNER_SPAWN_ITEM -> datavalue = ((Boolean) data) ? 1 : 0;
            case BONE_MEAL_USE -> datavalue = (Integer) data;
            case ELECTRIC_SPARK -> {
                if (data == null) {
                    datavalue = -1;
                } else {
                    datavalue = switch ((Axis) data) {
                        case X -> 0;
                        case Y -> 1;
                        case Z -> 2;
                    };
                }
            }
            default -> datavalue = 0;
        }
        return datavalue;
    }
}
