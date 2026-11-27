package com.empires;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModEntities {
	public static final EntityType<SoldierEntity> SOLDIER = Registry.register(
			Registries.ENTITY_TYPE, EmpiresMod.id("soldier"),
			EntityType.Builder.create(SoldierEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.maxTrackingRange(10)
					.build("soldier"));

	public static void register() {}
}
