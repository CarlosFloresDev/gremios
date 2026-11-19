package com.aventura.gremios.client;

import com.aventura.gremios.data.Gremio;
import com.aventura.gremios.net.GremiosPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.TeamColor;

/**
 * Panel de gremios (tecla K). Sin gremio: fundar uno, invitaciones y lista de gremios.
 * Con gremio: pestanas Miembros, Chat y Ajustes.
 */
public class GremiosScreen extends Screen {
	private static final int W = 320;
	private static final int H = 232;
	private static final int FILA = 18;
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
	private static final String[] RANGOS = {"Miembro", "★ Oficial", "♛ Líder"};
	private static final int[] COLOR_RANGO = {0xFF607D8B, 0xFF1E88E5, 0xFFF9A825};

	enum Pestana { MIEMBROS, CHAT, AJUSTES }

	private GremiosPayloads.Estado estado;
	private final List<Gremio.Mensaje> chat = new ArrayList<>();
	private Pestana pestana = Pestana.MIEMBROS;
	private int scroll;
	private boolean confirmarSalida;

	private EditBox entradaChat;
	private EditBox entradaNombre;
	private EditBox entradaTag;
	private EditBox entradaInvitar;
	private EditBox entradaDescripcion;
	private String textoChat = "";
	private String textoNombre = "";
	private String textoTag = "";
	private String textoInvitar = "";
	private String textoDescripcion;

	public GremiosScreen(GremiosPayloads.Estado estado) {
		super(Component.literal("Gremios"));
		this.estado = estado;
		this.estado.mio().ifPresent(m -> this.chat.addAll(m.chat()));
	}

	public void actualizar(GremiosPayloads.Estado nuevo) {
		this.guardarTextos();
		this.estado = nuevo;
		this.chat.clear();
		nuevo.mio().ifPresent(m -> this.chat.addAll(m.chat()));
		this.rebuildWidgets();
	}

	/** Llego un mensaje nuevo al chat (sin reconstruir la pantalla). */
	public void mensaje(Gremio.Mensaje m) {
		this.chat.add(m);
		while (this.chat.size() > Gremio.MAX_CHAT) {
			this.chat.removeFirst();
		}
	}

	private void guardarTextos() {
		if (this.entradaChat != null) {
			this.textoChat = this.entradaChat.getValue();
		}
		if (this.entradaNombre != null) {
			this.textoNombre = this.entradaNombre.getValue();
			this.textoTag = this.entradaTag.getValue();
		}
		if (this.entradaInvitar != null) {
			this.textoInvitar = this.entradaInvitar.getValue();
		}
		if (this.entradaDescripcion != null) {
			this.textoDescripcion = this.entradaDescripcion.getValue();
		}
	}

	private static void accion(int accion, String a, String b) {
		ClientPlayNetworking.send(new GremiosPayloads.Accion(accion, a, b));
	}

	private static void accion(int accion, String a) {
		accion(accion, a, "");
	}

	private static void accion(int accion) {
		accion(accion, "", "");
	}

	private int l() {
		return (this.width - W) / 2;
	}

	private int t() {
		return Math.max(2, (this.height - H) / 2);
	}

	private int miRango() {
		return this.estado.mio().map(GremiosPayloads.GremioVista::miRango).orElse(0);
	}

	// ================================================================== widgets

	@Override
	protected void init() {
		this.entradaChat = null;
		this.entradaNombre = null;
		this.entradaTag = null;
		this.entradaInvitar = null;
		this.entradaDescripcion = null;
		if (this.estado.mio().isEmpty()) {
			this.initSinGremio();
		} else {
			this.initConGremio(this.estado.mio().get());
		}
		this.addRenderableWidget(Button.builder(Component.literal("✕"), b -> this.onClose()).bounds(this.l() + W - 18, this.t() + 2, 16, 16).build());
	}

