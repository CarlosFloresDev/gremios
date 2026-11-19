package com.aventura.gremios.net;

import com.aventura.gremios.GremiosMod;
import com.aventura.gremios.data.Gremio;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Paquetes del panel de gremios. Se serializan con Codecs (vistas simples, sin registros). */
public final class GremiosPayloads {
	private GremiosPayloads() {
	}

	public record MiembroVista(String uuid, String nombre, int rango, boolean online, String ubicacion) {
		public static final Codec<MiembroVista> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("u").forGetter(MiembroVista::uuid),
			Codec.STRING.fieldOf("n").forGetter(MiembroVista::nombre),
			Codec.INT.fieldOf("r").forGetter(MiembroVista::rango),
			Codec.BOOL.fieldOf("o").forGetter(MiembroVista::online),
			Codec.STRING.fieldOf("p").forGetter(MiembroVista::ubicacion)
		).apply(i, MiembroVista::new));
	}

	public record GremioVista(String id, String nombre, String tag, int color, String descripcion, boolean abierto, boolean fuegoAmigo,
		String base, List<MiembroVista> miembros, List<Gremio.Mensaje> chat, int miRango) {
		public static final Codec<GremioVista> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(GremioVista::id),
			Codec.STRING.fieldOf("nombre").forGetter(GremioVista::nombre),
			Codec.STRING.fieldOf("tag").forGetter(GremioVista::tag),
			Codec.INT.fieldOf("color").forGetter(GremioVista::color),
			Codec.STRING.fieldOf("desc").forGetter(GremioVista::descripcion),
			Codec.BOOL.fieldOf("abierto").forGetter(GremioVista::abierto),
			Codec.BOOL.fieldOf("fuego").forGetter(GremioVista::fuegoAmigo),
			Codec.STRING.fieldOf("base").forGetter(GremioVista::base),
			MiembroVista.CODEC.listOf().fieldOf("miembros").forGetter(GremioVista::miembros),
			Gremio.Mensaje.CODEC.listOf().fieldOf("chat").forGetter(GremioVista::chat),
			Codec.INT.fieldOf("rango").forGetter(GremioVista::miRango)
		).apply(i, GremioVista::new));
	}

	/** Un gremio en las listas (invitaciones o gremios abiertos). */
	public record Resumen(String id, String nombre, String tag, int color, int miembros, boolean abierto, String lider) {
		public static final Codec<Resumen> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Resumen::id),
			Codec.STRING.fieldOf("nombre").forGetter(Resumen::nombre),
			Codec.STRING.fieldOf("tag").forGetter(Resumen::tag),
			Codec.INT.fieldOf("color").forGetter(Resumen::color),
			Codec.INT.fieldOf("miembros").forGetter(Resumen::miembros),
			Codec.BOOL.fieldOf("abierto").forGetter(Resumen::abierto),
			Codec.STRING.fieldOf("lider").forGetter(Resumen::lider)
		).apply(i, Resumen::new));
	}

	/** Servidor -> cliente: todo lo que muestra el panel. */
	public record Estado(boolean abrir, Optional<GremioVista> mio, List<Resumen> invitaciones, List<Resumen> gremios)
		implements CustomPacketPayload {
		public static final Type<Estado> TYPE = new Type<>(GremiosMod.id("estado"));
		private static final Codec<Estado> C = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("abrir").forGetter(Estado::abrir),
			GremioVista.CODEC.optionalFieldOf("mio").forGetter(Estado::mio),
			Resumen.CODEC.listOf().fieldOf("inv").forGetter(Estado::invitaciones),
			Resumen.CODEC.listOf().fieldOf("lista").forGetter(Estado::gremios)
		).apply(i, Estado::new));
		public static final StreamCodec<ByteBuf, Estado> CODEC = ByteBufCodecs.fromCodec(C);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Servidor -> cliente: llego un mensaje al chat del gremio (para el panel abierto). */
	public record Linea(Gremio.Mensaje mensaje) implements CustomPacketPayload {
		public static final Type<Linea> TYPE = new Type<>(GremiosMod.id("linea"));
		public static final StreamCodec<ByteBuf, Linea> CODEC = ByteBufCodecs.fromCodec(Gremio.Mensaje.CODEC).map(Linea::new, Linea::mensaje);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: una accion del panel con hasta dos textos de parametro. */
	public record Accion(int accion, String a, String b) implements CustomPacketPayload {
		public static final Type<Accion> TYPE = new Type<>(GremiosMod.id("accion"));
		public static final StreamCodec<ByteBuf, Accion> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Accion::accion, ByteBufCodecs.stringUtf8(256), Accion::a, ByteBufCodecs.stringUtf8(256), Accion::b, Accion::new);

		public static final int ABRIR = 0;
		public static final int CREAR = 1;
		public static final int INVITAR = 2;
		public static final int ACEPTAR = 3;
		public static final int RECHAZAR = 4;
		public static final int UNIRSE = 5;
		public static final int SALIR = 6;
		public static final int EXPULSAR = 7;
		public static final int ASCENDER = 8;
		public static final int DEGRADAR = 9;
		public static final int TRANSFERIR = 10;
		public static final int DISOLVER = 11;
		public static final int CHAT = 12;
		public static final int COLOR = 13;
		public static final int DESCRIPCION = 14;
		public static final int ABIERTO = 15;
		public static final int FUEGO_AMIGO = 16;
		public static final int FIJAR_BASE = 17;
		public static final int BANCO = 19;
		public static final int REFRESCAR = 20;

		public Accion(int accion) {
			this(accion, "", "");
		}

		public Accion(int accion, String a) {
			this(accion, a, "");
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
