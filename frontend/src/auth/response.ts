import { redirect } from 'react-router'
import { UiError, type ErrorKey } from '../errors'
import { forgetUser } from './session'

export class SessionExpired extends Error {}

export function requireOk(response: Response, failure: ErrorKey): Response {
  if (response.status === 401) {
    forgetUser()
    throw new SessionExpired()
  }
  if (response.status === 403) throw redirect('/forbidden')
  if (!response.ok) throw new UiError(failure)
  return response
}
