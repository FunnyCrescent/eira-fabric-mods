package com.villagermod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.village.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the "Hero of the Village" recognition entirely. {@link Raid#addHero}
 * normally records the UUID of every player who participated in winning a
 * raid; that set is then used to:
 *
 *   - Apply the Hero of the Village status effect.
 *   - Display the "Heroes of the Village" victory title.
 *   - Trigger the gift-giving AI task (also disabled separately in
 *     {@link GiveGiftsToHeroTaskMixin}).
 *
 * Cancelling addHero means the set is always empty, so none of the above
 * happens. The status effect itself is applied elsewhere in the raid victory
 * logic, but without a recorded hero UUID the effect has no targets — and
 * even if it somehow gets applied, its discount is neutralized by the
 * prepareOffersFor cancellation in {@link VillagerEntityMixin}, and its
 * gift-drop is neutralized by {@link GiveGiftsToHeroTaskMixin}.
 */
@Mixin(Raid.class)
public abstract class RaidMixin {

    @Inject(method = "addHero", at = @At("HEAD"), cancellable = true)
    private void villagermod$noHero(Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
