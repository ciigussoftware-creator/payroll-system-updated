import { useEffect } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { AuthProvider, useAuth } from './AuthContext'

// Logs in, then makes one more apiFetch call — used to prove that a 401 on
// *any* subsequent call (not just the login call itself) triggers the
// centralized clear-and-redirect behavior wired up in AuthProvider.
function LoginThenCallProtectedEndpoint() {
  const { login } = useAuth()

  useEffect(() => {
    void login('admin', 'secret123').then(() => apiFetch('/api/protected'))
  }, [login])

  return null
}

describe('AuthProvider 401 handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('clears auth state and redirects to /login when any apiFetch call returns 401', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'tok123' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <LoginThenCallProtectedEndpoint />
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/" element={<div>Home Shell</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument())
  })
})
