# StaffHub — iekcorbar staff-netwerk

Losstaande webapp + WebDAV-schijf + desktop-shell waar staff handleidingen,
lijstjes en bestanden deelt. Draait op de VPS, los van de site en Plesk.

## Wat zit erin (0.1.0)

- **Mappen + bestanden**: drag & drop (ook hele mappen), downloaden, hernoemen,
  verplaatsen, verwijderen. Zelfde naam = nieuwe **versie** (nooit weggeschreven;
  ouder ophalen of terugdraaien kan altijd).
- **Handleidingen**: rich-text pagina's (TipTap: koppen, lijsten, code, links,
  plaatjes die automatisch bij het bestand worden ge-upload).
- **Zoeken**: over namen, handleiding-inhoud en reacties.
- **Reacties** onder bestanden.
- **Rechten per map**: openbaar (iedereen), team, of besloten (owner). Kind-item
  erft van de dichtstbijzijnde ouder.
- **WebDAV** op `/dav`: Verkenner/Finder/iOS-Bestanden tonen dezelfde boom met
  dezelfde rechten. Inloggen met account-wachtwoord of een intrekbaar **token**.
- **Desktop**: `desktop/` is een Electron-shell om de webapp (menu bevat de
  WebDAV-plakplak-hulp). De eigenlijke "NAS-beleving" is de netwerkschijf.

## Lokaal draaien (Windows)

```
cd StaffHub
npm install
copy .env.example .env      # zet een lange willekeurige STAFFHUB_BOOTSTRAP
npm run dev                 # http://localhost:3120
```

Eerste bezoek → inlogscherm vraagt om de bootstrap-code → maak de owner aan.
Daarna: ⚓ rechtsboven → accounts voor staff.

Let op: op Windows wil `npm run dev` een `NODE_ENV`-omgeving; production build
testen doe je met `npm run build` + `npm start`.

## Draaien op de VPS (eigenaar voert uit — agent schrijft alleen voor)

1. Map naar de VPS: `/opt/staffhub` (deze bron, zonder node_modules/data).
2. `.env` maken met een sterke `STAFFHUB_BOOTSTRAP`.
3. `docker compose up -d --build` (docker-compose.yml beperkt het geheugen tot
   512 MB - de Minecraft-server mag er nooit last van hebben).
4. Voordeur: **nginx**, geen Caddy. Op onze VPS doet nginx al poort 80/443
   (map.iekcorbar.com), dus Caddy ernaast zou om die havens vechten.
   `deploy/nginx-site.example` kopiëren naar
   `/etc/nginx/sites-available/staff.iekcorbar.com`, symlink in sites-enabled,
   `nginx -t` en dan `certbot --nginx -d staff.iekcorbar.com` — certbot regelt
   het gratis HTTPS (nodig voor Verkenner-WebDAV en veilige cookies) en schrijft
   de SSL-regels zelf in dat bestand.
5. Open de URL, maak de owner aan met de bootstrap-code.
6. Back-up: `deploy/backup.sh` in de cron zetten (wekelijks; bewaart 8 tar's).

Beveiliging: alleen HTTPS, geen anoniem lezen, tokens zijn intrekbaar, en deze
accounts staan los van de site - een gestolen staff-wachtwoord raakt dus niets
op iekcorbar.com. De app zelf luistert alleen op 127.0.0.1:3120 (zie
docker-compose); naar buiten wijzen dus enkel 80/443 via nginx en SSH.
Zet op dit vhost géén `http2` aan: de Windows WebDAV-redirector (Verkenner)
hikt erop. Vooraf nodig: een A-record `staff.iekcorbar.com` → het VPS-adres
(gemaakt 28-10 bij Hostnet; de apex `iekcorbar.com` wijst naar de site-host,
dat is correct zo).

## Desktop-app bouwen (eigen Windows-machines)

```
cd StaffHub/desktop
npm install
npm start          # uitproberen
npm run dist       # bouwt een installeerbare app (NSIS-map in dist/)
```

URL staat in `desktop/config.json` (naar de eigen host aanpassen).

## Data

Alles staat in `data/`: `staffhub.db` (sqlite, WAL) + `blobs/`
(sha256-gebakken bestanden, content-addressed = dubbelingen zijn gratis).
Back-up = die map tarren. De `gcBlobs()`-aanroper wist ongebruikte blobs bij
elke delete; kwijt is dus écht kwijt na de laatste back-up-ronde.
