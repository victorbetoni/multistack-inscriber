package net.threader.aetransformer.mixin;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInscriber;
import appeng.container.slot.AppEngSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AppEngSlot.class)
public abstract class AppEngSlotMixin extends Slot {
    @Shadow
    private AEBaseContainer myContainer;

    public AppEngSlotMixin(IInventory inventoryIn, int index, int xPosition, int yPosition) {
        super(inventoryIn, index, xPosition, yPosition);
    }

    @Override
    public int getSlotStackLimit() {
        if(this.myContainer instanceof ContainerInscriber) {
            return 64;
        }
        return this.inventory.getInventoryStackLimit();
    }


}