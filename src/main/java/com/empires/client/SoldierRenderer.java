package com.empires.client;

import com.empires.SoldierEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** الجنود بشكل لاعبين؛ الدروع الجلدية الملونة تميّز الإمبراطورية. */
public class SoldierRenderer extends BipedEntityRenderer<SoldierEntity, PlayerEntityModel<SoldierEntity>> {
	private static final Identifier SOLDIER = Identifier.ofVanilla("textures/entity/player/wide/steve.png");
	private static final Identifier MINISTER = Identifier.ofVanilla("textures/entity/player/wide/kai.png");
	private static final Identifier PRESIDENT = Identifier.ofVanilla("textures/entity/player/wide/zuri.png");

	public SoldierRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5F);
		this.addFeature(new ArmorFeatureRenderer<>(this,
				new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
				new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
				ctx.getModelManager()));
	}

	@Override
	public Identifier getTexture(SoldierEntity e) {
		return e.getRole() == 2 ? PRESIDENT : e.getRole() == 1 ? MINISTER : SOLDIER;
	}
}
