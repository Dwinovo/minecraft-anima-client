package com.dwinovo.anima.datagen;

import com.dwinovo.anima.Constants;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = Constants.MOD_ID)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        boolean client = event.includeClient();

        event.getGenerator().addProvider(client, new ModLanguageProvider(output, "en_us"));
        event.getGenerator().addProvider(client, new ModLanguageProvider(output, "zh_cn"));
        event.getGenerator().addProvider(client, new ModItemModelProvider(output, existingFileHelper));
    }
}
