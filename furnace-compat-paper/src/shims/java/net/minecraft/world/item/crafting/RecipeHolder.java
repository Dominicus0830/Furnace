package net.minecraft.world.item.crafting;

/** 레시피 보관 값(Mojang 이름 shim). 바닐라 1.21.1 은 equals·hashCode·toString 을 직접 쓴다 — 번호(id)만으로 같음을 본다. */
record RecipeHolder {

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof RecipeHolder<?> holder && this.id.equals(holder.id);
    }

    @Override
    public int hashCode() { return this.id.hashCode(); }

    @Override
    public String toString() { return this.id.toString(); }
}
