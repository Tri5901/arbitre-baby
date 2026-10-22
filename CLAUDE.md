# Arbitre Baby — contexte pour Claude Code

Document de passation de la conversation Claude.ai du 18 au 22 octobre 2026. À placer à la racine du dépôt (en `CLAUDE.md` ou `CONTEXTE.md`) avant de continuer dans Claude Code.

## Projet

PWA d'arbitrage de baby-foot pour Tristan (arbitre, utilise l'appli sur son téléphone Android, Chrome).

- Dépôt GitHub (public) : https://github.com/Tri5901/arbitre-baby (branche `main`)
- Production (Vercel, déploiement automatique à chaque push sur `main`) : https://arbitre-baby.vercel.app
- Projet Vercel : `arbitre-baby`, équipe `tri5901's projects` (Hobby), preset « Other », aucun build
- Stack : un seul `index.html` autonome (HTML, CSS et JS vanilla, sans dépendance), plus `manifest.json`, `sw.js` et 3 icônes PNG
- Langue de l'interface : français. Tristan écrit en français, tutoiement.

## Fichiers du dépôt

| Fichier | Rôle |
|---|---|
| `index.html` | Toute l'appli (CSS dans `<style>`, JS dans un `<script>` en fin de page) |
| `manifest.json` | PWA : nom « Arbitre Baby », `display: standalone`, portrait, fond et thème `#000000`, 3 icônes |
| `sw.js` | Service worker : réseau d'abord, repli sur le cache (`CACHE = 'arbitre-baby-v1'`) |
| `icon-192.png`, `icon-512.png` | Icône normale |
| `icon-maskable-512.png` | Icône adaptative Android (marges plus larges) |

Pour forcer la mise à jour du cache chez les utilisateurs après un changement important, changer la valeur de `CACHE` dans `sw.js`.

## Fonctionnalités actuelles

- **Chrono** géant avec dixièmes de seconde (ex. `14.7`, `1:29.9`). Toucher le chrono = pause / reprise. Passe en jaune sous 3 s.
- **Boutons de départ** : 10 s et 15 s (grands boutons verts), puis une ligne « 3 s » · « Fin de set · 90 s » · « Autres ▾ », et « Stop ». Le menu « Autres » contient les chronos rares : Appel joueur absent 3 min, Retard 5 min, Entre éléments 180 s (chacun écrit une ligne dans l'historique). Un bouton chrono = `data-t` (secondes) + `data-name` (nom affiché) + `data-log` (ligne d'historique, facultatif).
- **Temps du règlement** (règles ITSF v2.0, traduction FFBF du 01/01/2026, dans `assistant-ffbf/sources/reglement-officiel.pdf`) : possession 10 s à la barre des 5 et 15 s ailleurs ; pause entre les points 5 s ; entre les manches 90 s ; temps mort 30 s (2 par manche) ; 3 s par étape de remise en jeu et mains lâchées 3 s = temps mort ; retard de jeu 5 s après avertissement. Code sportif art. 1.2.1 : 2e appel 3 min après le premier, puis une manche perdue toutes les 5 min. Cahier des charges des tournois mineurs : 180 s entre éléments (relais par équipes).
- **Temps morts** : deux grandes cases « 1 » et « 2 » par équipe (la case « 0 » a été retirée le 22/10/2026), qui prennent la place libre de la carte. Toucher une case compte le temps mort et lance un chrono de 30 s ; toucher une case déjà prise l'annule. Case prise = couleur de l'équipe + coche.
- **Score** : une grosse tuile par équipe (appui = +1 point **et chrono « Après but » de 5 s**, la pause entre les points du règlement ; demandé à 6 s puis ramené à 5 s le 22/10/2026), petit bouton « − » pour corriger (sans chrono), compteur de manches affiché dans la tuile, « Points à 0 ».
- **Bo5 / Bo3** : interrupteur (nombre de manches pour gagner : 3 ou 2).
- **Nouveau set** (avec confirmation) : donne la manche à l'équipe qui mène, remet points, temps morts et avertissements à zéro, lance 90 s. En cas d'égalité, aucune manche n'est attribuée. Une ligne « MATCH » est journalisée quand une équipe atteint le nombre de manches requis.
- **Avertissements** : 3 pastilles par équipe.
- **Historique** des événements (60 entrées max) avec bouton « Effacer ».
- **Temps de jeu** en haut, qui démarre au premier chrono ou point ; toucher = pause / reprise.
- **Noms d'équipes** modifiables (12 caractères).
- **Fin du temps** : vibration `[500,120,500,120,900]`, triple bip (Web Audio), flash jaune clignotant.
- Réglages en bas : Vibreur oui/non, Son oui/non, Tester, Tout à zéro (avec confirmation).
- État sauvegardé dans `localStorage` sous la clé `arbitre-baby-v2`. Wake Lock demandé pendant un chrono.
- Mise en page : une seule colonne, `max-width: 560px`, tient sur un écran de téléphone **sans défilement** (tailles en `dvh` + deux paliers `@media (max-height:800px)` et `(max-height:680px)`). Vérifié sans débordement le 22/10/2026 à 360×640, 360×680, 360×690, 390×690, 360×750, 390×760, 360×800, 390×800, 360×810, 390×844, 412×915. À revérifier à chaque ajout d'élément (mesurer `document.documentElement.scrollHeight` = `innerHeight`).
- Design (refait le 22/10/2026, « tableau de score ») : même disposition qu'avant, en cartes arrondies sur fond noir avec léger halo orange en haut. Polices Barlow (textes) et Barlow Condensed (chiffres), via Google Fonts. Orange (équipe 1) et argent (équipe 2) en dégradés, vert pour 10/15, jaune pour « Nouveau set », les avertissements (pastilles façon carton) et la confirmation. Barre de progression sous le chrono (`#bar`), manches en pastilles dans les tuiles de score (autant que de manches à gagner). Dans l'appli Android (`window.ArbitreApp` présent → classe `in-app` sur `<html>`), les boutons 10/15 affichent « Vol + / Vol − ». À l'origine, modelé sur une appli d'arbitrage que Tristan utilisait (capture fournie).

