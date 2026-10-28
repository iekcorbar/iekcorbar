import './globals.css';
import { LangProvider } from '@/lib/i18n';

export const metadata = { title: 'StaffHub - iekcorbar' };

export default function Root({ children }) {
  return (
    <html lang="nl">
      <body className="bg-ink text-parch">
        <LangProvider>{children}</LangProvider>
      </body>
    </html>
  );
}
