/** Shared catalog DTOs mirroring the public backend responses. */

export interface CategorySummary {
  id: string
  name: string
  slug: string
}

export interface Category extends CategorySummary {
  description: string | null
  parentId: string | null
  sortOrder: number
}

export interface ProductImage {
  id: string
  url: string
  altText: string | null
  sortOrder: number
  primary: boolean
}

export interface ProductVariant {
  id: string
  sku: string
  size: string | null
  color: string | null
  priceMinor: number
  compareAtPriceMinor: number | null
  stockQuantity: number
  inStock: boolean
}

export interface ProductSummary {
  id: string
  name: string
  slug: string
  shortDescription: string | null
  category: CategorySummary
  priceMinor: number
  compareAtPriceMinor: number | null
  currency: string
  onSale: boolean
  inStock: boolean
  featured: boolean
  primaryImage: ProductImage | null
  createdAt: string
}

export interface ProductDetail {
  id: string
  name: string
  slug: string
  shortDescription: string | null
  description: string | null
  category: CategorySummary
  basePriceMinor: number
  priceMinor: number
  compareAtPriceMinor: number | null
  currency: string
  onSale: boolean
  inStock: boolean
  featured: boolean
  material: string | null
  fit: string | null
  careInstructions: string | null
  images: ProductImage[]
  variants: ProductVariant[]
  createdAt: string
  updatedAt: string
}
