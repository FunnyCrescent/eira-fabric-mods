package com.hellimp.mixin;

import com.hellimp.AccessoriesSystem;
import com.hellimp.AntiGravityAccessory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on Entity.applyGravity — cancels gravity for arrows fired by players
 * who have the Anti-Gravity Accessory in any accessory slot.
 *
 * applyGravity() is called every tick on all entities. For projectiles, it
 * reduces the Y velocity by the gravity amount (0.05 for arrows).
 *
 * We inject at HEAD and cancel if:
 *   1. The entity is a PersistentProjectileEntity (arrow, trident, etc.)
 *   2. The projectile's owner is a player with the Anti-Gravity Accessory
 *
 * Result: arrows fly in a straight line with no gravity drop.
 */
@Mixin(Entity.class)
public abstract class EntityGravityMixin {

    @Inject(method = "applyGravity", at = @At("HEAD"), cancellable = true)
    private void hellimp$cancelArrowGravity(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        // Only handle projectiles (arrows, tridents, etc.)
        if (!(self instanceof PersistentProjectileEntity projectile)) return;

        // Only handle projectiles that are still in flight (not stuck in ground)
        if (projectile.isAlive() == false) return;

        // Get the owner of the projectile
        Entity owner = projectile.getOwner();
        if (owner == null) return;
        if (!(owner instanceof net.minecraft.entity.player.PlayerEntity player)) return;

        // Check if player has the Anti-Gravity Accessory
        SimpleInventory inv = AccessoriesSystem.getInventory(player);
        if (inv == null) return;

        boolean hasAccessory = false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (AntiGravityAccessory.isAntiGravity(stack)) {
                hasAccessory = true;
                break;
            }
        }

        if (hasAccessory) {
            // Cancel gravity — arrow keeps flying straight
            ci.cancel();
        }
    }
}
