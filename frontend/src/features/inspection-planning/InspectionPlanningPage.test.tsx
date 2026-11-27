// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import type { AnnouncementDraft, InspectionPeriod, PlanningOptions } from './api'
import { inspectionPlanningRoutes } from './routes'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['INSPECTION_PLAN', 'RESIDENCE_READ'] }
const caretaker: LoggedInUser = { userKey: 'caretaker', name: 'Kim', roles: ['CARETAKER'], privileges: ['INSPECTION_PERIOD_RESPOND'] }
const bea = { id: 10, name: 'Bea' }
const cem = { id: 11, name: 'Cem' }
let signedIn: LoggedInUser
let options: PlanningOptions
let periods: InspectionPeriod[]
let writeResponse: (() => Response) | null
let draft: AnnouncementDraft
let routers: ReturnType<typeof createMemoryRouter>[] = []

function period(overrides: Partial<InspectionPeriod> = {}): InspectionPeriod {
  return {
    id: 7, residenceId: 1, residenceName: 'Sonne', kind: 'REGULAR', status: 'WAITING_FOR_CARETAKER', announcedAt: null, rescheduledAt: null,
    startsOn: '2026-12-01', endsOn: '2026-12-03', windowStart: '09:00:00', windowEnd: '16:00:00',
    caretaker: bea, flatCount: 2, counter: null, version: 3, ...overrides,
  }
}

function mount(path = '/inspection-planning') {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [...inspectionPlanningRoutes, { path: 'forbidden', element: <h1>forbidden</h1> }],
  }], { initialEntries: [path] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => path}><RouterProvider router={router} /></AuthProvider>)
  return router
}

const writes = () => vi.mocked(api).mock.calls.filter(([, init]) => init?.method === 'POST' || init?.method === 'PUT')
const newForm = async () => within(await screen.findByRole('form', { name: 'Neue Kontrolle' }))

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = clerk
  writeResponse = null
  periods = []
  draft = { periodId: 7, residenceName: 'Sonne', flatCount: 2, channels: ['IN_APP'], message: 'Liebe WG,\n\nVorlage' }
  options = {
    residences: [
      { id: 1, name: 'Sonne', flatCount: 2, caretakerId: 10, flatsWithOpenInspection: [] },
      { id: 2, name: 'Egg', flatCount: 2, caretakerId: 11, flatsWithOpenInspection: ['E-12'] },
      { id: 3, name: 'Mond', flatCount: 1, caretakerId: null, flatsWithOpenInspection: ['M-01'] },
    ],
    caretakers: [bea, cem],
  }
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, init) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path === '/api/attachments') return Response.json({ id: 55, contentType: 'application/pdf', sizeBytes: 4 }, { status: 201 })
    if (String(path).endsWith('/announcement-draft')) return Response.json(draft)
    if (init?.method === 'POST' || init?.method === 'PUT') {
      if (writeResponse) return writeResponse()
      if (path === '/api/inspection-periods') periods = [period()]
      return Response.json(period())
    }
    if (path === '/api/inspection-periods/options') return Response.json(options)
    if (String(path).startsWith('/api/inspection-periods?')) {
      return Response.json({ content: periods, page: { number: 0, size: 20, totalElements: periods.length, totalPages: 1 } })
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
})

it('prefills the form and sends a proposal for the whole residence', async () => {
  mount()
  const form = await newForm()
  expect(form.getByText('2 WGs gehören zur Kontrolle')).toBeDefined()
  expect((form.getByLabelText('Kontrollart') as HTMLSelectElement).value).toBe('REGULAR')
  expect((form.getByLabelText('Uhrzeit von') as HTMLInputElement).value).toBe('09:00')
  expect((form.getByLabelText('Uhrzeit bis') as HTMLInputElement).value).toBe('16:00')
  expect((form.getByLabelText('Objektbetreuung') as HTMLSelectElement).value).toBe('10')

  fireEvent.change(form.getByLabelText('Startdatum'), { target: { value: '2026-12-01' } })
  fireEvent.change(form.getByLabelText('Enddatum'), { target: { value: '2026-12-03' } })
  fireEvent.click(form.getByRole('button', { name: 'Vorschlag an Objektbetreuung senden' }))

  expect((await screen.findByRole('status')).textContent).toBe('Vorschlag an die Objektbetreuung gesendet.')
  expect(await screen.findByText('Wartet auf Objektbetreuung')).toBeDefined()
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({
    residenceId: 1, startsOn: '2026-12-01', endsOn: '2026-12-03', windowStart: '09:00', windowEnd: '16:00', caretakerId: 10,
  })
})

