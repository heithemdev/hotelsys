# Direction visuelle HotelSys

Un outil de réception : calme, précis, accueillant. Le calendrier des séjours est le point fort visuel, comme un registre de réception lisible en un regard.

- Police des contrôles : Manrope, embarquée dans l'application.
- Titres : DM Serif Display, embarquée, pour rappeler les enseignes et registres d'hôtel.
- Fond porcelaine `#F6F5F1`, surfaces blanches, encre aubergine `#352E3A`, texte secondaire `#757078`.
- Action principale : terre cuite `#A94F32`. Séjours réservés : lavande grisée ; séjours en cours : sauge ; terminés : gris chaud.
- Navigation compacte de 232 px, espacements sur une grille de 4 px, contrôles de 36–40 px, coins de 8–12 px.
- Signature : une porte d'hôtel arquée ; pas de dégradés, d'émojis ni de décorations dans les tableaux.
- Mouvement : fondu de 220 ms et translation de 8 px à l'ouverture d'une page, sans animation permanente.

La maquette `calendar-reference.png` et le logo dans `src/main/resources/images/logo.png` ont été générés avec l'outil imagegen intégré. Les écrans JavaFX reprennent la composition de la maquette ; les informations restent de vrais contrôles et données.

Unslop par Damir Sokolovsky (@heydamir), CC BY 4.0, est installé localement dans `.agents/skills/unslop`. Ponytail était déjà installé pour cet environnement. Les nouvelles installations de skills seront détectées au prochain tour ; leurs instructions ont aussi été lues pour ce travail.
