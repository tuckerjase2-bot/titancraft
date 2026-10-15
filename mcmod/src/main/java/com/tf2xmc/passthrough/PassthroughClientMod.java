package com.tf2xmc.passthrough;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class PassthroughClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // M3: glowing marker renderer for the TF2 mirror entity
        EntityRendererRegistry.register(MirrorEntities.MIRROR, MirrorEntityRenderer::new);
    }
}
