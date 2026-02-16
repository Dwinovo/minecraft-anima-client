package com.dwinovo.anima.registry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.TestEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class FabricEntityRegistry {

    public static final EntityType<TestEntity> TEST_ENTITY = registerEntityType(
        "test_entity",
        EntityType.Builder.of(TestEntity::new, MobCategory.CREATURE).sized(0.9F, 1.4F)
    );
    public static final Item TEST_ENTITY_SPAWN_EGG = registerItem(
        "test_entity_spawn_egg",
        new SpawnEggItem(TEST_ENTITY, 0x7F5C2E, 0xF5F5F5, new Item.Properties())
    );

    private FabricEntityRegistry() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(TEST_ENTITY, Cow.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(TEST_ENTITY_SPAWN_EGG));
        Constants.LOG.info("Registered Fabric test entity and spawn egg.");
    }

    private static EntityType<TestEntity> registerEntityType(String path, EntityType.Builder<TestEntity> builder) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(id.toString()));
    }

    private static Item registerItem(String path, Item item) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
