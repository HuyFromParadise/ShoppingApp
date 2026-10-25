"use client"

import * as React from "react"
import { cn } from "@/lib/utils/cn"
import { Skeleton } from "@/components/ui/skeleton"

export interface Column<T> {
  key: string
  header: string
  /** Cell renderer; returning a node keeps formatting in the page. */
  render: (row: T) => React.ReactNode
  /** Hide below `sm`, for columns that are nice-to-have on phones. */
  hideOnMobile?: boolean
  align?: "left" | "right"
  className?: string
}

export interface DataTableProps<T> {
  caption: string
  columns: ReadonlyArray<Column<T>>
  rows: readonly T[]
  rowKey: (row: T) => string
  loading?: boolean
  emptyMessage?: string
}

/**
 * A plain, accessible table: a real `<table>` with a caption, so screen
 * readers get row/column context that a div grid would lose. Horizontal scroll
 * is contained rather than letting a wide table push the page sideways.
 */
export function DataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  loading = false,
  emptyMessage = "Nothing to show yet.",
}: DataTableProps<T>) {
  return (
    <div className="-mx-1 overflow-x-auto sm:mx-0">
      <table className="w-full min-w-[36rem] border-collapse text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-line text-left">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cn(
                  "px-2 py-3 text-xs font-medium uppercase tracking-label text-muted",
                  column.align === "right" && "text-right",
                  column.hideOnMobile && "hidden sm:table-cell",
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={columns.length} className="px-2 py-6">
                <div role="status">
                  <span className="sr-only">Loading {caption.toLowerCase()}</span>
                  <Skeleton lines={3} />
                </div>
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-2 py-10 text-center text-sm text-muted">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr
                key={rowKey(row)}
                className="border-b border-line last:border-0 hover:bg-[#f5f3f0]"
              >
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={cn(
                      "px-2 py-3 align-top text-ink",
                      column.align === "right" && "text-right tabular-nums",
                      column.hideOnMobile && "hidden sm:table-cell",
                      column.className,
                    )}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
