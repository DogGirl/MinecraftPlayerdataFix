package com.msdoggirl.playerdatafix;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber
public class PlayerDataCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, CompoundTag> backupDataMap = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("playerdata")
                .then(Commands.argument("player1", GameProfileArgument.gameProfile())
                    .then(Commands.argument("player2", StringArgumentType.string())
                        .executes(ctx -> execute(ctx))
                    )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        try {
            GameProfile p1Profile = GameProfileArgument.getGameProfiles(context, "player1").iterator().next();
            String p2Name = StringArgumentType.getString(context, "player2");

            ServerPlayer target = server.getPlayerList().getPlayer(p1Profile.getId());

            if (target == null) {
                context.getSource().sendFailure(Component.literal("Target player must be online."));
                return 0;
            }

            UUID uuid = target.getUUID();
    
            if (p2Name.equalsIgnoreCase("new")) {
                GameProfile newProfile = PlayerDataMod.loadFakePlayerProfile(server, UUID.fromString("00000000-0000-0000-0000-000000000000"));
                ServerPlayer newPlayer = PlayerData.loadOfflinePlayer(server, newProfile);
                CompoundTag sourceData = new CompoundTag();
                newPlayer.saveWithoutId(sourceData);
                PlayerData.applyFullPlayerData(target, sourceData);
                target.connection.disconnect(Component.literal("Player data reset in progress. Please rejoin the server."));
                return 1;
            }


            GameProfile p2Profile = server.getProfileCache().get(p2Name).orElse(null);
            if (p2Profile == null) {
                context.getSource().sendFailure(Component.literal("Player " + p2Name + " not found."));
                return 0;
            }

            ServerPlayer source = server.getPlayerList().getPlayer(p2Profile.getId());
            if (source == null) {
                source = PlayerData.loadOfflinePlayer(server, p2Profile);
                if (source == null) {
                    context.getSource().sendFailure(Component.literal("Cannot load source player."));
                    return 0;
                }
            }

            if (backupDataMap.containsKey(uuid)) {
                CompoundTag orig = backupDataMap.remove(uuid);
                PlayerData.applyFullPlayerData(target, orig);
                target.connection.disconnect(Component.literal("Restored original data. Please rejoin."));
                context.getSource().sendSuccess(
                    () -> Component.literal("Restored original data for " + target.getName().getString()),
                    true
                );
            } else {
                CompoundTag backup = new CompoundTag();
                target.saveWithoutId(backup);
                backupDataMap.put(uuid, backup);

                CompoundTag sourceData = new CompoundTag();
                source.saveWithoutId(sourceData);
                PlayerData.applyFullPlayerData(target, sourceData);
                target.connection.disconnect(Component.literal("Data copied. Please rejoin."));

                context.getSource().sendSuccess(
                    () -> Component.literal("Copied data from " + p2Profile.getName() +
                                            " to " + target.getName().getString()),
                    true
                );
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            LOGGER.error("playerdata command failed", e);
            return 0;
        }
    }
}