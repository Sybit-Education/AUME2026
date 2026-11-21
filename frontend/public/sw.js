const CACHE_NAME = 'aume-app-shell-v1'

async function cacheAsset(cache, url, seen = new Set()) {
  const assetUrl = new URL(url, self.location.origin)
  if (assetUrl.origin !== self.location.origin || seen.has(assetUrl.href)) return
  seen.add(assetUrl.href)

  const response = await fetch(assetUrl.href)
  if (!response.ok) return
  await cache.put(assetUrl.href, response.clone())

  if (!response.headers.get('Content-Type')?.includes('javascript')) return
  const source = await response.text()
  const imports = source.matchAll(/(?:from\s*|import\s*\()\s*['"]([^'"]+\.js(?:\?[^'"]*)?)['"]/g)
  await Promise.all([...imports].map((match) => cacheAsset(cache, match[1], seen)))
}

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME)
    const response = await fetch('/')
    if (!response.ok) throw new Error('Unable to precache the application shell.')
    const html = await response.clone().text()
    await cache.put('/', response.clone())
    await cache.put('/index.html', response.clone())

    const assets = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)]
      .map((match) => match[1])
    await Promise.all(assets.map((asset) => cacheAsset(cache, asset)))
    await self.skipWaiting()
  })())
})

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys()
    await Promise.all(names.filter((name) => name.startsWith('aume-app-shell-') && name !== CACHE_NAME)
      .map((name) => caches.delete(name)))
    await self.clients.claim()
  })())
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return

  if (request.mode === 'navigate') {
    event.respondWith((async () => {
      try {
        const response = await fetch(request)
        if (response.ok) {
          const cache = await caches.open(CACHE_NAME)
          await cache.put('/index.html', response.clone())
        }
        return response
      } catch {
        const cached = await caches.match('/index.html') ?? await caches.match('/')
        if (cached) return cached
        throw new Error('The application shell is not available offline.')
      }
    })())
    return
  }

  if (!url.pathname.startsWith('/assets/') && !['/favicon.svg', '/icons.svg'].includes(url.pathname)) return
  event.respondWith((async () => {
    const cached = await caches.match(request)
    if (cached) return cached
    const response = await fetch(request)
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME)
      await cache.put(request, response.clone())
    }
    return response
  })())
})
