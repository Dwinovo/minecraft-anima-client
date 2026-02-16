package com.dwinovo.anima.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class FabricAnimaDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider((output, registries) -> new FabricModLanguageProvider(output, "en_us", registries));
        pack.addProvider((output, registries) -> new FabricModLanguageProvider(output, "zh_cn", registries));
        pack.addProvider(FabricModItemModelProvider::new);
    }
}
