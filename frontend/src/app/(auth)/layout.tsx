import Link from "next/link"

export default function AuthLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="flex min-h-screen flex-col bg-paper">
      <header className="border-b border-line">
        <div className="container-store flex h-16 items-center">
          <Link
            href="/"
            className="text-base font-semibold tracking-[0.3em] text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
            aria-label="Clothing — home"
          >
            CLOTHING
          </Link>
        </div>
      </header>
      <main className="container-store flex flex-1 items-start justify-center py-12 sm:py-20">
        {children}
      </main>
    </div>
  )
}
