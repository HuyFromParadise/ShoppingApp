import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"

export default function NotFound() {
  return (
    <div className="container-store py-20">
      <EmptyState
        title="Page not found"
        description="The page you are looking for does not exist or has moved."
        action={
          <Link href="/" className={buttonVariants({ variant: "secondary" })}>
            Back to home
          </Link>
        }
      />
    </div>
  )
}
