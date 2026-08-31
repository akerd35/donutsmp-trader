package com.donutsmp.trader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TraderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = false; // Varsayılan olarak PASİF başlar (güvenlik)
    public int maxSlots = 18;
    public String targetItem = "ladder";
    public int lotSize = 1;
    public double fallbackPrice = 35000.0;
    public double minPriceFloor = 5000.0;
    public boolean autoUndercut = true;
    public double undercutAmount = 1.0;
    public double undercutPercent = 0.0;
    public boolean autoScan = true;
    public String marketCommand = "ah search %s";
    public boolean marketCommandFound = false;
    public int scanIntervalSeconds = 6;
    public int clickDelayMs = 250;
    public int marketPollSeconds = 15;
    public boolean simulationMode = false;
    public boolean dumpScreens = false;

    // autoplus: ucuz ilanları alıp daha pahalıya asma
    public boolean flipEnabled = false;
    public double flipBuyBelow = 0.0;
    public double flipSellAt = 0.0;
    public double flipBudget = 1_000_000.0;
    public double flipMinMargin = 1000.0;
    /** Gerçekten tıklamak için /trader autoplus arm gerekir; para harcayan tek yol budur. */
    public boolean flipArmed = false;

    private static TraderConfig INSTANCE = null;

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutsmp_trader.json");
    }

    public static synchronized TraderConfig get() {
        if (INSTANCE == null) {
            INSTANCE = read();
            INSTANCE.save();
        }
        return INSTANCE;
    }

    /** Dosyayı yeniden okur ve tekil örneğin alanlarına yazar; eldeki referanslar geçerli kalır. */
    public static synchronized TraderConfig reload() {
        TraderConfig fresh = read();
        TraderConfig live = get();
        live.enabled = fresh.enabled;
        live.maxSlots = fresh.maxSlots;
        live.targetItem = fresh.targetItem;
        live.lotSize = fresh.lotSize;
        live.fallbackPrice = fresh.fallbackPrice;
        live.minPriceFloor = fresh.minPriceFloor;
        live.autoUndercut = fresh.autoUndercut;
        live.undercutAmount = fresh.undercutAmount;
        live.undercutPercent = fresh.undercutPercent;
        live.autoScan = fresh.autoScan;
        live.marketCommand = fresh.marketCommand;
        live.marketCommandFound = fresh.marketCommandFound;
        live.scanIntervalSeconds = fresh.scanIntervalSeconds;
        live.clickDelayMs = fresh.clickDelayMs;
        live.marketPollSeconds = fresh.marketPollSeconds;
        live.simulationMode = fresh.simulationMode;
        live.dumpScreens = fresh.dumpScreens;
        live.flipEnabled = fresh.flipEnabled;
        live.flipBuyBelow = fresh.flipBuyBelow;
        live.flipSellAt = fresh.flipSellAt;
        live.flipBudget = fresh.flipBudget;
        live.flipMinMargin = fresh.flipMinMargin;
        live.flipArmed = fresh.flipArmed;
        live.clamp();
        return live;
    }

    private static TraderConfig read() {
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                TraderConfig config = GSON.fromJson(reader, TraderConfig.class);
                if (config != null) {
                    config.clamp();
                    return config;
                }
            } catch (Exception e) {
                LOGGER.error("Ayar dosyası okunamadı, varsayılanlar kullanılıyor: {}", e.getMessage());
            }
        }
        return new TraderConfig();
    }

    private void clamp() {
        if (targetItem == null || targetItem.isBlank()) targetItem = "ladder";
        targetItem = targetItem.toLowerCase().replace("minecraft:", "").trim();
        maxSlots = Math.max(1, Math.min(54, maxSlots));
        lotSize = Math.max(1, Math.min(64, lotSize));
        minPriceFloor = Math.max(0.0, minPriceFloor);
        fallbackPrice = Math.max(minPriceFloor, fallbackPrice);
        undercutAmount = Math.max(0.0, undercutAmount);
        undercutPercent = Math.max(0.0, Math.min(50.0, undercutPercent));
        if (marketCommand == null || marketCommand.isBlank() || !marketCommand.contains("%s")) {
            marketCommand = "ah search %s";
        }
        scanIntervalSeconds = Math.max(3, Math.min(600, scanIntervalSeconds));
        flipBuyBelow = Math.max(0.0, flipBuyBelow);
        flipSellAt = Math.max(0.0, flipSellAt);
        flipBudget = Math.max(0.0, flipBudget);
        flipMinMargin = Math.max(0.0, flipMinMargin);
        if (flipSellAt > 0 && flipSellAt <= flipBuyBelow) flipEnabled = false;
        clickDelayMs = Math.max(0, clickDelayMs);
        marketPollSeconds = Math.max(5, marketPollSeconds);
    }

    public void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Ayar dosyası kaydedilemedi: {}", e.getMessage());
        }
    }
}
