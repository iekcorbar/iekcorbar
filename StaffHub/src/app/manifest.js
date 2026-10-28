// Web-app-manifest: geeft Mac/iOS/Linux (en Windows) de mogelijkheid StaffHub
// als losse "app" te installeren - Dock, beginscherm, adresbalk-menu - zonder
// dat er per platform een binaire build onderhouden hoeft te worden. Next plakt
// de <link rel="manifest"> zelf in de pagina-head; de iconen zijn dezelfde als
// de favicon. Echte bestands-ervaring blijft trouwens WebDAV (map-knop).
export default function manifest() {
  return {
    name: 'StaffHub - iekcorbar',
    short_name: 'StaffHub',
    description: 'Only for crew - iekcorbar staff-netwerk.',
    id: '/',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    orientation: 'any',
    background_color: '#0c1117',
    theme_color: '#0c1117',
    icons: [
      { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/icon-384.png', sizes: '384x384', type: 'image/png', purpose: 'any' },
    ],
  };
}
