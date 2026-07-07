package com.dwinovo.numen.qq;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge entry point — the bridge is client-only, because companions (and
 * their message queues) live in the owner's game client; on a dedicated
 * server this mod loads and does nothing.
 */
@Mod(Constants.MOD_ID)
public class NumenQqNeoForge {

    public NumenQqNeoForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NumenQq.initClient(FMLPaths.CONFIGDIR.get());
        }
    }
}
