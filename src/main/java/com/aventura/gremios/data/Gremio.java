package com.aventura.gremios.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Un gremio (equipo de jugadores): miembros con rango, chat interno, base, banco compartido y ajustes. */
public class Gremio {
	public static final int MAX_CHAT = 60;
	public static final int BANCO = 27;

	public enum Rango implements StringRepresentable {
		MIEMBRO, OFICIAL, LIDER;

		public static final Codec<Rango> CODEC = StringRepresentable.fromEnum(Rango::values);

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase();
		}

		public String nombre() {
			return switch (this) {
				case LIDER -> "Líder";
				case OFICIAL -> "Oficial";
				case MIEMBRO -> "Miembro";
			};
		}
	}

	/** Mensaje del chat del gremio. */
	public record Mensaje(String autor, String texto, long tiempo, boolean sistema) {
		public static final Codec<Mensaje> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("autor").forGetter(Mensaje::autor),
			Codec.STRING.fieldOf("texto").forGetter(Mensaje::texto),
			Codec.LONG.fieldOf("tiempo").forGetter(Mensaje::tiempo),
			Codec.BOOL.fieldOf("sistema").forGetter(Mensaje::sistema)
		).apply(i, Mensaje::new));
	}

	public static final Codec<Gremio> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(g -> g.id),
		Codec.STRING.fieldOf("nombre").forGetter(g -> g.nombre),
		Codec.STRING.fieldOf("tag").forGetter(g -> g.tag),
		Codec.INT.fieldOf("color").forGetter(g -> g.color),
		Codec.STRING.optionalFieldOf("descripcion", "").forGetter(g -> g.descripcion),
		Codec.BOOL.optionalFieldOf("abierto", false).forGetter(g -> g.abierto),
		Codec.BOOL.optionalFieldOf("fuego_amigo", false).forGetter(g -> g.fuegoAmigo),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Rango.CODEC).fieldOf("miembros").forGetter(g -> g.miembros),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).fieldOf("nombres").forGetter(g -> g.nombres),
		GlobalPos.CODEC.optionalFieldOf("base").forGetter(g -> Optional.ofNullable(g.base)),
		Mensaje.CODEC.listOf().optionalFieldOf("chat", List.of()).forGetter(g -> g.chat),
		UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("invitados", List.of()).forGetter(g -> List.copyOf(g.invitados)),
		ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("banco", List.of()).forGetter(g -> g.banco)
	).apply(i, Gremio::new));

	public final UUID id;
	public String nombre;
	public String tag;
	/** Color de ChatFormatting (0-15). */
	public int color;
	public String descripcion;
	/** Cualquiera puede unirse sin invitacion. */
	public boolean abierto;
	/** Si los miembros se pueden hacer dano entre ellos. */
	public boolean fuegoAmigo;
	public final Map<UUID, Rango> miembros;
	/** Ultimo nombre conocido de cada miembro (para mostrarlo aunque este desconectado). */
	public final Map<UUID, String> nombres;
	public @Nullable GlobalPos base;
	public final List<Mensaje> chat;
	public final Set<UUID> invitados;
	public final NonNullList<ItemStack> banco = NonNullList.withSize(BANCO, ItemStack.EMPTY);

	private Gremio(UUID id, String nombre, String tag, int color, String descripcion, boolean abierto, boolean fuegoAmigo,
		Map<UUID, Rango> miembros, Map<UUID, String> nombres, Optional<GlobalPos> base, List<Mensaje> chat, List<UUID> invitados,
		List<ItemStack> banco) {
		this.id = id;
		this.nombre = nombre;
		this.tag = tag;
		this.color = color;
		this.descripcion = descripcion;
		this.abierto = abierto;
		this.fuegoAmigo = fuegoAmigo;
		this.miembros = new LinkedHashMap<>(miembros);
		this.nombres = new LinkedHashMap<>(nombres);
		this.base = base.orElse(null);
		this.chat = new ArrayList<>(chat);
		this.invitados = new HashSet<>(invitados);
		for (int i = 0; i < Math.min(BANCO, banco.size()); i++) {
			this.banco.set(i, banco.get(i));
		}
	}

	public static Gremio nuevo(String nombre, String tag, int color, UUID lider, String nombreLider) {
		Gremio g = new Gremio(UUID.randomUUID(), nombre, tag, color, "", false, false, Map.of(), Map.of(), Optional.empty(), List.of(),
			List.of(), List.of());
		g.miembros.put(lider, Rango.LIDER);
		g.nombres.put(lider, nombreLider);
		return g;
	}

	public Rango rango(UUID jugador) {
		return this.miembros.getOrDefault(jugador, Rango.MIEMBRO);
	}

	public @Nullable UUID lider() {
		return this.miembros.entrySet().stream().filter(e -> e.getValue() == Rango.LIDER).map(Map.Entry::getKey).findFirst().orElse(null);
	}

	public void agregarMensaje(Mensaje m) {
		this.chat.add(m);
		while (this.chat.size() > MAX_CHAT) {
			this.chat.removeFirst();
		}
	}

	/** Nombre del equipo del marcador (scoreboard) que pone el [TAG] y el color en el chat y sobre la cabeza. */
	public String equipo() {
		return "gremio_" + this.id.toString().substring(0, 8);
	}
}
