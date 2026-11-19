package com.aventura.gremios;

import com.aventura.gremios.data.Gremio;
import com.aventura.gremios.data.GremiosData;
import com.aventura.gremios.net.GremiosPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

/** Logica de servidor de los gremios: crear, invitar, rangos, chat, base, banco y equipos del marcador. */
public final class Gremios {
	/** Un solo inventario por gremio: todos los que abren el banco ven el mismo (sin duplicar items). */
	private static final Map<UUID, SimpleContainer> BANCOS = new HashMap<>();
	/** Colores bonitos para gremios nuevos. */
	private static final int[] COLORES_INICIALES = {9, 10, 11, 12, 13, 14, 6, 3, 5, 2};

	private Gremios() {
	}

	public static GremiosData datos(MinecraftServer server) {
		return GremiosData.de(server);
	}

	public static @Nullable Gremio deJugador(ServerPlayer player) {
		return datos(player.level().getServer()).deJugador(player.getUUID());
	}

	public static TeamColor color(Gremio g) {
		return TeamColor.VALUES.get(Math.floorMod(g.color, 16));
	}

	/** "[TAG] " con el color del gremio. */
	public static MutableComponent etiqueta(Gremio g) {
		return Component.literal("[" + g.tag + "] ").withStyle(s -> s.withColor(color(g).textColor()).withBold(true));
	}

	private static void aviso(ServerPlayer player, String texto, boolean error) {
		player.sendSystemMessage(Component.literal("[Gremios] ").withStyle(ChatFormatting.DARK_AQUA)
			.append(Component.literal(texto).withStyle(error ? ChatFormatting.RED : ChatFormatting.AQUA)));
	}

	// ============================================================ crear / unirse / salir

