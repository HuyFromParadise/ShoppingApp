import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"
import { catalogHref } from "@/features/catalog/query"
import type { CatalogFilters } from "@/lib/validation/catalog"
import { cn } from "@/lib/utils/cn"

export interface PaginationProps {
  basePath: string
  filters: CatalogFilters
  page: number
  totalPages: number
  totalElements: number
  size: number
  className?: string
}

function visiblePages(page: number, totalPages: number): Array<number | "ellipsis"> {
  const candidates = new Set<number>([0, totalPages - 1, page - 1, page, page + 1])
  const pages = Array.from(candidates)
    .filter((value) => value >= 0 && value < totalPages)
    .sort((a, b) => a - b)

  const result: Array<number | "ellipsis"> = []
  for (const value of pages) {
    const previous = result[result.length - 1]
    if (typeof previous === "number" && value - previous > 1) {
      result.push("ellipsis")
    }
    result.push(value)
  }
  return result
}

export function Pagination({
  basePath,
  filters,
  page,
  totalPages,
  totalElements,
  size,
  className,
}: PaginationProps) {
  if (totalPages <= 1) return null

  const first = page * size + 1
  const last = Math.min((page + 1) * size, totalElements)
  const pages = visiblePages(page, totalPages)

  return (
    <nav
      aria-label="Catalog pagination"
      className={cn(
        "flex flex-col gap-4 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between",
        className,
      )}
    >
      <p className="text-sm text-muted">
        Showing {first}-{last} of {totalElements}
      </p>

      <div className="flex flex-wrap items-center gap-2">
        {page > 0 ? (
          <Link
            href={catalogHref(basePath, filters, { page: page - 1 })}
            className={buttonVariants({ variant: "secondary", size: "sm" })}
            rel="prev"
          >
            Previous
          </Link>
        ) : (
          <span
            aria-disabled="true"
            className={cn(
              buttonVariants({ variant: "secondary", size: "sm" }),
              "pointer-events-none opacity-50",
            )}
          >
            Previous
          </span>
        )}

        <ul className="flex items-center gap-1">
          {pages.map((value, index) =>
            value === "ellipsis" ? (
              <li
                key={`ellipsis-${index}`}
                aria-hidden="true"
                className="px-1 text-sm text-muted"
              >
                ...
              </li>
            ) : (
              <li key={value}>
                <Link
                  href={catalogHref(basePath, filters, { page: value })}
                  aria-label={`Page ${value + 1}`}
                  aria-current={value === page ? "page" : undefined}
                  className={cn(
                    "inline-flex h-8 min-w-8 items-center justify-center rounded border px-2 text-sm transition-colors duration-150",
                    value === page
                      ? "border-ink bg-ink text-surface"
                      : "border-line bg-surface text-ink hover:border-ink",
                  )}
                >
                  {value + 1}
                </Link>
              </li>
            ),
          )}
        </ul>

        {page + 1 < totalPages ? (
          <Link
            href={catalogHref(basePath, filters, { page: page + 1 })}
            className={buttonVariants({ variant: "secondary", size: "sm" })}
            rel="next"
          >
            Next
          </Link>
        ) : (
          <span
            aria-disabled="true"
            className={cn(
              buttonVariants({ variant: "secondary", size: "sm" }),
              "pointer-events-none opacity-50",
            )}
          >
            Next
          </span>
        )}
      </div>
    </nav>
  )
}
