package com.eriksonn.createaeronautics.blocks.stirling_engine;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.base.GeneratingKineticTileEntity;
import com.simibubi.create.foundation.gui.widgets.InterpolatedChasingValue;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;

import static com.simibubi.create.content.contraptions.base.HorizontalKineticBlock.HORIZONTAL_FACING;
import static net.minecraft.block.AbstractFurnaceBlock.LIT;

public class StirlingEngineTileEntity extends GeneratingKineticTileEntity {
    private static final int INFINITE_TIME = 20*3600*24*30; // more burn time than this (1 month) is infinite

    int burnTime = 0;
    float generatedSpeed;
    float generatedCapacity = 32;
    protected ItemStack currentStack;
    public LazyOptional<IItemHandlerModifiable> invHandler;
    boolean hasInfiniteFuel=false;
    // Client
    InterpolatedChasingValue visualSpeed = new InterpolatedChasingValue();
    float angle;

    public StirlingEngineTileEntity(TileEntityType<?> typeIn) {
        super(typeIn);
        this.currentStack = ItemStack.EMPTY;
    }

    public void initialize() {
        super.initialize();
        this.invHandler = LazyOptional.of(this::createHandler);
    }

    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return this.isItemHandlerCap(cap) && this.invHandler != null ? this.invHandler.cast() : super.getCapability(cap, side);
    }

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(generatedSpeed, getBlockState().getValue(HORIZONTAL_FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        return lastCapacityProvided = generatedCapacity;
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) {
            float targetSpeed = isVirtual() ? speed : getGeneratedSpeed();
            visualSpeed.target(targetSpeed);
            visualSpeed.tick();
            angle += visualSpeed.value * 3 / 10f;
            angle %= 360;
        }

        if (isVirtual()) return;

        // exactly one of generated speed or current speed is zero
        if ((getGeneratedSpeed() == 0) ^ (getSpeed() == 0))
            updateGeneratedRotation();

        boolean isLit = false;

        hasInfiniteFuel = burnTime > INFINITE_TIME;

        if (hasInfiniteFuel) {
            // assumed intent of infinite fuel
            // does not decrease burn time
            isLit = true;
        } else if (burnTime > 0) {
            // lit, but not forever
            burnTime--;
            isLit = true;
        } else if (!currentStack.isEmpty()) {
            // try to burn fuel
            burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(currentStack, null);
            if(burnTime>0) {
                // fuel can be burned
                isLit = true;
                if (currentStack.getCount() == 1 && currentStack.hasContainerItem())
                    currentStack = currentStack.getContainerItem();
                else
                    currentStack.shrink(1);
            }
        }

        if(isLit && level.isClientSide)
        {
            spawnParticles();
        }

        boolean isLitState = StirlingEngineBlock.isLitState(this.getBlockState());
        generatedSpeed = isLit?32:0;

        // block state needs updating?
        if(isLitState ^ isLit) {
            // yes
            level.setBlock(getBlockPos(), this.getBlockState().setValue(LIT,isLit), 2);
        }
    }

    void spawnParticles() {
        if(Create.RANDOM.nextFloat()<0.12) {
            Vector3d pos = VecHelper.getCenterOf(this.worldPosition);

            Direction direction = getBlockState()
                    .getValue(BlockStateProperties.HORIZONTAL_FACING);
            Vector3i N = direction.getNormal();
            Vector3d N2 = new Vector3d(N.getX(),N.getY(),N.getZ());
            pos = pos.add(-N.getX() * 0.53, -0.1, -N.getZ() * 0.53);

            Vector3d random = VecHelper.offsetRandomly(Vector3d.ZERO,Create.RANDOM,0.15f);
            random = random.subtract(N2.scale(random.dot(N2)));
            pos=pos.add(random);

            Vector3d speed = VecHelper.offsetRandomly(Vector3d.ZERO,Create.RANDOM,0.01f);
            if(hasInfiniteFuel)
                level.addParticle(new RedstoneParticleData(0.8f,0,0.8f,1), pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
            else
                level.addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
        }
    }

    public void write(CompoundNBT compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putFloat("GeneratedSpeed", generatedSpeed);
        compound.put("CurrentStack", this.currentStack.serializeNBT());
        compound.putInt("BurnTime",burnTime);
    }

    protected void fromTag(BlockState blockState, CompoundNBT compound, boolean clientPacket) {
        super.fromTag(blockState, compound, clientPacket);
        this.currentStack = ItemStack.of(compound.getCompound("CurrentStack"));
        burnTime=compound.getInt("BurnTime");
        generatedSpeed = compound.getFloat("GeneratedSpeed");

        if (clientPacket)
            visualSpeed.withSpeed(1 / 8f)
                    .target(getGeneratedSpeed());
    }

    private IItemHandlerModifiable createHandler() {
        return new StirlingEngineItemHandler(this);
    }

    public boolean addToGoggleTooltip(List<ITextComponent> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(componentSpacing.plainCopy()
                .append(""));
        tooltip.add(componentSpacing.plainCopy()
                .append("Stirling engine:"));
        boolean hasByproduct = net.minecraftforge.common.ForgeHooks.getBurnTime(currentStack, null)==0;
        if(currentStack.isEmpty())
        {
            tooltip.add(componentSpacing.plainCopy().plainCopy()
                    .append(" Fuel: ")
                    .withStyle(TextFormatting.GRAY).append("None").withStyle(TextFormatting.RED));

        }else {
            if(!hasByproduct) {
                tooltip.add(componentSpacing.plainCopy().plainCopy()
                        .append(" Fuel: " + new TranslationTextComponent(currentStack.getItem()
                                .getDescriptionId(currentStack)).getString() + " x" + currentStack.getCount())
                        .withStyle(TextFormatting.GREEN));
            }else
            {
                tooltip.add(componentSpacing.plainCopy().plainCopy()
                        .append(" Byproduct: ")
                        .withStyle(TextFormatting.GRAY).append(new TranslationTextComponent(currentStack.getItem()
                                .getDescriptionId(currentStack)).getString() + " x" + currentStack.getCount())
                        .withStyle(TextFormatting.YELLOW));
            }

        }
        if(burnTime>0) {
            int sec = burnTime / 20;

            if (hasInfiniteFuel) {
                tooltip.add(componentSpacing.plainCopy().plainCopy()
                        .append(new StringTextComponent(" Burn time: ").withStyle(TextFormatting.GRAY))
                        .append(new StringTextComponent("Infinite").withStyle(TextFormatting.LIGHT_PURPLE)));
            } else {
                tooltip.add(componentSpacing.plainCopy().plainCopy()
                        .append(new StringTextComponent(" Burn time: ").withStyle(TextFormatting.GRAY))
                        .append(new StringTextComponent(getTime(sec)).withStyle(TextFormatting.AQUA)));
            }

            if(!currentStack.isEmpty() && !hasByproduct) {
                int nextBurnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(currentStack, null);
                if(nextBurnTime>20*3600*24*30||hasInfiniteFuel) {
                    tooltip.add(componentSpacing.plainCopy()
                            .append(new StringTextComponent(" Burn time total: ").withStyle(TextFormatting.GRAY))
                            .append(new StringTextComponent("Infinite").withStyle(TextFormatting.LIGHT_PURPLE)));
                }
                else {
                    int totalSec = sec;
                    totalSec += currentStack.getCount() * nextBurnTime / 20;
                    tooltip.add(componentSpacing.plainCopy()
                            .append(new StringTextComponent(" Burn time total: ").withStyle(TextFormatting.GRAY))
                            .append(new StringTextComponent(getTime(totalSec)).withStyle(TextFormatting.AQUA)));
                }
            }
        }
        return true;
    }

    private static String getTime(int sec) {
        String s;
        // handle negative times properly
        // may not technically be necessary
        if (sec < 0) {
            s = "-";
            sec = -sec;
        } else {
            s = "";
        }

        // split into hr/min/sec
        int min = sec/60;
        sec -= min * 60;
        int hour = min/60;
        min -= hour * 60;

        // hr is not 0
        // add h to display, and pad min
        // to 2 digits
        if (hour != 0) {
            s += hour + "h ";
            if (min < 10) s += 0;
        }

        // hr is not 0 or min is not zero
        // add min to display, and pad sec
        // to 2 digits
        if ((hour | min) != 0) {
            s += min + "m ";
            if (sec < 10) s += 0;
        }

        // add sec to display
        s += sec + "s";
        return s;
    }
}