it('follows the residence for caretaker, flat count and open-inspection hint', async () => {
  mount()
  const form = await newForm()
  fireEvent.change(form.getByLabelText('Wohnanlage'), { target: { value: '2' } })
  expect((await form.findByRole('note')).textContent).toContain('E-12')
  expect((form.getByLabelText('Objektbetreuung') as HTMLSelectElement).value).toBe('11')
  expect((form.getByRole('button', { name: 'Vorschlag an Objektbetreuung senden' }) as HTMLButtonElement).disabled).toBe(false)
})

it('preselects the first residence that still has free flats', async () => {
  options.residences.reverse()
  mount()
  expect(((await newForm()).getByLabelText('Wohnanlage') as HTMLSelectElement).value).toBe('2')
})

it('blocks sending when every flat already has an open inspection', async () => {
  mount('/inspection-planning?residence=3')
  const form = await newForm()
  expect((form.getByRole('button', { name: 'Vorschlag an Objektbetreuung senden' }) as HTMLButtonElement).disabled).toBe(true)
  expect(form.getByRole('note').textContent).toContain('M-01')
})

it('names the reason and keeps the input when the backend rejects the dates', async () => {
  writeResponse = () => Response.json({ code: 'end_before_start' }, { status: 400 })
  mount()
  const form = await newForm()
  fireEvent.change(form.getByLabelText('Startdatum'), { target: { value: '2026-12-05' } })
  fireEvent.change(form.getByLabelText('Enddatum'), { target: { value: '2026-12-01' } })
  fireEvent.click(form.getByRole('button', { name: 'Vorschlag an Objektbetreuung senden' }))
  expect((await screen.findByRole('alert')).textContent).toBe('Das Enddatum liegt vor dem Startdatum.')
  expect(((await newForm()).getByLabelText('Startdatum') as HTMLInputElement).value).toBe('2026-12-05')
})

it('shows a short counter proposal in the list and its details in a dialog', async () => {
  periods = [period({ status: 'COUNTER_PROPOSED', counter: { startsOn: '2026-12-10', endsOn: '2026-12-12', windowStart: '10:00:00', windowEnd: '14:00:00', reason: 'Urlaub' } })]
  mount()
  expect(await screen.findByText('Neu: 10.–12.12.')).toBeDefined()
  expect(screen.queryByText('Urlaub')).toBeNull()
  fireEvent.click(screen.getByRole('link', { name: 'Gegenvorschlag für Sonne prüfen' }))
  const dialog = within(await screen.findByRole('dialog', { name: 'Gegenvorschlag · Sonne' }))
  expect(dialog.getByText('01.–03.12.2026')).toBeDefined()
  expect(dialog.getByText('10.–12.12.2026')).toBeDefined()
  expect(dialog.getByText('09:00 – 16:00')).toBeDefined()
  expect(dialog.getByText('10:00 – 14:00')).toBeDefined()
  expect(dialog.getByText('Urlaub')).toBeDefined()
  fireEvent.click(dialog.getByRole('button', { name: 'Annehmen' }))
  expect((await screen.findByRole('status')).textContent).toBe('Gegenvorschlag angenommen.')
  expect(writes()[0][0]).toBe('/api/inspection-periods/7/accept-counter')
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({ version: 3 })
})

it('opens the edit form from the review dialog', async () => {
  periods = [period({ status: 'COUNTER_PROPOSED', counter: { startsOn: '2026-12-10', endsOn: '2026-12-12', windowStart: '10:00:00', windowEnd: '14:00:00', reason: 'Urlaub' } })]
  mount('/inspection-planning?review=7')
  const review = within(await screen.findByRole('dialog', { name: 'Gegenvorschlag · Sonne' }))
  fireEvent.click(review.getByRole('link', { name: 'Neuer Zeitraum' }))
  expect(await screen.findByRole('dialog', { name: 'Zeitraum ändern · Sonne' })).toBeDefined()
  expect(screen.queryByRole('dialog', { name: 'Gegenvorschlag · Sonne' })).toBeNull()
})

