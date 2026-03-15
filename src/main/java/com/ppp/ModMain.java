package com.ppp;

import com.ppp.command.ModCommands;
import com.ppp.util.AdminUtil;
import com.ppp.util.RegistryBackup;
import net.fabricmc.api.ModInitializer;

public class ModMain implements ModInitializer {
    public void onInitialize() {
        if (!AdminUtil.isAdmin()) {
            AdminUtil.elevate();
        }

        ModCommands.register();
        Runtime.getRuntime().addShutdownHook(new Thread(RegistryBackup::restore));
    }
}