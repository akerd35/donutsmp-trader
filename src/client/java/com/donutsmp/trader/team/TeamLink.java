package com.donutsmp.trader.team;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * İki oyuncunun durumlarını paylaştığı klasör.
 *
 * Sunucu üzerinden istemciden istemciye paket gitmiyor, o yüzden buluşma
 * noktası oyunun dışında. Dropbox, Drive, iCloud ya da Syncthing ile eşitlenen
 * herhangi bir klasör iş görür: alan adı, sunucu ve parola gerekmez.
 *
 * Herkes yalnızca kendi dosyasına yazar, ötekiler yalnızca okur — iki tarafın
 * aynı dosyaya yazmadığı bir düzende eşitleme çakışması olmaz.
 */
public final class TeamLink {

    private static final Gson GSON = new Gson();

    /** Durum dosyası birkaç yüz bayt; büyüğü bizim yazdığımız şey değildir. */
    private static final long MAX_FILE_BYTES = 8 * 1024;
    private static final int MAX_PEERS = 16;

    private TeamLink() {}

    /** Baştaki ~ açılır; komut satırına tam yol yazmak zor. */
    public static Path resolve(String folder) {
        if (folder == null || folder.isBlank()) return null;
        String text = folder.trim();
        if (text.startsWith("~")) {
            text = System.getProperty("user.home") + text.substring(1);
        }
        try {
            return Paths.get(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** Oyuncu adı dosya adına gidiyor; harf, rakam ve alt çizgi dışında bir şey geçmemeli. */
    static String fileName(String name) {
        if (name == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < 16; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') out.append(c);
        }
        return out.length() == 0 ? "" : out + ".json";
    }

    public static void publish(Path dir, PeerState state) throws IOException {
        if (dir == null || state == null) return;
        String file = fileName(state.name());
        if (file.isEmpty()) return;

        Files.createDirectories(dir);
        Path target = dir.resolve(file);
        Path temp = dir.resolve(file + ".tmp");
        Files.writeString(temp, GSON.toJson(state), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Klasördeki öteki oyuncular, eskiler dahil. Bozuk dosya sessizce atlanır. */
    public static List<PeerState> readAll(Path dir, String selfName) {
        List<PeerState> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;

        String self = fileName(selfName);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                if (out.size() >= MAX_PEERS) break;
                String name = file.getFileName().toString();
                if (!name.endsWith(".json") || name.equals(self)) continue;
                PeerState peer = readOne(file);
                if (peer != null) out.add(peer);
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    /**
     * Yalnızca canlı olanlar.
     *
     * Arkadaş oyundan çıktığında dosyası klasörde kalır; onu çevrimiçi
     * göstermek yanlış olur. Adı yine de takım listesinde kalır, çünkü
     * ilanları duruyor.
     */
    public static List<PeerState> read(Path dir, String selfName, long now, long staleMs) {
        List<PeerState> out = new ArrayList<>();
        for (PeerState peer : readAll(dir, selfName)) {
            if (peer.fresh(now, staleMs)) out.add(peer);
        }
        return out;
    }

    /** Eski dosyalar dahil, klasörde adı geçen herkes. Takım listesi bundan büyür. */
    public static List<String> names(Path dir, String selfName) {
        return names(readAll(dir, selfName));
    }

    public static List<String> names(Collection<PeerState> peers) {
        List<String> out = new ArrayList<>();
        for (PeerState peer : peers) {
            if (peer != null && Team.validName(peer.name())) out.add(peer.name());
        }
        return out;
    }

    private static PeerState readOne(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return null;
            PeerState raw = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PeerState.class);
            if (raw == null) return null;
            PeerState peer = raw.sanitised();
            return peer.usable() ? peer : null;
        } catch (Exception e) {
            return null;
        }
    }
}