	private void initSinGremio() {
		int l = this.l();
		int t = this.t();
		this.entradaNombre = new EditBox(this.font, l + 10, t + 50, 140, 16, Component.literal("Nombre"));
		this.entradaNombre.setMaxLength(24);
		this.entradaNombre.setHint(Component.literal("Nombre (ej. Los Misti)").withStyle(ChatFormatting.DARK_GRAY));
		this.entradaNombre.setValue(this.textoNombre);
		this.addRenderableWidget(this.entradaNombre);
		this.entradaTag = new EditBox(this.font, l + 10, t + 84, 60, 16, Component.literal("Etiqueta"));
		this.entradaTag.setMaxLength(4);
		this.entradaTag.setHint(Component.literal("TAG").withStyle(ChatFormatting.DARK_GRAY));
		this.entradaTag.setValue(this.textoTag);
		this.addRenderableWidget(this.entradaTag);
		this.addRenderableWidget(Button.builder(Component.literal("Fundar gremio"), b -> {
			this.guardarTextos();
			accion(GremiosPayloads.Accion.CREAR, this.textoNombre, this.textoTag);
		}).bounds(l + 10, t + 108, 140, 20).build());

		// invitaciones y gremios del servidor
		int x = l + 166;
		int y = t + 38;
		for (GremiosPayloads.Resumen r : this.estado.invitaciones()) {
			if (y > t + 100) {
				break;
			}
			this.addRenderableWidget(Button.builder(Component.literal("✔"), b -> accion(GremiosPayloads.Accion.ACEPTAR, r.id()))
				.bounds(x + 110, y, 18, 16).tooltip(Tooltip.create(Component.literal("Aceptar invitación"))).build());
			this.addRenderableWidget(Button.builder(Component.literal("✖"), b -> accion(GremiosPayloads.Accion.RECHAZAR, r.id()))
				.bounds(x + 130, y, 18, 16).tooltip(Tooltip.create(Component.literal("Rechazar"))).build());
			y += FILA;
		}
		List<GremiosPayloads.Resumen> lista = this.estado.gremios();
		int fy = t + 128;
		for (int i = this.scroll; i < lista.size() && fy < t + H - 24; i++) {
			GremiosPayloads.Resumen r = lista.get(i);
			if (r.abierto()) {
				this.addRenderableWidget(Button.builder(Component.literal("Unirse"), b -> accion(GremiosPayloads.Accion.UNIRSE, r.id()))
					.bounds(x + 104, fy, 44, 16).build());
			}
			fy += FILA;
		}
	}

	private void initConGremio(GremiosPayloads.GremioVista g) {
		int l = this.l();
		int t = this.t();
		// pestanas
		int px = l + 8;
		for (Pestana p : Pestana.values()) {
			String nombre = switch (p) {
				case MIEMBROS -> "Miembros (" + g.miembros().size() + ")";
				case CHAT -> "Chat";
				case AJUSTES -> "Ajustes";
			};
			Button b = Button.builder(Component.literal(nombre), x -> {
				this.guardarTextos();
				this.pestana = p;
				this.scroll = 0;
				this.confirmarSalida = false;
				this.rebuildWidgets();
			}).bounds(px, t + 22, 98, 18).build();
			b.active = this.pestana != p;
			this.addRenderableWidget(b);
			px += 102;
		}
		switch (this.pestana) {
			case MIEMBROS -> this.initMiembros(g);
			case CHAT -> this.initChat();
			case AJUSTES -> this.initAjustes(g);
		}
	}

	private int filasMiembros() {
		return (H - 46 - 30) / FILA;
	}

