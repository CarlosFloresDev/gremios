package com.aventura.gremios;

import com.aventura.gremios.data.Gremio;
import com.aventura.gremios.net.GremiosPayloads;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gremios: equipos de jugadores con panel (tecla K), rangos, chat interno, base, banco compartido
 * y la etiqueta [TAG] de color en el chat y sobre la cabeza.
 */
public class GremiosMod implements ModInitializer {
	public static final String MOD_ID = "gremios";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(GremiosPayloads.Estado.TYPE, GremiosPayloads.Estado.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GremiosPayloads.Linea.TYPE, GremiosPayloads.Linea.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GremiosPayloads.Accion.TYPE, GremiosPayloads.Accion.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GremiosPayloads.Accion.TYPE, (payload, context) -> accion(context.player(), payload));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Gremios.alConectar(handler.getPlayer());
			Gremios.sincronizar(handler.getPlayer(), false);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			for (Gremio g : Gremios.datos(server).todos()) {
				Gremios.actualizarEquipo(server, g);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Gremios.limpiar());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("gremio")
				.executes(ctx -> {
					Gremios.sincronizar(ctx.getSource().getPlayerOrException(), true);
					return 1;
				})
				.then(Commands.literal("crear").then(Commands.argument("tag", StringArgumentType.word())
					.then(Commands.argument("nombre", StringArgumentType.greedyString()).executes(ctx -> Gremios.crear(
						ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nombre"),
						StringArgumentType.getString(ctx, "tag")) ? 1 : 0))))
				.then(Commands.literal("invitar").then(Commands.argument("jugador", StringArgumentType.word()).executes(ctx ->
					Gremios.invitar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "jugador")) ? 1 : 0)))
				.then(Commands.literal("aceptar").then(Commands.argument("tag", StringArgumentType.word()).executes(ctx ->
					Gremios.aceptar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "tag")) ? 1 : 0)))
				.then(Commands.literal("salir").executes(ctx -> {
					Gremios.salir(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("banco").executes(ctx -> {
					Gremios.abrirBanco(ctx.getSource().getPlayerOrException());
					return 1;
				})));
			// chat del gremio: /g hola
			dispatcher.register(Commands.literal("g").then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(ctx -> {
				Gremios.chat(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mensaje"));
				return 1;
			})));
		});
		LOGGER.info("Gremios cargado");
	}

	private static UUID uuid(String texto) {
		try {
			return UUID.fromString(texto);
		} catch (IllegalArgumentException e) {
			return new UUID(0, 0);
		}
	}

	private static void accion(ServerPlayer player, GremiosPayloads.Accion a) {
		switch (a.accion()) {
			case GremiosPayloads.Accion.CREAR -> Gremios.crear(player, a.a(), a.b());
			case GremiosPayloads.Accion.INVITAR -> Gremios.invitar(player, a.a());
			case GremiosPayloads.Accion.ACEPTAR -> Gremios.aceptar(player, a.a());
			case GremiosPayloads.Accion.RECHAZAR -> Gremios.rechazar(player, a.a());
			case GremiosPayloads.Accion.UNIRSE -> Gremios.unirse(player, a.a());
			case GremiosPayloads.Accion.SALIR -> Gremios.salir(player);
			case GremiosPayloads.Accion.EXPULSAR -> Gremios.expulsar(player, uuid(a.a()));
			case GremiosPayloads.Accion.ASCENDER -> Gremios.cambiarRango(player, uuid(a.a()), true);
			case GremiosPayloads.Accion.DEGRADAR -> Gremios.cambiarRango(player, uuid(a.a()), false);
			case GremiosPayloads.Accion.TRANSFERIR -> Gremios.transferir(player, uuid(a.a()));
			case GremiosPayloads.Accion.DISOLVER -> Gremios.disolver(player);
			case GremiosPayloads.Accion.CHAT -> Gremios.chat(player, a.a());
			case GremiosPayloads.Accion.COLOR, GremiosPayloads.Accion.DESCRIPCION, GremiosPayloads.Accion.ABIERTO,
				GremiosPayloads.Accion.FUEGO_AMIGO -> Gremios.ajustes(player, a.accion(), a.a());
			case GremiosPayloads.Accion.FIJAR_BASE -> Gremios.fijarBase(player);
			case GremiosPayloads.Accion.BANCO -> Gremios.abrirBanco(player);
			case GremiosPayloads.Accion.ABRIR -> Gremios.sincronizar(player, true);
			default -> Gremios.sincronizar(player, false);
		}
	}
}
