package com.dwinovo.anima.datagen;

import com.dwinovo.anima.registry.FabricEntityRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

public class FabricModItemModelProvider extends FabricModelProvider {

    public FabricModItemModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockStateModelGenerator) {
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerator) {
        itemModelGenerator.generateFlatItem(FabricEntityRegistry.TEST_ENTITY_SPAWN_EGG, ModelTemplates.FLAT_ITEM);
    }
}