Logo : baby-foot vu de dessus (terrain vert, barres, joueurs orange en haut et gris en bas, balle blanche). Les PNG ont été dessinés au canvas ; la fonction de dessin n'est pas dans le dépôt (voir plus bas).

## Problème ouvert : le vibreur ne fonctionne pas

- Sur le téléphone de Tristan (Android, Chrome, appli installée, probablement Realme/Oppo), le bouton **Tester** affiche « Chrome a envoyé la vibration au téléphone », mais le téléphone ne vibre pas. `navigator.vibrate()` renvoie donc `true`.
- Conclusion probable : blocage côté Android (mode silencieux, « Ne pas déranger », économiseur de batterie, réglage de vibration de la surcouche constructeur). Je lui ai suggéré de tester ces réglages, il n'a pas encore répondu.
- Piste proposée et non faite : renforcer l'alerte de fin de temps (alarme qui se répète jusqu'à l'appui sur Stop, flash plein écran, bip plus long).

## Demande en cours : boutons de volume

Tristan veut que les boutons volume + / − du téléphone lancent le chrono 10 s / 15 s.

- Dans Chrome Android (page web ou PWA), les boutons de volume ne sont pas transmis à la page : impossible tel quel.
- Pistes proposées : (1) Media Session API (`previoustrack` / `nexttrack`) pour des boutons de casque ou télécommande Bluetooth, avec un son silencieux en boucle, ce qui donne aussi des commandes sur l'écran de verrouillage (à tester, Android peut refuser) ; (2) agrandir ou repositionner les boutons à l'écran ; (3) vraie appli Android (APK) qui lit les boutons de volume.
- Question posée à Tristan, sans réponse : utilise-t-il un accessoire Bluetooth ou seulement les boutons du téléphone ?

## Appli Android (dossier `android/`, depuis le 22/10/2026)

