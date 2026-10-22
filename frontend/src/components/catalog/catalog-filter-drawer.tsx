"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { Dialog } from "@/components/ui/dialog"
import type { Category } from "@/types/catalog"
import type { CatalogFilters } from "@/lib/validation/catalog"
import { CatalogFilterPanel } from "./catalog-filter-panel"

export interface CatalogFilterDrawerProps {
  basePath: string
  filters: CatalogFilters
  categories: Category[]
  lockedCategory?: string
}

export function CatalogFilterDrawer({
  basePath,
  filters,
  categories,
  lockedCategory,
}: CatalogFilterDrawerProps) {
  const [open, setOpen] = React.useState(false)

  return (
    <div className="lg:hidden">
      <Button type="button" variant="secondary" onClick={() => setOpen(true)}>
        Filters
      </Button>
      <Dialog open={open} onOpenChange={setOpen} title="Filter products">
        <CatalogFilterPanel
          basePath={basePath}
          filters={filters}
          categories={categories}
          lockedCategory={lockedCategory}
          onApplied={() => setOpen(false)}
          idPrefix="mobile-catalog"
        />
      </Dialog>
    </div>
  )
}
