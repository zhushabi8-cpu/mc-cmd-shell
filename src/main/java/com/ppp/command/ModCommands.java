package com.ppp.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ppp.util.ServerLanguageManage;
import com.ppp.util.OSDetector;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            boolean isWindows = OSDetector.isWindows();

            if (isWindows) {
                dispatcher.register(literal("cmd")
                        .then(literal("start")
                                .executes(CommandHandler::executeStart))
                        .then(literal("run")
                                .then(argument("command", StringArgumentType.greedyString())
                                        .executes(CommandHandler::executeRun)))
                        .then(literal("stop")
                                .executes(CommandHandler::executeStop))
                        .then(literal("clear")
                                .executes(CommandHandler::executeClear))
                );
                dispatcher.register(literal("cmdconfirm")
                        .executes(CommandHandler::executeConfirm));
            }

            var shellBuilder = literal("shell");
            shellBuilder.then(literal("page")
                    .then(argument("page", IntegerArgumentType.integer(1))
                            .executes(CommandHandler::executePage)));
            shellBuilder.then(literal("help")
                    .executes(CommandHandler::executeShellHelp));
            dispatcher.register(shellBuilder);

            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                var shellLangBuilder = literal("shell");
                shellLangBuilder.then(literal("language")
                        .then(literal("set")
                                .then(argument("lang", StringArgumentType.word())
                                        .executes(ModCommands::executeSetLanguage))));
                dispatcher.register(shellLangBuilder);
            }
        });
    }

    private static int executeSetLanguage(CommandContext<ServerCommandSource> context) {
        String lang = StringArgumentType.getString(context, "lang");
        boolean success = ServerLanguageManage.setLanguage(lang);
        if (success) {
            context.getSource().sendMessage(ServerLanguageManage.getText("text.mc-cmd-shell.info.language_set", lang));
        } else {
            context.getSource().sendError(ServerLanguageManage.getText("text.mc-cmd-shell.error.language_not_found", lang));
        }
        return success ? 1 : 0;
    }
}