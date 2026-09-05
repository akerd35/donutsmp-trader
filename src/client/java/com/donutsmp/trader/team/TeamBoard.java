package com.donutsmp.trader.team;

import com.donutsmp.trader.config.TraderConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Takımın anlık tablosu.
 *
 * Oyun döngüsü kendi durumunu buraya bırakır, arka plan iş parçacığı diske
 * yazıp ötekileri okur. Dosya işi hiçbir zaman tick içinde yapılmaz.
 */
public class TeamBoard {

    private volatile PeerState mine;
    private volatile List<PeerState> peers = List.of();
    private volatile String lastError = "";

    public void setMine(PeerState state) {
        this.mine = state;
    }

    public PeerState mine() {
        return mine;
    }

    public List<PeerState> peers() {
        return peers;
    }

    public String lastError() {
        return lastError;
    }

    public boolean linked() {
        return !peers.isEmpty();
    }

    /** Rakip sayılmayacak adlar: kendimiz ve takım listesi. */
    public Set<String> ourNames(TraderConfig config, String self) {
        return Team.ourNames(config.teammates, self);
    }

    /** Arka plandan çağrılır: kendi durumunu yaz, ötekileri oku, yeni adları listeye al. */
    public void poll(TraderConfig config, String selfName) {
        Path dir = TeamLink.resolve(config.teamFolder);
        if (dir == null) {
            peers = List.of();
            return;
        }
        long staleMs = Math.max(10, config.teamStaleSeconds) * 1000L;
        long now = System.currentTimeMillis();

        PeerState snapshot = mine;
        List<PeerState> everyone;
        try {
            if (snapshot != null) TeamLink.publish(dir, snapshot);
            everyone = TeamLink.readAll(dir, selfName);
            lastError = "";
        } catch (Exception e) {
            lastError = String.valueOf(e.getMessage());
            return;
        }

        List<PeerState> live = new ArrayList<>();
        for (PeerState peer : everyone) {
            if (peer.fresh(now, staleMs)) live.add(peer);
        }
        peers = List.copyOf(live);

        // Klasörü paylaşmak zaten "bu kişiye karşı fiyat kırma" demek. Ad
        // listeye kalıcı giriyor: arkadaş çıkış yaptığında ilanları duruyor,
        // korumanın onunla birlikte kalkmaması gerek.
        boolean changed = false;
        for (String name : TeamLink.names(everyone)) {
            if (Team.add(config.teammates, name)) changed = true;
        }
        if (changed) config.save();
    }
}
