import Link from "next/link"
import type { Category } from "@/types/catalog"
import { cn } from "@/lib/utils/cn"

export interface CategoryNavigationProps {
  categories: Category[]
  className?: string
}

export function CategoryNavigation({
  categories,
  className,
}: CategoryNavigationProps) {
  if (categories.length === 0) return null

  return (
    <nav aria-label="Shop by category" className={className}>
      <ul className="grid gap-px overflow-hidden border border-line bg-line sm:grid-cols-2 lg:grid-cols-4">
        {categories.map((category) => (
          <li key={category.id} className="bg-surface">
            <Link
              href={`/categories/${encodeURIComponent(category.slug)}`}
              className={cn(
                "flex h-full min-h-32 flex-col justify-between gap-6 p-5 transition-colors duration-150",
                "hover:bg-[#f8f6f2] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent",
              )}
            >
              <span className="text-base font-medium tracking-tight text-ink">
                {category.name}
              </span>
              <span className="line-clamp-2 text-sm leading-relaxed text-muted">
                {category.description?.trim() || "Explore the edit"}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
