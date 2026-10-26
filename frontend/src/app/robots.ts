import type { MetadataRoute } from "next"

const SITE_URL =
  process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/+$/, "") ?? "http://localhost:3000"

/**
 * Robots (project step: SEO). Everything public is crawlable; the authenticated
 * and admin areas are disallowed as well as protected by the API, so a crawler
 * never indexes an empty or erroring shell.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: [
          "/account",
          "/account/",
          "/admin",
          "/admin/",
          "/cart",
          "/checkout",
          "/login",
          "/register",
          "/wishlist",
        ],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  }
}
