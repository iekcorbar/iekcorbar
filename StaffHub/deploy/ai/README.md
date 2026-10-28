# Boord-AI - opencode-sandbox op de VPS

De chatknop "Boord-AI" in StaffHub luistert door naar deze dienst. Het is een
los docker-project dat opzettelijk **geen** poort naar buiten heeft: alleen de
staffhub-container bereikt het, via het interne netwerk, met basic-auth.

## Eenmalige install (VPS-host)

```sh
mkdir -p /opt/opencode-ai
# zipStaffHub/deploy/ai/* uitpakken in /opt/opencode-ai  (Dockerfile, docker-compose.yml, config/)
cd /opt/opencode-ai
echo "AI_PASS=$(openssl rand -hex 24)" > .env
chmod 600 .env
docker compose up -d --build          # eerste build duurt ~2 min (npm + image)
```

## Model-login (eigenaar, één keer, interactief)

```sh
docker exec -it opencode-ai opencode auth login
```

Kies dezelfde provider/subscription als op de laptop - dan kost het geen losse
tokens. Zonder login draait de container wél, maar geeft StaffHub "Boord-AI
staat uit"- achtige foutmeldingen op vragen.

## Kennis vullen (optioneel, maar de bedoeling)

```sh
git clone --depth 1 https://github.com/baaye16/iekcorbar-final.git /opt/opencode-ai/workspace/repo
```

Gebruik een fijnmazige GitHub-token met **alleen contents:read** (privé-repo!).
Verversen: `git -C /opt/opencode-ai/workspace/repo pull`.

## StaffHub koppelen

In `/opt/staffhub/.env` twee regels toevoegen (waarde van AI_PASS staat in
`/opt/opencode-ai/.env`, nooit in de repo noteren):

```
STAFFHUB_AI_URL=http://opencode-ai:4096
STAFFHUB_AI_PASS=<zelfde geheim>
```

Daarna `docker compose up -d` in /opt/staffhub (herstart ~10 s).

## Gegevens-verkeer

vraag = tekst + staff-naam · antwoord = platte tekst. Geen bestanden, geen
wachtwoorden: de agent leest alleen wat er in ./workspace staat (bash/edit op
deny, zie config/opencode.json).
