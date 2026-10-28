import './globals.css';

export const metadata = { title: 'StaffHub - iekcorbar' };

export default function Root({ children }) {
  return (
    <html lang="nl">
      <body className="bg-ink text-parch">{children}</body>
    </html>
  );
}
