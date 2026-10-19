# IMMERSIVE-CCTV

Versiune: 1.0.0  
Mod ID: `immersivecctv`  
Autor: ImmersiveCCTV Dev  
Licență: MIT

---

## Ce este (pe scurt)
IMMERSIVE-CCTV este un mod Minecraft (Forge) care adaugă un set de blocuri și obiecte ce simulează un sistem profesional CCTV: unitate centrală XVR, monitoare CCTV, camere bullet și cabluri (HDMI, PoE). Modul include logica de conectare între dispozitive, GUI pentru monitor, entități de bloc pentru a păstra starea și asset-uri (texturi, modele JSON) generate automat.

Am dedus aceasta din conținutul fișierelor din `.github/workflows/` (în special `main.yml`) care conțin scripturi ce generează structura proiectului, `build.gradle`, fișiere Java (pachetele `com.immersivecctv.*`), JSON-uri pentru modele/blockstates și script Python (Pillow) pentru generarea texturilor.

---

## Ce conține (preview rapid)
- Blocuri: `xvr_unit`, `cctv_monitor`, `bullet_camera`
- Iteme: `hdmi_cable`, `poe_cable`
- BlockEntities cu persistare NBT: `XvrBlockEntity`, `MonitorBlockEntity`, `CameraBlockEntity`
- GUI client: `XvrMonitorScreen` + utilitar `ClientGuiOpener`
- Assets generate: texturi 64x64 (PIL), modele JSON, blockstates, localization `en_us.json`
- Gradle + ForgeGradle config pentru Minecraft 1.20.1 (dependință: `net.minecraftforge:forge:1.20.1-47.2.0`)

---

## Funcționalități cheie (detaliat)
- XVR (XvrBlock & XvrBlockEntity)
  - Toggle power la right-click (când mâna goală). Stare reținută în NBT (`Powered`).
  - Păstrează listă de camere conectate (`Cameras`) și poziția monitorului conectat (`Monitor`).
  - `lanBlinkTimer` pentru efecte vizuale/logică periodică (tick).
- Monitor (MonitorBlock & MonitorBlockEntity)
  - Right-click cu mâna goală deschide un GUI (client-side): `XvrMonitorScreen`.
  - Stochează poziția XVR-ului la care este conectat (`XvrPos`).
- Cameră Bullet (CameraBlock & CameraBlockEntity)
  - Entitate de bloc care păstrează poziția XVR-ului conectat (`XvrPos`) — folosită cu cablul PoE.
- Cablu HDMI (HdmiCableItem)
  - Mecanism de conectare în două etape: la primul click stochează `FirstPos` în NBT-ul itemului, la al doilea verifică tipurile de block entity (Monitor ↔ XVR) și creează legătura.
  - Mesaje feedback în chat (mesaje în Română în cod: ex. "✔ Conexiune HDMI realizată cu succes!").
- Cablu PoE (PoeCableItem)
  - Similar HDMI, dar conectează `CameraBlockEntity` cu `XvrBlockEntity`. Mesaje de feedback pentru conexiuni corecte/erronate.
- Assets & Models
  - Script Python (PIL) generează texturi 64x64 pentru blocuri (xvr_top, xvr_side, xvr_front, camera_lens etc.) și pentru iteme (hdmi_cable, poe_cable).
  - JSON-urile pentru `models/` și `blockstates/` sunt generate în workflow pentru a atașa texturi la blocuri și iteme.
- Creative Tab
  - Modul declară o categorie creativă `immersivecctv_tab` cu iconă XVR și adaugă toate itemele/blocurile.

---

## Unde am găsit informațiile (probe)
- `.github/workflows/main.yml` — conține pași expliciți care:
  - Setează Java 17, instalează Gradle 8.5 și Python 3.10.
  - Rulează scripturi `cat << 'EOF' > ...` care creează `settings.gradle`, `build.gradle`, `gradle.properties`.
  - Generează resurse în `src/main/resources/assets/immersivecctv/...` (json, texturi).
  - Generează cod Java complet în `src/main/java/com/immersivecctv/...` (clasele menționate mai sus).
- `README.md` din repo curent e momentan minimal (un singur header). De aceea propun acest README extins.

---

## Structura proiectului (așa cum o generează workflow-ul)
(fișiere generate automat de workflow în `src/`)

```
src/
  main/
    java/
      com/immersivecctv/
        ImmersiveCCTV.java            # înregistrări DeferredRegister (blocks, items, block entities, creative tab)
        block/
          XvrBlock.java
          MonitorBlock.java
          CameraBlock.java
        blockentity/
          XvrBlockEntity.java
          MonitorBlockEntity.java
          CameraBlockEntity.java
        item/
          HdmiCableItem.java
          PoeCableItem.java
        client/
          ClientGuiOpener.java
          XvrMonitorScreen.java
    resources/
      META-INF/
        mods.toml                      # metadata mod (modId, version, description, deps)
      assets/immersivecctv/
        lang/en_us.json
        blockstates/*.json
        models/block/*.json
        models/item/*.json
        textures/block/*.png          # generate via PIL
        textures/item/*.png           # generate via PIL
build.gradle                          # ForgeGradle setup (1.20.1)
settings.gradle
gradle.properties
```