	public static boolean crear(ServerPlayer player, String nombre, String tag) {
		GremiosData datos = datos(player.level().getServer());
		nombre = nombre.trim();
		tag = tag.trim().toUpperCase();
		if (datos.deJugador(player.getUUID()) != null) {
			aviso(player, "Ya estás en un gremio. Sal primero para crear otro.", true);
			return false;
		}
		if (nombre.length() < 3 || nombre.length() > 24 || !nombre.matches("[\\p{L}0-9 '_-]+")) {
			aviso(player, "El nombre debe tener de 3 a 24 letras o números.", true);
			return false;
		}
		if (!tag.matches("[A-Z0-9]{2,4}")) {
			aviso(player, "La etiqueta debe tener de 2 a 4 letras o números (ej. TEC, AQP).", true);
			return false;
		}
		if (datos.porNombre(nombre) != null || datos.porTag(tag) != null) {
			aviso(player, "Ya existe un gremio con ese nombre o etiqueta.", true);
			return false;
		}
		int color = COLORES_INICIALES[Math.floorMod(nombre.hashCode(), COLORES_INICIALES.length)];
		Gremio g = Gremio.nuevo(nombre, tag, color, player.getUUID(), player.getName().getString());
		g.agregarMensaje(sistema(player.getName().getString() + " fundó el gremio"));
		datos.agregar(g);
		actualizarEquipo(player.level().getServer(), g);
		player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.2F);
		aviso(player, "¡Fundaste el gremio " + nombre + " [" + tag + "]! Invita a tus amigos desde el panel (tecla K).", false);
		sincronizar(player, false);
		return true;
	}

	public static boolean invitar(ServerPlayer actor, String nombreJugador) {
		Gremio g = deJugador(actor);
		if (g == null || g.rango(actor.getUUID()) == Gremio.Rango.MIEMBRO) {
			aviso(actor, "Solo el líder y los oficiales pueden invitar.", true);
			return false;
		}
		MinecraftServer server = actor.level().getServer();
		ServerPlayer objetivo = server.getPlayerList().getPlayerByName(nombreJugador.trim());
		if (objetivo == null) {
			aviso(actor, "No hay ningún jugador conectado llamado \"" + nombreJugador.trim() + "\".", true);
			return false;
		}
		if (datos(server).deJugador(objetivo.getUUID()) != null) {
			aviso(actor, objetivo.getName().getString() + " ya está en un gremio.", true);
			return false;
		}
		g.invitados.add(objetivo.getUUID());
		datos(server).setDirty();
		aviso(actor, "Invitaste a " + objetivo.getName().getString() + ".", false);
		objetivo.sendSystemMessage(Component.literal("[Gremios] ").withStyle(ChatFormatting.DARK_AQUA)
			.append(Component.literal(actor.getName().getString() + " te invitó al gremio ").withStyle(ChatFormatting.AQUA))
			.append(etiqueta(g)).append(Component.literal(g.nombre + "  ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal("[Aceptar]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand("/gremio aceptar " + g.tag))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Unirte a " + g.nombre))))));
		objetivo.level().playSound(null, objetivo.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		sincronizar(objetivo, false);
		return true;
	}

	private static void entrar(ServerPlayer player, Gremio g) {
		MinecraftServer server = player.level().getServer();
		for (Gremio otro : datos(server).todos()) {
			otro.invitados.remove(player.getUUID());
		}
		g.miembros.put(player.getUUID(), Gremio.Rango.MIEMBRO);
		g.nombres.put(player.getUUID(), player.getName().getString());
		datos(server).setDirty();
		actualizarEquipo(server, g);
		anunciar(server, g, player.getName().getString() + " se unió al gremio");
		sincronizarGremio(server, g);
	}

	public static boolean aceptar(ServerPlayer player, String idOTag) {
		GremiosData datos = datos(player.level().getServer());
		Gremio g = buscar(datos, idOTag);
		if (g == null || !g.invitados.contains(player.getUUID())) {
			aviso(player, "No tienes una invitación de ese gremio.", true);
			return false;
		}
		if (datos.deJugador(player.getUUID()) != null) {
			aviso(player, "Ya estás en un gremio.", true);
			return false;
		}
		entrar(player, g);
		return true;
	}

	public static void rechazar(ServerPlayer player, String idOTag) {
		Gremio g = buscar(datos(player.level().getServer()), idOTag);
		if (g != null && g.invitados.remove(player.getUUID())) {
			datos(player.level().getServer()).setDirty();
		}
		sincronizar(player, false);
	}

	public static boolean unirse(ServerPlayer player, String idOTag) {
		GremiosData datos = datos(player.level().getServer());
		Gremio g = buscar(datos, idOTag);
		if (g == null || !(g.abierto || g.invitados.contains(player.getUUID()))) {
			aviso(player, "Ese gremio es privado: necesitas una invitación.", true);
			return false;
		}
		if (datos.deJugador(player.getUUID()) != null) {
			aviso(player, "Ya estás en un gremio.", true);
			return false;
		}
		entrar(player, g);
		return true;
	}

	public static void salir(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Gremio g = deJugador(player);
		if (g == null) {
			return;
		}
		if (g.miembros.size() == 1) {
			disolver(player);
			return;
		}
		boolean eraLider = g.rango(player.getUUID()) == Gremio.Rango.LIDER;
		quitarMiembro(server, g, player.getUUID());
		if (eraLider) {
			// el liderazgo pasa al oficial mas antiguo, o al miembro mas antiguo
			UUID nuevo = g.miembros.entrySet().stream().filter(e -> e.getValue() == Gremio.Rango.OFICIAL).map(Map.Entry::getKey).findFirst()
				.orElse(g.miembros.keySet().iterator().next());
			g.miembros.put(nuevo, Gremio.Rango.LIDER);
			anunciar(server, g, g.nombres.getOrDefault(nuevo, "?") + " es el nuevo líder");
		}
		anunciar(server, g, player.getName().getString() + " salió del gremio");
		aviso(player, "Saliste de " + g.nombre + ".", false);
		sincronizarGremio(server, g);
		sincronizar(player, false);
	}

	private static void quitarMiembro(MinecraftServer server, Gremio g, UUID jugador) {
		g.miembros.remove(jugador);
		String nombre = g.nombres.remove(jugador);
		datos(server).setDirty();
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam equipo = scoreboard.getPlayerTeam(g.equipo());
		if (nombre != null && equipo != null && scoreboard.getPlayersTeam(nombre) == equipo) {
			scoreboard.removePlayerFromTeam(nombre, equipo);
		}
	}

	// ============================================================ administracion

	private static @Nullable Gremio comoRango(ServerPlayer actor, Gremio.Rango minimo, String accion) {
		Gremio g = deJugador(actor);
		if (g == null) {
			return null;
		}
		if (g.rango(actor.getUUID()).ordinal() < minimo.ordinal()) {
			aviso(actor, "Solo " + (minimo == Gremio.Rango.LIDER ? "el líder" : "el líder o los oficiales") + " pueden " + accion + ".", true);
			return null;
		}
		return g;
	}

	public static void expulsar(ServerPlayer actor, UUID objetivo) {
		Gremio g = comoRango(actor, Gremio.Rango.OFICIAL, "expulsar");
		if (g == null || !g.miembros.containsKey(objetivo) || objetivo.equals(actor.getUUID())) {
			return;
		}
		if (g.rango(objetivo).ordinal() >= g.rango(actor.getUUID()).ordinal()) {
			aviso(actor, "No puedes expulsar a alguien de tu mismo rango o mayor.", true);
			return;
		}
		MinecraftServer server = actor.level().getServer();
		String nombre = g.nombres.getOrDefault(objetivo, "?");
		quitarMiembro(server, g, objetivo);
		anunciar(server, g, nombre + " fue expulsado por " + actor.getName().getString());
		ServerPlayer expulsado = server.getPlayerList().getPlayer(objetivo);
		if (expulsado != null) {
			aviso(expulsado, "Te expulsaron de " + g.nombre + ".", true);
			sincronizar(expulsado, false);
		}
		sincronizarGremio(server, g);
	}

	public static void cambiarRango(ServerPlayer actor, UUID objetivo, boolean ascender) {
		Gremio g = comoRango(actor, Gremio.Rango.LIDER, "cambiar rangos");
		if (g == null || !g.miembros.containsKey(objetivo) || g.rango(objetivo) == Gremio.Rango.LIDER) {
			return;
		}
		Gremio.Rango nuevo = ascender ? Gremio.Rango.OFICIAL : Gremio.Rango.MIEMBRO;
		if (g.rango(objetivo) == nuevo) {
			return;
		}
		g.miembros.put(objetivo, nuevo);
		MinecraftServer server = actor.level().getServer();
		datos(server).setDirty();
		anunciar(server, g, g.nombres.getOrDefault(objetivo, "?") + " ahora es " + nuevo.nombre());
		sincronizarGremio(server, g);
	}

	public static void transferir(ServerPlayer actor, UUID objetivo) {
		Gremio g = comoRango(actor, Gremio.Rango.LIDER, "pasar el liderazgo");
		if (g == null || !g.miembros.containsKey(objetivo) || objetivo.equals(actor.getUUID())) {
			return;
		}
		g.miembros.put(objetivo, Gremio.Rango.LIDER);
		g.miembros.put(actor.getUUID(), Gremio.Rango.OFICIAL);
		MinecraftServer server = actor.level().getServer();
		datos(server).setDirty();
		anunciar(server, g, g.nombres.getOrDefault(objetivo, "?") + " es el nuevo líder");
		sincronizarGremio(server, g);
	}

	public static void disolver(ServerPlayer actor) {
		Gremio g = comoRango(actor, Gremio.Rango.LIDER, "disolver el gremio");
		if (g == null) {
			return;
		}
		MinecraftServer server = actor.level().getServer();
		// el banco no se pierde: se lo lleva el lider
		SimpleContainer banco = banco(server, g);
		for (ItemStack stack : banco.removeAllItems()) {
			actor.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
		BANCOS.remove(g.id);
		List<UUID> miembros = new ArrayList<>(g.miembros.keySet());
		anunciar(server, g, "El gremio fue disuelto");
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam equipo = scoreboard.getPlayerTeam(g.equipo());
		if (equipo != null) {
			scoreboard.removePlayerTeam(equipo);
		}
		datos(server).quitar(g);
		for (UUID id : miembros) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				sincronizar(p, false);
			}
		}
	}

	public static void ajustes(ServerPlayer actor, int accion, String valor) {
		Gremio.Rango minimo = accion == GremiosPayloads.Accion.DESCRIPCION ? Gremio.Rango.OFICIAL : Gremio.Rango.LIDER;
		Gremio g = comoRango(actor, minimo, "cambiar los ajustes");
		if (g == null) {
			return;
		}
		MinecraftServer server = actor.level().getServer();
		switch (accion) {
			case GremiosPayloads.Accion.COLOR -> {
				try {
					g.color = Math.floorMod(Integer.parseInt(valor), 16);
				} catch (NumberFormatException e) {
					return;
				}
			}
			case GremiosPayloads.Accion.DESCRIPCION -> g.descripcion = valor.length() > 120 ? valor.substring(0, 120) : valor;
			case GremiosPayloads.Accion.ABIERTO -> g.abierto = !g.abierto;
			case GremiosPayloads.Accion.FUEGO_AMIGO -> g.fuegoAmigo = !g.fuegoAmigo;
			default -> {
				return;
			}
		}
		datos(server).setDirty();
		actualizarEquipo(server, g);
		sincronizarGremio(server, g);
	}

	// ============================================================ base y banco

	public static void fijarBase(ServerPlayer actor) {
		Gremio g = comoRango(actor, Gremio.Rango.OFICIAL, "fijar la base");
		if (g == null) {
			return;
		}
		g.base = GlobalPos.of(actor.level().dimension(), actor.blockPosition());
		MinecraftServer server = actor.level().getServer();
		datos(server).setDirty();
		anunciar(server, g, actor.getName().getString() + " fijó la base en " + textoBase(g));
		sincronizarGremio(server, g);
	}

	static SimpleContainer banco(MinecraftServer server, Gremio g) {
		return BANCOS.computeIfAbsent(g.id, id -> {
			SimpleContainer container = new SimpleContainer(Gremio.BANCO) {
				@Override
				public void setChanged() {
					super.setChanged();
					for (int i = 0; i < Gremio.BANCO; i++) {
						g.banco.set(i, this.getItem(i).copy());
					}
					datos(server).setDirty();
				}
			};
			for (int i = 0; i < Gremio.BANCO; i++) {
				container.setItem(i, g.banco.get(i).copy());
			}
			return container;
		});
	}

	public static void abrirBanco(ServerPlayer player) {
		Gremio g = deJugador(player);
		if (g == null) {
			return;
		}
		SimpleContainer container = banco(player.level().getServer(), g);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
			Component.literal("Banco de " + g.nombre)));
	}

	/** Al apagar el servidor los inventarios compartidos se descartan (ya estan guardados en los datos). */
	public static void limpiar() {
		BANCOS.clear();
	}

	// ============================================================ chat

	private static Gremio.Mensaje sistema(String texto) {
		return new Gremio.Mensaje("", texto, System.currentTimeMillis(), true);
	}

	public static void chat(ServerPlayer player, String texto) {
		Gremio g = deJugador(player);
		texto = texto.trim();
		if (g == null) {
			aviso(player, "No estás en ningún gremio.", true);
			return;
		}
		if (texto.isEmpty()) {
			return;
		}
		if (texto.length() > 200) {
			texto = texto.substring(0, 200);
		}
		Gremio.Mensaje m = new Gremio.Mensaje(player.getName().getString(), texto, System.currentTimeMillis(), false);
		enviar(player.level().getServer(), g, m);
	}

	/** Mensaje del sistema para el gremio ("X se unio", etc.). */
	public static void anunciar(MinecraftServer server, Gremio g, String texto) {
		enviar(server, g, sistema(texto));
	}

	private static void enviar(MinecraftServer server, Gremio g, Gremio.Mensaje m) {
		g.agregarMensaje(m);
		datos(server).setDirty();
		Component linea = m.sistema()
			? etiqueta(g).append(Component.literal(m.texto()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
			: etiqueta(g).append(Component.literal(m.autor() + ": ").withStyle(s -> s.withColor(color(g).textColor())))
				.append(Component.literal(m.texto()).withStyle(ChatFormatting.WHITE));
		for (UUID id : g.miembros.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendSystemMessage(linea);
				ServerPlayNetworking.send(p, new GremiosPayloads.Linea(m));
			}
		}
	}

	// ============================================================ equipos del marcador

	/** Crea/actualiza el equipo del marcador: [TAG] y color en el chat, en la lista de jugadores y sobre la cabeza. */
	public static void actualizarEquipo(MinecraftServer server, Gremio g) {
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam equipo = scoreboard.getPlayerTeam(g.equipo());
		if (equipo == null) {
			equipo = scoreboard.addPlayerTeam(g.equipo());
		}
		equipo.setDisplayName(Component.literal(g.nombre));
		equipo.setPlayerPrefix(etiqueta(g));
		equipo.setColor(Optional.of(color(g)));
		equipo.setAllowFriendlyFire(g.fuegoAmigo);
		equipo.setSeeFriendlyInvisibles(true);
		for (String nombre : g.nombres.values()) {
			if (scoreboard.getPlayersTeam(nombre) != equipo) {
				scoreboard.addPlayerToTeam(nombre, equipo);
			}
		}
	}

	/** Al entrar un jugador: actualiza su nombre, lo pone en su equipo y avisa al gremio. */
	public static void alConectar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Gremio g = deJugador(player);
		if (g != null) {
			String nombre = player.getName().getString();
			String anterior = g.nombres.put(player.getUUID(), nombre);
			if (anterior != null && !anterior.equals(nombre)) {
				server.getScoreboard().removePlayerFromTeam(anterior);
			}
			datos(server).setDirty();
			actualizarEquipo(server, g);
			for (UUID id : g.miembros.keySet()) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null && p != player) {
					p.sendSystemMessage(etiqueta(g).append(Component.literal(nombre + " se conectó").withStyle(ChatFormatting.GRAY)));
				}
			}
		}
	}

	// ============================================================ sincronizar con el panel

	private static @Nullable Gremio buscar(GremiosData datos, String idOTag) {
		try {
			Gremio g = datos.porId(UUID.fromString(idOTag));
			if (g != null) {
				return g;
			}
		} catch (IllegalArgumentException ignored) {
			// no es un UUID: buscar por etiqueta
		}
		return datos.porTag(idOTag);
	}

	public static String textoBase(Gremio g) {
		if (g.base == null) {
			return "";
		}
		String dim = g.base.dimension().identifier().getPath().replace("the_", "").replace("overworld", "superficie");
		return g.base.pos().getX() + ", " + g.base.pos().getY() + ", " + g.base.pos().getZ() + " (" + dim + ")";
	}

	/** Base con distancia y direccion desde el jugador (si esta en la misma dimension). */
	public static String textoBase(Gremio g, ServerPlayer player) {
		String texto = textoBase(g);
		if (g.base == null || !g.base.dimension().equals(player.level().dimension())) {
			return texto;
		}
		double dx = g.base.pos().getX() + 0.5 - player.getX();
		double dz = g.base.pos().getZ() + 0.5 - player.getZ();
		long distancia = Math.round(Math.sqrt(dx * dx + dz * dz));
		String[] rosa = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
		double ang = (Math.toDegrees(Math.atan2(-dx, dz)) + 360 + 22.5) % 360;
		return texto + " · a " + distancia + " bloques al " + rosa[(int) (ang / 45) % 8];
	}

	private static GremiosPayloads.Resumen resumen(Gremio g) {
		UUID lider = g.lider();
		return new GremiosPayloads.Resumen(g.id.toString(), g.nombre, g.tag, g.color, g.miembros.size(), g.abierto,
			lider == null ? "" : g.nombres.getOrDefault(lider, "?"));
	}

	public static void sincronizar(ServerPlayer player, boolean abrir) {
		MinecraftServer server = player.level().getServer();
		GremiosData datos = datos(server);
		Gremio mio = datos.deJugador(player.getUUID());
		Optional<GremiosPayloads.GremioVista> vista = Optional.empty();
		if (mio != null) {
			List<GremiosPayloads.MiembroVista> miembros = new ArrayList<>();
			mio.miembros.entrySet().stream()
				.sorted((a, b) -> b.getValue().ordinal() - a.getValue().ordinal())
				.forEach(e -> {
					ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
					String ubicacion = p == null ? "" : p.blockPosition().getX() + ", " + p.blockPosition().getY() + ", " + p.blockPosition().getZ()
						+ " · " + p.level().dimension().identifier().getPath().replace("the_", "").replace("overworld", "superficie");
					miembros.add(new GremiosPayloads.MiembroVista(e.getKey().toString(), mio.nombres.getOrDefault(e.getKey(), "?"),
						e.getValue().ordinal(), p != null, ubicacion));
				});
			vista = Optional.of(new GremiosPayloads.GremioVista(mio.id.toString(), mio.nombre, mio.tag, mio.color, mio.descripcion, mio.abierto,
				mio.fuegoAmigo, textoBase(mio, player), miembros, List.copyOf(mio.chat), mio.rango(player.getUUID()).ordinal()));
		}
		List<GremiosPayloads.Resumen> invitaciones = new ArrayList<>();
		List<GremiosPayloads.Resumen> lista = new ArrayList<>();
		for (Gremio g : datos.todos()) {
			if (g.invitados.contains(player.getUUID())) {
				invitaciones.add(resumen(g));
			}
			lista.add(resumen(g));
		}
		ServerPlayNetworking.send(player, new GremiosPayloads.Estado(abrir, vista, invitaciones, lista));
	}

	/** Manda el estado a todos los miembros conectados (sin abrirles el panel). */
	public static void sincronizarGremio(MinecraftServer server, Gremio g) {
		for (UUID id : g.miembros.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				sincronizar(p, false);
			}
		}
	}
}
