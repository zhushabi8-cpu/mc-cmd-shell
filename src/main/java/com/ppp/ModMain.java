package com.ppp;

import com.ppp.command.ModCommands;
import com.ppp.util.AdminUtil;
import com.ppp.util.RegistryBackup;
import com.ppp.util.ServerLanguageManage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ModMain implements ModInitializer {
    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            ServerLanguageManage.loadConfig();
        }

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            if (!AdminUtil.isAdmin()) {
                AdminUtil.elevate();
            }
        }

        ModCommands.register();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (com.ppp.util.OSDetector.isWindows() &&
                    FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                RegistryBackup.restore();
            }
        }));
    }
}