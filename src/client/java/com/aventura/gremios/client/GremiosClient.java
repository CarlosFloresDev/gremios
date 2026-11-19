package com.aventura.gremios.client;

import com.aventura.gremios.GremiosMod;
import com.aventura.gremios.net.GremiosPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** Cliente de Gremios: tecla K para abrir el panel y actualizaciones en vivo (chat). */
public class GremiosClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORIA = KeyMapping.Category.register(GremiosMod.id("gremios"));
	public static final KeyMapping ABRIR = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.gremios.abrir", InputConstants.KEY_K, CATEGORIA));

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(GremiosPayloads.Estado.TYPE, (payload, context) -> {
			var gui = context.client().gui;
			if (gui.screen() instanceof GremiosScreen screen) {
				screen.actualizar(payload);
			} else if (payload.abrir()) {
				gui.setScreen(new GremiosScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(GremiosPayloads.Linea.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof GremiosScreen screen) {
				screen.mensaje(payload.mensaje());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ABRIR.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					ClientPlayNetworking.send(new GremiosPayloads.Accion(GremiosPayloads.Accion.ABRIR));
				}
			}
		});
	}
}
