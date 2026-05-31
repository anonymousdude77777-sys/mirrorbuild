package com.mirrorbuild.mod;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on BlockItem.place() so we can detect when any block is successfully placed
 * and immediately mirror it to the other side of the arena.
 *
 * This is the ONLY Mixin in the mod.  It is kept minimal:
 *  - injects at RETURN with cancellable=false (read-only, cannot crash)
 *  - all logic is in try-catch in MirrorGameManager
 */
@Mixin(BlockItem.class)
public class MirrorPlaceMixin {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void onBlockPlaced(ItemPlacementContext context, CallbackInfoReturnable<?> cir) {
        try {
            if (context == null) return;
            var world = context.getWorld();
            if (world == null || world.isClient()) return;
            if (!(world instanceof ServerWorld sw)) return;

            var hitResult = context.getSide();
            // The placement position is the block that was just placed
            BlockPos pos = context.getBlockPos();
            if (pos == null) return;

            BlockState placed = sw.getBlockState(pos);
            if (placed == null || placed.isAir()) return;

            MirrorGameManager mgr = MirrorBuildMod.getGameManager();
            if (mgr != null) {
                mgr.handleBlockPlaced(sw, pos, placed);
            }
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] MirrorPlaceMixin error: {}", e.getMessage(), e);
            // Never rethrow — the original placement already happened; mirroring is best-effort
        }
    }
}
