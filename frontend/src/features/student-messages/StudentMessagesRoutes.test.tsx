// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
// eslint-disable-next-line no-restricted-imports -- Exercise the real app in routing integration tests.
import App from '@/app/App'
// eslint-disable-next-line no-restricted-imports -- Use the production route tree in this test suite.
import { createAppRoutes } from '@/app/router'
import { api, devLogin, login, type LoggedInUser } from '@/lib/auth/client'
import { invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import { downloadMessageChecklist, type StudentMessage } from './api'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  devLogin: vi.fn(),
  logout: vi.fn(),
}))

const route = '/student/messages'
const endpoint = '/api/student/notifications'
const student: LoggedInUser = {
  userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['CHECKLIST_READ', 'WG_MEMBERSHIP'],
}
const announcement: StudentMessage = {
  id: 11, type: 'ANNOUNCEMENT', createdAt: '2026-11-28T00:01:00Z', occurredAt: '2026-11-27T23:30:00Z',
  readAt: null, flatLabel: 'S-01', startsOn: '2026-12-07', endsOn: '2026-12-11',
  windowStart: '09:00', windowEnd: '16:00', hasChecklistPdf: true,
  announcementKind: null, announcementMessage: null, attachment: null,
}
const fileName = 'Hinweise für die Küche.pdf'
const originalText = 'Liebe WG,\n\nbitte räumt die Spüle frei.\n<img src="x" onerror="alert(1)">'
const week: StudentMessage = { ...announcement, id: 12, type: 'REMINDER_7_DAYS', hasChecklistPdf: false }
const tomorrow: StudentMessage = { ...week, id: 13, type: 'REMINDER_1_DAY' }
const readCalls = () => vi.mocked(api).mock.calls.filter(([path, options]) =>
  path.endsWith('/read') && options?.method === 'PUT')

let account: LoggedInUser | null
let pages: StudentMessage[][]
let readResponse: () => Response | Promise<Response>
let pdfResponse: () => Response
let attachmentResponse: () => Response | Promise<Response>
let listStatus: number
let routers: ReturnType<typeof createMemoryRouter>[]

function mount(path = route) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  routers.push(router)
  render(<App router={router} />)
  return router
}

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  await i18n.changeLanguage('de')
  account = student
  pages = [[announcement, week, tomorrow]]
  readResponse = () => new Response(null, { status: 204 })
  pdfResponse = () => new Response('PDF', { headers: { 'Content-Type': 'application/pdf' } })
  attachmentResponse = () => new Response('Originaldatei')
  listStatus = 200
  routers = []
  vi.mocked(login).mockImplementation(async () => { account = student })
  vi.mocked(devLogin).mockImplementation(async () => { account = student })
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/me') return account ? Response.json(account) : new Response(null, { status: 401 })
    if (path === `${endpoint}/unread-count`) {
      return Response.json({ count: pages.flat().filter((message) => message.readAt === null).length })
    }
    if (path.startsWith(`${endpoint}?page=`)) {
      if (listStatus !== 200) return new Response(null, { status: listStatus })
      const number = Number(new URL(path, 'http://localhost').searchParams.get('page'))
      return Response.json({
        content: pages[number] ?? [],
        page: { size: 20, number, totalPages: pages.length, totalElements: pages.flat().length },
      })
    }
    if (path.endsWith('/read')) {
      const response = await readResponse()
      if (response.ok) {
        const id = Number(path.split('/').at(-2))
        pages = pages.map((page) => page.map((message) =>
          message.id === id ? { ...message, readAt: '2026-11-28T08:00:00Z' } : message))
      }
      return response
    }
    if (path.endsWith('/checklist.pdf')) return pdfResponse()
    if (path.endsWith('/attachment')) return attachmentResponse()
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it('links to messages above My Flat, keeps More active and provides a back link', async () => {
  const router = mount('/student/more')
  const living = within(await screen.findByRole('region', { name: 'Wohnen' }))
  expect(screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent))
    .toEqual(['Wohnen', 'Konto und Datenschutz'])
  const entry = living.getByRole('link', { name: /Nachrichten.*Ankündigungen und Erinnerungen/ })
  expect(living.getAllByRole('link')).toEqual([entry, living.getByRole('link', { name: /Meine WG/ })])
  expect(entry.getAttribute('href')).toBe(route)
  fireEvent.click(entry)

  await screen.findByRole('heading', { name: 'Nachrichten', level: 1 })
  expect(router.state.location.pathname).toBe(route)
  expect(screen.getByRole('link', { name: 'Mehr' }).getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('link', { name: 'Zurück' }).getAttribute('href')).toBe('/student/more')
  expect(screen.getByRole('button', { name: 'Abmelden' })).toBeTruthy()
})

