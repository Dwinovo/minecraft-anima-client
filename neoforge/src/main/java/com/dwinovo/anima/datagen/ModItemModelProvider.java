package com.dwinovo.anima.datagen;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.registry.NeoForgeEntityRegistry;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Constants.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        spawnEggItem(NeoForgeEntityRegistry.TEST_ENTITY_SPAWN_EGG.get());
    }
}
