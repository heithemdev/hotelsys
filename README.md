# HotelSys

Application JavaFX en français pour gérer la réception d'un hôtel : chambres, clients, réservations, planning, équipe et caisse. Projet scolaire, conçu pour un poste local et une base SQLite simple.

![Planning HotelSys](docs/design/calendar.png)

## Lancer

**JDK 21 requis.** Il est déjà installé pour cet utilisateur Windows. Double-cliquez sur `run.cmd`, ou lancez depuis le dossier du projet :

```powershell
.\run.cmd
```

Au premier lancement, créez votre hôtel et son administrateur. Choisissez un mot de passe de 12 caractères minimum. La case **« Charger un exemple pour la démonstration »** ajoute huit chambres et des séjours illustrant le planning ; laissez-la décochée pour démarrer avec des données vides.

Il n'y a aucun identifiant ou mot de passe par défaut. L'exemple ne remplace jamais des données existantes. Toutes les données de l'application restent dans **`data/hotelsys.db`**, dans ce projet.

Si un terminal était ouvert avant l'installation de Java :

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
```

Le premier build télécharge Maven et les dépendances. Ensuite, la réception et ses données fonctionnent hors connexion. Une ancienne installation Java peut encore répondre à `java -version` ; `run.cmd` et Maven utilisent le JDK défini par `JAVA_HOME`.

## Ce qui fonctionne

| Écran | Fonctionnalités |
|---|---|
| Connexion | Premier administrateur, mots de passe hachés, comptes actifs, déconnexion |
| Tableau de bord | Occupation, arrivées/départs du jour, encaissements et graphique sur sept jours pour l'admin |
| Réservations | Recherche, devis, plusieurs chambres avec dates indépendantes, occupants et repas par chambre, modification avant arrivée |
| Dossier de séjour | Prix et solde, contrôle familial, arrivée, départ, annulation et accès au paiement |
| Calendrier | Semaine par chambre, navigation et choix de date, états colorés, ouverture des dossiers |
| Clients | Création, recherche et correction des fiches |
| Chambres | Création/modification de chambres et modèles, équipements, prix, activation, suppression sans historique |
| Caisse | Fond initial, paiements partiels, remboursements, dépenses, historique, clôture et écart compté |
| Équipe | Création, rôles, changement de mot de passe et désactivation des comptes |
| Paramètres | Hôtel, seuils enfants, réduction des animations et exemple facultatif |

Les employés assurent la réception et leur caisse. L'administrateur contrôle aussi les comptes, modèles, tarifs, paramètres, remboursements, dépenses et statistiques financières. Le dernier administrateur actif reste protégé.

## Quelques règles importantes

- Le titulaire doit avoir 19 ans pour un homme, 21 pour une femme : politique de projet demandée, sans qualification juridique.
- L'hébergement est au **prix fixe de la chambre par nuit**, sans remise enfant.
- Par défaut, un enfant de moins de 6 ans partage sans lit ; à partir de 6 ans, il nécessite un lit. La limite d'enfants sans lit est propre au modèle.
- Les repas s'appliquent à toute la chambre et se facturent par personne/nuit. Le modèle définit un prix enfant fixe ou une remise ; le seuil enfant vaut 12 ans par défaut.
- Les prix acceptés sont sauvegardés. Modifier le catalogue ne change pas une réservation existante. Une modification du séjour demande un nouveau devis et renouvelle son contrôle familial.
- Une chambre mixte exige une vérification familiale avant l'arrivée ; seule la trace du contrôle est conservée.
- Les chambres et occupants ne peuvent avoir deux séjours simultanés. Un départ et une nouvelle arrivée le même jour sont autorisés.
- Les corrections de naissance/sexe liées à un séjour actif sont bloquées pour préserver les conditions acceptées ; corrigez d'abord le séjour concerné.
- Une annulation conserve les paiements. Le remboursement se fait séparément dans une caisse ouverte.

## Choix techniques

Java 21, JavaFX 21, SQLite/JDBC, Maven Wrapper et CSS. Des classes JavaFX directes évitent une seconde couche de contrôleurs FXML. `HotelStore` contient les règles métier ; `Db` gère les requêtes préparées et transactions. Les opérations de données passent par un thread de travail, sans bloquer l'interface.

La police **Manrope**, les titres **DM Serif Display**, le logo et le thème terre cuite/porcelaine sont embarqués. Aucune police n'est installée sur Windows. Le calendrier reprend la [maquette générée](docs/design/calendar-reference.png) ; les textes et séjours restent de vrais contrôles JavaFX.

```text
src/main/java/com/hotelsys/
├── app/           # Lancement, shell et tâches en arrière-plan
├── model/         # User, Row, StayDraft
├── repository/    # Db : SQLite, migrations, transactions
├── service/       # HotelStore, Passwords, exemple facultatif
└── ui/            # Pages JavaFX et composants simples
src/main/resources/
├── db/migrations/ # SQL canonique
├── css/           # Palette et styles
├── fonts/         # Polices et licences OFL
└── images/        # Logo transparent
src/test/java/     # Six vérifications métier et un passage graphique facultatif
docs/             # Besoins, architecture, dictionnaire, UML et conception visuelle
```

## Vérifier

```powershell
.\mvnw.cmd -B -ntp clean verify

# Facultatif, avec affichage graphique et données temporaires :
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-ui.ps1
```

Six tests regroupent les scénarios importants : réservation/admission/paiement, enfants et lits, conflits et prix historiques, droits, remboursements et intégrité. La compilation traite les avertissements comme des erreurs. Le passage graphique vérifie les huit pages, un devis et une réservation persistée, puis la reconnexion. Ses captures et bases temporaires vont dans `target/ui-check`, jamais dans vos données réelles.

## Documents

- [Besoins et règles métier](docs/requirements.md)
- [Architecture et décisions](docs/architecture.md)
- [Dictionnaire et relations de la base](docs/database.md)
- [Diagrammes UML](docs/uml/README.md)
- [Schéma SQL](src/main/resources/db/migrations/001_initial.sql)
- [Direction visuelle et crédits](docs/design/direction.md)

Pour sauvegarder : fermez toutes les instances puis copiez `data/hotelsys.db`. Pour restaurer, conservez une copie du fichier actuel avant de le remplacer, application fermée. Données, mots de passe, caches et builds ne sont pas suivis dans Git.

Le périmètre reste un poste local : pas de cloud, synchronisation réseau, installateur Windows ni comptabilité générale.
