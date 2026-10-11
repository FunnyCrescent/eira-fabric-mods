package com.hellimp.mixin;

import com.hellimp.AccessoriesSystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on PlayerEntity — saves/loads accessory data with the player.
 *
 * writeCustomData: saves accessory inventory + extended flag to player NBT
 * readCustomData: loads them back
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityNbtMixin {

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void hellimp$writeAccessories(WriteView view, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        AccessoriesSystem.saveToView(self, view);
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void hellimp$readAccessories(ReadView view, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        AccessoriesSystem.loadFromView(self, view);
    }
}
