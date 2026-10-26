import type { Metadata } from "next"
import { Inter } from "next/font/google"
import { Providers } from "./providers"
import "./globals.css"

const inter = Inter({
  subsets: ["latin", "vietnamese"],
  variable: "--font-inter",
  display: "swap",
})

export const metadata: Metadata = {
  // Canonical and Open Graph URLs need an absolute base (project step: SEO).
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/+$/, "") ?? "http://localhost:3000",
  ),
  title: {
    default: "Clothing — Considered everyday wardrobe",
    template: "%s — Clothing",
  },
  description:
    "A calm, editorial clothing storefront for discovering and buying everyday essentials.",
}

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className={inter.variable}>
      <body className="font-sans antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
