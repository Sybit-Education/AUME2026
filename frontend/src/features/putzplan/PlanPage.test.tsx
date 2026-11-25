// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from '@/lib/auth/AuthProvider'
import { api, type LoggedInUser } from '@/lib/auth/client'
import { ensureSession, invalidateSession } from '@/lib/auth/session'
import i18n from '@/lib/i18n'
import type { Plan, PlanTask } from './api'
import { planRoutes } from './routes'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const student: LoggedInUser = { userKey: 'student', name: 'Alex', roles: ['STUDENT'], privileges: ['WG_MEMBERSHIP'] }
const clerk: LoggedInUser = { userKey: 'clerk', name: 'Sam', roles: ['CLERK'], privileges: ['RESIDENCE_READ'] }
const week = '2026-11-23'
const mine: PlanTask = {
  id: 1, title: 'Bad reinigen', rhythm: 'WEEKLY', startWeek: week, assigneeId: 1, assigneeName: 'Alex',
  mine: true, done: false, doneBy: null, checkable: true,
}
const theirs: PlanTask = {
  id: 2, title: 'Flur wischen', rhythm: 'BIWEEKLY', startWeek: week, assigneeId: 2, assigneeName: 'Bea',
  mine: false, done: true, doneBy: 'Bea', checkable: false,
}
let signedIn: LoggedInUser
let plan: Plan | null
let routers: ReturnType<typeof createMemoryRouter>[] = []

function mount(path = '/student/cleaning-plan') {
  const router = createMemoryRouter([{
    path: '/',
    loader: ensureSession,
    HydrateFallback: () => null,
    children: [
      { path: 'student', children: [...planRoutes, { path: 'flat', element: <h1>wg</h1> }] },
      { path: 'forbidden', element: <h1>forbidden</h1> },
    ],
  }], { initialEntries: [path] })
  routers.push(router)
  render(<AuthProvider getRequestedPath={() => path}><RouterProvider router={router} /></AuthProvider>)
  return router
}

const calls = (method: string) => vi.mocked(api).mock.calls.filter(([, options]) => options?.method === method)

beforeEach(async () => {
  vi.resetAllMocks()
  invalidateSession()
  signedIn = student
  plan = {
    week, currentWeek: week, me: 1, members: [{ id: 1, name: 'Alex' }, { id: 2, name: 'Bea' }],
    tasks: [mine, theirs], allTitles: ['Bad reinigen', 'Flur wischen'],
    suggestions: [
      { criterionId: 1, area: 'Küche', title: 'Arbeitsflächen' },
      { criterionId: 2, area: 'Küche', title: 'Spüle' },
      { criterionId: 6, area: 'Boden', title: 'Küchenboden' },
      { criterionId: 9, area: 'Bad', title: 'Dusche & WC' },
    ],
  }
  await i18n.changeLanguage('de')
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/me') return Response.json(signedIn)
    if (path.startsWith('/api/wg/tasks') && !options?.method) {
      return plan ? Response.json(plan) : Response.json({ code: 'not_member' }, { status: 404 })
    }
    if (path === '/api/wg/tasks' && options?.method === 'POST') {
      return JSON.parse(String(options.body)).title === 'Doppelt'
        ? Response.json({ code: 'invalid_assignee' }, { status: 400 }) : new Response(null, { status: 201 })
    }
    if (path.startsWith('/api/wg/tasks/')) {
      if (path.endsWith('/completion') && plan) {
        plan = { ...plan, tasks: plan.tasks.map((task) => task.id === 1
          ? { ...task, done: options?.method === 'PUT', doneBy: options?.method === 'PUT' ? 'Alex' : null } : task) }
      } else if (options?.method === 'DELETE' && plan) {
        plan = { ...plan, tasks: plan.tasks.filter((task) => task.id !== 1) }
      }
      return new Response(null, { status: 204 })
    }
    throw new Error(`Unexpected API call: ${path}`)
  })
})

afterEach(() => {
  cleanup()
  routers.forEach((router) => router.dispose())
  routers = []
  vi.restoreAllMocks()
})

