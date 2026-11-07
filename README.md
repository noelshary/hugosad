# HugoSMP Auction - Neoloni Edition 1.4.0 (Quellcode)

Fabric-Mod (nur Client), Minecraft 1.21.11, Java 21, Mojang-Mappings.

Einbauen:
1. Ordner in ein GitHub-Repo legen (oder src/ + fabric.mod.json in ein bestehendes).
2. In gradle.properties `fabric_version` aus deinem noel-styles Repo uebernehmen.
3. Push -> GitHub Actions -> Artifact `hugoauction-jar`.
4. Die alte Config `config/hugoauction-neu.json` wird automatisch migriert (Bedrock-Namen-Fix im Pattern).

Bedienung: J = Menue (Erstellen), waehrend/10 s nach Ende = Leaderboard.
Im Leaderboard: Klick auf Zeile = Namen kopieren, C = Fuehrenden, A = alle Namen, J/Esc = schliessen.
