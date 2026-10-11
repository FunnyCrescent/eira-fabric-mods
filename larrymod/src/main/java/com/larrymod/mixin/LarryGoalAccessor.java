package com.larrymod.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for protected goalSelector and targetSelector fields on MobEntity.
 */
@Mixin(MobEntity.class)
public interface LarryGoalAccessor {

    @Accessor("goalSelector")
    GoalSelector larrymod$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector larrymod$getTargetSelector();
}
