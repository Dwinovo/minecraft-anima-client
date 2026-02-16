package com.dwinovo.anima.datagen;

import java.util.function.BiConsumer;

public final class LanguageData {

    private LanguageData() {
    }

    public static void addTranslations(String locale, BiConsumer<String, String> adder) {
        if ("zh_cn".equals(locale)) {
            adder.accept("entity.anima.test_entity", "测试生物");
            adder.accept("item.anima.test_entity_spawn_egg", "测试生物刷怪蛋");
            return;
        }

        adder.accept("entity.anima.test_entity", "Test Entity");
        adder.accept("item.anima.test_entity_spawn_egg", "Test Entity Spawn Egg");
    }
}
