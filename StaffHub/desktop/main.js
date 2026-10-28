// StaffHub desktop-shell: een venster op de webapp + handige menu's.
// De echte bestands-ervaring staat in Verkenner via WebDAV (menu: koppelen).
//
// Sinds 0.1.3 doet de shell twee dingen die hij niet kon, en die precies de
// klachten van de eigenaar dekken:
//  - hij ZIET of de hub onbereikbaar is of dat de sessie weg is, en zegt dat
//    netjes in plaats van een leeg venster te tonen ("website werkt niet");
//  - hij controleert of er een nieuwe installateur op de hub staat en biedt die
//    aan om te downloaden. Bewust GEEN stille automatische update: die vraagt om
//    een code-signing certificaat, en zonder certificaat krijgt elke update alsnog
//    een SmartScreen-scherm - dan is "automatisch" half werk met meer drempels.
const { app, BrowserWindow, Menu, shell, dialog, session } = require('electron');
const path = require('path');
const fs = require('fs');

const cfg = (() => {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8')); }
  catch { return { url: 'https://staff.iekcorbar.com' }; }
})();

let win;
let menu = null;
let info = null;          // { version, file, size, updated, fout }
let gevraagd = false;     // éénmaal per opstart omhoog komen, niet zeuren

/** "0.1.3" -> [0,1,3]; onbruikbare tekst -> lege lijst. */
function numerics(v) {
  const m = String(v || '').match(/\d+(\.\d+)*/);
  return m ? m[0].split('.').map(Number) : [];
}

/** Is `a` nieuwer dan `b`? Ontbreekt er iets, dan: nee. */
function nieuwer(a, b) {
  const x = numerics(a), y = numerics(b);
  if (!x.length || !y.length) return false;
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d !== 0) return d > 0;
  }
  return false;
}

/**
 * Vraag de hub wat de huidige installateur is. Het endpoint staat achter login,
 * dus dit MOET via de eigen sessie van de app - de Node-fetch kent die cookie
 * niet en zou 401 terugkrijgen.
 */
async function peilUpdate() {
  const eigen = app.getVersion();
  try {
    const r = await session.defaultSession.fetch(cfg.url + '/api/desktop?info=1', { credentials: 'include', cache: 'no-store' });
    if (r.status === 401) { info = { fout: 'niet-ingelogd', eigen }; }
    else if (!r.ok) { info = { fout: 'geen-installateur', eigen }; }
    else {
      const j = await r.json();
      info = { eigen, versie: j.version || '', bestand: j.file || '', omvang: j.size || 0, updates: nieuwer(j.version, eigen) };
    }
  } catch (e) {
    info = { fout: 'hub-onbereikbaar', eigen };
  }
  werkMenuBij();
  // Eén beleefde melding per opstart; hierna is het stil en alleen nog het menu.
  if (info && info.updates && !gevraagd) {
    gevraagd = true;
    const k = await dialog.showMessageBox(win, {
      type: 'info', title: 'StaffHub update', buttons: ['Downloaden', 'Later'], defaultId: 0, cancelId: 1,
      message: 'Er staat een nieuwe versie op de hub: ' + info.versie + ' (jij hebt ' + info.eigen + ').',
      detail: 'Downloaden haalt de installateur op in je map Download en laat hem in Verkenner zien. Dubbelklik hem; hij zet de oude versie weg.\n\nIn het menu StaffHub kun je dit later opnieuw doen.',
    });
    if (k.response === 0) downloadUpdate();
  }
  return info;
}

/** De installateur op halen via dezelfde sessie (zelfde reden als hierboven). */
async function downloadUpdate() {
  const bestemming = path.join(app.getPath('downloads'), info.bestand || 'StaffHub-setup.exe');
  try {
    const r = await session.defaultSession.fetch(cfg.url + '/api/desktop', { credentials: 'include' });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const buf = Buffer.from(await r.arrayBuffer());
    fs.writeFileSync(bestemming, buf);
    dialog.showMessageBox(win, {
      type: 'info', title: 'Gedownload', message: 'Klaar: ' + (info.versie || '') ,
      detail: 'Bestand: ' + bestemming + '\n\nDubbelklik het in Verkenner om te installeren; de nieuwe versie neemt het menu en de snelkoppeling over.',
      buttons: ['Toon in Verkenner', 'Oké'], defaultId: 0,
    }).then((k) => { if (k.response === 0) shell.showItemInFolder(bestemming); });
  } catch (e) {
    dialog.showMessageBox(win, { type: 'error', title: 'Downloaden mislukt', message: 'De hub gaf een fout: ' + e.message, detail: 'Probeer het later opnieuw via menu StaffHub → Controleren op updates.' });
  }
}

