# HotelSys

Application JavaFX en français pour gérer les réservations d’un hôtel. Le mini-projet relie les clients, les chambres et les séjours dans une base SQLite locale.

## Démarrer

Installez le **JDK 21** et définissez `JAVA_HOME`. Sous Windows, double-cliquez sur `run.cmd` ou lancez cette commande depuis le projet.

```powershell
.\run.cmd
```

Au premier lancement, renseignez l’hôtel et créez le compte administrateur avec un mot de passe d’au moins 12 caractères. À la connexion suivante, choisissez votre rôle. L’administrateur saisit son nom et les réceptionnistes choisissent leur compte dans la liste.

Le premier lancement télécharge les dépendances Maven. Les données sont enregistrées dans `data/hotelsys.db`. Une ancienne base est sauvegardée dans `data/backups` avant sa conversion au format courant.

## Démonstration

1. Dans **Chambres**, créez un modèle avec un tarif par nuit, puis ajoutez une chambre.
2. Dans **Clients**, créez la fiche d’un adulte avec sa date de naissance et son document d’identité.
3. Dans **Réservations**, sélectionnez le titulaire, la chambre, les dates et les occupants. Vérifiez le total, puis confirmez pour enregistrer le séjour et son paiement.
4. Dans **Calendrier**, ouvrez le séjour depuis la chambre. **Caisse** présente le paiement et **Tableau de bord** les mouvements du jour.

Les étapes de réception, la gestion des clés, les permissions et les conditions de réservation sont décrites dans les [besoins fonctionnels](docs/requirements.md).

## Données de démonstration

Après la configuration initiale, fermez l’application puis exécutez `.\run.cmd --seed-demo` depuis le projet. La commande sauvegarde la base et ajoute une seule fois les chambres, clients et séjours fictifs. Les encaissements et le remboursement de démonstration sont signalés dans le journal et entrent dans le calcul des espèces attendues.

Le bouton **Écouter** dans **Paramètres** permet d’entendre le son d’encaissement. Le réglage **Son à l’encaissement** commande le son des paiements enregistrés.

## Compiler

```powershell
.\mvnw.cmd -B -ntp clean package
```

## Documents du mini-projet

- [Rapport HotelSys](Rapport-HotelSys.pdf)
- [Énoncé](docs/assignment.png)
- [Besoins fonctionnels](docs/requirements.md)
- [Diagrammes StarUML](docs/uml/README.md)
- [Modèle relationnel](docs/database.md)
- [Script SQL complet](src/main/resources/db/migrations/001_initial.sql)
- [Architecture](docs/architecture.md)
- [Direction visuelle et crédits](docs/design/direction.md)

Pour sauvegarder les données, fermez l’application puis copiez `data/hotelsys.db`.
