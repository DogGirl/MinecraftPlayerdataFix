package com.example.examplemod;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class PlayerDataCommand {
    private static final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("playerdata")
                .then(Commands.argument("player1", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                        .then(Commands.argument("player2", StringArgumentType.string())
                                .executes(PlayerDataCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            GameProfile player1Profile = net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(context, "player1").iterator().next();
            String player2Argument = StringArgumentType.getString(context, "player2");

            // Handle player1 (resolve online/offline)
            ServerPlayer player1 = server.getPlayerList().getPlayer(player1Profile.getId());
            ServerPlayer resolvedPlayer1 = player1;
            if (resolvedPlayer1 == null) {
                resolvedPlayer1 = loadOfflinePlayer(server, player1Profile);
                if (resolvedPlayer1 == null) {
                    context.getSource().sendFailure(Component.literal("Failed to load data for player 1 (offline)."));
                    return 0;
                }
            }

            if (player2Argument.equalsIgnoreCase("new")) {
                // Reset player1 to a blank state
                resetPlayerToDefault(resolvedPlayer1);
                final ServerPlayer finalPlayer1 = resolvedPlayer1; // Declare final for lambda
                context.getSource().sendSuccess(() -> Component.literal("Player data for " + finalPlayer1.getName().getString() + " has been reset to default state."), true);
                return 1;
            } else {
                GameProfile player2Profile = server.getProfileCache().get(player2Argument).orElse(null);
                if (player2Profile == null) {
                    context.getSource().sendFailure(Component.literal("Player 2 does not exist or is invalid."));
                    return 0;
                }

                ServerPlayer player2 = server.getPlayerList().getPlayer(player2Profile.getId());
                if (player2 == null) {
                    player2 = loadOfflinePlayer(server, player2Profile);
                    if (player2 == null) {
                        context.getSource().sendFailure(Component.literal("Failed to load data for player 2 (offline)."));
                        return 0;
                    }
                }

                final ServerPlayer finalPlayer1 = resolvedPlayer1; // Declare final for lambda
                final GameProfile finalPlayer2Profile = player2Profile; // Declare final for lambda
                UUID player1UUID = finalPlayer1.getUUID();

                // Check if player1's data is already stored
                if (playerDataMap.containsKey(player1UUID)) {
                    // Restore original data
                    PlayerData originalData = playerDataMap.remove(player1UUID);
                    originalData.applyToPlayer(finalPlayer1);
                    context.getSource().sendSuccess(() -> Component.literal("Player data for " + finalPlayer1.getName().getString() + " has been restored."), true);
                } else {
                    // Store player1's current data and copy player2's data to player1
                    PlayerData player1Data = new PlayerData(finalPlayer1);
                    playerDataMap.put(player1UUID, player1Data);

                    PlayerData player2Data = new PlayerData(player2);
                    player2Data.applyToPlayer(finalPlayer1);

                    context.getSource().sendSuccess(() -> Component.literal("Player data for " + finalPlayer1.getName().getString() + " has been copied from " + finalPlayer2Profile.getName() + "."), true);
                }

                return 1; // Command executed successfully
            }
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Failed to parse player arguments: " + e.getMessage()));
            return 0; // Command failed due to syntax error
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("An unexpected error occurred: " + e.getMessage()));
            return 0; // General error handling
        }
    }

    private static void resetPlayerToDefault(ServerPlayer player) {

            Level level = player.level();
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

            // Set to world's default gamemode
            if (!level.isClientSide) {
                GameType defaultGameType = level.getServer().getDefaultGameType();
                player.setGameMode(defaultGameType);
            }
            
        }


    private static ServerPlayer loadOfflinePlayer(MinecraftServer server, GameProfile profile) {
        try {
            PlayerList playerList = server.getPlayerList();
            ServerLevel overworld = server.overworld();
            ServerPlayer offlinePlayer = new ServerPlayer(server, overworld, profile);

            // Load the offline player's data from disk
            var playerData = playerList.load(offlinePlayer);
            if (playerData == null) {
                return null; // Return null if no data was found
            }

            offlinePlayer.load(playerData); // Apply the loaded data to the player
            return offlinePlayer;
        } catch (Exception e) {
            return null; // Return null if any error occurs
        }
    }

}


