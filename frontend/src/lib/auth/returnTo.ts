// Only application-relative destinations may be used after authentication.
export function safeReturnTo(value: string | null | undefined): string {
  if (!value?.startsWith('/') || value.startsWith('//')) return '/'
  try {
    const origin = 'https://aume.invalid'
    const url = new URL(value, origin)
    const path = decodeURIComponent(url.pathname).replace(/\/+$/, '').toLowerCase()
    if (url.origin !== origin || path === '/login' || path === '/api' || path.startsWith('/api/')) return '/'
    return url.pathname + url.search + url.hash
  } catch {
    return '/'
  }
}

export function loginDestination(path: string): string {
  return `/login?${new URLSearchParams({ returnTo: safeReturnTo(path) })}`
}