function statusTekst() {
  if (!info) return 'Controleren…';
  if (info.fout === 'niet-ingelogd') return 'Niet ingelogd — log in in het venster';
  if (info.fout === 'hub-onbereikbaar') return 'Hub niet bereikbaar (netwerk of server)';
  if (info.fout === 'geen-installateur') return 'Hub bereikbaar, geen installateur gevonden';
  if (info.updates) return 'Nieuwe versie ' + info.versie + ' beschikbaar';
  return 'Versie ' + info.eigen + ' — actueel';
}

function werkMenuBij() {
  if (!menu) return;
  const item = menu.getMenuItemById('update');
  if (!item) return;
  const submenu = item.submenu;
  if (!submenu) return;
  submenu.items[0].label = statusTekst();
  submenu.items[0].enabled = false;
  const d = submenu.items[1];
  if (d) d.enabled = Boolean(info && info.updates);
}

function bouwMenu() {
  menu = Menu.buildFromTemplate([{
    label: 'StaffHub',
    submenu: [
      { label: 'Vernieuwen', accelerator: 'CmdOrCtrl+R', click: () => win?.webContents.reload() },
      { label: 'Volledig scherm', accelerator: 'F11', click: () => win?.setFullScreen(!win.isFullScreen()) },
      { type: 'separator' },
      {
        id: 'update', label: 'Updater', submenu: [
          { label: 'Controleren…', enabled: false },
          { label: 'Download nieuwe versie', enabled: false, click: () => downloadUpdate() },
          { type: 'separator' },
          { label: 'Controleren op updates', click: () => peilUpdate() },
        ],
      },
      { type: 'separator' },
      { label: 'Help: web-URL openen', click: () => shell.openExternal(cfg.url) },
      {
        label: 'Netwerkschijf koppelen (Verkenner)…',
        click: () => dialog.showMessageBox(win, {
          type: 'info', title: 'Netwerkschijf',
          message: 'Map "deze pc" → rechtermuisknop → Netwerklocatie toevoegen → een netwerklocatie kiezen, en vul in:\n\n' + cfg.url.replace(/^https?:\/\//, 'https://') + '/dav\n\nGebruikersnaam: jouw StaffHub-account · Wachtwoord: een token uit de webapp (sleutel-icoon rechtsboven).\n\nSneller via cmd:  net use H: ' + cfg.url + '/dav /user:jouwnaam JOUW-TOKEN',
        }),
      },
      { type: 'separator' },
      { role: 'quit' },
    ],
  },
  { label: 'Beeld', submenu: [{ role: 'zoomIn' }, { role: 'zoomOut' }, { role: 'resetZoom' }, { type: 'separator' }, { role: 'toggleDevTools' }] },
  ]);
  Menu.setApplicationMenu(menu);
}

function show() {
  if (!win) {
    win = new BrowserWindow({
      width: 1280, height: 820, minWidth: 900, minHeight: 560,
      backgroundColor: '#0d0a07', title: 'StaffHub', autoHideMenuBar: false,
      webPreferences: { spellcheck: true },
    });
    win.loadURL(cfg.url);
    win.on('closed', () => { win = null; });
    // Externe links in de echte browser.
    win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
    // Eerste pagina echt geladen: dan pas peilen (dan is er een sessie-cookie om
    // mee te sturen én is er een venster waar de melding op mag verschijnen).
    win.webContents.once('did-finish-load', () => { peilUpdate(); });
    // De klacht "website werkt niet, ik zie niets": een gefaalde main-frame-load
    // liet tot nu toe een leeg donker venster achter. Zeg wat er scheelt.
    win.webContents.on('did-fail-load', (e, code, desc, url, isMainFrame) => {
      if (!isMainFrame || code === -3) return;    // -3 = door gebruiker geannuleerd
      info = { fout: 'hub-onbereikbaar', eigen: app.getVersion() };
      werkMenuBij();
      dialog.showMessageBox(win, {
        type: 'warning', title: 'StaffHub is niet bereikbaar',
        message: 'De hub antwoordt niet (' + desc + ').',
        detail: 'Controleer je verbinding. Blijft het, dan is de server bezig - probeer over een paar minuten, en log anders in via ' + cfg.url + ' in een gewone browser om te zien of het daar wel lukt.',
        buttons: ['Opnieuw proberen', 'Sluiten'], defaultId: 0,
      }).then((k) => { if (k.response === 0 && win) win.webContents.reload(); });
    });
  }
  win.show();
}

app.whenReady().then(() => {
  bouwMenu();
  show();
  app.on('activate', show);
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
