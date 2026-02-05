package com.msdoggirl.playerdatafix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;



@Mod("playerdatamod")
public class PlayerDataMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MOD_ID = "playerdatamod";

    public PlayerDataMod() {

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        generateAndSavePlayerData(server);
    }
    
    @SubscribeEvent
    public void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("PlayerDataMod: Setup method registered");
    }


    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        Player player = event.getEntity();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            Path playerDataPath = getPlayerDataPath(serverPlayer);
            

        File playerDataFolder = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
            System.err.println("Playerdata folder not found!");
            return;
        }

        Path DummyPlayerdataPath = serverPlayer.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("00000000-0000-0000-0000-000000000000.dat");
        File playerDataFile = new File(playerDataFolder, player.getStringUUID() + ".dat");
        if (!playerDataFile.exists()) {
            PlayerData.loadPlayerData(serverPlayer, DummyPlayerdataPath);
            System.out.println("Loading dummy player data from path:" + DummyPlayerdataPath);
        } else {
            PlayerData.loadPlayerData(serverPlayer, playerDataPath);
            System.out.println("Loading player data.");
            
        }
    }
}

    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            MinecraftServer server = serverPlayer.getServer();
            Path playerDataPath = getPlayerDataPath(serverPlayer);
            savePlayerData(serverPlayer, playerDataPath);
       }
    }

    private Path getPlayerDataPath(ServerPlayer player) {
        return player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
    }

    private void generateAndSavePlayerData(MinecraftServer server) {
        
        if (server == null) return;

        ServerLevel level = server.overworld(); 

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000000"); 
        GameProfile profile = new GameProfile(uuid, "MyCustomPlayer");

        ServerPlayer fakePlayer = FakePlayerFactory.get(level, profile);

        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Path playerDataPath = playerDataDir.resolve(uuid + ".dat");


        if (java.nio.file.Files.exists(playerDataPath)) {
            LOGGER.warn("Player data already exists for UUID {}, skipping save.", uuid);
            fakePlayer.remove(Entity.RemovalReason.DISCARDED);
            return;
        }

        savePlayerData(fakePlayer, playerDataPath);

        fakePlayer.remove(Entity.RemovalReason.DISCARDED);

        LOGGER.info("Generated playerdata file for {}", uuid);
    }

    // Helper method to load GameProfile from playerdata/{uuid}.dat
    public static GameProfile loadFakePlayerProfile(MinecraftServer server, UUID fakeUUID) {
        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Path playerDataPath = playerDataDir.resolve(fakeUUID + ".dat");

        if (!Files.exists(playerDataPath)) {
            LOGGER.warn("No playerdata file found for UUID {}", fakeUUID);
            return null;
        }

        CompoundTag playerNbt;
        try (FileInputStream fis = new FileInputStream(playerDataPath.toFile())) {
            playerNbt = NbtIo.readCompressed(fis);
        } catch (IOException e) {
            LOGGER.error("Failed to load playerdata for {}: {}", fakeUUID, e.getMessage());
            return null;
        }

        UUID nbtUUID = playerNbt.getUUID("UUID");
        String playerName = playerNbt.getString("Name");

        if (!nbtUUID.equals(fakeUUID)) {
            LOGGER.warn("UUID mismatch in playerdata for {}", fakeUUID);
            return null;
        }

        LOGGER.info("Loaded profile for {} (UUID: {})", playerName, fakeUUID);
        return new GameProfile(nbtUUID, playerName);
    }

    private void savePlayerData(ServerPlayer player, Path playerDataPath) {
        CompoundTag playerData = new CompoundTag();
        player.saveWithoutId(playerData);
        File playerDataFile = playerDataPath.toFile();

        try (FileOutputStream fileOutputStream = new FileOutputStream(playerDataFile)) {
            NbtIo.writeCompressed(playerData, fileOutputStream);
            LOGGER.info("Saved player data for {} to {}", player.getName().getString(), playerDataPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save player data for {}: {}", player.getName().getString(), e);
        }
    }

}