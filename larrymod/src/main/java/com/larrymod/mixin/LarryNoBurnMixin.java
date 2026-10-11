package com.larrymod.mixin;

import com.larrymod.LarryEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels tickBurnInDaylight for LarryEntity — Larry never burns in sunlight.
 */
@Mixin(MobEntity.class)
public class LarryNoBurnMixin {

    @Inject(method = "tickBurnInDaylight", at = @At("HEAD"), cancellable = true)
    private void larrymod$noBurn(CallbackInfo ci) {
        if ((Object) this instanceof LarryEntity) {
            ci.cancel();
        }
    }
}
