package net.threader.aetransformer.mixin;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IComparableDefinition;
import appeng.api.features.IInscriberRecipe;
import appeng.api.features.InscriberProcessType;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.GridAccessException;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.TileInscriber;
import appeng.util.Platform;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(TileInscriber.class)
public abstract class TileInscriberMixin extends AENetworkPowerTile {

    @Shadow protected abstract IInscriberRecipe makeNamePressRecipe(ItemStack input, ItemStack plateA, ItemStack plateB);

    @Shadow @Final private AppEngInternalInventory sideItemHandler;

    @Shadow public abstract void setSmash(boolean smash);

    @Shadow private int finalStep;

    @Shadow protected abstract void setProcessingTime(int processingTime);

    @Shadow public abstract int getProcessingTime();

    @Shadow public abstract int getMaxProcessingTime();

    @Shadow @Final private UpgradeInventory upgrades;

    @Shadow @Nullable public abstract IInscriberRecipe getTask();

    @Shadow protected abstract boolean hasWork();

    @Shadow @Final private AppEngInternalInventory topItemHandler;

    @Shadow @Final private AppEngInternalInventory bottomItemHandler;

    @Shadow public abstract boolean isSmash();

    /**
     * @author
     */
    @Overwrite(remap = false)
    @Nullable
    private IInscriberRecipe getTask(final ItemStack input, final ItemStack plateA, final ItemStack plateB )
    {

        final IComparableDefinition namePress = AEApi.instance().definitions().materials().namePress();
        final boolean isNameA = namePress.isSameAs( plateA );
        final boolean isNameB = namePress.isSameAs( plateB );

        if( ( isNameA && isNameB ) || isNameA && plateB.isEmpty() )
        {
            return this.makeNamePressRecipe( input, plateA, plateB );
        }
        else if( plateA.isEmpty() && isNameB )
        {
            return this.makeNamePressRecipe( input, plateB, plateA );
        }

        for( final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes() )
        {

            final boolean matchA = ( plateA.isEmpty() && !recipe.getTopOptional().isPresent() ) || ( Platform.itemComparisons()
                    .isSameItem( plateA,
                            recipe.getTopOptional().orElse( ItemStack.EMPTY ) ) ) && // and...
                    ( ( plateB.isEmpty() && !recipe.getBottomOptional().isPresent() ) || ( Platform.itemComparisons()
                            .isSameItem( plateB,
                                    recipe.getBottomOptional().orElse( ItemStack.EMPTY ) ) ) );

            final boolean matchB = ( plateB.isEmpty() && !recipe.getTopOptional().isPresent() ) || ( Platform.itemComparisons()
                    .isSameItem( plateB,
                            recipe.getTopOptional().orElse( ItemStack.EMPTY ) ) ) && // and...
                    ( ( plateA.isEmpty() && !recipe.getBottomOptional().isPresent() ) || ( Platform.itemComparisons()
                            .isSameItem( plateA,
                                    recipe.getBottomOptional().orElse( ItemStack.EMPTY ) ) ) );

            if( matchA || matchB )
            {
                for( final ItemStack option : recipe.getInputs() )
                {
                    if( Platform.itemComparisons().isSameItem( input, option ) )
                    {
                        return recipe;
                    }
                }
            }
        }

        return null;
    }

    /**
     * @author
     */
    @Overwrite(remap = false)
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall )
    {
        if( this.isSmash() )
        {
            this.finalStep++;
            if( this.finalStep == 8 )
            {
                final IInscriberRecipe out = this.getTask();
                if( out != null )
                {
                    final ItemStack outputCopy = out.getOutput().copy();

                    if( this.sideItemHandler.insertItem( 1, outputCopy, false ).isEmpty() )
                    {
                        this.setProcessingTime( 0 );
                        if( out.getProcessType() == InscriberProcessType.PRESS )
                        {
                            ItemStack top = this.topItemHandler.getStackInSlot(0);
                            if(top.getCount() > 1) {
                                top = this.topItemHandler.getStackInSlot(0).copy();
                                top.setCount(top.getCount()-1);
                            } else {
                                top = ItemStack.EMPTY;
                            }

                            ItemStack bottom = this.bottomItemHandler.getStackInSlot(0);
                            if(bottom.getCount() > 1) {
                                bottom = this.bottomItemHandler.getStackInSlot(0).copy();
                                bottom.setCount(bottom.getCount()-1);
                            } else {
                                bottom = ItemStack.EMPTY;
                            }

                            this.topItemHandler.setStackInSlot( 0, top );
                            this.bottomItemHandler.setStackInSlot( 0, bottom );
                        }

                        ItemStack side = this.sideItemHandler.getStackInSlot(0);
                        if(side.getCount() > 1) {
                            side = this.sideItemHandler.getStackInSlot(0).copy();
                            side.setCount(side.getCount()-1);
                        } else {
                            side = ItemStack.EMPTY;
                        }

                        this.sideItemHandler.setStackInSlot( 0, side );
                    }
                }
                this.saveChanges();
            }
            else if( this.finalStep == 16 )
            {
                this.finalStep = 0;
                this.setSmash( false );
                this.markForUpdate();
            }
        }
        else
        {
            try
            {
                final IEnergyGrid eg = this.getProxy().getEnergy();
                IEnergySource src = this;

                // Base 1, increase by 1 for each card
                final int speedFactor = 1 + this.upgrades.getInstalledUpgrades( Upgrades.SPEED );
                final int powerConsumption = 10 * speedFactor;
                final double powerThreshold = powerConsumption - 0.01;
                double powerReq = this.extractAEPower( powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG );

                if( powerReq <= powerThreshold )
                {
                    src = eg;
                    powerReq = eg.extractAEPower( powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG );
                }

                if( powerReq > powerThreshold )
                {
                    src.extractAEPower( powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG );

                    if( this.getProcessingTime() == 0 )
                    {
                        this.setProcessingTime( this.getProcessingTime() + speedFactor );
                    }
                    else
                    {
                        this.setProcessingTime( this.getProcessingTime() + ticksSinceLastCall * speedFactor );
                    }
                }
            }
            catch( final GridAccessException e )
            {
                // :P
            }

            if( this.getProcessingTime() > this.getMaxProcessingTime() )
            {
                this.setProcessingTime( this.getMaxProcessingTime() );
                final IInscriberRecipe out = this.getTask();
                if( out != null )
                {
                    final ItemStack outputCopy = out.getOutput().copy();
                    if( this.sideItemHandler.insertItem( 1, outputCopy, true ).isEmpty() )
                    {
                        this.setSmash( true );
                        this.finalStep = 0;
                        this.markForUpdate();
                    }
                }
            }
        }

        return this.hasWork() ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
    }


}
