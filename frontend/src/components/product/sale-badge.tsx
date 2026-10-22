import { Badge } from "@/components/ui/badge"

export function SaleBadge({ className }: { className?: string }) {
  return (
    <Badge variant="accent" className={className}>
      Sale
    </Badge>
  )
}
