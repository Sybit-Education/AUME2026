interface Page<T> {
  content: T[]
  page: { number: number; size: number; totalElements: number; totalPages: number }
}

/**
 * Lädt alle Seiten einer Liste und gibt sie als eine Seite zurück. Die Tabellen filtern und zählen im Browser,
 * dafür braucht es alle Einträge, nicht nur die der ersten Seite.
 */
export async function loadAllPages<T>(load: (page: number) => Promise<Page<T>>): Promise<Page<T>> {
  const first = await load(0)
  const content = [...first.content]
  for (let number = 1; number < first.page.totalPages; number++) {
    content.push(...(await load(number)).content)
  }
  return { content, page: { ...first.page, number: 0, size: content.length, totalPages: 1 } }
}
