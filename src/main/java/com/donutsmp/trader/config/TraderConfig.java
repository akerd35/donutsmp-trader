package com.donutsmp.trader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class TraderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_PATH = "config/donutsmp_trader.json";

    public boolean enabled = false; // Varsayılan olarak PASİF başlar (Güvenlik için)
    public int maxSlots = 18;
    public String targetItem = "ladder";
    public int lotSize = 1;
    public double fallbackPrice = 35000.0;
    public double minPriceFloor = 5000.0;
    public boolean autoUndercut = true;
    public int clickDelayMs = 250;
    public int relistCheckIntervalSeconds = 15;
    public boolean simulationMode = false;

    private static TraderConfig INSTANCE = null;

    public static synchronized TraderConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static TraderConfig load() {
        File file = new File(CONFIG_PATH);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                TraderConfig config = GSON.fromJson(reader, TraderConfig.class);
                if (config != null) {
                    LOGGER.info("Ayar dosyası yüklendi: {}", CONFIG_PATH);
                    return config;
                }
            } catch (Exception e) {
                LOGGER.error("Ayar dosyası okunamadı, varsayılanlar kullanılıyor: {}", e.getMessage());
            }
        }
        TraderConfig def = new TraderConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            File file = new File(CONFIG_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(this, writer);
                LOGGER.info("Ayar dosyası kaydedildi: {}", CONFIG_PATH);
            }
        } catch (Exception e) {
            LOGGER.error("Ayar dosyası kaydedilemedi: {}", e.getMessage());
        }
    }
}