import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from './AuthContext'
import type { AuthContextValue } from './AuthContext'
import { ProtectedRoute } from './ProtectedRoute'

const baseAuthValue: AuthContextValue = {
  token: null,
  username: null,
  isAuthenticated: false,
  login: vi.fn(),
  logout: vi.fn(),
}

function renderProtected(initialEntries: string[], authValue: AuthContextValue) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthContext.Provider value={authValue}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <div>Secret Dashboard</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  it('redirects to /login when unauthenticated', () => {
    renderProtected(['/'], baseAuthValue)

    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Secret Dashboard')).not.toBeInTheDocument()
  })

  it('renders its children when authenticated', () => {
    renderProtected(['/'], { ...baseAuthValue, token: 'tok123', username: 'admin', isAuthenticated: true })

    expect(screen.getByText('Secret Dashboard')).toBeInTheDocument()
  })
})
