"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { catalogHref } from "@/features/catalog/query"
import type { CatalogFilters } from "@/lib/validation/catalog"
import { cn } from "@/lib/utils/cn"

export interface CatalogSearchProps {
  basePath: string
  filters: CatalogFilters
  className?: string
  placeholder?: string
}

export function CatalogSearch({
  basePath,
  filters,
  className,
  placeholder = "Search the collection",
}: CatalogSearchProps) {
  const router = useRouter()
  const [value, setValue] = React.useState(filters.q ?? "")

  React.useEffect(() => {
    setValue(filters.q ?? "")
  }, [filters.q])

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const q = value.trim()
    router.push(catalogHref(basePath, filters, { q: q || undefined, page: 0 }))
  }

  return (
    <form
      role="search"
      onSubmit={submit}
      className={cn("flex w-full items-center gap-2", className)}
    >
      <label htmlFor="catalog-search" className="sr-only">
        Search products
      </label>
      <Input
        id="catalog-search"
        name="q"
        type="search"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder={placeholder}
        autoComplete="off"
      />
      <Button type="submit" variant="secondary">
        Search
      </Button>
    </form>
  )
}