Cum se leagă între ele
- `ImmersiveCCTV.java` înregistrează blocurile, itemele și BlockEntityType-urile — acesta este punctul central de boot al modului.
- Blocurile creează BlockEntities care stochează legături și stare. Itemele (cablu) interacționează cu BlockEntities pentru a forma legături.
- Modelele/blockstates/texturile sunt consumate la runtime pentru a afișa blocurile/itemele.

---

## Cum să rulezi local (instrucțiuni practice)
Notă: Repozitorul curent conține workflow care *generează* fișierele la CI. Pentru dezvoltare locală recomand să reproduceți pașii din workflow și să comiteți fișierele generate (sau rulați workflow pe GitHub Actions).

1. Cerințe
   - Java 17 (temurin adoptium recomandat)
   - Gradle 8.5 (sau wrapper setat la 8.5)
   - Python 3.10+ și pip (pentru Pillow)
   - Conexiune la internet pentru a descărca dependențele Forge

2. Pași minimali (pe Linux/macOS, în terminal)
   - Clonați repo:
     git clone https://github.com/iustinmartac20140-dev/IMMERSIVE-CCTV.git
     cd IMMERSIVE-CCTV
   - (Opțional) Reproduceți pașii de generare din workflow local:
     - Creați directoarele și fișierele (sau rulați scriptul din workflow local)
     - Instalați pillow:
       pip3 install pillow
     - Dacă nu aveți wrapper: instalați Gradle 8.5 sau creați wrapper:
       gradle wrapper --gradle-version 8.5
   - Rulează build / client:
     ./gradlew --no-daemon build
     ./gradlew --no-daemon runClient
   - Observații:
     - `runClient` pornește o instanță de Minecraft de dezvoltare (Forge) cu mod-ul încărcat, dacă dependențele Forge se rezolvă corect.
     - Dacă întâmpinați probleme cu dependențele Forge (maven.minecraftforge.net), verificați setările de rețea / proxy.

3. Alternativ — folosește GitHub Actions
   - Workflow-ul `.github/workflows/main.yml` instalează Gradle 8.5 și Python + Pillow și generează proiectul. Poți declanșa workflow manual (Actions → run workflow) sau face push la branch.

---

## Sugestii practice / Observații despre repo
- Observație importantă: În prezent branch-ul `main` conține doar workflow-ul care *creează* codul și resursele la execuție. Pentru o dezvoltare mai convenabilă pe mașina locală, e recomandat să rulați scriptul o dată, verificați fișierele generate și apoi să comiteți codul sursă în repo (adică fișierele din `src/`, `build.gradle`, `settings.gradle`, `gradle.properties`). Altfel, cine clonează repo nu are cod sursă direct fără a rula workflow-ul.
- Mesaje în cod: multe mesaje de feedback în iteme sunt în română — se pot lăsa așa sau internaționaliza.
- Dependențe: projektul folosește Forge 1.20.1 (versiunea indicată în build.gradle generat). Asigurați-vă că folosiți exact acea versiune pentru compatibilitate.

---

## Probleme cunoscute și lucruri de verificat
- Workflow-urile din `.github/workflows/` conțin multe versiuni / copii: `build(2).yml`, `build(3).yml` etc. Curățare/normalizare ar ajuta.
- Scriptul de generare în workflow conține porțiuni tăiate/trunchiate în unele fișiere (vezi linii cu `[...]` în YAML extras). Verificați ca toate fișierele Java generate să fie complete înainte de a construi.
- ForgeGradle poate necesita versiuni specifice de Gradle/Java; folosiți Java 17 + Gradle 8.5 cum configurează workflow-ul.

---

## Roadmap / Funcționalități posibile
- Integrare reală a semnalelor video (în joc, simulare de fluxuri video pe monitor).
- Suport pentru mai multe tipuri de camere/porturi (PTZ, night-vision toggles).
- Persistență avansată / salvare de configurări per XVR (layout camere, etichete).
- Sistem de alertare / motion detection in-game (alrimi vizuale/sonore).

---

## Cum poți contribui
- Dacă dorești, pot:
  - crea fișierul README.md în repo (commit) — spune-mi branch-ul țintă,
  - rula local scriptul de generare și comite fișierele `src/` generate,
  - curăța și unifica workflow-urile duplicate și versiunile lor.
- Sugestii de PR: extracție de resurse, documentare pentru dezvoltare locală, adăugare `gradlew` wrapper, example world/test map.

---

## Întrebări la care te pot ajuta imediat
- Vrei să comit acest README.md în repo? Pe ce branch?
- Vrei să generez și să comit fișierele `src/` create de workflow astfel încât repo-ul să conțină codul sursă?
- Preferi să păstrăm mesajele din joc în română sau să le facem bilingual (en/ro)?

---

## Referințe din repo (path-uri utile)
- `.github/workflows/main.yml` — script de generare și build CI
- `src/main/java/com/immersivecctv/ImmersiveCCTV.java` — entrypoint (generat în workflow)
- `src/main/java/com/immersivecctv/block/XvrBlock.java` — comportament XVR
- `src/main/java/com/immersivecctv/item/HdmiCableItem.java` — logica de conectare HDMI
- `src/main/resources/META-INF/mods.toml` — metadata mod (modId, versiune, description)
- `src/main/resources/assets/immersivecctv/lang/en_us.json` — localizare

---

Mulțumesc — am parcurs și analizat `.github/workflows` și fișierele top-level pentru a construi acest README detaliat. Spune-mi dacă vrei să-l adaug direct în repository (îl pot crea / comite) sau dacă vrei o versiune prescurtată/extendată.
