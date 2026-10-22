"use client"

import * as React from "react"
import { cn } from "@/lib/utils/cn"

export type ToastVariant = "success" | "error" | "info"

export interface ToastOptions {
  title: string
  description?: string
  variant?: ToastVariant
  durationMs?: number
}

interface ToastRecord extends Required<Omit<ToastOptions, "description">> {
  id: string
  description?: string
}

interface ToastContextValue {
  toast: (options: ToastOptions) => string
  dismiss: (id: string) => void
}

const ToastContext = React.createContext<ToastContextValue | null>(null)

const variantClasses: Record<ToastVariant, string> = {
  success: "border-[#c9dfd3] bg-[#eef6f1] text-ink",
  error: "border-[#e7c9c9] bg-[#faeeee] text-ink",
  info: "border-line bg-surface text-ink",
}

const accentClasses: Record<ToastVariant, string> = {
  success: "bg-success",
  error: "bg-accent",
  info: "bg-ink",
}

function createId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID()
  }
  return `toast-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = React.useState<ToastRecord[]>([])
  const timers = React.useRef(new Map<string, ReturnType<typeof setTimeout>>())

  const dismiss = React.useCallback((id: string) => {
    const timer = timers.current.get(id)
    if (timer) {
      clearTimeout(timer)
      timers.current.delete(id)
    }
    setToasts((current) => current.filter((item) => item.id !== id))
  }, [])

  const toast = React.useCallback(
    (options: ToastOptions) => {
      const id = createId()
      const record: ToastRecord = {
        id,
        title: options.title,
        description: options.description,
        variant: options.variant ?? "info",
        durationMs: options.durationMs ?? 4500,
      }
      setToasts((current) => [...current.slice(-3), record])
      const timer = setTimeout(() => dismiss(id), record.durationMs)
      timers.current.set(id, timer)
      return id
    },
    [dismiss],
  )

  React.useEffect(() => {
    const pending = timers.current
    return () => {
      pending.forEach((timer) => clearTimeout(timer))
      pending.clear()
    }
  }, [])

  const value = React.useMemo(() => ({ toast, dismiss }), [toast, dismiss])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-3 p-4 sm:items-end sm:p-6"
        aria-live="polite"
        aria-atomic="false"
      >
        {toasts.map((item) => (
          <div
            key={item.id}
            role={item.variant === "error" ? "alert" : "status"}
            className={cn(
              "pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded border p-4 shadow-sm animate-fade-in",
              variantClasses[item.variant],
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                "mt-1 h-2 w-2 shrink-0 rounded-full",
                accentClasses[item.variant],
              )}
            />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-ink">{item.title}</p>
              {item.description ? (
                <p className="mt-1 text-sm text-muted">{item.description}</p>
              ) : null}
            </div>
            <button
              type="button"
              onClick={() => dismiss(item.id)}
              aria-label="Dismiss notification"
              className="-mr-1 -mt-1 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded text-muted transition-colors hover:bg-[#e8e5df] hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              <span aria-hidden="true" className="text-lg leading-none">
                ×
              </span>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const context = React.useContext(ToastContext)
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider")
  }
  return context
}
