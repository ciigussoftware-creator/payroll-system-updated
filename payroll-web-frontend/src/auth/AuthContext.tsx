import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, setAuthToken, setUnauthorizedHandler } from '../api/client'

interface LoginResponse {
  token: string
}

export interface AuthContextValue {
  token: string | null
  username: string | null
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

// Exported (rather than kept private) so tests can inject an auth state
// directly via <AuthContext.Provider> instead of driving the real async
// login() flow just to get a component into an authenticated state.
export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

const GENERIC_LOGIN_ERROR = 'Invalid username or password'

// Deliberate security tradeoff: the JWT lives only in this in-memory React
// state, never in localStorage/sessionStorage. It is lost on page refresh
// or tab close, trading convenience for reduced exposure to XSS-based
// token theft. Do not "fix" this by adding persistent storage without
// flagging it first — this is a locked decision, not an oversight.
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [token, setToken] = useState<string | null>(null)
  const [username, setUsername] = useState<string | null>(null)

  const logout = useCallback(() => {
    setToken(null)
    setUsername(null)
  }, [])

  useEffect(() => {
    setAuthToken(token)
  }, [token])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout()
      navigate('/login', { replace: true })
    })
    return () => setUnauthorizedHandler(null)
  }, [logout, navigate])

  const login = useCallback(async (usernameInput: string, password: string) => {
    const response = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: usernameInput, password }),
    })

    if (!response.ok) {
      // Deliberately generic: a 400 (validation) and a 401 (bad
      // credentials) must be indistinguishable to the caller, matching
      // the backend's generic-401 behavior for unknown vs. wrong password.
      throw new Error(GENERIC_LOGIN_ERROR)
    }

    const data: LoginResponse = await response.json()
    setToken(data.token)
    setUsername(usernameInput)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ token, username, isAuthenticated: token !== null, login, logout }),
    [token, username, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
