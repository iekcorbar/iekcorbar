// StaffHub desktop-shell: een venster op de webapp + handige menu's.
// De echte bestands-ervaring staat in Verkenner via WebDAV (menu: koppelen).
const { app, BrowserWindow, Menu, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

const cfg = (() => {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8')); }
  catch { return { url: 'https://staff.iekcorbar.com' }; }
})();

let win;

function show() {
  if (!win) {
    win = new BrowserWindow({
      width: 1280, height: 820, minWidth: 900, minHeight: 560,
      backgroundColor: '#0d0a07', title: 'StaffHub', autoHideMenuBar: false,
      webPreferences: { spellcheck: true },
    });
    win.loadURL(cfg.url);
    win.on('closed', () => { win = null; });
    // externe links in de echte browser
    win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  }
  win.show();
}

app.whenReady().then(() => {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: 'StaffHub',
      submenu: [
        { label: 'Vernieuwen', accelerator: 'CmdOrCtrl+R', click: () => win?.webContents.reload() },
        { label: 'Volledig scherm', accelerator: 'F11', click: () => win?.setFullScreen(!win.isFullScreen()) },
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
  ]));
  show();
  app.on('activate', show);
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