it('returns to the requested page after login without making a request while signed out', async () => {
  account = null
  const router = mount(`${route}?page=1`)
  await screen.findByRole('button', { name: 'Dev-Login' })
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: 'Dev-Login' }))
  await screen.findByRole('heading', { name: 'Nachrichten', level: 1 })
  expect(router.state.location.search).toBe('?page=1')
})

it.each(['CLERK', 'CARETAKER'])('blocks direct access for %s before calling the API', async (role) => {
  account = { ...student, roles: [role] }
  const router = mount()
  await screen.findByRole('heading', { name: 'Zugriff nicht erlaubt' })
  expect(router.state.location.pathname).toBe('/forbidden')
  expect(vi.mocked(api).mock.calls.every(([path]) => path === '/api/me')).toBe(true)
})

it('only marks a message after intentional opening and prevents duplicate requests while pending', async () => {
  let complete!: (response: Response) => void
  readResponse = () => new Promise((resolve) => { complete = resolve })
  mount()
  const head = await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  await screen.findByRole('link', { name: 'Nachrichten, 3 ungelesene Nachrichten' })
  expect(head.textContent).toContain('28.11.2026')
  expect(head.textContent).toContain('Neu')
  expect(head.getAttribute('aria-expanded')).toBe('false')
  expect(screen.queryByRole('button', { name: /herunterladen/ })).toBeNull()
  expect(readCalls()).toHaveLength(0)

  fireEvent.click(head)
  await waitFor(() => expect(readCalls()).toHaveLength(1))
  expect(readCalls()[0][0]).toBe(`${endpoint}/11/read`)
  expect(head.getAttribute('aria-expanded')).toBe('true')
  expect(screen.getByText(/Die Sauberkeitskontrolle für WG S-01/)).toBeTruthy()
  expect(screen.getByText('Die Objektbetreuung kommt zwischen 09:00 und 16:00 Uhr.')).toBeTruthy()
  expect(head.textContent).toContain('Neu')
  fireEvent.click(head)
  fireEvent.click(head)
  expect(readCalls()).toHaveLength(1)

  await act(async () => { complete(new Response(null, { status: 204 })) })
  await waitFor(() => expect(head.textContent).not.toContain('Neu'))
  expect(await screen.findByRole('link', { name: 'Nachrichten, 2 ungelesene Nachrichten' })).toBeTruthy()
  fireEvent.click(head)
  fireEvent.click(head)
  expect(readCalls()).toHaveLength(1)
})

it('keeps the unread badge on failure and allows a retry', async () => {
  readResponse = () => new Response(null, { status: 500 })
  mount()
  const head = await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  fireEvent.click(head)
  await screen.findByText('Die Nachricht konnte nicht als gelesen markiert werden.')
  expect(head.textContent).toContain('Neu')
  readResponse = () => new Response(null, { status: 204 })
  fireEvent.click(screen.getByRole('button', { name: 'Erneut als gelesen markieren' }))
  await waitFor(() => expect(head.textContent).not.toContain('Neu'))
  expect(readCalls()).toHaveLength(2)
})

it('uses pagination from the backend without marking closed messages as read', async () => {
  pages = [[announcement], [tomorrow]]
  const router = mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Nächste Seite' }))
  await screen.findByRole('button', { name: /Kontrollzeitraum beginnt morgen/ })
  expect(router.state.location.search).toBe('?page=1')
  expect(readCalls()).toHaveLength(0)
  fireEvent.click(screen.getByRole('link', { name: 'Vorherige Seite' }))
  await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  expect(readCalls()).toHaveLength(0)
})

it('does not apply a pending read to a message on another page', async () => {
  pages = [[announcement], [week]]
  let complete!: (response: Response) => void
  readResponse = () => new Promise((resolve) => { complete = resolve })
  mount()
  fireEvent.click(await screen.findByRole('button', { name: /Sauberkeitskontrolle/ }))
  await waitFor(() => expect(readCalls()).toHaveLength(1))
  fireEvent.click(screen.getByRole('link', { name: 'Nächste Seite' }))
  const next = await screen.findByRole('button', { name: /Kontrollzeitraum beginnt in 7 Tagen/ })
  await act(async () => { complete(new Response(null, { status: 204 })) })
  expect(next.textContent).toContain('Neu')
  expect(readCalls()).toHaveLength(1)
})

