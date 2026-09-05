package com.donutsmp.trader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
    /** Bu kadarlık bir fiyat farkı için ilan yenilenmez. */
    public double minRepriceStep = 10.0;
    /** Rakip bizden en az bu kadar ucuz olmadan fiyat kırılmaz. */
    public double minUndercutGap = 2000.0;
    /** Bu sayıdan az ucuz ilan varsa satılmaları beklenir. */
    public int minCompetitorsBelow = 3;
    public boolean autoScan = true;
    public String marketCommand = "ah search %s";
    public boolean marketCommandFound = false;
    public int scanIntervalSeconds = 6;
    public int clickDelayMs = 250;
    public int marketPollSeconds = 15;
    public boolean simulationMode = false;
    /** Bu kadar çalışıp dinlenir; 0 kapatır. */
    public int workSeconds = 300;
    public int restSeconds = 60;
    public String licenseKey = "";
    public boolean dumpScreens = false;

    /**
     * Fiyatının altına inilmeyecek oyuncular.
     *
     * Arka plandaki takım görevi bu listeye yazarken oyun döngüsü ve Gson onu
     * geziyor. Kopyalayarak yazan bir liste her gezinmeyi kendi anlık görüntüsü
     * üzerinde yapar; kilit gerekmez, ConcurrentModificationException olmaz.
     */
    public List<String> teammates = new CopyOnWriteArrayList<>();
    /** Durumların paylaşıldığı eşitlenen klasör; boşsa paylaşım kapalı. */
    public String teamFolder = "";
    /** Bu kadar süredir güncellenmemiş bir arkadaş çevrimdışı sayılır. */
    public int teamStaleSeconds = 90;


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
        live.minRepriceStep = fresh.minRepriceStep;
        live.minUndercutGap = fresh.minUndercutGap;
        live.minCompetitorsBelow = fresh.minCompetitorsBelow;
        live.autoScan = fresh.autoScan;
        live.marketCommand = fresh.marketCommand;
        live.marketCommandFound = fresh.marketCommandFound;
        live.scanIntervalSeconds = fresh.scanIntervalSeconds;
        live.clickDelayMs = fresh.clickDelayMs;
        live.marketPollSeconds = fresh.marketPollSeconds;
        live.simulationMode = fresh.simulationMode;
        live.workSeconds = fresh.workSeconds;
        live.restSeconds = fresh.restSeconds;
        live.licenseKey = fresh.licenseKey;
        live.dumpScreens = fresh.dumpScreens;
        live.teammates = fresh.teammates;
        live.teamFolder = fresh.teamFolder;
        live.teamStaleSeconds = fresh.teamStaleSeconds;
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
        if (licenseKey == null) licenseKey = "";
        if (targetItem == null || targetItem.isBlank()) targetItem = "ladder";
        targetItem = targetItem.toLowerCase().replace("minecraft:", "").trim();
        maxSlots = Math.max(1, Math.min(54, maxSlots));
        lotSize = Math.max(1, Math.min(64, lotSize));
        minPriceFloor = Math.max(0.0, minPriceFloor);
        fallbackPrice = Math.max(minPriceFloor, fallbackPrice);
        undercutAmount = Math.max(0.0, undercutAmount);
        minRepriceStep = Math.max(0.0, minRepriceStep);
        minUndercutGap = Math.max(0.0, minUndercutGap);
        minCompetitorsBelow = Math.max(1, minCompetitorsBelow);
        workSeconds = Math.max(0, workSeconds);
        restSeconds = Math.max(0, restSeconds);
        undercutPercent = Math.max(0.0, Math.min(50.0, undercutPercent));
        if (marketCommand == null || marketCommand.isBlank() || !marketCommand.contains("%s")) {
            marketCommand = "ah search %s";
        }
        scanIntervalSeconds = Math.max(3, Math.min(600, scanIntervalSeconds));
        clickDelayMs = Math.max(0, clickDelayMs);
        marketPollSeconds = Math.max(5, marketPollSeconds);
        // Gson kendi ArrayList'ini koyar; okumadan sonra tipi geri almak şart.
        if (teammates == null) teammates = new CopyOnWriteArrayList<>();
        else if (!(teammates instanceof CopyOnWriteArrayList)) {
            teammates = new CopyOnWriteArrayList<>(teammates);
        }
        teammates.removeIf(name -> !com.donutsmp.trader.team.Team.validName(name));
        if (teamFolder == null) teamFolder = "";
        teamStaleSeconds = Math.max(10, Math.min(3600, teamStaleSeconds));
    }

    /**
     * Arka plandaki takım görevi de kaydediyor, komutlar da. İki yazıcı aynı
     * dosyada buluşursa yarısı yazılmış bir ayar dosyası kalır ve okuma
     * başarısız olunca bütün ayarlar varsayılana döner.
     */
    public synchronized void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Ayar dosyası kaydedilemedi: {}", e.getMessage());
        }
    }
}
