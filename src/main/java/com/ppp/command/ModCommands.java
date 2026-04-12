package com.ppp.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ppp.common.Common;
import com.ppp.util.ServerLanguageManage;
import com.ppp.util.OSDetector;
import com.ppp.win.Windows;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            boolean isWindows = OSDetector.isWindows();

            if (isWindows) {
                dispatcher.register(literal("cmd")
                        .then(literal("start")
                                .executes(Windows::executeStart))
                        .then(literal("run")
                                .then(argument("command", StringArgumentType.greedyString())
                                        .executes(Windows::executeRun)))
                        .then(literal("stop")
                                .executes(Windows::executeStop))
                        .then(literal("clear")
                                .executes(Windows::executeClear))
                );
                dispatcher.register(literal("cmdconfirm")
                        .executes(Windows::executeConfirm));
            }

            var shellBuilder = literal("shell");
            shellBuilder.then(literal("page")
                    .then(argument("page", IntegerArgumentType.integer(1))
                            .executes(Common::executePage)));
            shellBuilder.then(literal("help")
                    .executes(Common::executeShellHelp));
            dispatcher.register(shellBuilder);

            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                var shellLangBuilder = literal("shell");
                shellLangBuilder.then(literal("language")
                        .then(literal("set")
                                .then(argument("lang", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String[] languages = {"zh_cn", "en_us"};
                                            for (String lang : languages) {
                                                builder.suggest(lang);
                                            }
                                            return CompletableFuture.completedFuture(builder.build());
                                        })
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