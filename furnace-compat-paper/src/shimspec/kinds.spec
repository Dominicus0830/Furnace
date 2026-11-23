# 블록·아이템 종류 클래스 계층(T-231, 2026-11-23). 종류 표 kind-classes-<판>.txt(바닐라에서 뽑음 — tools/vanilla-extract 의 kind-classes)의 클래스는
# 멤버 없는 class 줄로 저절로 더해지고(instanceof·상속 사슬용 선언), 여기에는 멤버를 잇는 클래스만 적는다.
import org.domi.furnace.compat.nms.NmsBlocks
import org.domi.furnace.compat.nms.NmsBlockEntityClasses

class net.minecraft.world.level.block.CropBlock
  MAX_AGE = 7
  AGE = net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7
class net.minecraft.world.level.block.StemBlock
  MAX_AGE = 7
  AGE = net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7
# 블록 엔티티가 있는 블록 전부(상자·표지판·침대…): 그 상태의 블록 엔티티 종류로 만든다(서버 블록 엔티티 표)
class net.minecraft.world.level.block.EntityBlock
  newBlockEntity -> @each(NmsBlockEntityClasses.newBlockEntity) read=any
# 색유리·색유리판: 이름 앞의 색
class net.minecraft.world.level.block.BeaconBeamBlock
  getColor -> @each(NmsBlocks.beaconColor) pure
class net.minecraft.world.item.BlockItem
  getBlock -> NmsBlocks.blockOfItem read=any
  updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;) -> @hand read=any   # Axiom 블록 놓기(NMS shim 5d) — 대상이 그 칸 주인 스레드 규칙을 따른다