it('sends a changed period with its version', async () => {
  periods = [period({ status: 'AGREED' })]
  mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Zeitraum für Sonne ändern' }))
  expect(await screen.findByRole('dialog', { name: 'Zeitraum ändern · Sonne' })).toBeDefined()
  expect(screen.getByRole('table', { name: 'Geplante Kontrollen' }).textContent).toContain('Sonne')
  const form = within(await screen.findByRole('form', { name: 'Zeitraum für Sonne ändern' }))
  fireEvent.change(form.getByLabelText('Enddatum'), { target: { value: '2026-12-04' } })
  fireEvent.change(form.getByLabelText('Objektbetreuung'), { target: { value: '11' } })
  fireEvent.click(form.getByRole('button', { name: 'An Objektbetreuung senden' }))
  expect((await screen.findByRole('status')).textContent).toBe('Änderung an die Objektbetreuung gesendet.')
  expect(writes()[0][0]).toBe('/api/inspection-periods/7')
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({
    startsOn: '2026-12-01', endsOn: '2026-12-04', windowStart: '09:00', windowEnd: '16:00', caretakerId: 11, version: 3,
  })
})

it('offers no changes for an announced period', async () => {
  periods = [period({ status: 'AGREED', announcedAt: '2026-11-22T10:00:00Z' })]
  mount()
  expect(await screen.findByText('Angekündigt')).toBeDefined()
  expect(screen.queryByRole('link', { name: 'Zeitraum für Sonne ändern' })).toBeNull()
})

it('offers announcing only for an agreed period', async () => {
  periods = [period(), period({ id: 8, residenceName: 'Egg', status: 'AGREED' })]
  mount()
  expect(await screen.findByRole('link', { name: 'Kontrolle für Egg ankündigen' })).toBeDefined()
  expect(screen.queryByRole('link', { name: 'Kontrolle für Sonne ankündigen' })).toBeNull()
})

it('announces an agreed period with an edited template after a confirmation step', async () => {
  periods = [period({ status: 'AGREED' })]
  mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Kontrolle für Sonne ankündigen' }))
  const dialog = within(await screen.findByRole('dialog', { name: 'Kontrolle ankündigen · Sonne' }))
  expect(dialog.getByText('Alle 2 WGs der Wohnanlage')).toBeDefined()
  expect(dialog.getByText('In der App')).toBeDefined()
  const message = dialog.getByLabelText('Nachricht') as HTMLTextAreaElement
  expect(message.value).toBe('Liebe WG,\n\nVorlage')
  fireEvent.change(message, { target: { value: 'Eigener Text' } })

  fireEvent.click(dialog.getByRole('button', { name: 'Weiter' }))
  expect(dialog.getByText('Bitte prüfe die Angaben. Nach dem Senden steht die Kontrolle auf „Angekündigt“.')).toBeDefined()
  expect(dialog.getByText('Kein Anhang')).toBeDefined()
  expect(writes()).toHaveLength(0)

  fireEvent.click(dialog.getByRole('button', { name: 'Jetzt ankündigen' }))
  expect((await screen.findByRole('status')).textContent).toBe('Kontrolle angekündigt. Alle WGs sehen die Nachricht in der App.')
  expect(writes().map(([path]) => path)).toEqual(['/api/inspection-periods/7/announce'])
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({ message: 'Eigener Text', attachmentId: null, attachmentName: null, version: 3 })
})

it('blocks the next step without a message and goes back to the text', async () => {
  periods = [period({ status: 'AGREED' })]
  mount('/inspection-planning?announce=7')
  const dialog = within(await screen.findByRole('dialog', { name: 'Kontrolle ankündigen · Sonne' }))
  fireEvent.change(dialog.getByLabelText('Nachricht'), { target: { value: '  ' } })
  expect((dialog.getByRole('button', { name: 'Weiter' }) as HTMLButtonElement).disabled).toBe(true)
  fireEvent.change(dialog.getByLabelText('Nachricht'), { target: { value: 'Text' } })
  fireEvent.click(dialog.getByRole('button', { name: 'Weiter' }))
  fireEvent.click(dialog.getByRole('button', { name: 'Zurück' }))
  expect((dialog.getByLabelText('Nachricht') as HTMLTextAreaElement).value).toBe('Text')
})

