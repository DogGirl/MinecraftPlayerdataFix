package com.msdoggirl.playerdatafix;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

public class PlayerData {

    private static final Logger LOGGER = LogUtils.getLogger();

        public static void applyFullPlayerData(ServerPlayer player, CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                LOGGER.warn("applyFullPlayerData called with null or empty tag");
                return;
            }
            player.readAdditionalSaveData(tag);
            player.addAdditionalSaveData(tag);
            player.load(tag);
            player.saveWithoutId(tag);

            player.removeAllEffects();
            if (tag.contains("ActiveEffects", Tag.TAG_LIST)) {
                player.load(tag);
            }

            LOGGER.info("Applied full player data for {}", player.getName().getString());
        }

        public static void loadPlayerData(ServerPlayer player, Path playerDataPath) {
            File playerDataFile = playerDataPath.toFile();

            if (playerDataFile.exists() && playerDataFile.isFile()) {
                try (FileInputStream fileInputStream = new FileInputStream(playerDataFile)) {
                    CompoundTag playerData = NbtIo.readCompressed(fileInputStream);
                    applyFullPlayerData(player, playerData);
                    LOGGER.info("Loaded player data for {} from {}", player.getName().getString(), playerDataPath);
                } catch (IOException e) {
                    LOGGER.error("Failed to load player data for {}: {}", player.getName().getString(), e);
                }
            } else {
                LOGGER.warn("Player data file does not exist or is not a file for player: {}", player.getName().getString());
            }
        }

    public static ServerPlayer loadOfflinePlayer(MinecraftServer server, GameProfile profile) {
        try {
            PlayerList playerList = server.getPlayerList();
            ServerLevel overworld = server.overworld();
            ServerPlayer offlinePlayer = new ServerPlayer(server, overworld, profile);

            // Load the offline player's data from disk
            var playerData = playerList.load(offlinePlayer);
            if (playerData == null) {
                return null;
            }

            offlinePlayer.load(playerData);
            return offlinePlayer;
        } catch (Exception e) {
            return null;
        }
    }

    public static CompoundTag loadTagFromFile(Path path) {
        if (!java.nio.file.Files.exists(path)) {
            LOGGER.warn("Player data file not found: {}", path);
            return null;
        }
        try (FileInputStream in = new FileInputStream(path.toFile())) {
            return NbtIo.readCompressed(in);
        } catch (IOException e) {
            LOGGER.error("Failed to read NBT from {}", path, e);
            return null;
        }
    }

    
}