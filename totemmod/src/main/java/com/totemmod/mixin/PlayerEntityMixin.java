package com.totemmod.mixin;

// This mixin is no longer needed — the Totem of Inventory now:
// 1. Clears the player's inventory in ServerPlayerDeathMixin (so nothing drops)
// 2. Stores items in a map
// 3. Drops a Heart of the Sea for recovery
//
// No need to cancel dropInventory — the inventory is already empty when
// dropInventory runs.
