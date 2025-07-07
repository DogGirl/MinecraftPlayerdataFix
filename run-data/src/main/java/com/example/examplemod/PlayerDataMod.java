package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;

import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.nio.file.Files;



@Mod("playerdatamod")
public class PlayerDataMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MOD_ID = "playerdatamod";

    // Create the Deferred Register for items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    // Register the Reset Item
    public static final RegistryObject<Item> RESET_ITEM = ITEMS.register("reset_item", 
        () -> new Item(new Item.Properties()));

    public PlayerDataMod() {
        // Register the item with the event bus
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());

        // Register the event listener
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Level level = player.level();

        // Check if the player has the reset item in their inventory
        if (player.getInventory().contains(new ItemStack(RESET_ITEM.get()))) {
            // Clear inventory
            player.getInventory().clearContent();

            // Reset experience
            player.giveExperienceLevels(-player.experienceLevel);
            player.experienceProgress = 0;

            // Reset health
            player.setHealth(player.getMaxHealth());

            // Reset hunger
            player.getFoodData().setFoodLevel(20); // Full hunger
            player.getFoodData().setSaturation(5.0F); // Full saturation

            // Teleport to world spawn
            player.teleportTo(level.getSharedSpawnPos().getX(),
                              level.getSharedSpawnPos().getY(),
                              level.getSharedSpawnPos().getZ());

            // Set to world's default gamemode
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                GameType defaultGameType = level.getServer().getDefaultGameType();
                serverPlayer.setGameMode(defaultGameType);
            }
            
        }
    }
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        // Locate the world's playerdata folder
        Path playerDataFolderPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);

        // Ensure the playerdata folder exists
        File playerDataFolder = playerDataFolderPath.toFile();
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }

        // Define the target file in the playerdata folder
        File targetFile = new File(playerDataFolder, "00000-0000-0000-0000-000000000000.dat");

        // Get the file from your mod's resources folder
        try (InputStream input = PlayerDataMod.class.getResourceAsStream("/assets/playerdatamod/world/00000-0000-0000-0000-000000000000.dat")) {
            if (input != null) {
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log any errors
        }
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
            loadPlayerData(serverPlayer, playerDataPath);
            
        File playerDataFolder = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
            System.err.println("Playerdata folder not found!");
            return;
        }

        Path DummyPlayerdataPath = serverPlayer.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("00000-0000-0000-0000-000000000000.dat");
        File playerDataFile = new File(playerDataFolder, player.getStringUUID() + ".dat");
        if (!playerDataFile.exists()) {
            loadPlayerData(serverPlayer, DummyPlayerdataPath);
            serverPlayer.getPersistentData().putBoolean("RunLoginCode", true); // Mark to run the code on login
        }
    }
}

@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    Player player = event.getEntity();
    MinecraftServer server = player.getServer();
    if (server == null) return;
    if (player instanceof ServerPlayer) {
        ServerPlayer serverPlayer = (ServerPlayer) player;

        // Check if the condition was met during loading
        if (serverPlayer.getPersistentData().getBoolean("RunLoginCode")) {
            String commandToCheck = "vampirism level none";

            // Check if the command exists
            boolean commandExists = server.getCommands().getDispatcher().getRoot().getChildren().stream()
                .anyMatch(node -> node.getName().equalsIgnoreCase(commandToCheck.split(" ")[0]));

            if (commandExists) {
                // Run the command if it exists
                CommandSourceStack source = server.createCommandSourceStack();
                ParseResults<CommandSourceStack> parseResults = server.getCommands().getDispatcher().parse(commandToCheck, source);
                server.getCommands().performCommand(parseResults, commandToCheck);
            } else {
                System.err.println("Command does not exist: " + commandToCheck);
            }
        }
    }
}



    @SubscribeEvent
    public void onPlayerSave(PlayerEvent.SaveToFile event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            Path playerDataPath = getPlayerDataPath(serverPlayer);
            savePlayerData(serverPlayer, playerDataPath);
        }
    }
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        resetPlayerData(server);
    }

    private void resetPlayerData(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            Path levelDatPath = world.getServer().getWorldPath(LevelResource.ROOT).resolve("level.dat");
            File levelDatFile = levelDatPath.toFile();

            if (levelDatFile.exists() && levelDatFile.isFile()) {
                try (FileInputStream fileInputStream = new FileInputStream(levelDatFile)) {
                    CompoundTag levelDat = NbtIo.readCompressed(fileInputStream);
                    CompoundTag dataTag = levelDat.getCompound("Data");

                    // Reset contents of the data/player tag to the state of a new world
                    if (dataTag.contains("Player")) {
                        CompoundTag playerTag = new CompoundTag();
                        playerTag.putLong("UUIDMost", 0L);
                        playerTag.putLong("UUIDLeast", 0L);
                        playerTag.putString("PlayerName", "");
                        playerTag.putInt("XpTotal", 0);
                        playerTag.putInt("XpLevel", 0);
                        playerTag.putInt("XpSeed", 0);
                        playerTag.putInt("Score", 0);
                        dataTag.put("Player", playerTag);
                        LOGGER.info("Reset contents of 'Player' tag for world {}.", world.dimension().location());
                    }

                    // Write the updated level.dat file
                    try (FileOutputStream fileOutputStream = new FileOutputStream(levelDatFile)) {
                        NbtIo.writeCompressed(levelDat, fileOutputStream);
                        LOGGER.info("Updated level.dat file for world {}.", world.dimension().location());
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to modify level.dat: " + levelDatFile.getName(), e);
                }
            } else {
                LOGGER.warn("level.dat file does not exist or is not a file for world: {}", world.dimension().location());
            }
        }
    }

    private Path getPlayerDataPath(ServerPlayer player) {
        return player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
    }

    private static void loadPlayerData(ServerPlayer player, Path playerDataPath) {
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
