import { expect, it } from 'vitest'
import { safeReturnTo } from './returnTo'

it.each(['https://other.example', '//other.example', '/\\other.example', '/login', '/login/', '/LOGIN', '/log%69n', '/api/users', 'javascript:alert(1)', null])(
  'rejects unsafe or looping post-login destinations: %s', (value) => {
    expect(safeReturnTo(value)).toBe('/')
  },
)

it('keeps a relative destination with query and fragment', () => {
  expect(safeReturnTo('/residences?page=1#assigned')).toBe('/residences?page=1#assigned')
})