it('shows actionable checklist links on reminders and never a PDF', async () => {
  pages = [[{
    ...week, hasChecklistPdf: true, attachment: { fileName }, announcementMessage: originalText,
    announcementKind: 'RESCHEDULE',
  }, tomorrow]]
  mount()
  for (const title of [/Kontrollzeitraum beginnt in 7 Tagen/, /Kontrollzeitraum beginnt morgen/]) {
    const head = await screen.findByRole('button', { name: title })
    fireEvent.click(head)
    const card = within(head.closest('article')!)
    expect(card.getByRole('link', { name: 'Zur Checkliste' }).getAttribute('href')).toBe('/student/inspection/checklist')
    expect(card.queryByRole('button', { name: /herunterladen/ })).toBeNull()
    expect(card.queryByText('Termin geändert')).toBeNull()
    expect(card.queryByText(/bitte räumt/)).toBeNull()
    expect(card.getByText(/Euer Kontrollzeitraum/)).toBeTruthy()
  }
})

it.each([
  ['de', 'Ankündigung', 'Termin geändert', /Sauberkeitskontrolle/, 'WG S-01', /herunterladen/],
  ['en', 'Announcement', 'Inspection date changed', /Cleanliness inspection/, 'Flat S-01', /Download/],
] as const)('renders the original message as plain text and labels revisions in %s', async (
  language, initialLabel, revisedLabel, title, flat, download,
) => {
  await i18n.changeLanguage(language)
  const readAt = '2026-11-28T08:00:00Z'
  account = { ...student, privileges: [] }
  pages = [[{
    ...announcement, readAt, announcementKind: 'ANNOUNCEMENT', announcementMessage: originalText,
    hasChecklistPdf: false, attachment: { fileName },
  }, {
    ...announcement, id: 21, readAt, startsOn: '2026-12-09', hasChecklistPdf: false,
    announcementKind: 'RESCHEDULE',
  }]]
  mount()
  const head = await screen.findByRole('button', { name: new RegExp(initialLabel + '.*' + title.source) })
  expect(screen.queryByRole('button', { name: download })).toBeNull()
  fireEvent.click(head)

  const card = within(head.closest('article')!)
  const body = card.getByText(/Liebe WG/)
  expect(body.textContent).toBe(originalText)
  expect(body.childElementCount).toBe(0)
  expect(body.classList.contains('student-message-original')).toBe(true)
  expect(card.getByText(flat)).toBeTruthy()
  expect(card.queryByText(/Die Sauberkeitskontrolle für|will take place during/)).toBeNull()
  expect(card.getByRole('button', { name: download }).textContent).toBe(fileName)
  const revised = screen.getByRole('button', { name: new RegExp(revisedLabel + '.*' + title.source) })
  fireEvent.click(revised)
  const revisedCard = within(revised.closest('article')!)
  expect(revisedCard.getByText(/Die Sauberkeitskontrolle für|will take place during/)).toBeTruthy()
  expect(revisedCard.queryByRole('button', { name: download })).toBeNull()
  expect(readCalls()).toHaveLength(0)
})

