# Direction visuelle HotelSys

- **Produit.** Un poste de réception pour gérer chambres, séjours, paiements et clés.
- **Caractère.** Calme, précis, sobre.
- **Univers.** Le comptoir de réception, son registre et ses clés : un outil de travail lisible.
- **Moments.** Le formulaire de connexion et le planning des chambres.
- **Signature.** La porte arquée du logo existant, avec des icônes de ligne homogènes.

## Poste de réception actuel

Le vert du comptoir distingue la navigation du registre clair. Les numéros de chambre, le nom de l’hôtel et les titres de page utilisent DM Serif Display. Les champs, tableaux, montants et actions utilisent Segoe UI. La couleur et le libellé indiquent ensemble l’état du séjour.

Le tableau de bord rassemble les mouvements du jour et la remise des clés. Le graphique financier suit les indicateurs de la période. Dans le planning, la colonne du jour est soulignée et chaque séjour ouvre son dossier. Le formulaire de réservation garde le total et la confirmation dans un pied de fenêtre fixe. Les petits formulaires prennent la hauteur de leur contenu.

## Référence de connexion précédente

- Le grand panneau vert et ses slogans occupaient davantage de place que la connexion : supprimés.
- Le sous-titre répétait le contexte de l’application : supprimé.
- La typographie éditoriale rendait un formulaire métier trop décoratif : remplacée par Segoe UI / Segoe UI Semibold.
- Les styles comportaient plusieurs corrections successives des mêmes sélecteurs : réécrits dans un seul ensemble cohérent.
- Les icônes et labels des champs manquaient de hiérarchie : icônes secondaires, labels lisibles et bordure de focus commune.

### Référence et reproduction

La [référence générée](login-reference-v3.png) a été créée avec l’outil imagegen intégré avant l’implémentation. Le [prompt exact](login-reference-v3-prompt.txt) fixe la composition, les textes, les couleurs et les exclusions. La capture de l’application est [login-clean.png](login-clean.png).

La carte de connexion mesure 432 px, avec 32 px de padding, un logo d’hôtel, son nom et son classement. Aucun slogan ni panneau secondaire. Les noms, mots de passe, choix de rôle et affichage/masquage sont de vrais contrôles JavaFX. Le formulaire garde le défilement uniquement lorsque la hauteur disponible l’exige.

### Passes de cette version

**Espacement et couleur.** Grille de 4 px ; page à 32 px ; champs de 44 px ; surfaces `#F5F6F8` et blanc ; encre `#252E32` ; accent fonctionnel `#273B34`.

**Icônes et copie.** Une famille de traits ; icônes de personne, cadenas, visibilité et hôtel ; nom et classement réels. Les descriptions promotionnelles ont disparu.

**Type et disposition.** Titres sans empattement de 24–28 px ; corps de 14 px ; labels de 13 px. Formulaire centré et contenu administratif à sa taille de travail.

**Détails et visuels.** Bordures discrètes, coins de 6 px pour les champs et 12 px pour la carte ; aucun effet décoratif. Le visuel généré sert de référence, il n’est pas affiché à la place de l’interface.

**Mouvement.** Curseurs glissants, menus et calendriers par fondu, réponse à la pression ; une courbe commune et réduction du mouvement existante. Aucun mouvement permanent.

Méthode **unslop par Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), CC BY 4.0** ; skill dans `.agents/skills/unslop`. Le logo et les références précédentes restent archivés avec leurs prompts.
