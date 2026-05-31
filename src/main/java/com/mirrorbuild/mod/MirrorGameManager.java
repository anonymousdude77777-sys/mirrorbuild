package com.mirrorbuild.mod;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for the Mirror Building Combat minigame.
 *
 * Thread-safety: all block mutations are guarded to run on the server thread.
 * Re-entrancy: a "currently mirroring" flag prevents infinite mirror loops.
 */
public class MirrorGameManager {

    private final MirrorArena arena = new MirrorArena();

    // Players currently holding the selection wand (UUID set)
    private final Set<UUID> wandHolders = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Re-entrancy guard: prevents the mirrored placement from triggering another mirror
    private final ThreadLocal<Boolean> isMirroring = ThreadLocal.withInitial(() -> false);

    // Tracks UUIDs of disconnected players so we don't act on stale references
    private final Set<UUID> disconnectedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public MirrorArena getArena() {
        return arena;
    }

    // -------------------------------------------------------
    // Wand handling
    // -------------------------------------------------------

    private boolean isHoldingWand(PlayerEntity player) {
        if (player == null) return false;
        try {
            ItemStack main = player.getMainHandStack();
            return main != null && main.isOf(Items.WOODEN_AXE);
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] isHoldingWand error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Left-click with wand → set pos1.
     */
    public ActionResult handleAttackBlock(PlayerEntity player, World world,
                                          Hand hand, BlockPos pos, Direction direction) {
        try {
            if (world == null || world.isClient() || player == null || pos == null) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (isHoldingWand(sp)) {
                arena.setSelPos1(pos);
                sp.sendMessage(
                    net.minecraft.text.Text.literal("§a[Mirror] §7Pos1 set to §f"
                        + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                    false);
                showSelectionParticles(sp);
                return ActionResult.SUCCESS;
            }
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] handleAttackBlock error: {}", e.getMessage(), e);
        }
        return ActionResult.PASS;
    }

    /**
     * Right-click with wand → set pos2.
     * Right-click without wand (normal block use) → handled for block placement mirroring.
     */
    public ActionResult handleUseBlock(PlayerEntity player, World world,
                                       Hand hand, BlockHitResult hitResult) {
        try {
            if (world == null || world.isClient() || player == null || hitResult == null) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            // Wand right-click
            if (isHoldingWand(sp)) {
                BlockPos pos = hitResult.getBlockPos();
                if (pos == null) return ActionResult.PASS;
                arena.setSelPos2(pos);
                sp.sendMessage(
                    net.minecraft.text.Text.literal("§a[Mirror] §7Pos2 set to §f"
                        + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                    false);
                showSelectionParticles(sp);
                return ActionResult.SUCCESS;
            }

            // Not wand — block placement mirroring is handled via a different hook
            // (see scheduleBlockPlaceMirror called from the break/place flow below)

        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] handleUseBlock error: {}", e.getMessage(), e);
        }
        return ActionResult.PASS;
    }

    // -------------------------------------------------------
    // Block break mirroring
    // -------------------------------------------------------

    /**
     * Called BEFORE a block is broken. If mirroring is active and the block is in
     * the arena, we schedule the mirror-side break for after the original break completes.
     *
     * Returns false to cancel the original break (never done here — we always let it proceed).
     */
    public boolean handleBlockBreak(World world, PlayerEntity player,
                                    BlockPos pos, BlockState state) {
        try {
            if (world == null || world.isClient() || player == null || pos == null || state == null) {
                return true;
            }
            if (!(world instanceof ServerWorld sw)) return true;
            if (!arena.isEnabled() || !arena.hasArena()) return true;
            if (isMirroring.get()) return true; // re-entrancy guard

            BlockPos mirroredPos = arena.mirror(pos);
            if (mirroredPos == null) return true;

            // Schedule on the same server tick (after original break)
            sw.getServer().execute(() -> {
                try {
                    if (isMirroring.get()) return;
                    isMirroring.set(true);
                    try {
                        if (sw.isChunkLoaded(mirroredPos.getX() >> 4, mirroredPos.getZ() >> 4)) {
                            BlockState mirrorState = sw.getBlockState(mirroredPos);
                            if (mirrorState != null && !mirrorState.isAir()) {
                                sw.breakBlock(mirroredPos, false);
                            }
                        }
                    } finally {
                        isMirroring.set(false);
                    }
                } catch (Exception e) {
                    isMirroring.set(false);
                    MirrorBuildMod.LOGGER.error(
                        "[MirrorBuild] Mirror break failed at {}: {}", mirroredPos, e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] handleBlockBreak error: {}", e.getMessage(), e);
        }
        return true; // always allow original break
    }

    /**
     * Called by the AfterBlockPlaced event logic (wired in MirrorPlaceHook).
     * Mirrors a newly placed block to the opposite side.
     */
    public void handleBlockPlaced(ServerWorld world, BlockPos pos, BlockState state) {
        try {
            if (world == null || pos == null || state == null) return;
            if (!arena.isEnabled() || !arena.hasArena()) return;
            if (isMirroring.get()) return;

            BlockPos mirroredPos = arena.mirror(pos);
            if (mirroredPos == null) return;

            world.getServer().execute(() -> {
                try {
                    if (isMirroring.get()) return;
                    isMirroring.set(true);
                    try {
                        if (!world.isChunkLoaded(mirroredPos.getX() >> 4, mirroredPos.getZ() >> 4)) return;
                        // Mirror the block state (no need to mirror directional properties for basic gameplay)
                        world.setBlockState(mirroredPos, state, 3);
                    } finally {
                        isMirroring.set(false);
                    }
                } catch (Exception e) {
                    isMirroring.set(false);
                    MirrorBuildMod.LOGGER.error(
                        "[MirrorBuild] Mirror place failed at {}: {}", mirroredPos, e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] handleBlockPlaced error: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------
    // Player disconnect
    // -------------------------------------------------------

    public void handlePlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) return;
        try {
            disconnectedPlayers.add(player.getUuid());
            wandHolders.remove(player.getUuid());
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] handlePlayerDisconnect error: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------
    // Particle selection outline
    // -------------------------------------------------------

    private void showSelectionParticles(ServerPlayerEntity player) {
        try {
            if (player == null || !arena.hasSelection()) return;
            ServerWorld world = player.getServerWorld();
            if (world == null) return;

            BlockPos p1 = arena.getSelPos1();
            BlockPos p2 = arena.getSelPos2();
            if (p1 == null || p2 == null) return;

            int minX = Math.min(p1.getX(), p2.getX());
            int minY = Math.min(p1.getY(), p2.getY());
            int minZ = Math.min(p1.getZ(), p2.getZ());
            int maxX = Math.max(p1.getX(), p2.getX());
            int maxY = Math.max(p1.getY(), p2.getY());
            int maxZ = Math.max(p1.getZ(), p2.getZ());

            // Clamp particle count for very large selections
            int stepX = Math.max(1, (maxX - minX) / 20);
            int stepY = Math.max(1, (maxY - minY) / 20);
            int stepZ = Math.max(1, (maxZ - minZ) / 20);

            net.minecraft.particle.ParticleTypes types = null; // static access below

            // Draw 12 edges of the bounding box
            for (int x = minX; x <= maxX; x += stepX) {
                spawnParticle(world, player, x + 0.5, minY + 0.5, minZ + 0.5);
                spawnParticle(world, player, x + 0.5, maxY + 1.5, minZ + 0.5);
                spawnParticle(world, player, x + 0.5, minY + 0.5, maxZ + 1.5);
                spawnParticle(world, player, x + 0.5, maxY + 1.5, maxZ + 1.5);
            }
            for (int y = minY; y <= maxY; y += stepY) {
                spawnParticle(world, player, minX + 0.5, y + 0.5, minZ + 0.5);
                spawnParticle(world, player, maxX + 1.5, y + 0.5, minZ + 0.5);
                spawnParticle(world, player, minX + 0.5, y + 0.5, maxZ + 1.5);
                spawnParticle(world, player, maxX + 1.5, y + 0.5, maxZ + 1.5);
            }
            for (int z = minZ; z <= maxZ; z += stepZ) {
                spawnParticle(world, player, minX + 0.5, minY + 0.5, z + 0.5);
                spawnParticle(world, player, maxX + 1.5, minY + 0.5, z + 0.5);
                spawnParticle(world, player, minX + 0.5, maxY + 1.5, z + 0.5);
                spawnParticle(world, player, maxX + 1.5, maxY + 1.5, z + 0.5);
            }

        } catch (Exception e) {
            MirrorBuildMod.LOGGER.warn("[MirrorBuild] Particle display failed (non-fatal): {}", e.getMessage());
            // Non-fatal; game continues without particles
        }
    }

    private void spawnParticle(ServerWorld world, ServerPlayerEntity player,
                               double x, double y, double z) {
        world.spawnParticles(player,
            net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
            true, x, y, z, 1, 0, 0, 0, 0);
    }

    // -------------------------------------------------------
    // Reload / reset
    // -------------------------------------------------------

    /**
     * Safe reload — clears everything without crashing.
     */
    public void reload() {
        try {
            arena.clearSelection();
            wandHolders.clear();
            disconnectedPlayers.clear();
            isMirroring.set(false);
            MirrorBuildMod.LOGGER.info("[MirrorBuild] Reloaded successfully.");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] Error during reload: {}", e.getMessage(), e);
        }
    }
}