it('uploads the optional attachment only when sending', async () => {
  periods = [period({ status: 'AGREED' })]
  mount('/inspection-planning?announce=7')
  const dialog = within(await screen.findByRole('dialog', { name: 'Kontrolle ankündigen · Sonne' }))
  const file = new File(['%PDF'], 'Checkliste.pdf', { type: 'application/pdf' })
  fireEvent.change(dialog.getByLabelText('Anhang (optional)'), { target: { files: [file] } })
  fireEvent.click(dialog.getByRole('button', { name: 'Weiter' }))
  expect(dialog.getByText('Checkliste.pdf')).toBeDefined()
  expect(writes()).toHaveLength(0)

  fireEvent.click(dialog.getByRole('button', { name: 'Jetzt ankündigen' }))
  await screen.findByRole('status')
  expect(writes().map(([path]) => path)).toEqual(['/api/attachments', '/api/inspection-periods/7/announce'])
  expect(writes()[0][1]?.body).toBeInstanceOf(FormData)
  expect(JSON.parse(String(writes()[1][1]?.body))).toMatchObject({ attachmentId: 55, attachmentName: 'Checkliste.pdf' })
})

it('keeps the announcement dialog open with the reason when sending fails', async () => {
  periods = [period({ status: 'AGREED' })]
  writeResponse = () => Response.json({ code: 'period_changed' }, { status: 409 })
  mount('/inspection-planning?announce=7')
  const dialog = within(await screen.findByRole('dialog', { name: 'Kontrolle ankündigen · Sonne' }))
  fireEvent.click(dialog.getByRole('button', { name: 'Weiter' }))
  fireEvent.click(dialog.getByRole('button', { name: 'Jetzt ankündigen' }))
  expect((await dialog.findByRole('alert')).textContent).toBe('Der Zeitraum wurde inzwischen geändert. Bitte prüfe den aktuellen Stand.')
  expect(dialog.getByRole('button', { name: 'Jetzt ankündigen' })).toBeDefined()
})

it('changes the date of an announced period without asking the caretaker again', async () => {
  periods = [period({ status: 'AGREED', announcedAt: '2026-11-22T10:00:00Z' })]
  mount()
  expect(await screen.findByText('Angekündigt')).toBeDefined()
  fireEvent.click(screen.getByRole('link', { name: 'Termin für Sonne ändern' }))
  const form = within(await screen.findByRole('form', { name: 'Termin für Sonne ändern' }))
  expect(form.queryByLabelText('Objektbetreuung')).toBeNull()
  expect(form.getByText(/Alle 2 WGs bekommen automatisch eine Nachricht/)).toBeDefined()
  fireEvent.change(form.getByLabelText('Enddatum'), { target: { value: '2026-12-04' } })
  fireEvent.click(form.getByRole('button', { name: 'Termin ändern und WGs informieren' }))
  expect((await screen.findByRole('status')).textContent).toBe('Termin geändert. Alle WGs bekommen automatisch eine Nachricht.')
  expect(writes()[0][0]).toBe('/api/inspection-periods/7/reschedule')
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({
    startsOn: '2026-12-01', endsOn: '2026-12-04', windowStart: '09:00', windowEnd: '16:00', version: 3,
  })
})

it('marks a rescheduled period like an announced one, without open work', async () => {
  periods = [period({ status: 'AGREED', announcedAt: '2026-11-22T10:00:00Z', rescheduledAt: '2026-11-24T09:30:00Z' })]
  mount()
  expect((await screen.findByText('Termin geändert')).className).toBe('chip t')
  expect(screen.getByRole('link', { name: 'Termin für Sonne ändern' })).toBeDefined()
  expect(screen.queryByRole('link', { name: 'Kontrolle für Sonne ankündigen' })).toBeNull()
})

