package com.empires;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.util.Identifier;

public class EmpiresMod implements ModInitializer {
	public static final String MOD_ID = "empires";

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.register();
		FabricDefaultAttributeRegistry.register(ModEntities.SOLDIER, SoldierEntity.createAttrs());

		ServerLifecycleEvents.SERVER_STARTED.register(EmpireManager::onStart);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				EmpireManager.onJoin(handler.getPlayer(), server));
		ServerTickEvents.END_SERVER_TICK.register(EmpireManager::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				EmpireCommands.register(dispatcher));
	}
}
