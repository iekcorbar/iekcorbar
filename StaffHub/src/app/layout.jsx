import './globals.css';
import { LangProvider } from '@/lib/i18n';

// `manifest` uit src/app/manifest.js en het apple/pictogram uit src/app
// worden door Next zelf in de head gezet; hier alleen de identiteit van de
// app en het "losse venster"-gedrag op iOS (Delen → Zet op beginscherm).
export const metadata = {
  title: 'StaffHub - iekcorbar',
  applicationName: 'StaffHub',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'black-translucent',
    title: 'StaffHub',
  },
};

export const viewport = {
  themeColor: '#0d0a07',
  colorScheme: 'dark',
};

export default function Root({ children }) {
  return (
    <html lang="nl">
      <body className="bg-ink text-parch">
        <LangProvider>{children}</LangProvider>
      </body>
    </html>
  );
}
