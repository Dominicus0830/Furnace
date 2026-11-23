package net.minecraft.world.entity.decoration;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.domi.furnace.api.internal.ChunkInternals.EntityEdit;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
import org.domi.furnace.compat.nms.NmsEntityOwner;

/** 액자 안 아이템의 돌림. 월드 밖 엔티티의 규칙은 {@link HangingEntity} 조각과 같다. */
class ItemFrame {

    /** 아직 월드에 없는 액자의 돌림(바닐라 DATA_ROTATION 기본 0). */
    private volatile int furnaceDetachedRotation;

    /** 바닐라 getRotation: 아이템 돌림(0~7). */
    public int getRotation() {
        NmsEntityOwner.read(this, "ItemFrame#getRotation");
        if (furnaceNotInWorld()) return furnaceDetachedRotation;
        return (Integer) furnaceEditOf("ItemFrame#getRotation", new EntityEdit.ItemFrameRotation());
    }

    /** 바닐라 setRotation(int): 8 로 나눈 나머지로 두고 붙은 블록 둘레에 비교기 갱신(월드 밖 액자는 넣을 때 서버가 같은 순서로 재생). */
    public void setRotation(int rotation) {
        NmsEntityOwner.write(this, "ItemFrame#setRotation");
        if (!furnaceNotInWorld()) {
            furnaceEditOf("ItemFrame#setRotation", new EntityEdit.SetItemFrameRotation(rotation));
            return;
        }
        furnaceDetachedOp("ItemFrame#setRotation", new EntityOp.SetItemFrameRotation(rotation));
        furnaceDetachedRotation = rotation % 8;
    }

    /** 바닐라 ItemFrame#setDirection: 수평이면 xRot 0·yRot 2D 값 × 90, 위·아래면 xRot −90 × 축 방향·yRot 0. */
    @Override
    protected void furnaceDetachedSetDirection(Direction d) {
        super.furnaceDetachedSetDirection(d);
        if (d.getAxis().isHorizontal()) furnaceDetachedRot(d.get2DDataValue() * 90, 0.0F);
        else furnaceDetachedRot(0.0F, -90 * d.getAxisDirection().getStep());
    }

    /**
     * 바닐라 ItemFrame#readAdditionalSaveData: 아이템이 있으면 "ItemRotation" 으로 돌림(이웃 갱신 없이), 그다음 "Facing"(3D 값)으로 setDirection.
     * 아이템이 비었는지는 바닐라 ItemStack.parse 의 앞 규칙으로 본다: 아이템 레지스트리에 있는 id 이고 공기가 아니며 count 가 1~99(또는 없음).
     * 가정: components 가 잘못된 태그(바닐라는 파싱 실패 → 빈 스택)는 비지 않았다고 본다 — 넣은 뒤에는 서버 값이 정답이다.
     */
    @Override
    protected void furnaceDetachedLoaded(CompoundTag tag) {
        if (furnaceItemPresent(tag)) furnaceDetachedRotation = tag.getByte("ItemRotation") % 8;
        furnaceDetachedSetDirection(Direction.from3DDataValue(tag.getByte("Facing")));
    }

    private static boolean furnaceItemPresent(CompoundTag tag) {
        if (!tag.contains("Item", 10)) return false;
        CompoundTag item = tag.getCompound("Item");
        String id = item.getString("id");
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null || key.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"))
                || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) return false;
        if (!item.contains("count", 99)) return true;
        int count = item.getInt("count");
        return count >= 1 && count <= 99;
    }
}
