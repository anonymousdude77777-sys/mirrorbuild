package com.mirrorbuild.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MirrorBuildMod implements ModInitializer {

    public static final String MOD_ID = "mirrorbuild";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MirrorGameManager gameManager;

    @Override
    public void onInitialize() {
        LOGGER.info("[MirrorBuild] Initializing Mirror Building Combat mod...");

        try {
            gameManager = new MirrorGameManager();

            // Register commands
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                try {
                    MirrorCommands.register(dispatcher, gameManager);
                } catch (Exception e) {
                    LOGGER.error("[MirrorBuild] Failed to register commands: {}", e.getMessage(), e);
                }
            });

            // Register block break event
            PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
                try {
                    if (gameManager != null) {
                        return gameManager.handleBlockBreak(world, player, pos, state);
                    }
                } catch (Exception e) {
                    LOGGER.error("[MirrorBuild] Error in block break handler: {}", e.getMessage(), e);
                }
                return true;
            });

            // Register block place / wand right-click via UseBlockCallback
            UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
                try {
                    if (gameManager != null) {
                        return gameManager.handleUseBlock(player, world, hand, hitResult);
                    }
                } catch (Exception e) {
                    LOGGER.error("[MirrorBuild] Error in use-block handler: {}", e.getMessage(), e);
                }
                return net.minecraft.util.ActionResult.PASS;
            });

            // Register wand left-click (attack block)
            AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
                try {
                    if (gameManager != null) {
                        return gameManager.handleAttackBlock(player, world, hand, pos, direction);
                    }
                } catch (Exception e) {
                    LOGGER.error("[MirrorBuild] Error in attack-block handler: {}", e.getMessage(), e);
                }
                return net.minecraft.util.ActionResult.PASS;
            });

            // Clean up on player disconnect
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
                try {
                    if (gameManager != null && handler != null && handler.player != null) {
                        gameManager.handlePlayerDisconnect(handler.player);
                    }
                } catch (Exception e) {
                    LOGGER.error("[MirrorBuild] Error handling player disconnect: {}", e.getMessage(), e);
                }
            });

            LOGGER.info("[MirrorBuild] Initialization complete.");

        } catch (Exception e) {
            LOGGER.error("[MirrorBuild] CRITICAL: Failed to initialize mod: {}", e.getMessage(), e);
            // Don't rethrow — we log and degrade gracefully so the server still starts
        }
    }

    public static MirrorGameManager getGameManager() {
        return gameManager;
    }
}