	private void initMiembros(GremiosPayloads.GremioVista g) {
		int l = this.l();
		int t = this.t();
		int yo = this.miRango();
		List<GremiosPayloads.MiembroVista> miembros = g.miembros();
		int y = t + 46;
		for (int i = this.scroll; i < miembros.size() && i < this.scroll + this.filasMiembros(); i++) {
			GremiosPayloads.MiembroVista m = miembros.get(i);
			int x = l + W - 10;
			boolean soyYo = this.minecraft.player != null && this.minecraft.player.getUUID().toString().equals(m.uuid());
			if (!soyYo && yo > m.rango()) {
				x -= 18;
				this.addRenderableWidget(Button.builder(Component.literal("✖").withStyle(ChatFormatting.RED),
						b -> accion(GremiosPayloads.Accion.EXPULSAR, m.uuid())).bounds(x, y, 16, 16)
					.tooltip(Tooltip.create(Component.literal("Expulsar a " + m.nombre()))).build());
			}
			if (!soyYo && yo == 2) {
				x -= 18;
				this.addRenderableWidget(Button.builder(Component.literal("♛"), b -> accion(GremiosPayloads.Accion.TRANSFERIR, m.uuid()))
					.bounds(x, y, 16, 16).tooltip(Tooltip.create(Component.literal("Hacer líder a " + m.nombre() + " (tú pasas a oficial)"))).build());
				x -= 18;
				boolean esOficial = m.rango() == 1;
				this.addRenderableWidget(Button.builder(Component.literal(esOficial ? "▼" : "▲"),
						b -> accion(esOficial ? GremiosPayloads.Accion.DEGRADAR : GremiosPayloads.Accion.ASCENDER, m.uuid()))
					.bounds(x, y, 16, 16).tooltip(Tooltip.create(Component.literal(esOficial ? "Bajar a miembro" : "Subir a oficial"))).build());
			}
			y += FILA;
		}
		if (yo >= 1) {
			this.entradaInvitar = new EditBox(this.font, l + 10, t + H - 24, 190, 18, Component.literal("Jugador"));
			this.entradaInvitar.setMaxLength(16);
			this.entradaInvitar.setHint(Component.literal("Nombre del jugador a invitar").withStyle(ChatFormatting.DARK_GRAY));
			this.entradaInvitar.setValue(this.textoInvitar);
			this.addRenderableWidget(this.entradaInvitar);
			this.addRenderableWidget(Button.builder(Component.literal("Invitar"), b -> {
				accion(GremiosPayloads.Accion.INVITAR, this.entradaInvitar.getValue());
				this.entradaInvitar.setValue("");
				this.textoInvitar = "";
			}).bounds(l + 206, t + H - 25, 104, 20).build());
		}
	}

	private void initChat() {
		int l = this.l();
		int t = this.t();
		this.entradaChat = new EditBox(this.font, l + 10, t + H - 24, 240, 18, Component.literal("Mensaje"));
		this.entradaChat.setMaxLength(200);
		this.entradaChat.setHint(Component.literal("Escribe al gremio y presiona Enter").withStyle(ChatFormatting.DARK_GRAY));
		this.entradaChat.setValue(this.textoChat);
		this.addRenderableWidget(this.entradaChat);
		this.setInitialFocus(this.entradaChat);
		this.addRenderableWidget(Button.builder(Component.literal("Enviar"), b -> this.enviarChat()).bounds(l + 256, t + H - 25, 54, 20).build());
	}

	private void enviarChat() {
		if (this.entradaChat != null && !this.entradaChat.getValue().isBlank()) {
			accion(GremiosPayloads.Accion.CHAT, this.entradaChat.getValue());
			this.entradaChat.setValue("");
			this.textoChat = "";
			this.scroll = 0;
		}
	}

