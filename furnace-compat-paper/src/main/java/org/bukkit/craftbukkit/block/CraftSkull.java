package org.bukkit.craftbukkit.block;

import com.destroystokyo.paper.profile.SharedPlayerProfile;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.SkullType;
import org.bukkit.block.BlockFace;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatBlockData;
import org.domi.furnace.compat.paper.ProfileValue;

/**
 * Paper CraftSkull: 머리 블록 엔티티 NBT(바닐라 SkullBlockEntity — profile, note_block_sound) 사본 위에서 소유자·소리를 읽고 쓴다.
 * 소유자는 플레이어 머리일 때만 사본에 적고(Paper applyTo 와 같다), 서버의 확인(Mojang 에 스킨을 묻는 resolve)은 하지 않는다 — 받은 값 그대로 둔다.
 */
public class CraftSkull extends CraftBlockEntityState implements org.bukkit.block.Skull {

    private static final int MAX_OWNER_LENGTH = 16;

    /** 바닥 머리 rotation 0..15 의 방향(0 = 남쪽, 4 = 서쪽, 8 = 북쪽, 12 = 동쪽). */
    private static final BlockFace[] ROTATIONS = {
        BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
        BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
        BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
        BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    private ProfileValue profile;
    private NamespacedKey noteBlockSound;

    public CraftSkull(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        NbtCompound n = snapshotNbt();
        if (n.contains("profile")) profile = ProfileValue.fromNbt(n.get("profile"));
        if (n.get("note_block_sound") instanceof String s) noteBlockSound = NamespacedKey.fromString(s);
    }

    // ------------------------------------------------------------------ 소유자

    @Override
    public boolean hasOwner() { return profile != null; }

    @Override
    public String getOwner() { return hasOwner() ? profile.name() : null; }

    @Override
    public boolean setOwner(String name) {
        if (name == null || name.length() > MAX_OWNER_LENGTH) return false;
        var found = Compat.furnace().profileByName(name).orElse(null);
        if (found == null) return false;
        profile = new ProfileValue(found.id(), found.name(), ProfileValue.fromApi(found.properties()));
        return true;
    }

    @Override
    public OfflinePlayer getOwningPlayer() {
        if (hasOwner()) {
            if (profile.id() != null && !profile.id().equals(new UUID(0L, 0L))) return Bukkit.getOfflinePlayer(profile.id());
            if (profile.name() != null && !profile.name().isEmpty()) return Bukkit.getOfflinePlayer(profile.name());
        }
        return null;
    }

    @Override
    public void setOwningPlayer(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft) {
            profile = ((SharedPlayerProfile) craft.getPlayerProfile()).buildValue();
        } else {
            profile = new ProfileValue(player.getUniqueId(), player.getName() == null ? "" : player.getName(), java.util.List.of());
        }
    }

    @Override
    public void setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        this.profile = ((SharedPlayerProfile) profile).buildValue();
    }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile() {
        return profile != null ? new com.destroystokyo.paper.profile.CraftPlayerProfile(profile) : null;
    }

    @Override
    public org.bukkit.profile.PlayerProfile getOwnerProfile() {
        return hasOwner() ? new org.bukkit.craftbukkit.profile.CraftPlayerProfile(profile) : null;
    }

    @Override
    public void setOwnerProfile(org.bukkit.profile.PlayerProfile profile) {
        this.profile = profile == null ? null
                : org.bukkit.craftbukkit.profile.CraftPlayerProfile.validateSkullProfile(((SharedPlayerProfile) profile).buildValue());
    }

    // ------------------------------------------------------------------ 소리

    @Override
    public NamespacedKey getNoteBlockSound() { return noteBlockSound; }

    @Override
    public void setNoteBlockSound(NamespacedKey noteBlockSound) { this.noteBlockSound = noteBlockSound; }

    // ------------------------------------------------------------------ 방향·종류

    @Override
    public BlockFace getRotation() {
        var props = ((CompatBlockData) getBlockData()).furnace().properties();
        if (props.containsKey("rotation")) return ROTATIONS[Integer.parseInt(props.get("rotation"))];
        return BlockFace.valueOf(props.get("facing").toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public void setRotation(BlockFace rotation) {
        Objects.requireNonNull(rotation, "rotation");
        var data = ((CompatBlockData) getBlockData()).furnace();
        if (data.properties().containsKey("rotation")) {
            for (int i = 0; i < ROTATIONS.length; i++) {
                if (ROTATIONS[i] == rotation) {
                    setBlockData(CompatBlockData.of(data.with("rotation", Integer.toString(i))));
                    return;
                }
            }
            throw new IllegalArgumentException("Illegal rotation " + rotation);
        }
        switch (rotation) {
            case NORTH, SOUTH, EAST, WEST -> setBlockData(CompatBlockData.of(data.with("facing", rotation.name().toLowerCase(java.util.Locale.ROOT))));
            default -> throw new IllegalArgumentException("Invalid facing " + rotation + ", only NORTH, EAST, SOUTH and WEST are allowed for a wall head");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public SkullType getSkullType() {
        return switch (getType()) {
            case SKELETON_SKULL, SKELETON_WALL_SKULL -> SkullType.SKELETON;
            case WITHER_SKELETON_SKULL, WITHER_SKELETON_WALL_SKULL -> SkullType.WITHER;
            case ZOMBIE_HEAD, ZOMBIE_WALL_HEAD -> SkullType.ZOMBIE;
            case PIGLIN_HEAD, PIGLIN_WALL_HEAD -> SkullType.PIGLIN;
            case PLAYER_HEAD, PLAYER_WALL_HEAD -> SkullType.PLAYER;
            case CREEPER_HEAD, CREEPER_WALL_HEAD -> SkullType.CREEPER;
            case DRAGON_HEAD, DRAGON_WALL_HEAD -> SkullType.DRAGON;
            default -> throw new IllegalArgumentException("Unknown SkullType for " + getType());
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setSkullType(SkullType skullType) { throw new UnsupportedOperationException("Must change block type"); }

    // ------------------------------------------------------------------ 사본에 적기

    /**
     * 소유자(플레이어 머리만)와 소리를 사본에 적는다. 소유자가 없으면 사본에서 profile 을 지운다 — 서버가 사본을 실을 때 플레이어 머리의
     * 소유자를 비운다(Paper skull.setOwner(null)). 소리는 지워도 서버의 옛 값이 남는다(Paper 와 같은 저장→읽기 길).
     */
    @Override
    protected void applyToSnapshot() {
        super.applyToSnapshot();
        NbtCompound n = snapshotNbt();
        if (getSkullType() == SkullType.PLAYER) {
            if (profile != null) n.putCompound("profile", profile.toNbt());
            else n.remove("profile");
        }
        if (noteBlockSound != null) n.putString("note_block_sound", noteBlockSound.toString());
        else n.remove("note_block_sound");
    }
}
