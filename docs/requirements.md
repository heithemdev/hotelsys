# Besoins fonctionnels

## Contexte et objectif

Mini-projet de génie logiciel : gestion des réservations d'hôtel, personnes adultes/enfants, chambres et prix, avec besoins fonctionnels, diagrammes de cas d'utilisation et de classes sous StarUML, modèle relationnel et script SQL. L'énoncé original se trouve dans [assignment.png](assignment.png).

HotelSys concerne un hôtel, un poste de réception et une caisse physique. Les utilisateurs applicatifs partagent ce poste ; chacun possède son propre compte. L'interface est française, la monnaie est le DZD et le calendrier métier utilise `Africa/Algiers`.

## Acteurs et permissions

| Fonction | Réceptionniste | Administrateur |
|---|---|---|
| Se connecter, se déconnecter | Oui | Oui |
| Créer/corriger les fiches des clients | Oui | Oui |
| Chercher une chambre disponible et établir un devis | Oui | Oui |
| Réserver plusieurs chambres avec des dates différentes | Oui | Oui |
| Vérifier les documents familiaux à l'accueil | Oui | Oui |
| Enregistrer arrivée, départ et annulation avant arrivée | Oui | Oui |
| Encaisser un paiement et consulter le solde d'une réservation | Oui | Oui |
| Ouvrir/clôturer sa caisse | Oui | Oui |
| Consulter/clôturer la caisse d'un autre employé | Non | Oui |
| Rembourser ou enregistrer une dépense | Non | Oui |
| Gérer comptes, modèles, chambres, tarifs et politiques | Non | Oui |
| Consulter les statistiques globales | Non | Oui |

Le premier compte est administrateur. Les comptes utilisés sont désactivés plutôt qu'effacés ; le dernier administrateur actif reste protégé.

## Chambres et modèles

- Un modèle définit un nom, un nombre de lits, une limite d'enfants partageant sans lit et une description des équipements : télévision, balcon, jacuzzi, etc.
- Une chambre physique possède un numéro unique, un modèle, un prix fixe par nuit et un état actif/inactif.
- Une chambre inutilisée peut être supprimée. Une chambre liée à une réservation reste conservée.
- La désactivation empêche de nouvelles réservations ; elle conserve et honore les séjours déjà enregistrés.
- Un lit correspond à une place nécessitant un lit dans cette version.

## Personnes, âges et admission

- Une personne possède un nom, un prénom, une naissance et un sexe pour les règles d'admission demandées. Téléphone et e-mail sont facultatifs.
- Le titulaire doit avoir **19 ans pour un homme, 21 ans pour une femme**, à la date de réservation. Il peut réserver pour des accompagnants plus jeunes et ne doit pas nécessairement occuper chaque chambre.
- Ces seuils constituent une politique de projet demandée par le commanditaire, sans qualification juridique.
- **Enfant sans lit : moins de 6 ans par défaut**, seuil modifiable par l'administrateur. À son sixième anniversaire, l'enfant nécessite un lit.
- **Tarif enfant des repas : moins de 12 ans par défaut**, seuil distinct et modifiable.
- La capacité et le tarif des accompagnants utilisent l'âge à l'arrivée de chaque chambre. Pour une naissance le 29 février, l'anniversaire tombe le 28 février en année non bissextile.
- La limite d'enfants sans lit vaut 1 par défaut dans chaque modèle. Exemple : modèle de deux lits et allowance de 1 → deux adultes + un enfant de 5 ans sont acceptés ; deux adultes + un enfant de 6 ans nécessitent trois lits.
- Tous les occupants sont nommés lors de la réservation. Un même occupant ne peut avoir deux séjours qui se chevauchent.
- Pour une chambre avec occupants masculins et féminins, la réception confirme avant l'arrivée qu'il s'agit d'époux ou de parents par le sang. Un groupe familial peut combiner les deux situations.
- La vérification conserve la base de parenté, le type de document, l'employé, l'instant et une note facultative. Aucune copie ni numéro de document n'est stocké.
- L'absence de vérification bloque l'arrivée, pas la réservation. Une modification des occupants, des dates ou de l'identité pertinente invalide une vérification en attente.

