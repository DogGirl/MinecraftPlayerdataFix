package com.msdoggirl.playerdatafix;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

public class PlayerData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<ItemStack> inventory;
    private final float health;
    private final int foodLevel;
    private final float saturation;
    private final BlockPos lastPosition; // Stores the player's last position
    private final ServerLevel lastDimension; // Stores the player's dimension

    public PlayerData(ServerPlayer player) {
        this.inventory = new ArrayList<>();
        player.getInventory().items.forEach(itemStack -> this.inventory.add(itemStack.copy()));
        this.health = player.getHealth();
        this.foodLevel = player.getFoodData().getFoodLevel();
        this.saturation = player.getFoodData().getSaturationLevel();
        this.lastPosition = player.blockPosition(); // Get the player's current position
        this.lastDimension = (ServerLevel) player.level(); // Get the player's current dimension
    }

    public void applyToPlayer(ServerPlayer player) {
        // Restore inventory
        for (int i = 0; i < this.inventory.size(); i++) {
            player.getInventory().items.set(i, this.inventory.get(i).copy());
        }

        // Restore health and food stats
        player.setHealth(this.health);
        player.getFoodData().setFoodLevel(this.foodLevel);
        player.getFoodData().setSaturation(this.saturation);

        // Teleport the player back to their last saved position
        if (lastDimension != null && lastPosition != null) {
            player.teleportTo(lastDimension, lastPosition.getX() + 0.5, lastPosition.getY(), lastPosition.getZ() + 0.5, player.getYRot(), player.getXRot());
        }

        // Example for restoring a mod capability (item handler)
        player.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            // Apply mod capability restoration logic if necessary
        });
    }

        public static void loadPlayerData(ServerPlayer player, Path playerDataPath) {
        File playerDataFile = playerDataPath.toFile();

        if (playerDataFile.exists() && playerDataFile.isFile()) {
            try (FileInputStream fileInputStream = new FileInputStream(playerDataFile)) {
                CompoundTag playerData = NbtIo.readCompressed(fileInputStream);
                player.load(playerData);
                LOGGER.info("Loaded player data for {} from {}", player.getName().getString(), playerDataPath);
            } catch (IOException e) {
                LOGGER.error("Failed to load player data for {}: {}", player.getName().getString(), e);
            }
        } else {
            LOGGER.warn("Player data file does not exist or is not a file for player: {}", player.getName().getString());
        }
    }
}