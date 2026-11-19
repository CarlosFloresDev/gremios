package com.aventura.gremios.test;

import com.aventura.gremios.Gremios;
import com.aventura.gremios.client.GremiosScreen;
import com.aventura.gremios.data.Gremio;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.server.level.ServerPlayer;

/** Capturas del panel de gremios: sin gremio, miembros, chat y ajustes. */
public class GremiosClientTests implements FabricClientGameTest {
	private static void pulsar(Minecraft client, String texto) {
		if (client.gui.screen() instanceof GremiosScreen screen) {
			screen.children().stream()
				.filter(w -> w instanceof Button b && b.getMessage().getString().startsWith(texto))
				.findFirst().ifPresent(w -> ((Button) w).onPress(new KeyEvent(0, 0, 0)));
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runOnServer(server -> {
				Gremio otro = Gremio.nuevo("Los Picanteros", "PIC", 12, UUID.randomUUID(), "Rosa");
				otro.abierto = true;
				Gremios.datos(server).agregar(otro);
				Gremio privado = Gremio.nuevo("Club del Misti", "AQP", 6, UUID.randomUUID(), "Lucho");
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				privado.invitados.add(player.getUUID());
				Gremios.datos(server).agregar(privado);
				Gremios.sincronizar(player, true);
			});
			context.waitTicks(10);
			context.takeScreenshot("gremios_sin_gremio");
			context.setScreen(() -> null);

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Gremios.crear(player, "Tecsup Ingenieros", "TEC");
				Gremio g = Gremios.deJugador(player);
				UUID carla = UUID.randomUUID();
				g.miembros.put(carla, Gremio.Rango.OFICIAL);
				g.nombres.put(carla, "Carla");
				UUID mateo = UUID.randomUUID();
				g.miembros.put(mateo, Gremio.Rango.MIEMBRO);
				g.nombres.put(mateo, "Mateo");
				Gremios.actualizarEquipo(server, g);
				Gremios.fijarBase(player);
				Gremios.chat(player, "¡Bienvenidos al gremio! Mañana vamos por el dirigible del Capitán Barbanube");
				Gremios.chat(player, "Traigan pociones y flechas");
				Gremios.sincronizar(player, true);
			});
			context.waitTicks(10);
			context.takeScreenshot("gremios_miembros");
			context.runOnClient(client -> pulsar(client, "Chat"));
			context.waitTicks(5);
			context.takeScreenshot("gremios_chat");
			context.runOnClient(client -> pulsar(client, "Ajustes"));
			context.waitTicks(5);
			context.takeScreenshot("gremios_ajustes");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.takeScreenshot("gremios_chat_normal");
		}
	}
}
