package com.dwinovo.anima.registry;

import com.dwinovo.anima.Constants;
import com.dwinovo.anima.entity.TestEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoForgeEntityRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Constants.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TestEntity>> TEST_ENTITY = ENTITY_TYPES.register("test_entity",
        () -> EntityType.Builder.of(TestEntity::new, MobCategory.CREATURE)
            .sized(0.9F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "test_entity").toString())
    );

    public static final DeferredHolder<Item, DeferredSpawnEggItem> TEST_ENTITY_SPAWN_EGG = ITEMS.register("test_entity_spawn_egg",
        () -> new DeferredSpawnEggItem(TEST_ENTITY, 0x7F5C2E, 0xF5F5F5, new Item.Properties())
    );

    private NeoForgeEntityRegistry() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        ITEMS.register(eventBus);
    }

    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(TEST_ENTITY.get(), Cow.createAttributes().build());
    }

    public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(TEST_ENTITY_SPAWN_EGG.get());
        }
    }
}