	private void initAjustes(GremiosPayloads.GremioVista g) {
		int l = this.l();
		int t = this.t();
		int yo = this.miRango();
		// descripcion
		this.entradaDescripcion = new EditBox(this.font, l + 10, t + 58, 230, 18, Component.literal("Descripción"));
		this.entradaDescripcion.setMaxLength(120);
		this.entradaDescripcion.setValue(this.textoDescripcion != null ? this.textoDescripcion : g.descripcion());
		this.entradaDescripcion.setHint(Component.literal("Mensaje del gremio (ej. ¡Reunión en la base!)").withStyle(ChatFormatting.DARK_GRAY));
		this.entradaDescripcion.setEditable(yo >= 1);
		this.addRenderableWidget(this.entradaDescripcion);
		Button guardar = Button.builder(Component.literal("Guardar"), b -> {
			accion(GremiosPayloads.Accion.DESCRIPCION, this.entradaDescripcion.getValue());
			this.textoDescripcion = null;
		}).bounds(l + 246, t + 57, 64, 20).build();
		guardar.active = yo >= 1;
		this.addRenderableWidget(guardar);

		// colores (solo lider): 16 cuadritos, los dibuja extractRenderState
		// interruptores
		Button abierto = Button.builder(Component.literal("Abierto a todos: " + (g.abierto() ? "Sí" : "No")),
				b -> accion(GremiosPayloads.Accion.ABIERTO)).bounds(l + 10, t + 124, 148, 20)
			.tooltip(Tooltip.create(Component.literal("Si está abierto, cualquiera puede unirse sin invitación"))).build();
		abierto.active = yo == 2;
		this.addRenderableWidget(abierto);
		Button fuego = Button.builder(Component.literal("Fuego amigo: " + (g.fuegoAmigo() ? "Sí" : "No")),
				b -> accion(GremiosPayloads.Accion.FUEGO_AMIGO)).bounds(l + 162, t + 124, 148, 20)
			.tooltip(Tooltip.create(Component.literal("Si está activado, los miembros pueden hacerse daño entre ellos"))).build();
		fuego.active = yo == 2;
		this.addRenderableWidget(fuego);

		// base y banco
		Button fijar = Button.builder(Component.literal("Fijar base aquí"), b -> accion(GremiosPayloads.Accion.FIJAR_BASE))
			.bounds(l + 10, t + 162, 148, 20)
			.tooltip(Tooltip.create(Component.literal("Guarda este lugar como punto de reunión del gremio"))).build();
		fijar.active = yo >= 1;
		this.addRenderableWidget(fijar);
		this.addRenderableWidget(Button.builder(Component.literal("Banco del gremio"), b -> accion(GremiosPayloads.Accion.BANCO))
			.bounds(l + 162, t + 162, 148, 20).tooltip(Tooltip.create(Component.literal("Cofre compartido de 27 casillas para todo el gremio"))).build());

		// salir / disolver
		boolean lider = yo == 2;
		boolean solo = g.miembros().size() == 1;
		String texto = this.confirmarSalida ? "¿Seguro? Click otra vez" : lider && solo ? "Disolver gremio" : "Salir del gremio";
		this.addRenderableWidget(Button.builder(Component.literal(texto).withStyle(ChatFormatting.RED), b -> {
			if (!this.confirmarSalida) {
				this.confirmarSalida = true;
				this.rebuildWidgets();
			} else {
				accion(GremiosPayloads.Accion.SALIR);
				this.confirmarSalida = false;
			}
		}).bounds(l + 10, t + H - 26, 148, 20).build());
		if (lider && !solo) {
			this.addRenderableWidget(Button.builder(Component.literal("Disolver gremio").withStyle(ChatFormatting.DARK_RED),
					b -> accion(GremiosPayloads.Accion.DISOLVER)).bounds(l + 162, t + H - 26, 148, 20)
				.tooltip(Tooltip.create(Component.literal("Borra el gremio para todos. El banco pasa a tu inventario."))).build());
		}
	}

