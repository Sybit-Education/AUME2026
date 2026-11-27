// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import i18n from '@/lib/i18n'
import { api } from '@/lib/auth/client'
import { UiError } from '@/lib/errors'
import FindingPhotos from './FindingPhotos'
import { addPhoto, loadPhoto, removePhoto } from './api'

vi.mock('@/lib/auth/client', () => ({
  initializeAuth: vi.fn(async () => ({ mode: 'dev' })),
  api: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

// Nur das Laden der Bilddatei wird ersetzt; addPhoto und removePhoto laufen echt gegen das gemockte `api`.
vi.mock('./api', async (original) => ({ ...(await original<typeof import('./api')>()), loadPhoto: vi.fn() }))

const revoke = vi.fn()

beforeEach(async () => {
  vi.resetAllMocks()
  await i18n.changeLanguage('de')
  vi.stubGlobal('URL', Object.assign(URL, { revokeObjectURL: revoke }))
  vi.mocked(loadPhoto).mockImplementation(async (_id, photoId) => `blob:photo-${photoId}`)
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

it('removes a photo with the trash button in the corner of the thumbnail', async () => {
  const onRemove = vi.fn()
  render(<FindingPhotos inspectionId={1} photoIds={[5]} label="Foto zum Mangel Herd" editable onRemove={onRemove} />)
  await screen.findByRole('img', { name: 'Foto zum Mangel Herd, Bild 1' })
  fireEvent.click(screen.getByRole('button', { name: 'Foto entfernen' }))
  expect(onRemove).toHaveBeenCalledWith(5)
})

it('closes the zoom when the zoomed photo disappears', async () => {
  const { rerender } = render(<FindingPhotos inspectionId={1} photoIds={[5]} label="Foto zum Mangel Herd" />)
  await screen.findByRole('img', { name: 'Foto zum Mangel Herd, Bild 1' })
  fireEvent.click(screen.getByRole('button', { name: 'Foto zum Mangel Herd, Bild 1 vergrößern' }))
  expect(screen.getByRole('dialog')).toBeDefined()
  rerender(<FindingPhotos inspectionId={1} photoIds={[]} label="Foto zum Mangel Herd" editable />)
  expect(screen.queryByRole('dialog')).toBeNull()
})

it('frees the object URL of a photo that was hidden while it was loading', async () => {
  let finish!: (url: string) => void
  vi.mocked(loadPhoto).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
  const { unmount } = render(<FindingPhotos inspectionId={1} photoIds={[5]} label="Foto zum Mangel Herd" />)
  unmount()
  finish('blob:photo-5')
  await vi.waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:photo-5'))
})

it('shows a specific message when the backend rejects a photo', async () => {
  vi.mocked(api).mockResolvedValue(Response.json({ code: 'too_many_photos' }, { status: 400 }))
  await expect(removePhoto(1, 2, 3)).rejects.toEqual(new UiError('errors.photoTooMany'))
})

it('accepts only raster images, no SVG', async () => {
  const svg = new File(['<svg/>'], 'evil.svg', { type: 'image/svg+xml' })
  await expect(addPhoto(1, 2, svg)).rejects.toEqual(new UiError('errors.photoType'))
  expect(api).not.toHaveBeenCalled()
})
