package com.mirrorbuild.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * All /mirror sub-commands.
 * Each handler is wrapped in try-catch so a bad argument can never crash the server.
 */
public class MirrorCommands {

    static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                         MirrorGameManager gameManager) {

        dispatcher.register(
            literal("mirror")
                .requires(src -> src.hasPermissionLevel(2)) // operator only

                // ── /mirror help ──────────────────────────────────────────────────
                .then(literal("help").executes(ctx -> cmdHelp(ctx)))

                // ── /mirror wand ──────────────────────────────────────────────────
                .then(literal("wand").executes(ctx -> cmdWand(ctx)))

                // ── /mirror pos1 <x> <y> <z> ─────────────────────────────────────
                .then(literal("pos1")
                    .then(argument("x", IntegerArgumentType.integer())
                    .then(argument("y", IntegerArgumentType.integer(-64, 320))
                    .then(argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> cmdPos(ctx, 1, gameManager))))))

                // ── /mirror pos2 <x> <y> <z> ─────────────────────────────────────
                .then(literal("pos2")
                    .then(argument("x", IntegerArgumentType.integer())
                    .then(argument("y", IntegerArgumentType.integer(-64, 320))
                    .then(argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> cmdPos(ctx, 2, gameManager))))))

                // ── /mirror setarea ───────────────────────────────────────────────
                .then(literal("setarea").executes(ctx -> cmdSetArea(ctx, gameManager)))

                // ── /mirror cleararea ─────────────────────────────────────────────
                .then(literal("cleararea").executes(ctx -> cmdClearArea(ctx, gameManager)))

                // ── /mirror showbounds ────────────────────────────────────────────
                .then(literal("showbounds").executes(ctx -> cmdShowBounds(ctx, gameManager)))

                // ── /mirror axis x|z ──────────────────────────────────────────────
                .then(literal("axis")
                    .then(argument("axis", StringArgumentType.word())
                        .executes(ctx -> cmdAxis(ctx, gameManager))))

                // ── /mirror enable ────────────────────────────────────────────────
                .then(literal("enable").executes(ctx -> cmdEnable(ctx, gameManager, true)))

                // ── /mirror disable ───────────────────────────────────────────────
                .then(literal("disable").executes(ctx -> cmdEnable(ctx, gameManager, false)))

                // ── /mirror status ────────────────────────────────────────────────
                .then(literal("status").executes(ctx -> cmdStatus(ctx, gameManager)))

                // ── /mirror reload ────────────────────────────────────────────────
                .then(literal("reload").executes(ctx -> cmdReload(ctx, gameManager)))
        );
    }

    // ------------------------------------------------------------------ helpers

    private static void reply(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
    }

    private static void replyError(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendError(Text.literal(msg));
    }

    // ------------------------------------------------------------------ /mirror help

    private static int cmdHelp(CommandContext<ServerCommandSource> ctx) {
        try {
            reply(ctx, "§e=== Mirror Build Setup Guide ===");
            reply(ctx, "§71. §e/mirror wand §7— Get the selection wand (wooden axe)");
            reply(ctx, "§72. §7Left-click a block for §ePos1§7, right-click for §ePos2");
            reply(ctx, "   §7Or use: §e/mirror pos1 <x> <y> <z>§7 and §e/mirror pos2 <x> <y> <z>");
            reply(ctx, "§73. §e/mirror setarea §7— Confirm the arena (min 3x3 footprint)");
            reply(ctx, "§74. §e/mirror axis x|z §7— Choose mirror axis (default: Z)");
            reply(ctx, "   §7axis=Z mirrors East-West  |  axis=X mirrors North-South");
            reply(ctx, "§75. §e/mirror enable §7— Start mirroring!");
            reply(ctx, "§7Other commands:");
            reply(ctx, "  §e/mirror status   §7— Show arena info");
            reply(ctx, "  §e/mirror showbounds §7— Visualise arena with particles");
            reply(ctx, "  §e/mirror cleararea §7— Reset everything");
            reply(ctx, "  §e/mirror reload   §7— Safe reset without crashing");
            reply(ctx, "  §e/mirror disable  §7— Pause mirroring");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdHelp error: {}", e.getMessage(), e);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror wand

    private static int cmdWand(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayer();
            if (player == null) { replyError(ctx, "Must be run by a player."); return 0; }

            ItemStack wand = new ItemStack(Items.WOODEN_AXE);
            // Use displayName via item NBT-less approach for 1.21
            player.getInventory().offerOrDrop(wand);
            reply(ctx, "§a[Mirror] §7Wand given! §eLeft-click §7= Pos1, §eRight-click §7= Pos2.");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdWand error: {}", e.getMessage(), e);
            replyError(ctx, "Error giving wand: " + e.getMessage());
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror pos1|pos2

    private static int cmdPos(CommandContext<ServerCommandSource> ctx, int which,
                               MirrorGameManager mgr) {
        try {
            int x = IntegerArgumentType.getInteger(ctx, "x");
            int y = IntegerArgumentType.getInteger(ctx, "y");
            int z = IntegerArgumentType.getInteger(ctx, "z");

            // Validate world border (rough limit)
            if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000) {
                replyError(ctx, "Coordinates out of world range.");
                return 0;
            }

            BlockPos pos = new BlockPos(x, y, z);
            if (which == 1) {
                mgr.getArena().setSelPos1(pos);
                reply(ctx, "§a[Mirror] §7Pos1 set to §f" + x + ", " + y + ", " + z);
            } else {
                mgr.getArena().setSelPos2(pos);
                reply(ctx, "§a[Mirror] §7Pos2 set to §f" + x + ", " + y + ", " + z);
            }
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdPos error: {}", e.getMessage(), e);
            replyError(ctx, "Error setting position: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror setarea

    private static int cmdSetArea(CommandContext<ServerCommandSource> ctx, MirrorGameManager mgr) {
        try {
            MirrorArena arena = mgr.getArena();
            if (!arena.hasSelection()) {
                replyError(ctx, "No selection! Use the wand or /mirror pos1 / pos2 first.");
                return 0;
            }
            boolean ok = arena.applySelection();
            if (!ok) {
                replyError(ctx, "Selection is too small. Arena must be at least 3 blocks wide on both X and Z axes.");
                return 0;
            }
            reply(ctx, "§a[Mirror] §7Arena set! Use §e/mirror enable §7to start mirroring.");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdSetArea error: {}", e.getMessage(), e);
            replyError(ctx, "Error setting area: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror cleararea

    private static int cmdClearArea(CommandContext<ServerCommandSource> ctx, MirrorGameManager mgr) {
        try {
            mgr.getArena().clearSelection();
            reply(ctx, "§a[Mirror] §7Arena cleared. Mirroring disabled.");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdClearArea error: {}", e.getMessage(), e);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror showbounds

    private static int cmdShowBounds(CommandContext<ServerCommandSource> ctx, MirrorGameManager mgr) {
        try {
            MirrorArena arena = mgr.getArena();
            if (!arena.hasArena() && !arena.hasSelection()) {
                replyError(ctx, "No area selected. Use the wand first.");
                return 0;
            }

            ServerPlayerEntity player = ctx.getSource().getPlayer();
            if (player == null) {
                replyError(ctx, "Must be run by a player.");
                return 0;
            }

            // Show bounds in chat as fallback
            BlockPos p1 = arena.hasArena() ? arena.getArenaMin() : arena.getSelPos1();
            BlockPos p2 = arena.hasArena() ? arena.getArenaMax() : arena.getSelPos2();
            if (p1 != null) reply(ctx, "§7Corner 1: §f" + p1.getX() + ", " + p1.getY() + ", " + p1.getZ());
            if (p2 != null) reply(ctx, "§7Corner 2: §f" + p2.getX() + ", " + p2.getY() + ", " + p2.getZ());

            // Try particle display
            try {
                var world = player.getServerWorld();
                if (world != null && p1 != null && p2 != null) {
                    int minX = Math.min(p1.getX(), p2.getX());
                    int minY = Math.min(p1.getY(), p2.getY());
                    int minZ = Math.min(p1.getZ(), p2.getZ());
                    int maxX = Math.max(p1.getX(), p2.getX());
                    int maxY = Math.max(p1.getY(), p2.getY());
                    int maxZ = Math.max(p1.getZ(), p2.getZ());

                    int stepX = Math.max(1, (maxX - minX) / 30);
                    int stepZ = Math.max(1, (maxZ - minZ) / 30);
                    for (int x = minX; x <= maxX; x += stepX) {
                        for (int z = minZ; z <= maxZ; z += stepZ) {
                            world.spawnParticles(player,
                                net.minecraft.particle.ParticleTypes.END_ROD,
                                true, x + 0.5, minY, z + 0.5, 1, 0, 0, 0, 0);
                            world.spawnParticles(player,
                                net.minecraft.particle.ParticleTypes.END_ROD,
                                true, x + 0.5, maxY + 1, z + 0.5, 1, 0, 0, 0, 0);
                        }
                    }
                    reply(ctx, "§a[Mirror] §7Particles shown (look at floor/ceiling of arena).");
                }
            } catch (Exception pe) {
                MirrorBuildMod.LOGGER.warn("[MirrorBuild] Particle fallback: {}", pe.getMessage());
                reply(ctx, "§e[Mirror] §7Particles unavailable — see coordinates above.");
            }

        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdShowBounds error: {}", e.getMessage(), e);
            replyError(ctx, "Error: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror axis

    private static int cmdAxis(CommandContext<ServerCommandSource> ctx, MirrorGameManager mgr) {
        try {
            String axisStr = StringArgumentType.getString(ctx, "axis").toUpperCase().trim();
            if (!axisStr.equals("X") && !axisStr.equals("Z")) {
                replyError(ctx, "Invalid axis. Use 'x' or 'z'. (axis=Z: East-West mirror, axis=X: North-South mirror)");
                return 0;
            }
            mgr.getArena().setAxis(MirrorArena.Axis.valueOf(axisStr));
            reply(ctx, "§a[Mirror] §7Axis set to §e" + axisStr + "§7. "
                + (axisStr.equals("Z") ? "(mirrors East↔West)" : "(mirrors North↔South)"));
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdAxis error: {}", e.getMessage(), e);
            replyError(ctx, "Error setting axis: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror enable|disable

    private static int cmdEnable(CommandContext<ServerCommandSource> ctx,
                                  MirrorGameManager mgr, boolean enable) {
        try {
            if (enable && !mgr.getArena().hasArena()) {
                replyError(ctx, "No arena set! Run /mirror setarea first.");
                return 0;
            }
            mgr.getArena().setEnabled(enable);
            reply(ctx, "§a[Mirror] §7Mirroring is now §"
                + (enable ? "aENABLED" : "cDISABLED") + "§7.");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdEnable error: {}", e.getMessage(), e);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror status

    private static int cmdStatus(CommandContext<ServerCommandSource> ctx, MirrorGameManager mgr) {
        try {
            String[] lines = mgr.getArena().getStatusText().split("\n");
            for (String line : lines) reply(ctx, line);
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdStatus error: {}", e.getMessage(), e);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /mirror reload

    private static int cmdReload(CommandContext<ServerCommandSource> ctx, MirrorGameManager mgr) {
        try {
            mgr.reload();
            reply(ctx, "§a[Mirror] §7Reloaded safely. All state cleared.");
        } catch (Exception e) {
            MirrorBuildMod.LOGGER.error("[MirrorBuild] cmdReload error: {}", e.getMessage(), e);
            replyError(ctx, "Reload encountered an error (see server log), but server is stable.");
        }
        return 1;
    }
}
