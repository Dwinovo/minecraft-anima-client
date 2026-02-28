package com.dwinovo.anima.datagen;

import java.util.function.BiConsumer;

public final class LanguageData {

    private LanguageData() {
    }

    public static void addTranslations(String locale, BiConsumer<String, String> adder) {
        adder.accept("key.categories.anima", "Anima");

        if ("zh_cn".equals(locale)) {
            adder.accept("key.anima.open_feed", "打开 Anima 动态");
            return;
        }

        adder.accept("key.anima.open_feed", "Open Anima Feed");
    }
}
