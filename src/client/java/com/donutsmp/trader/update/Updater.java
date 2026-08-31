package com.donutsmp.trader.update;

import com.donutsmp.trader.DonutTraderMod;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Release'inden yeni jar indirir.
 *
 * İndirilen jar mods/ klasörüne KONULMAZ; oyun kapanırken eskisi silinebilirse
 * o zaman taşınır. İkisi aynı anda mods/ içinde olursa Fabric "duplicate mod
 * id" ile hiç açılmaz — güncellemenin uygulanmaması, oyunun açılmamasından
 * iyidir.
 */
public final class Updater {

    private static final String REPO = "akerd35/donutsmp-trader";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Pattern JAR_NAME = Pattern.compile("^donutsmp-trader-([0-9A-Za-z.+-]{1,32})\\.jar$");
    private static final long MAX_JAR_BYTES = 16L * 1024 * 1024;
    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Updater() {}

    private static final class Asset {
        String name;
        String browser_download_url;
        long size;
    }

    private static final class Release {
        String tag_name;
        List<Asset> assets;
    }

    public static Path stagingDir() {
        return FabricLoader.getInstance().getGameDir().resolve("donutsmp-trader-update");
    }

    private static Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(DonutTraderMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    /** Çalışan jar'ın kendi yolu; kapanışta silinecek olan. */
    public static Optional<Path> runningJar() {
        return FabricLoader.getInstance().getModContainer(DonutTraderMod.MOD_ID)
                .map(ModContainer::getOrigin)
                .map(origin -> origin.getPaths().isEmpty() ? null : origin.getPaths().get(0))
                .filter(p -> p != null && Files.isRegularFile(p));
    }

    /** Ağ işi; asla render thread'inde çağrılmamalı. */
    public static void run(Consumer<String> say) {
        try {
            Release release = fetchLatest();
            if (release == null || release.assets == null) {
                say.accept("§cGüncelleme bilgisi alınamadı.");
                return;
            }

            Asset jar = null;
            String latest = null;
            for (Asset asset : release.assets) {
                if (asset == null || asset.name == null) continue;
                Matcher matcher = JAR_NAME.matcher(asset.name);
                if (matcher.matches()) {
                    jar = asset;
                    latest = matcher.group(1);
                    break;
                }
            }

            if (jar == null) {
                say.accept("§cSürüm " + release.tag_name + " içinde jar bulunamadı.");
                return;
            }

            String current = currentVersion();
            if (!isNewer(latest, current)) {
                say.accept("§aZaten güncelsiniz: §f" + current);
                return;
            }

            Asset checksum = findAsset(release.assets, jar.name + ".sha256");
            if (checksum == null) {
                say.accept("§cSürümde sha256 dosyası yok, indirme iptal edildi.");
                return;
            }

            if (jar.size > MAX_JAR_BYTES) {
                say.accept("§cJar beklenenden büyük (" + jar.size + " bayt), indirme iptal edildi.");
                return;
            }

            say.accept("§e" + current + " -> " + latest + " indiriliyor...");

            String expected = firstToken(download(checksum.browser_download_url));
            Path staging = stagingDir();
            Files.createDirectories(staging);
            Path target = staging.resolve(jar.name);
            Path temp = staging.resolve(jar.name + ".part");

            byte[] bytes = downloadBytes(jar.browser_download_url);
            String actual = sha256(bytes);
            if (expected == null || !expected.equalsIgnoreCase(actual)) {
                say.accept("§cSHA256 uyuşmadı, dosya atıldı. Beklenen: " + expected + " Gelen: " + actual);
                return;
            }

            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            cleanOlderStaged(staging, jar.name);

            say.accept("§aSürüm §f" + latest + " §aindirildi ve doğrulandı.");
            say.accept("§eOyunu kapatıp açın; eski jar kapanışta değiştirilecek.");
            DonutTraderMod.LOGGER.info("[Update] {} indirildi: {}", latest, target);
        } catch (Exception e) {
            say.accept("§cGüncelleme başarısız: " + e.getMessage());
            DonutTraderMod.LOGGER.error("[Update] hata", e);
        }
    }

    /**
     * Kapanışta eski jar'ı silip yenisini mods/ içine taşır.
     *
     * Windows açık dosyayı silmeyi reddedebilir; o durumda hazırlanan jar
     * yerinde bırakılır ve oyun bir sonraki açılışta eski sürümle sorunsuz
     * çalışmaya devam eder.
     */
    public static void applyStagedUpdate() {
        try {
            Path staging = stagingDir();
            if (!Files.isDirectory(staging)) return;

            Path staged = null;
            try (var stream = Files.list(staging)) {
                staged = stream.filter(p -> JAR_NAME.matcher(p.getFileName().toString()).matches())
                        .findFirst().orElse(null);
            }
            if (staged == null) return;

            Path running = runningJar().orElse(null);
            if (running == null) {
                DonutTraderMod.LOGGER.warn("[Update] Calisan jar bulunamadi, degisim atlandi.");
                return;
            }

            // Once yeni jar mods/ icine .part olarak konur, sonra eskisi silinir,
            // en son adi duzeltilir. Hangi adimda patlarsa patlasin mods/ icinde
            // her zaman tam olarak bir calisir jar kalir.
            Path destination = modsDir().resolve(staged.getFileName());
            Path partial = destination.resolveSibling(staged.getFileName() + ".part");
            Files.createDirectories(destination.getParent());
            Files.copy(staged, partial, StandardCopyOption.REPLACE_EXISTING);

            try {
                Files.delete(running);
            } catch (Exception e) {
                Files.deleteIfExists(partial);
                DonutTraderMod.LOGGER.warn("[Update] Eski jar silinemedi ({}), guncelleme bekliyor: {}",
                        e.toString(), running);
                return;
            }

            Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(staged);
            DonutTraderMod.LOGGER.info("[Update] Guncelleme uygulandi: {}", destination);
        } catch (Exception e) {
            DonutTraderMod.LOGGER.warn("[Update] Degisim yapilamadi, eski surum kaliyor: {}", e.toString());
        }
    }

    static boolean isNewer(String candidate, String current) {
        if (candidate == null || current == null) return false;
        try {
            return Version.parse(candidate).compareTo(Version.parse(current)) > 0;
        } catch (Exception e) {
            return !candidate.equals(current);
        }
    }

    static String firstToken(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        int space = trimmed.indexOf(' ');
        String token = space < 0 ? trimmed : trimmed.substring(0, space);
        return token.matches("(?i)[0-9a-f]{64}") ? token : null;
    }

    private static Asset findAsset(List<Asset> assets, String name) {
        for (Asset asset : assets) {
            if (asset != null && name.equals(asset.name)) return asset;
        }
        return null;
    }

    private static void cleanOlderStaged(Path staging, String keep) throws Exception {
        try (var stream = Files.list(staging)) {
            for (Path path : stream.toList()) {
                if (!path.getFileName().toString().equals(keep)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Release fetchLatest() throws Exception {
        HttpRequest request = request(LATEST_URL).header("Accept", "application/vnd.github+json").build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub " + response.statusCode());
        }
        return GSON.fromJson(response.body(), Release.class);
    }

    private static String download(String url) throws Exception {
        HttpResponse<String> response = HTTP.send(request(url).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return response.body();
    }

    private static byte[] downloadBytes(String url) throws Exception {
        HttpResponse<InputStream> response = HTTP.send(request(url).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes((int) MAX_JAR_BYTES + 1);
            if (bytes.length > MAX_JAR_BYTES) throw new IllegalStateException("Dosya cok buyuk");
            return bytes;
        }
    }

    /** Adres yalnızca GitHub olabilir; sürüm verisi de sonuçta uzaktan gelen veridir. */
    private static HttpRequest.Builder request(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                && (host.equals("github.com") || host.endsWith(".github.com")
                || host.endsWith(".githubusercontent.com"));
        if (!allowed) {
            throw new IllegalArgumentException("Beklenmeyen adres: " + url);
        }
        return HttpRequest.newBuilder(uri)
                .header("User-Agent", "DonutTrader-Fabric")
                .timeout(Duration.ofSeconds(60))
                .GET();
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