it('shows the week with who does what and keeps suggestions out of the main page', async () => {
  mount()
  expect(await screen.findByText('KW 48 · 23.11. bis 29.11.')).toBeTruthy()
  expect(screen.getByText('wöchentlich')).toBeTruthy()
  expect(screen.getByText('alle 2 Wochen · erledigt von Bea')).toBeTruthy()
  expect(screen.getByText('1 von 2 Aufgaben erledigt')).toBeTruthy()
  expect(screen.queryByRole('heading', { name: 'Putzplan' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Arbeitsflächen' })).toBeNull()
  expect(screen.getAllByRole('checkbox')).toHaveLength(1)
})

it('ticks off a task and can undo it', async () => {
  mount()
  const checkbox = await screen.findByRole('checkbox')
  await act(async () => { fireEvent.click(checkbox) })
  expect(await screen.findByText('wöchentlich · erledigt von Alex')).toBeTruthy()
  expect(calls('PUT').map(([path]) => path)).toEqual(['/api/wg/tasks/1/completion'])

  await act(async () => { fireEvent.click(screen.getByRole('checkbox')) })
  expect(await screen.findByText('wöchentlich')).toBeTruthy()
  expect(calls('DELETE').map(([path]) => path)).toEqual(['/api/wg/tasks/1/completion'])
})

it('picks a checklist suggestion in the form, links it and closes the form', async () => {
  const router = mount()
  fireEvent.click(await screen.findByRole('link', { name: '+ Aufgabe' }))
  const form = await screen.findByRole('form', { name: 'Aufgabe anlegen' })
  fireEvent.click(within(form).getByRole('button', { name: 'Arbeitsflächen' }))
  expect((within(form).getByLabelText('Aufgabe') as HTMLInputElement).value).toBe('Arbeitsflächen')
  expect(within(form).getByRole('button', { name: 'Arbeitsflächen' }).getAttribute('aria-pressed')).toBe('true')
  expect(within(form).queryByRole('button', { name: 'Spüle' })).toBeNull()

  await act(async () => { fireEvent.click(within(form).getByRole('button', { name: 'Speichern' })) })
  expect(JSON.parse(String(calls('POST')[0][1]?.body))).toEqual({
    title: 'Arbeitsflächen', assigneeId: 1, rhythm: 'WEEKLY', startWeek: week, criterionId: 1,
  })
  expect(router.state.location.search).toBe(`?week=${week}`)
  expect(screen.queryByRole('form', { name: 'Aufgabe anlegen' })).toBeNull()
})

it('filters suggestions while typing and saves own text without a checklist link', async () => {
  mount('/student/cleaning-plan?edit=new')
  const form = await screen.findByRole('form', { name: 'Aufgabe anlegen' })
  const title = within(form).getByLabelText('Aufgabe')

  fireEvent.change(title, { target: { value: 'kü' } })
  expect(within(form).getByRole('button', { name: 'Spüle' })).toBeTruthy()
  expect(within(form).getByRole('button', { name: 'Küchenboden' })).toBeTruthy()
  expect(within(form).queryByRole('button', { name: 'Dusche & WC' })).toBeNull()

  fireEvent.change(title, { target: { value: 'Fenster putzen' } })
  expect(within(form).queryByRole('group', { name: 'Aus der Checkliste' })).toBeNull()
  await act(async () => { fireEvent.click(within(form).getByRole('button', { name: 'Speichern' })) })
  expect(JSON.parse(String(calls('POST')[0][1]?.body))).toEqual({
    title: 'Fenster putzen', assigneeId: 1, rhythm: 'WEEKLY', startWeek: week,
  })
})

it('removes the link again when a picked suggestion is edited or tapped twice', async () => {
  mount('/student/cleaning-plan?edit=new')
  const form = await screen.findByRole('form', { name: 'Aufgabe anlegen' })
  const title = within(form).getByLabelText('Aufgabe') as HTMLInputElement

  fireEvent.click(within(form).getByRole('button', { name: 'Spüle' }))
  fireEvent.click(within(form).getByRole('button', { name: 'Spüle' }))
  expect(title.value).toBe('')
  expect(within(form).getByRole('button', { name: 'Arbeitsflächen' })).toBeTruthy()

  fireEvent.click(within(form).getByRole('button', { name: 'Spüle' }))
  fireEvent.change(title, { target: { value: 'Spüle entkalken' } })
  await act(async () => { fireEvent.click(within(form).getByRole('button', { name: 'Speichern' })) })
  expect(JSON.parse(String(calls('POST')[0][1]?.body)).criterionId).toBeUndefined()
})

it('opens the add form as a bottom sheet and closes it by button, escape and backdrop', async () => {
  const router = mount()
  const open = await screen.findByRole('link', { name: '+ Aufgabe' })
  const closed = async () => {
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Aufgabe anlegen' })).toBeNull())
    expect(router.state.location.search).toBe(`?week=${week}`)
  }

  fireEvent.click(open)
  await screen.findByRole('dialog', { name: 'Aufgabe anlegen' })
  expect(screen.queryByText('Aus der Checkliste')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
  await closed()

  fireEvent.click(screen.getByRole('link', { name: '+ Aufgabe' }))
  fireEvent.keyDown(await screen.findByRole('dialog', { name: 'Aufgabe anlegen' }), { key: 'Escape' })
  await closed()

  fireEvent.click(screen.getByRole('link', { name: '+ Aufgabe' }))
  const dialog = await screen.findByRole('dialog', { name: 'Aufgabe anlegen' })
  fireEvent.click(dialog.parentElement!)
  await closed()
})

it('offers a first-task button only while the flat has no tasks', async () => {
  plan = { ...plan!, tasks: [], allTitles: [] }
  const router = mount()
  fireEvent.click(await screen.findByRole('link', { name: 'Erste Aufgabe anlegen' }))
  expect(await screen.findByRole('form', { name: 'Aufgabe anlegen' })).toBeTruthy()
  expect(router.state.location.search).toContain('edit=new')
  expect(screen.queryByRole('link', { name: 'Erste Aufgabe anlegen' })).toBeNull()
})

it('keeps the form open with a translated error when saving fails', async () => {
  mount('/student/cleaning-plan?edit=new')
  const form = await screen.findByRole('form', { name: 'Aufgabe anlegen' })
  fireEvent.change(within(form).getByLabelText('Aufgabe'), { target: { value: 'Doppelt' } })
  await act(async () => { fireEvent.click(within(form).getByRole('button', { name: 'Speichern' })) })
  expect((await screen.findByRole('alert')).textContent).toBe('Bitte wähle eine Person aus deiner WG aus.')
  expect(screen.getByRole('form', { name: 'Aufgabe anlegen' })).toBeTruthy()
})

it('deletes only after confirming in a small bottom dialog', async () => {
  const confirm = vi.spyOn(window, 'confirm')
  mount()
  fireEvent.click(await screen.findByRole('button', { name: 'Bad reinigen löschen' }))
  const first = screen.getByRole('dialog', { name: 'Aufgabe löschen?' })
  expect(within(first).getByText('Bad reinigen')).toBeTruthy()
  fireEvent.click(within(first).getByRole('button', { name: 'Abbrechen' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(calls('DELETE')).toHaveLength(0)

  fireEvent.click(screen.getByRole('button', { name: 'Bad reinigen löschen' }))
  const second = screen.getByRole('dialog', { name: 'Aufgabe löschen?' })
  await act(async () => { fireEvent.click(within(second).getByRole('button', { name: 'Löschen' })) })
  expect(calls('DELETE').map(([path]) => path)).toEqual(['/api/wg/tasks/1'])
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(screen.queryByText('Bad reinigen')).toBeNull()
  expect(screen.getByText('Flur wischen')).toBeTruthy()
  expect(confirm).not.toHaveBeenCalled()
})

it('asks people without a flat to join first', async () => {
  plan = null
  mount()
  expect(await screen.findByText('Tritt zuerst deiner WG bei, um den Putzplan zu sehen.')).toBeTruthy()
  expect(screen.getByRole('link', { name: 'WG beitreten' }).getAttribute('href')).toBe('/student/flat')
})

it('sends staff to the forbidden page without loading the plan', async () => {
  signedIn = clerk
  mount()
  expect(await screen.findByRole('heading', { name: 'forbidden' })).toBeTruthy()
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith('/api/wg/tasks'))).toBe(false)
})
