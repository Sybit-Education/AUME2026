import type { LoaderFunctionArgs } from 'react-router'
import type { LoggedInUser } from '../auth'
import { ensureSession, forgetUser } from '../auth/session'
import { UiError } from '../errors'
import { SessionExpired } from '../auth/response'

// Layout guards run after loaders. Every protected loader must guard its own API calls.
export function protectedLoader<T>(
  privilege: string | undefined,
  load: (args: LoaderFunctionArgs, user: LoggedInUser) => Promise<T>,
) {
  return async (args: LoaderFunctionArgs): Promise<T | null> => {
    const { user } = await ensureSession()
    if (!user || (privilege && !user.privileges.includes(privilege))) return null
    try {
      return await load(args, user)
    } catch (cause) {
      if (cause instanceof SessionExpired) return null
      if (cause instanceof UiError && cause.key === 'errors.sessionExpired') {
        forgetUser()
        return null
      }
      throw cause
    }
  }
}
