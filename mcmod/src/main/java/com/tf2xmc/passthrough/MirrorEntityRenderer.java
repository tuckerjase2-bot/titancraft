package com.tf2xmc.passthrough;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * M3 glowing marker renderer for the TF2 mirror entity.
 * - Cyan translucent core (debug quads), slow pulse so it reads as "live".
 * - Bright wireframe shell (debug lines).
 * - Vanilla glow outline comes from MirrorEntity.isGlowing() and shows
 *   THROUGH terrain — that is the through-wall visibility layer.
 */
public class MirrorEntityRenderer extends EntityRenderer<MirrorEntity> {

    private static final float R = 0.25f;
    private static final float G = 0.95f;
    private static final float B = 1.00f;

    public MirrorEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(MirrorEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers);

        float age = entity.age + tickDelta;
        float pulse = 0.22f + 0.10f * MathHelper.sin(age * 0.25f);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                -MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw())));

        Box core = new Box(-0.45, 0.0, -0.45, 0.45, 1.8, 0.45);

        VertexConsumer quads = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
        WorldRenderer.drawBox(matrices, quads, core, R, G, B, pulse);

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getDebugLines(2.0));
        WorldRenderer.drawBox(matrices, lines, core, R, G, B, 0.9f);

        matrices.pop();
    }

    @Override
    public Identifier getTexture(MirrorEntity entity) {
        return Identifier.of("minecraft", "textures/misc/unknown_server.png");
    }
}
