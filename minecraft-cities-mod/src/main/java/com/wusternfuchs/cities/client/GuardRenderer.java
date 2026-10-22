package com.wusternfuchs.cities.client;

import com.wusternfuchs.cities.CitiesMod;
import com.wusternfuchs.cities.entity.Guard;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws a guard as a person in armour, using the player model. */
public class GuardRenderer extends HumanoidMobRenderer<Guard, HumanoidModel<Guard>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CitiesMod.MODID, "textures/entity/guard.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(Guard guard) {
        return TEXTURE;
    }
}