Choix de Tristan (pas d'accessoire Bluetooth) : une vraie appli Android pour les boutons de volume.

- `android/src/.../MainActivity.java` : WebView plein écran qui charge https://arbitre-baby.vercel.app (donc un `git push` met aussi l'appli à jour, sans refaire l'APK). Si pas de réseau ni de cache : copie embarquée `file:///android_asset/index.html` (son `localStorage` est séparé de celui du site).
- **Volume + = chrono 10 s, volume − = chrono 15 s** : l'appli injecte `window.arbitreVolume()` qui clique sur les boutons `[data-t="10"]` / `[data-t="15"]`. Garder ces attributs dans `index.html`. Le volume du téléphone ne bouge plus tant que l'appli est au premier plan.
- **Vibration** : l'appli remplace `navigator.vibrate` par une vibration native en usage **ALARM** (amplitude max). Raison : Chrome ne vibre pas quand le téléphone est en mode silencieux et Android filtre les vibrations « sans usage » ; une vibration d'alarme passe.
- Écran toujours allumé dans l'appli ; touche Retour = mise en arrière-plan (le chrono continue).
- Paquet `com.tri5901.arbitrebaby`, minSdk 26, targetSdk 34 (34 et pas 35 pour éviter l'affichage bord à bord forcé d'Android 15).
- Fabrication : `bash android/build.sh` (sans Gradle : aapt2, javac, d8, apksigner). JDK 17 Microsoft dans `C:\Program Files\Microsoft\jdk-17…`, SDK dans `%LOCALAPPDATA%\Android\Sdk`. Résultat : `android/out/arbitre-baby.apk`.
- **Clé de signature hors dépôt** : `~/.android/arbitre-baby.jks` + mot de passe dans `~/.android/arbitre-baby.pass`. À sauvegarder : sans elle, une nouvelle version ne s'installe plus par-dessus l'ancienne. Monter `versionCode` dans `AndroidManifest.xml` à chaque nouvel APK.

## Historique des décisions

1. Première version : artefact Claude.ai, thème clair/sombre, avec boutons 10 s, 15 s, temps morts 30 s, fin de set 90 s, compteurs, vibration.
2. Ajout des dixièmes de seconde.
3. Refonte « plus proche de ça » sur la base d'une capture d'une autre appli d'arbitrage (fond noir, deux équipes orange / gris, historique central, grosses tuiles de score, boutons 10 et 15 verts en bas).
4. Le vibreur est bloqué dans le cadre protégé des artefacts Claude.ai : mise en ligne sur GitHub et Vercel pour avoir une vraie page web.
5. Transformation en PWA (manifeste, service worker, icônes baby-foot).
6. Mise en page compacte pour tenir sur un écran sans défilement, boutons du bas raccourcis (« Vibreur : oui », « Son : oui », « Tester », « Tout à zéro »), message de diagnostic du bouton Tester plus précis.

## Pièges et choix techniques à connaître

- Le dépôt a été créé et alimenté **depuis le navigateur** (interface web de GitHub), car la session cloud n'avait pas de droit d'écriture sur `Tri5901`. Dans Claude Code, avec `git` et le jeton de Tristan, on peut faire des commits normaux.
- L'application GitHub « Vercel » a été installée sur le compte `Tri5901` ; l'étendue choisie (tous les dépôts ou seulement `arbitre-baby`) n'a pas été vérifiée. À contrôler dans GitHub > Réglages > Applications.
- Vercel avait proposé d'activer la 2FA ; Tristan s'en est occupé lui-même.
- Le service worker met en cache les réponses de même origine ; en cas d'ancienne version affichée, fermer l'appli et la rouvrir, ou changer `CACHE`.
- Le `<script>` est écrit en JS ancien style (`var`, fonctions) ; garder ce style par cohérence, sauf demande contraire.
- La mise en page repose sur `display:grid` avec `grid-template-rows` et des hauteurs en `dvh` ; une media query `@media (max-height:740px)` réduit les éléments sur les petits écrans.

## Reprise dans Claude Code

```bash
git clone https://github.com/Tri5901/arbitre-baby.git
cd arbitre-baby
# copier ce fichier à la racine sous le nom CLAUDE.md
python3 -m http.server 8000   # test local, ouvrir http://localhost:8000
```

Notes :
- Le déploiement est automatique : un `git push` sur `main` met `arbitre-baby.vercel.app` à jour.
- Le vibreur ne se teste que sur le téléphone, en https (la production Vercel convient).

## Idées de suite, par ordre de priorité

1. Débloquer ou contourner le vibreur (voir plus haut), par exemple une alarme répétée jusqu'à l'appui sur Stop.
2. Boutons de volume : décider entre Media Session, gros boutons à l'écran ou appli Android.
3. Nombre maximum de temps morts configurable (la maquette d'origine en affichait 2 par équipe).
4. Manche attribuée automatiquement à 5 points, ou score cible configurable.
5. Export du résumé de match (historique) en texte à partager.
