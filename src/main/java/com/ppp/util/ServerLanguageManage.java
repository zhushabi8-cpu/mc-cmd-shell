package com.ppp.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ServerLanguageManage {
    private static final Logger LOGGER = LoggerFactory.getLogger("mc-cmd-shell");
    private static final String DEFAULT_LANG = "en_us";
    private static String currentLang = DEFAULT_LANG;
    private static final Map<String, Map<String, String>> languageCache = new HashMap<>();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mc-cmd-shell/lang.json");

    static {
        loadLanguageFile(DEFAULT_LANG);
        loadLanguageFile("zh_cn");
    }

    private static void loadLanguageFile(String langCode) {
        if (languageCache.containsKey(langCode)) return;
        String path = "/assets/mc-cmd-shell/lang/" + langCode + ".json";
        try (InputStream is = ServerLanguageManage.class.getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.warn("Language file not found: {}", path);
                return;
            }
            InputStreamReader reader = new InputStreamReader(is, "UTF-8");
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            Map<String, String> map = new HashMap<>();
            json.entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsString()));
            languageCache.put(langCode, map);
            LOGGER.info("Loaded language: {}", langCode);
        } catch (Exception e) {
            LOGGER.error("Failed to load language file: {}", path, e);
        }
    }

    public static boolean setLanguage(String langCode) {
        if (!languageCache.containsKey(langCode)) {
            loadLanguageFile(langCode);
            if (!languageCache.containsKey(langCode)) {
                return false;
            }
        }
        currentLang = langCode;
        saveConfig();
        return true;
    }

    public static void loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                if (json.has("language")) {
                    String lang = json.get("language").getAsString();
                    if (languageCache.containsKey(lang)) {
                        currentLang = lang;
                    } else {
                        loadLanguageFile(lang);
                        if (languageCache.containsKey(lang)) {
                            currentLang = lang;
                        } else {
                            LOGGER.warn("Saved language '{}' not available, using default.", lang);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load language config", e);
            }
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                JsonObject json = new JsonObject();
                json.addProperty("language", currentLang);
                new Gson().toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save language config", e);
        }
    }

    public static Text getText(String key, Object... args) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return Text.translatable(key, args);
        } else {
            Map<String, String> langMap = languageCache.get(currentLang);
            String pattern = langMap != null ? langMap.get(key) : null;
            if (pattern == null) {
                langMap = languageCache.get(DEFAULT_LANG);
                pattern = langMap != null ? langMap.get(key) : key;
            }
            String formatted = String.format(pattern, args);
            return Text.literal(formatted);
        }
    }
}