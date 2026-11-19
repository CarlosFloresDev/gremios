package com.aventura.gremios.test;

import com.aventura.gremios.Gremios;
import com.aventura.gremios.data.Gremio;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

public class GremiosGameTests {
	private static int contador;

	/** Jugador real del servidor con nombre unico (los gremios se guardan en el mundo entre pruebas). */
	static ServerPlayer jugador(GameTestHelper helper, String base) {
		String nombre = base + (contador++);
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), nombre.substring(0, Math.min(16, nombre.length()))), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return player;
	}

	/** Etiqueta al azar: el mundo de pruebas se guarda entre ejecuciones y las etiquetas no se pueden repetir. */
	private static String tag() {
		String azar = Integer.toString(java.util.concurrent.ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36).toUpperCase();
		return "T" + "000".substring(azar.length()) + azar;
	}

	/** Fundar, invitar, aceptar: ambos en el gremio y en el equipo del marcador ([TAG] en el chat). */
	@GameTest
	public void crearInvitarAceptar(GameTestHelper helper) {
		ServerPlayer lider = jugador(helper, "lider");
		ServerPlayer amigo = jugador(helper, "amigo");
		String tag = tag();
		helper.assertTrue(Gremios.crear(lider, "Los Misti " + tag, tag), "Se funda el gremio");
		helper.assertTrue(!Gremios.crear(lider, "Otro " + tag, "Z" + tag), "No se puede tener dos gremios");
		helper.assertTrue(!Gremios.aceptar(amigo, tag), "Sin invitacion no se entra");
		helper.assertTrue(Gremios.invitar(lider, amigo.getName().getString()), "Invitar al amigo");
		helper.assertTrue(Gremios.aceptar(amigo, tag), "El amigo acepta");
		Gremio g = Gremios.deJugador(amigo);
		helper.assertTrue(g != null && g.miembros.size() == 2, "Dos miembros");
		helper.assertTrue(g.rango(amigo.getUUID()) == Gremio.Rango.MIEMBRO && g.rango(lider.getUUID()) == Gremio.Rango.LIDER, "Rangos iniciales");
		PlayerTeam equipo = helper.getLevel().getServer().getScoreboard().getPlayersTeam(amigo.getName().getString());
		helper.assertTrue(equipo != null && equipo.getName().equals(g.equipo()), "El amigo esta en el equipo del gremio");
		helper.assertTrue(lider.getTeam() == amigo.getTeam(), "Mismo equipo => mismo color y etiqueta");
		helper.succeed();
	}

	/** Solo el lider sube rangos; un oficial puede expulsar miembros. */
	@GameTest
	public void rangosYExpulsar(GameTestHelper helper) {
		ServerPlayer lider = jugador(helper, "jefe");
		ServerPlayer oficial = jugador(helper, "ofi");
		ServerPlayer miembro = jugador(helper, "novato");
		String tag = tag();
		Gremios.crear(lider, "Rangos " + tag, tag);
		Gremios.invitar(lider, oficial.getName().getString());
		Gremios.aceptar(oficial, tag);
		Gremios.invitar(lider, miembro.getName().getString());
		Gremios.aceptar(miembro, tag);
		Gremio g = Gremios.deJugador(lider);
		Gremios.cambiarRango(miembro, oficial.getUUID(), true);
		helper.assertTrue(g.rango(oficial.getUUID()) == Gremio.Rango.MIEMBRO, "Un miembro no puede ascender a nadie");
		Gremios.cambiarRango(lider, oficial.getUUID(), true);
		helper.assertTrue(g.rango(oficial.getUUID()) == Gremio.Rango.OFICIAL, "El lider asciende a oficial");
		Gremios.expulsar(oficial, miembro.getUUID());
		helper.assertTrue(!g.miembros.containsKey(miembro.getUUID()), "El oficial expulsa al miembro");
		helper.assertTrue(miembro.getTeam() == null, "El expulsado sale del equipo");
		helper.succeed();
	}

	/** Si el lider se va, el liderazgo pasa al oficial; el chat guarda el historial. */
	@GameTest
	public void salirYChat(GameTestHelper helper) {
		ServerPlayer lider = jugador(helper, "capi");
		ServerPlayer otro = jugador(helper, "segundo");
		String tag = tag();
		Gremios.crear(lider, "Chat " + tag, tag);
		Gremios.invitar(lider, otro.getName().getString());
		Gremios.aceptar(otro, tag);
		Gremio g = Gremios.deJugador(lider);
		Gremios.cambiarRango(lider, otro.getUUID(), true);
		Gremios.chat(otro, "Hola equipo");
		helper.assertTrue(g.chat.stream().anyMatch(m -> m.texto().equals("Hola equipo") && !m.sistema()), "El mensaje queda en el historial");
		Gremios.salir(lider);
		helper.assertTrue(g.rango(otro.getUUID()) == Gremio.Rango.LIDER, "El oficial pasa a ser lider");
		helper.assertTrue(Gremios.deJugador(lider) == null, "El que salio ya no tiene gremio");
		helper.succeed();
	}

	/** El banco es uno solo para todo el gremio y al disolver vuelve al lider. */
	@GameTest
	public void bancoCompartido(GameTestHelper helper) {
		ServerPlayer lider = jugador(helper, "banquero");
		String tag = tag();
		Gremios.crear(lider, "Banco " + tag, tag);
		Gremios.abrirBanco(lider);
		lider.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 5));
		Gremio g = Gremios.deJugador(lider);
		helper.assertTrue(g.banco.get(0).is(Items.DIAMOND) && g.banco.get(0).getCount() == 5, "El banco guarda los diamantes");
		lider.closeContainer();
		Gremios.disolver(lider);
		helper.assertTrue(Gremios.deJugador(lider) == null, "Gremio disuelto");
		helper.assertTrue(lider.getInventory().countItem(Items.DIAMOND) == 5, "El banco vuelve al lider");
		helper.succeed();
	}
}
