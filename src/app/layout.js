import { Outfit, Playfair_Display } from "next/font/google";
import "./globals.css";
import ClientProviders from "./providers";

const outfit = Outfit({
  variable: "--font-outfit",
  subsets: ["latin"],
});

const playfair = Playfair_Display({
  variable: "--font-playfair",
  subsets: ["latin"],
});

export const metadata = {
  title: "iekcorbar | Steampunk Japanese Minecraft Server",
  description: "A custom RPG Paper Minecraft server themed around mechanical steampunk, Japanese folklore, custom trinkets, land claims, and brewery dynamics.",
  keywords: ["minecraft", "steampunk", "japanese", "rpg", "server", "dungeons", "brewery", "paper", "lands"],
  manifest: "/manifest.webmanifest",
  icons: {
    icon: "/icon.png",
    apple: "/apple-touch-icon.png",
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "iekcorbar",
  },
};

export const viewport = {
  width: "device-width",
  initialScale: 1.0,
  themeColor: "#141416",
};

export default function RootLayout({ children }) {
  return (
    <html
      lang="en"
      className={`${outfit.variable} ${playfair.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body
        className="min-h-full flex flex-col font-sans bg-bg-dark text-text-warm selection:bg-sp-gold/30 selection:text-white"
        suppressHydrationWarning
      >
        <ClientProviders>
          {children}
        </ClientProviders>
      </body>
    </html>
  );
}
