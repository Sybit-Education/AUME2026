import { api } from '@/lib/auth/client'
import { requireOk } from '@/lib/auth/response'
import { UiError, type ErrorKey } from '@/lib/errors'

export interface Membership {
  flat: string
  residence: string
  validFrom: string
  members: string[]
}

// Backend error codes for joining.
const joinErrors: Record<string, ErrorKey> = {
  invalid_code: 'errors.wgInvalidCode',
  already_member: 'errors.wgAlreadyMember',
  flat_full: 'errors.wgFlatFull',
}

async function joinErrorKey(response: Response): Promise<ErrorKey> {
  try {
    const body = (await response.json()) as { code?: unknown }
    return (typeof body.code === 'string' && joinErrors[body.code]) || 'errors.wgJoin'
  } catch {
    return 'errors.wgJoin'
  }
}

// 204 means the user has no flat.
export async function getMembership(signal: AbortSignal): Promise<Membership | null> {
  const response = requireOk(await api('/api/memberships/me', { signal }), 'errors.wg')
  return response.status === 204 ? null : (await response.json()) as Membership
}

export async function joinWg(code: string, signal: AbortSignal): Promise<void> {
  const response = await api('/api/memberships', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code }),
    signal,
  })
  if (response.status === 400 || response.status === 409) throw new UiError(await joinErrorKey(response))
  requireOk(response, 'errors.wgJoin')
}

export async function leaveWg(signal: AbortSignal): Promise<void> {
  const response = await api('/api/memberships/me/leave', { method: 'POST', signal })
  // 404 means the user already left.
  if (response.status !== 404) requireOk(response, 'errors.wgLeave')
}
