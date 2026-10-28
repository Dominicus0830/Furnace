// 바닐라 SkullBlockEntity: 머리의 프로필(profile)·소리 블록 소리(note_block_sound)·이름(custom_name) 보존과 컴포넌트 주고받기.
// 가정: 프로필 확인(ResolvableProfile.resolve — Mojang 세션 서버 조회)은 하지 않는다. 받은 값을 그대로 둔다.
// 용 머리 입 움직임 틱은 클라이언트 전용이라 없다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.AbstractSkullBlock;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class SkullBlockEntity extends BlockEntity {

    /** ResolvableProfile 정규 값(없으면 null). */
    private Object profile;
    private String noteBlockSound;
    private String customName;

    public SkullBlockEntity(Pos pos, int state) { super("minecraft:skull", pos, state); }

    @Override
    public boolean isValid(int state) { return BlockBehaviors.of(state) instanceof AbstractSkullBlock; }

    public Object profile() { return profile; }

    public String noteBlockSound() { return noteBlockSound; }

    public String customName() { return customName; }

    @Override
    public void load(NbtCompound tag) {
        if (tag.contains("profile")) {
            try {
                profile = DataComponents.PROFILE_TYPE.persistent().normalize(tag.tags().get("profile"));
            } catch (RuntimeException e) {
                // Failed to load profile from player head
            }
        }
        if (tag.tags().get("note_block_sound") instanceof String s) {
            var id = org.domi.furnace.server.data.ResourceId.tryParse(s);
            noteBlockSound = id == null ? null : id.toString();
        }
        customName = tag.tags().get("custom_name") instanceof String s ? s : null;
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (profile != null) tag.put("profile", DataComponents.PROFILE_TYPE.persistent().toNbt(profile));
        if (noteBlockSound != null) tag.putString("note_block_sound", noteBlockSound);
        if (customName != null) tag.putString("custom_name", customName);
    }

    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        profile = in.get("minecraft:profile");   // 바닐라 setOwner(…) — 확인(resolve)은 하지 않는다
        noteBlockSound = in.get("minecraft:note_block_sound") instanceof String s ? s : null;
        customName = in.get("minecraft:custom_name") instanceof String n ? n : null;
    }

    @Override
    protected void collectImplicitComponents(java.util.Map<String, Object> out) {
        out.put("minecraft:profile", profile);
        out.put("minecraft:note_block_sound", noteBlockSound);
        out.put("minecraft:custom_name", customName);
    }

    /** getUpdateTag = saveCustomOnly. */
    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }

    @Override
    public boolean hasUpdatePacket() { return true; }
}