## Réservations

- Une réservation regroupe une ou plusieurs lignes de chambres, chacune avec ses dates et son état propres.
- États d'une ligne : `RESERVED → CHECKED_IN → CHECKED_OUT` ; `RESERVED → CANCELLED`. Une annulation libère la chambre. Une réactivation SQL doit de nouveau respecter la disponibilité.
- La date de départ est exclue : départ le 30, nouvelle arrivée le 30 autorisée.
- Un chevauchement existe si `arrivée existante < nouveau départ` et `départ existant > nouvelle arrivée`.
- Les séjours annulés ne bloquent pas la disponibilité ; les séjours terminés restent protégés contre les doublons historiques.
- L'arrivée s'enregistre à partir de la date prévue, avant le départ. Un séjour commencé conserve sa chambre, ses dates et ses occupants dans cette version. Le départ anticipé ne réduit pas automatiquement le forfait réservé.
- Une réservation avec plusieurs chambres est atomique : si une ligne échoue, aucune partie de cette nouvelle réservation n'est conservée.
- Une modification du séjour nécessite un nouveau devis explicite aux tarifs actuels. La correction de naissance/sexe d'une personne liée à un séjour actif est bloquée ; corriger le séjour concerné d'abord.

## Hébergement et repas

L'hébergement est facturé **par chambre et par nuit**, indépendamment des lits vides et sans remise enfant. Un enfant sans lit ne crée aucun supplément d'hébergement.

L'administrateur définit, dans chaque modèle, les tarifs du petit-déjeuner, déjeuner et dîner. Chaque repas possède un prix adulte et **soit** un prix enfant fixe, **soit** un pourcentage de réduction. Un repas sans tarif configuré ne peut pas être choisi.

Les repas sont sélectionnés pour une chambre réservée entière. Ils sont facturés par personne, y compris les enfants sans lit, selon leur tarif applicable, une fois par nuit réservée.

```text
prix enfant d'un repas = prix fixe
                      OU arrondi(prix adulte × (100 − remise) / 100)
repas quotidiens       = somme des repas choisis pour chaque occupant
total chambre         = nuits × (prix chambre/nuit + repas quotidiens)
total réservation     = somme des chambres non annulées
solde                 = total réservation − paiements + remboursements
```

L'arrondi est au centime, au plus proche, les demis vers le haut (`HALF_UP`). Les prix et politiques acceptés sont sauvegardés : une modification du catalogue ne change pas les séjours existants.

## Caisse

- Une seule session ouverte, appartenant à son employé d'ouverture. La relève nécessite une clôture et une nouvelle ouverture.
- Fond de caisse positif ou nul ; paiements, remboursements et dépenses strictement positifs.
- Paiement partiel possible, sans dépasser le solde à régler.
- Remboursement réservé à l'administrateur, limité aux encaissements nets de la réservation.
- Remboursement et dépense nécessitent un motif et suffisamment d'espèces dans la session courante.
- Les écritures et les sessions clôturées sont immuables. Une correction financière utilise une nouvelle écriture.
- L'annulation ne supprime aucun paiement et ne déclenche aucun remboursement automatique. Un solde négatif indique une somme à restituer.
- Espèces attendues = fond + paiements − remboursements − dépenses. Écart = espèces comptées − espèces attendues.
- Les encaissements nets du tableau de bord correspondent aux paiements moins remboursements du jour local ; les dépenses restent séparées.

## Livraison progressive

L'application implémente les comptes, le catalogue, les clients, les réservations, le planning par chambre, l'admission, la caisse et le tableau de bord. Les statistiques sont calculées depuis les écritures et les séjours, sans table de compteurs. Les données restent dans le dossier du projet.
