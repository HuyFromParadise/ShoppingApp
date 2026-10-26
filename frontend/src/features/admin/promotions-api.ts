import { api } from "@/lib/api/client"
import { ENDPOINTS } from "@/lib/api/endpoints"
import type {
  AdminPromotion,
  CreatePromotionInput,
  UpdatePromotionInput,
} from "@/types/p1"

/**
 * Admin promotions (PROJECT_SPEC §6, §11). A promotion is deactivated, never
 * deleted, so historical orders keep referring to a code that still exists.
 */

export function listPromotions(): Promise<AdminPromotion[]> {
  return api.get<AdminPromotion[]>(ENDPOINTS.admin.promotions)
}

export function createPromotion(
  input: CreatePromotionInput,
): Promise<AdminPromotion> {
  return api.post<AdminPromotion>(ENDPOINTS.admin.promotions, input)
}

export function updatePromotion(
  promotionId: string,
  input: UpdatePromotionInput,
): Promise<AdminPromotion> {
  return api.patch<AdminPromotion>(ENDPOINTS.admin.promotion(promotionId), input)
}
