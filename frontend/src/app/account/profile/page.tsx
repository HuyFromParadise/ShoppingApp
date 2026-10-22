"use client"

import { ProfileForm } from "@/components/account/profile-form"
import { ErrorState } from "@/components/ui/error-state"
import { Skeleton } from "@/components/ui/skeleton"
import { useProfile } from "@/features/account/hooks"

export default function ProfilePage() {
  const { data: profile, isPending, error, refetch } = useProfile()

  if (isPending) {
    return <Skeleton lines={5} />
  }

  if (error || !profile) {
    return (
      <ErrorState
        title="We could not load your profile"
        onRetry={() => void refetch()}
      />
    )
  }

  return <ProfileForm profile={profile} />
}
