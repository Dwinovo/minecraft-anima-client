package com.dwinovo.anima.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;

public class TestEntity extends Cow implements IAnimaEntity {

    public TestEntity(EntityType<? extends Cow> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String profile() {
        return "你是一个非常有人情味的牛";
    }
}