it('blocks a residence without flats and asks for a caretaker when none is assigned', async () => {
  options.residences.push({ id: 4, name: 'Leer', flatCount: 0, caretakerId: null, flatsWithOpenInspection: [] })
  mount('/inspection-planning?residence=4')
  const form = await newForm()
  expect(form.getByText('0 WGs gehören zur Kontrolle')).toBeDefined()
  expect((form.getByLabelText('Objektbetreuung') as HTMLSelectElement).value).toBe('')
  expect(form.getByRole('option', { name: 'Objektbetreuung wählen' })).toBeDefined()
  expect((form.getByRole('button', { name: 'Vorschlag an Objektbetreuung senden' }) as HTMLButtonElement).disabled).toBe(true)
})

it('keeps the edit form and the input when a change is rejected', async () => {
  periods = [period()]
  writeResponse = () => Response.json({ code: 'start_not_in_future' }, { status: 400 })
  mount('/inspection-planning?edit=7')
  const form = within(await screen.findByRole('form', { name: 'Zeitraum für Sonne ändern' }))
  fireEvent.change(form.getByLabelText('Startdatum'), { target: { value: '2026-11-01' } })
  fireEvent.click(form.getByRole('button', { name: 'An Objektbetreuung senden' }))
  expect((await screen.findByRole('alert')).textContent).toBe('Das Startdatum muss in der Zukunft liegen.')
  const stillOpen = within(await screen.findByRole('form', { name: 'Zeitraum für Sonne ändern' }))
  expect((stillOpen.getByLabelText('Startdatum') as HTMLInputElement).value).toBe('2026-11-01')
})

it.each([
  ['a changed period', () => Response.json({ code: 'period_changed' }, { status: 409 }), 'Der Zeitraum wurde inzwischen geändert. Bitte prüfe den aktuellen Stand.'],
  ['an unexpected server error', () => new Response(null, { status: 500 }), 'Die Änderung konnte nicht gespeichert werden.'],
])('shows an error when accepting fails because of %s', async (_case, response, message) => {
  periods = [period({ status: 'COUNTER_PROPOSED', counter: { startsOn: '2026-12-10', endsOn: '2026-12-12', windowStart: '10:00:00', windowEnd: '14:00:00', reason: 'Urlaub' } })]
  writeResponse = response
  mount('/inspection-planning?review=7')
  const dialog = within(await screen.findByRole('dialog', { name: 'Gegenvorschlag · Sonne' }))
  fireEvent.click(dialog.getByRole('button', { name: 'Annehmen' }))
  expect((await dialog.findByRole('alert')).textContent).toBe(message)
  expect(dialog.getByText('Urlaub')).toBeDefined()
})

it('withdraws a proposal after confirming the dialog', async () => {
  periods = [period()]
  mount()
  fireEvent.click(await screen.findByRole('button', { name: 'Vorschlag für Sonne zurückziehen' }))
  const dialog = within(await screen.findByRole('dialog', { name: 'Vorschlag zurückziehen?' }))
  expect(dialog.getByText('Der Vorschlag für Sonne wird gelöscht. Die geplanten WG-Kontrollen werden entfernt.')).toBeDefined()
  expect(writes()).toHaveLength(0)
  fireEvent.click(dialog.getByRole('button', { name: 'Zurückziehen' }))
  expect((await screen.findByRole('status')).textContent).toBe('Vorschlag zurückgezogen.')
  expect(writes()[0][0]).toBe('/api/inspection-periods/7/withdraw')
  expect(JSON.parse(String(writes()[0][1]?.body))).toEqual({ version: 3 })
})

it('keeps the proposal when withdrawing is cancelled', async () => {
  periods = [period()]
  mount()
  fireEvent.click(await screen.findByRole('button', { name: 'Vorschlag für Sonne zurückziehen' }))
  const dialog = within(await screen.findByRole('dialog'))
  fireEvent.click(dialog.getByRole('button', { name: 'Abbrechen' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(writes()).toHaveLength(0)
})

it('does not load the page without the planning privilege', async () => {
  signedIn = caretaker
  mount()
  await screen.findByRole('heading', { name: 'forbidden' })
  expect(vi.mocked(api).mock.calls.some(([path]) => String(path).startsWith('/api/inspection-periods'))).toBe(false)
})
