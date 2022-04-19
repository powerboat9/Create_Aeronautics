package com.eriksonn.createaeronautics.events;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class CommonEvents {
    @SubscribeEvent
    public static void onEntityAdded(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        World world = event.getWorld();
    }
}
