// Bukkit SkullMeta — 소유자는 minecraft:profile(바닐라 ResolvableProfile), 음표 소리는 minecraft:note_block_sound 컴포넌트에서 읽고 쓴다.
// 프로필 값은 블록 쪽(CraftSkull)과 같은 ProfileValue. 서버가 Mojang 에 물어 스킨을 채우지는 않는다(받은 값 그대로 둔다).
package org.bukkit.craftbukkit.inventory;

import com.destroystokyo.paper.profile.SharedPlayerProfile;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.compat.paper.ItemPdc;
import org.domi.furnace.compat.paper.MetaKind;
import org.domi.furnace.compat.paper.ProfileValue;
import org.domi.furnace.compat.paper.SkullProfileFill;
import org.domi.furnace.api.nbt.Snbt;

@DelegateDeserialization(SerializableMeta.class)
class CraftMetaSkull extends CraftMetaItem implements SkullMeta {

    static final String NOTE_BLOCK_SOUND = "minecraft:note_block_sound";
    static final int MAX_OWNER_LENGTH = 16;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    CraftMetaSkull(ItemStack work, EnumSet<ItemFlag> flags, ItemPdc pdc) { super(work, flags, pdc); }

    @Override
    MetaKind kind() { return MetaKind.SKULL; }

    private ProfileValue profile() {
        String raw = work.components().get(SkullProfileFill.PROFILE);
        return raw == null ? null : ProfileValue.fromNbt(Snbt.parse(raw));
    }

    private void setProfile(ProfileValue value) {
        if (value == null) clearComponent(SkullProfileFill.PROFILE);
        else setComponent(SkullProfileFill.PROFILE, Snbt.write(value.toNbt()));
    }

    // ------------------------------------------------------------------ 소유자

    @Override
    public boolean hasOwner() { return profile() != null; }

    @Override
    public String getOwner() {
        ProfileValue p = profile();
        return p == null ? null : p.name();
    }

    @Override
    public boolean setOwner(String name) {
        if (name != null && name.length() > MAX_OWNER_LENGTH) return false;
        if (name == null) {
            setProfile(null);
            return true;
        }
        // Paper: 접속 중인 플레이어면 그 프로필(스킨 포함), 아니면 UUID 0 + 이름
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(name);
        if (online instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft) setProfile(((SharedPlayerProfile) craft.getPlayerProfile()).buildValue());
        else setProfile(new ProfileValue(NIL_UUID, name, List.of()));
        return true;
    }

    @Override
    public OfflinePlayer getOwningPlayer() {
        ProfileValue p = profile();
        if (p == null) return null;
        if (p.id() != null && !p.id().equals(NIL_UUID)) return Bukkit.getOfflinePlayer(p.id());
        if (p.name() != null && !p.name().isEmpty()) return Bukkit.getOfflinePlayer(p.name());
        return null;
    }

    @Override
    public boolean setOwningPlayer(OfflinePlayer owner) {
        if (owner == null) setProfile(null);
        else if (owner instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft) setProfile(((SharedPlayerProfile) craft.getPlayerProfile()).buildValue());
        else setProfile(new ProfileValue(owner.getUniqueId(), owner.getName() == null ? "" : owner.getName(), List.of()));
        return true;
    }

    @Override
    public void setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile profile) {
        setProfile(profile == null ? null : ((SharedPlayerProfile) profile).buildValue());
    }

    @Override
    public com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile() {
        ProfileValue p = profile();
        return p == null ? null : new com.destroystokyo.paper.profile.CraftPlayerProfile(p);
    }

    @Override
    public PlayerProfile getOwnerProfile() {
        ProfileValue p = profile();
        return p == null ? null : new org.bukkit.craftbukkit.profile.CraftPlayerProfile(p);
    }

    @Override
    public void setOwnerProfile(PlayerProfile profile) {
        // Paper: 이 계층이 만든 프로필이 아니면 지운다
        if (profile instanceof SharedPlayerProfile shared) setProfile(org.bukkit.craftbukkit.profile.CraftPlayerProfile.validateSkullProfile(shared.buildValue()));
        else setProfile(null);
    }

    // ------------------------------------------------------------------ 음표 소리

    @Override
    public void setNoteBlockSound(NamespacedKey noteBlockSound) {
        if (noteBlockSound == null) clearComponent(NOTE_BLOCK_SOUND);
        else setComponent(NOTE_BLOCK_SOUND, Snbt.write(noteBlockSound.toString()));
    }

    @Override
    public NamespacedKey getNoteBlockSound() {
        String raw = work.components().get(NOTE_BLOCK_SOUND);
        return raw != null && Snbt.parse(raw) instanceof String s ? NamespacedKey.fromString(s) : null;
    }

    @Override
    public CraftMetaSkull clone() { return (CraftMetaSkull) super.clone(); }

    // ------------------------------------------------------------------ 직렬화

    @Override
    void serializeKind(Map<String, Object> out) {
        ProfileValue p = profile();
        if (p != null) out.put("skull-owner", new com.destroystokyo.paper.profile.CraftPlayerProfile(p));
        NamespacedKey sound = getNoteBlockSound();
        if (sound != null) out.put("note_block_sound", sound.toString());
    }

    @Override
    void deserializeKind(Map<String, Object> map) {
        if (!work.components().containsKey(SkullProfileFill.PROFILE)) {
            Object owner = map.get("skull-owner");
            if (owner instanceof PlayerProfile profile) setOwnerProfile(profile);
            else if (owner == null || owner instanceof String) setOwner((String) owner);
            else throw new IllegalArgumentException("skull-owner(" + owner + ") is not a valid " + String.class);
        }
        if (!work.components().containsKey(NOTE_BLOCK_SOUND)) {
            Object sound = map.get("note_block_sound");
            if (sound != null) setNoteBlockSound(NamespacedKey.fromString(sound.toString()));
        }
    }
}
