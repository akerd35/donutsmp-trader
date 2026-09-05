package com.donutsmp.trader.team;

import java.util.Collection;

/**
 * Takım arkadaşının altına inme.
 *
 * Ekran taraması arkadaşın ilanını zaten atlıyor, ama fiyat her zaman ekrandan
 * gelmiyor: tarama tutmazsa API'nin en ucuz fiyatı kullanılıyor ve API ilanın
 * kime ait olduğunu söylemiyor. O fiyat arkadaşınki olabilir. Arkadaş kendi
 * fiyatını paylaştığı için burada kontrol edilebiliyor: altına inme, eşitle.
 */
public final class TeamPrice {

    private TeamPrice() {}

    /** Aynı eşyayı satan en ucuz takım arkadaşının tane fiyatı; yoksa 0. */
    public static double cheapestUnit(String item, Collection<PeerState> peers) {
        if (peers == null) return 0;
        double cheapest = 0;
        for (PeerState peer : peers) {
            if (peer == null || !peer.sells(item)) continue;
            double unit = peer.unitPrice();
            if (unit <= 0) continue;
            if (cheapest <= 0 || unit < cheapest) cheapest = unit;
        }
        return cheapest;
    }

    /**
     * Fiyatı arkadaşın fiyatına kadar yükseltir, asla altına indirmez.
     *
     * Eşitlemek yeter: ikimiz de aynı fiyattaysak kimse kimseyi kırmıyor ve bir
     * sonraki taramada ikimiz de yerinde duruyoruz.
     */
    public static double floor(double price, String item, int lotSize, Collection<PeerState> peers) {
        double unit = cheapestUnit(item, peers);
        if (unit <= 0) return price;
        return Math.max(price, unit * Math.max(1, lotSize));
    }

    /** Takım fiyatı bizimkini yukarı çekiyor mu? Durum ekranında sebebi yazmak için. */
    public static boolean binding(double price, String item, int lotSize, Collection<PeerState> peers) {
        return floor(price, item, lotSize, peers) > price;
    }
}