	// ================================================================== entrada

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.entradaChat != null && this.entradaChat.isFocused()
			&& (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			this.enviarChat();
			return true;
		}
		boolean escribiendo = this.getFocused() instanceof EditBox;
		if (!escribiendo && GremiosClient.ABRIR.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int max = switch (this.pestana) {
			case MIEMBROS -> this.estado.mio().map(g -> Math.max(0, g.miembros().size() - this.filasMiembros())).orElse(0);
			case CHAT -> Math.max(0, this.chat.size() - 1);
			case AJUSTES -> 0;
		};
		if (this.estado.mio().isEmpty()) {
			max = Math.max(0, this.estado.gremios().size() - 4);
		}
		int nuevo = Math.max(0, Math.min(max, this.scroll + (scrollY > 0 ? (this.pestana == Pestana.CHAT ? 1 : -1) : (this.pestana == Pestana.CHAT ? -1 : 1))));
		if (nuevo != this.scroll) {
			this.scroll = nuevo;
			this.guardarTextos();
			this.rebuildWidgets();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		// cuadritos de color (solo lider, pestana ajustes)
		if (this.estado.mio().isPresent() && this.pestana == Pestana.AJUSTES && this.miRango() == 2) {
			for (int i = 0; i < 16; i++) {
				int cx = this.l() + 10 + i * 18;
				int cy = this.t() + 96;
				if (event.x() >= cx && event.x() < cx + 16 && event.y() >= cy && event.y() < cy + 12) {
					accion(GremiosPayloads.Accion.COLOR, Integer.toString(i));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================== dibujo

	private static int rgb(int color) {
		return 0xFF000000 | TeamColor.VALUES.get(Math.floorMod(color, 16)).rgb();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int l = this.l();
		int t = this.t();
		g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFF101418);
		g.fill(l, t, l + W, t + H, 0xF0ECEFF1);
		if (this.estado.mio().isEmpty()) {
			g.fill(l, t, l + W, t + 20, 0xFF00838F);
			g.text(this.font, Component.literal("⚑ Gremios").withStyle(ChatFormatting.BOLD), l + 8, t + 6, 0xFFFFFFFF);
			this.dibujarSinGremio(g);
		} else {
			GremiosPayloads.GremioVista gv = this.estado.mio().get();
			g.fill(l, t, l + W, t + 20, rgb(gv.color()));
			g.fill(l, t + 18, l + W, t + 20, 0x40000000);
			g.text(this.font, Component.literal("[" + gv.tag() + "] " + gv.nombre()).withStyle(ChatFormatting.BOLD), l + 8, t + 6, 0xFFFFFFFF);
			switch (this.pestana) {
				case MIEMBROS -> this.dibujarMiembros(g, gv);
				case CHAT -> this.dibujarChat(g, gv);
				case AJUSTES -> this.dibujarAjustes(g, gv);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void dibujarSinGremio(GuiGraphicsExtractor g) {
		int l = this.l();
		int t = this.t();
		g.text(this.font, Component.literal("Fundar un gremio").withStyle(ChatFormatting.BOLD), l + 10, t + 26, 0xFF263238);
		g.text(this.font, "Nombre (3 a 24 letras)", l + 10, t + 40, 0xFF607D8B, false);
		g.text(this.font, "Etiqueta (2 a 4: TEC, AQP...)", l + 10, t + 74, 0xFF607D8B, false);
		List<FormattedCharSequence> ayuda = this.font.split(Component.literal(
			"Con un gremio tienes chat interno, la etiqueta [TAG] de color, una base, un banco compartido y rangos."), 144);
		int y = t + 136;
		for (FormattedCharSequence linea : ayuda) {
			g.text(this.font, linea, l + 10, y, 0xFF78909C, false);
			y += 10;
		}
		g.fill(l + 160, t + 24, l + 161, t + H - 6, 0xFFB0BEC5);

		int x = l + 166;
		g.text(this.font, Component.literal("Invitaciones").withStyle(ChatFormatting.BOLD), x, t + 26, 0xFF263238);
		if (this.estado.invitaciones().isEmpty()) {
			g.text(this.font, "No tienes invitaciones", x, t + 40, 0xFF90A4AE, false);
		}
		int fy = t + 38;
		for (GremiosPayloads.Resumen r : this.estado.invitaciones()) {
			if (fy > t + 100) {
				break;
			}
			g.text(this.font, Component.literal("[" + r.tag() + "] ").withStyle(s -> s.withColor(rgb(r.color()) & 0xFFFFFF))
				.append(Component.literal(this.font.plainSubstrByWidth(r.nombre(), 70)).withStyle(ChatFormatting.BLACK)), x, fy + 4, 0xFF263238, false);
			fy += FILA;
		}
		g.text(this.font, Component.literal("Gremios del servidor").withStyle(ChatFormatting.BOLD), x, t + 116, 0xFF263238);
		List<GremiosPayloads.Resumen> lista = this.estado.gremios();
		if (lista.isEmpty()) {
			g.text(this.font, "Todavía no hay ninguno", x, t + 130, 0xFF90A4AE, false);
		}
		fy = t + 128;
		for (int i = this.scroll; i < lista.size() && fy < t + H - 24; i++) {
			GremiosPayloads.Resumen r = lista.get(i);
			g.text(this.font, Component.literal("[" + r.tag() + "] ").withStyle(s -> s.withColor(rgb(r.color()) & 0xFFFFFF)), x, fy + 4, 0xFF263238, false);
			String cuenta = " (" + r.miembros() + ")";
			String nombre = this.font.plainSubstrByWidth(r.nombre(), 70 - this.font.width(cuenta));
			g.text(this.font, nombre + cuenta, x + 30, fy + 4, 0xFF263238, false);
			if (!r.abierto()) {
				g.text(this.font, "privado", x + 148 - this.font.width("privado"), fy + 4, 0xFF90A4AE, false);
			}
			fy += FILA;
		}
	}

	private void dibujarMiembros(GuiGraphicsExtractor g, GremiosPayloads.GremioVista gv) {
		int l = this.l();
		int t = this.t();
		List<GremiosPayloads.MiembroVista> miembros = gv.miembros();
		int y = t + 46;
		for (int i = this.scroll; i < miembros.size() && i < this.scroll + this.filasMiembros(); i++) {
			GremiosPayloads.MiembroVista m = miembros.get(i);
			if (i % 2 == 0) {
				g.fill(l + 6, y - 1, l + W - 6, y + FILA - 1, 0xFFE0E6EA);
			}
			g.fill(l + 10, y + 5, l + 16, y + 11, m.online() ? 0xFF43A047 : 0xFF9E9E9E);
			g.text(this.font, RANGOS[Math.min(2, m.rango())], l + 20, y + 4, COLOR_RANGO[Math.min(2, m.rango())], false);
			g.text(this.font, m.nombre(), l + 76, y + 4, 0xFF212121, false);
			String donde = m.online() ? m.ubicacion() : "desconectado";
			g.text(this.font, this.font.plainSubstrByWidth(donde, 110), l + 160, y + 4, 0xFF78909C, false);
			y += FILA;
		}
		if (this.miRango() == 0) {
			g.text(this.font, "Solo el líder y los oficiales pueden invitar", l + 10, t + H - 18, 0xFF90A4AE, false);
		}
	}

	private void dibujarChat(GuiGraphicsExtractor g, GremiosPayloads.GremioVista gv) {
		int l = this.l();
		int t = this.t();
		int arriba = t + 44;
		int abajo = t + H - 30;
		g.fill(l + 6, arriba - 2, l + W - 6, abajo + 2, 0xFF263238);
		// armar las lineas (con ajuste de linea) y mostrar las ultimas que entran
		List<FormattedCharSequence> lineas = new ArrayList<>();
		List<Integer> colores = new ArrayList<>();
		for (Gremio.Mensaje m : this.chat) {
			Component texto = m.sistema()
				? Component.literal(HORA.format(Instant.ofEpochMilli(m.tiempo())) + " · " + m.texto()).withStyle(ChatFormatting.ITALIC)
				: Component.literal(HORA.format(Instant.ofEpochMilli(m.tiempo())) + " ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(m.autor() + ": ").withStyle(s -> s.withColor(rgb(gv.color()) & 0xFFFFFF)))
					.append(Component.literal(m.texto()).withStyle(ChatFormatting.WHITE));
			for (FormattedCharSequence linea : this.font.split(texto, W - 24)) {
				lineas.add(linea);
				colores.add(m.sistema() ? 0xFF90A4AE : 0xFFFFFFFF);
			}
		}
		int visibles = (abajo - arriba) / 10;
		int fin = Math.max(0, lineas.size() - this.scroll);
		int inicio = Math.max(0, fin - visibles);
		int y = abajo - (fin - inicio) * 10;
		for (int i = inicio; i < fin; i++) {
			g.text(this.font, lineas.get(i), l + 10, y, colores.get(i), false);
			y += 10;
		}
		if (lineas.isEmpty()) {
			g.centeredText(this.font, "Todavía no hay mensajes. ¡Saluda!", l + W / 2, (arriba + abajo) / 2, 0xFF90A4AE);
		}
	}

	private void dibujarAjustes(GuiGraphicsExtractor g, GremiosPayloads.GremioVista gv) {
		int l = this.l();
		int t = this.t();
		int yo = this.miRango();
		g.text(this.font, "Descripción" + (yo >= 1 ? "" : " (solo líder y oficiales)"), l + 10, t + 47, 0xFF455A64, false);
		g.text(this.font, "Color" + (yo == 2 ? "" : " (solo el líder)"), l + 10, t + 84, 0xFF455A64, false);
		for (int i = 0; i < 16; i++) {
			int cx = l + 10 + i * 18;
			int cy = t + 96;
			if (i == gv.color()) {
				g.fill(cx - 2, cy - 2, cx + 18, cy + 14, 0xFF000000);
			}
			g.fill(cx, cy, cx + 16, cy + 12, rgb(i));
		}
		g.text(this.font, "Base: " + (gv.base().isEmpty() ? "sin fijar" : gv.base()), l + 10, t + 150, 0xFF455A64, false);
		g.text(this.font, "Tu rango: " + RANGOS[Math.min(2, yo)], l + 10, t + H - 40, COLOR_RANGO[Math.min(2, yo)], false);
	}
}
