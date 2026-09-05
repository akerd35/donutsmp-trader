package com.donutsmp.trader.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraderConfigTest {

    /**
     * Her ayar alani kopyalanmali.
     *
     * Onceden alanlar elle tek tek kopyalaniyordu; yeni bir ayar eklerken
     * listeye yazmayi unutmak, o ayarin /trader reload ile yenilenmemesi
     * demekti ve bunu kimse fark etmiyordu. Bu test unutulan alani yakalar.
     */
    @Test
    void reloadCopiesEverySetting() throws Exception {
        TraderConfig from = new TraderConfig();
        TraderConfig to = new TraderConfig();

        List<Field> settings = settings();
        assertTrue(settings.size() >= 25, "ayar alanlari bulunamadi");

        for (Field field : settings) field.set(from, distinct(field));

        TraderConfig.copyInto(from, to);

        for (Field field : settings) {
            assertEquals(field.get(from), field.get(to), field.getName() + " kopyalanmadi");
        }
    }

    @Test
    void copyingCarriesTheTeamList() throws Exception {
        TraderConfig from = new TraderConfig();
        TraderConfig to = new TraderConfig();
        from.teammates.add("Kaan");

        TraderConfig.copyInto(from, to);
        assertEquals(List.of("Kaan"), to.teammates);
    }

    private static List<Field> settings() {
        List<Field> out = new ArrayList<>();
        for (Field field : TraderConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) continue;
            out.add(field);
        }
        return out;
    }

    /** Varsayilandan farkli, tipine uygun bir deger. */
    private static Object distinct(Field field) {
        Class<?> type = field.getType();
        if (type == boolean.class) return true;
        if (type == int.class) return 4242;
        if (type == double.class) return 42.5;
        if (type == String.class) return "degisti-" + field.getName();
        if (List.class.isAssignableFrom(type)) return new ArrayList<>(List.of("x"));
        throw new IllegalStateException("bilinmeyen ayar tipi: " + type);
    }
}
