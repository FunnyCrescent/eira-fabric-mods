package com.totemmod.mixin;

import com.totemmod.LootDropState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.context.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * Mixin on LootPool that filters emerald drops while a mob is dropping loot.
 *
 * The vanilla LootPool#addGeneratedLoot(Consumer, LootContext) calls
 * consumer.accept(stack) for every stack it generates. We @Redirect that
 * accept call and silently drop the stack when it is an emerald AND the
 * current thread is in the middle of dropping mob loot (tracked by
 * {@link LootDropState}, which is set by the LivingEntity dropLoot mixin).
 *
 * This catches ALL mob loot — vanilla raid mobs (pillager, vindicator,
 * evoker, ravager, witch), and any modded mob whose loot table produces
 * emeralds — without touching chest / block / fishing loot tables.
 */
@Mixin(LootPool.class)
public abstract class LootPoolMixin {

    @Redirect(
        method = "addGeneratedLoot",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
        )
    )
    private void totemmod$filterEmeralds(Consumer<Object> consumer, Object item) {
        if (LootDropState.isDropping() && item instanceof ItemStack stack && stack.isOf(Items.EMERALD)) {
            return; // skip emerald drops from mobs
        }
        consumer.accept(item);
    }
}