it('only offers the individual attachment and allows retry after it fails', async () => {
  pages = [[{ ...announcement, readAt: '2026-11-28T08:00:00Z', attachment: { fileName } }]]
  let finishAttachment!: (response: Response) => void
  attachmentResponse = () => new Promise((resolve) => { finishAttachment = resolve })
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:message')
    static revokeObjectURL = vi.fn()
  })
  const saved: string[] = []
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    saved.push(this.download)
  })
  mount()
  fireEvent.click(await screen.findByRole('button', { name: /Sauberkeitskontrolle/ }))
  const attachment = screen.getByRole('button', { name: fileName + ' herunterladen' }) as HTMLButtonElement
  expect(screen.getAllByRole('button', { name: /herunterladen/ })).toEqual([attachment])
  expect(screen.queryByText(/liegt als Anhang bei/)).toBeNull()

  fireEvent.click(attachment)
  expect(attachment.disabled).toBe(true)
  expect(attachment.getAttribute('aria-busy')).toBe('true')
  await act(async () => { finishAttachment(new Response(null, { status: 404 })) })
  await screen.findByText('Der Anhang konnte nicht heruntergeladen werden.')
  expect(screen.queryByText('Die Checkliste konnte nicht heruntergeladen werden.')).toBeNull()
  expect(attachment.disabled).toBe(false)

  attachmentResponse = () => new Response('Originaldatei')
  fireEvent.click(attachment)
  await waitFor(() => expect(saved).toEqual([fileName]))
  expect(screen.queryByText('Der Anhang konnte nicht heruntergeladen werden.')).toBeNull()
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === endpoint + '/11/attachment')).toHaveLength(2)
  expect(vi.mocked(api).mock.calls.some(([path]) => path.endsWith('/checklist.pdf'))).toBe(false)
  expect(readCalls()).toHaveLength(0)
})

it('omits the attachment, incomplete time window and the checklist link when unavailable', async () => {
  account = { ...student, privileges: [] }
  pages = [[{ ...announcement, hasChecklistPdf: false, windowEnd: null }, tomorrow]]
  mount()
  fireEvent.click(await screen.findByRole('button', { name: /Sauberkeitskontrolle/ }))
  expect(screen.queryByText(/liegt als Anhang bei/)).toBeNull()
  expect(screen.queryByText(/Objektbetreuung kommt zwischen/)).toBeNull()
  expect(screen.queryByRole('button', { name: /herunterladen/ })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: /Kontrollzeitraum beginnt morgen/ }))
  expect(screen.queryByRole('link', { name: 'Zur Checkliste' })).toBeNull()
})

it('formats a year boundary and translates both reminder types in English', async () => {
  await i18n.changeLanguage('en')
  pages = [[{ ...announcement, startsOn: '2026-12-31', endsOn: '2027-01-02' }, week, tomorrow]]
  mount()
  await screen.findByRole('button', { name: /Cleanliness inspection 31 Dec 2026 to 02 Jan 2027/ })
  const reminder = screen.getByRole('button', { name: /The inspection period starts in 7 days/ })
  expect(screen.getByRole('button', { name: /The inspection period starts tomorrow/ })).toBeTruthy()
  fireEvent.click(reminder)
  expect(within(reminder.closest('article')!).getByRole('link', { name: 'Open checklist' })).toBeTruthy()
})

it.each([
  ['de', 'Checkliste_Sauberkeit.pdf'],
  ['en', 'Cleanliness_Checklist.pdf'],
] as const)('retains the generated PDF download helper with the %s filename', async (language, file) => {
  await i18n.changeLanguage(language)
  pdfResponse = () => new Response('PDF', { headers: {
    'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="${file}"`,
  } })
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:checklist')
    static revokeObjectURL = vi.fn()
  })
  const save = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    expect(this.download).toBe(file)
  })
  await downloadMessageChecklist(announcement.id, language, new AbortController().signal)
  expect(save).toHaveBeenCalledOnce()
  expect(vi.mocked(api).mock.calls.some(([path]) => path === `${endpoint}/11/checklist.pdf`)).toBe(true)
  expect(readCalls()).toHaveLength(0)
})

it('hides the generated PDF and its hint when the announcement has no individual attachment', async () => {
  pages = [[{ ...announcement, readAt: '2026-11-28T08:00:00Z' }]]
  mount()
  const head = await screen.findByRole('button', { name: /Sauberkeitskontrolle/ })
  fireEvent.click(head)
  const card = within(head.closest('article')!)
  expect(card.getByText(/Die Sauberkeitskontrolle für WG S-01/)).toBeTruthy()
  expect(card.queryByText(/liegt als Anhang bei/)).toBeNull()
  expect(card.queryByRole('button', { name: /herunterladen/ })).toBeNull()
  expect(vi.mocked(api).mock.calls.some(([path]) => path.endsWith('/checklist.pdf'))).toBe(false)
})

it('shows a usable empty state', async () => {
  pages = [[]]
  mount()
  expect(await screen.findByText('Keine Nachrichten vorhanden.')).toBeTruthy()
})

it('shows the translated loading error', async () => {
  listStatus = 500
  mount()
  expect(await screen.findByText('Die Nachrichten konnten nicht geladen werden.')).toBeTruthy()
})
