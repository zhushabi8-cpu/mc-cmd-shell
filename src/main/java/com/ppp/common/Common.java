package com.ppp.common;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ppp.util.PaginationManager;
import com.ppp.util.ServerLanguageManage;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class Common {

    public static boolean checkServerPermission(ServerCommandSource source, ServerPlayerEntity player) {
        if (player == null) return true;
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            if (!player.hasPermissionLevel(4)) {
                source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.op_only"));
                return false;
            }
        }
        return true;
    }

    public static int executeShellHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!checkServerPermission(source, player)) return 0;

        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.title"));
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.start"));
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.run"));
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.stop"));
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.clear"));
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.page"));
        source.sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.shell.help.confirm"));
        return 1;
    }

    public static int executePage(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.player_only"));
            return 0;
        }
        if (!checkServerPermission(source, player)) return 0;

        int page = IntegerArgumentType.getInteger(context, "page");
        String title = ServerLanguageManage.getText("text.mc-cmd-shell.cmd.output_prefix").getString();
        PaginationManager.sendPage(source, player.getUuid(), page, title);
        return 1;
    }
}