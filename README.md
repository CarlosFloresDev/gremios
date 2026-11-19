# Gremios

Mod de Fabric para **Minecraft 26.3**: equipos de jugadores (gremios) con su propio panel, rangos, chat interno,
etiqueta de color, base y banco compartido.

## Instalar
Fabric Loader 0.19.5+ y Fabric API para 26.3, y `build/libs/gremios-0.2.0.jar` en `mods`
(en el servidor **y** en cada cliente).

## Cómo se usa
Presiona **K** (o `/gremio`) para abrir el panel.

**Sin gremio**
- **Fundar gremio**: nombre (3 a 24 letras) y etiqueta de 2 a 4 letras (ej. `TEC`, `AQP`).
- **Invitaciones**: acepta ✔ o rechaza ✖. También puedes hacer click en **[Aceptar]** en el mensaje del chat.
- **Gremios del servidor**: si un gremio está "abierto", te unes con **Unirse**.

**Con gremio** (3 pestañas)
- **Miembros**: quién está conectado y dónde (coordenadas y dimensión).
  - El líder sube ▲ o baja ▼ a oficial, pasa el liderazgo ♛ y expulsa ✖.
  - Los oficiales pueden invitar y expulsar miembros.
  - Para invitar escribe el nombre de un jugador conectado y presiona **Invitar**.
- **Chat**: chat interno del gremio con historial (últimos 60 mensajes). Enter para enviar.
  Fuera del panel puedes escribir `/g tu mensaje`.
- **Ajustes**:
  - descripción del gremio;
  - color (16 colores);
  - **abierto a todos** (cualquiera se une sin invitación);
  - **fuego amigo** (si está apagado, los miembros no se pueden herir entre ellos);
  - **Fijar base aquí**: punto de reunión; el panel muestra a cuántos bloques está y en qué dirección;
  - **Banco**: cofre compartido de 27 casillas para todo el gremio;
  - Salir del gremio / Disolver.

**En el juego**: tu etiqueta `[TAG]` aparece con el color del gremio en el chat, en la lista de jugadores (Tab)
y sobre tu cabeza. Los miembros ven a sus compañeros aunque estén invisibles.

**Reglas**
- Si el líder sale, el liderazgo pasa al oficial más antiguo (o al miembro más antiguo).
- Si el gremio se disuelve, el banco pasa al inventario del líder (no se pierde nada).

## Comandos
`/gremio` · `/gremio crear <TAG> <nombre>` · `/gremio invitar <jugador>` · `/gremio aceptar <TAG>` · `/gremio salir` ·
`/gremio banco` · `/g <mensaje>`

## Desarrollo
```
gradlew-d.cmd build              # jar en build/libs
gradlew-d.cmd runGametest        # pruebas: fundar/invitar/aceptar, rangos y expulsión, salir y chat, banco
gradlew-d.cmd runClientGameTest  # capturas del panel (sin gremio, miembros, chat, ajustes)
```
