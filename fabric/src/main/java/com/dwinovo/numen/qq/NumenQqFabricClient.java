package com.dwinovo.numen.qq;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric client entry point — the bridge is client-only, because companions
 * (and their message queues) live in the owner's game client.
 */
public class NumenQqFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NumenQq.initClient(FabricLoader.getInstance().getConfigDir());
    }
}
