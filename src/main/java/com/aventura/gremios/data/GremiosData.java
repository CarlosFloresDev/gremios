package com.aventura.gremios.data;

import com.aventura.gremios.GremiosMod;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Todos los gremios del mundo (se guardan en data/gremios_gremios.dat del overworld). */
public class GremiosData extends SavedData {
	public static final Codec<GremiosData> CODEC = Gremio.CODEC.listOf().fieldOf("gremios").codec()
		.xmap(GremiosData::new, d -> List.copyOf(d.gremios.values()));
	public static final SavedDataType<GremiosData> TYPE = new SavedDataType<>(GremiosMod.id("gremios"), GremiosData::new, CODEC, null);

	private final Map<UUID, Gremio> gremios = new LinkedHashMap<>();

	public GremiosData() {
	}

	private GremiosData(List<Gremio> lista) {
		for (Gremio g : lista) {
			this.gremios.put(g.id, g);
		}
	}

	public static GremiosData de(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<Gremio> todos() {
		return this.gremios.values();
	}

	public @Nullable Gremio porId(UUID id) {
		return this.gremios.get(id);
	}

	public @Nullable Gremio deJugador(UUID jugador) {
		for (Gremio g : this.gremios.values()) {
			if (g.miembros.containsKey(jugador)) {
				return g;
			}
		}
		return null;
	}

	public @Nullable Gremio porNombre(String nombre) {
		for (Gremio g : this.gremios.values()) {
			if (g.nombre.equalsIgnoreCase(nombre)) {
				return g;
			}
		}
		return null;
	}

	public @Nullable Gremio porTag(String tag) {
		for (Gremio g : this.gremios.values()) {
			if (g.tag.equalsIgnoreCase(tag)) {
				return g;
			}
		}
		return null;
	}

	public void agregar(Gremio g) {
		this.gremios.put(g.id, g);
		this.setDirty();
	}

	public void quitar(Gremio g) {
		this.gremios.remove(g.id);
		this.setDirty();
	}
}
