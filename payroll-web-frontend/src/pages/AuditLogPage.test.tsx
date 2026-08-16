import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { AuthContext, AuthProvider } from '../auth/AuthContext'
import type { AuthContextValue } from '../auth/AuthContext'
import { AuditLogPage } from './AuditLogPage'

const baseAuthValue: AuthContextValue = {
  token: 'tok123',
  username: 'admin',
  isAuthenticated: true,
  login: vi.fn(),
  logout: vi.fn(),
}

const companiesResponse = [
  { id: 1, name: 'Wood Lanka' },
  { id: 2, name: 'DCH Plywood' },
]

const auditEntriesResponse = [
  {
    id: 1,
    entryDatetime: '2026-06-21T10:00:00Z',
    username: 'admin',
    action: 'NOTE_ADDED',
    targetRef: 'employee=EMP-001,companyId=1,date=2026-06-20',
    oldValue: null,
    newValue: 'Left early',
    reason: null,
  },
]

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderAuditLogPage(authValue: AuthContextValue = baseAuthValue) {
  return render(
    <MemoryRouter initialEntries={['/audit-log']}>
      <AuthContext.Provider value={authValue}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/audit-log" element={<AuditLogPage />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

async function selectCompany(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
  await user.selectOptions(screen.getByLabelText('Company'), 'Wood Lanka')
}

async function loadEntries(user: ReturnType<typeof userEvent.setup>) {
  await selectCompany(user)
  await user.click(screen.getByRole('button', { name: 'Load' }))
}

describe('AuditLogPage', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('loads and renders entries from a mocked GET', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(auditEntriesResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderAuditLogPage()
    await loadEntries(user)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[1][0]).toContain('/api/audit-log/1')

    expect(await screen.findByText('NOTE_ADDED')).toBeInTheDocument()
    expect(screen.getByText('admin')).toBeInTheDocument()
  })

  it('entity-type filter changes the GET query correctly', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(auditEntriesResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderAuditLogPage()
    await selectCompany(user)
    await user.selectOptions(screen.getByLabelText('Entity Type'), 'Notes')
    await user.click(screen.getByRole('button', { name: 'Load' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url] = fetchMock.mock.calls[1]
    expect(url).toContain('/api/audit-log/1')
    expect(url).toContain('entityType=NOTE_ADDED')
  })

  it('date-range filter changes the GET query correctly', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(auditEntriesResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderAuditLogPage()
    await selectCompany(user)
    await user.type(screen.getByLabelText('From'), '2026-06-01')
    await user.type(screen.getByLabelText('To'), '2026-06-30')
    await user.click(screen.getByRole('button', { name: 'Load' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url] = fetchMock.mock.calls[1]
    expect(url).toContain('from=2026-06-01')
    expect(url).toContain('to=2026-06-30')
  })

  it('shows a generic error state for a non-401 error response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderAuditLogPage()
    await loadEntries(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the audit log')
  })

  it('triggers the 6A redirect-to-login behavior on a 401 response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(
      <MemoryRouter initialEntries={['/audit-log']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/audit-log" element={<AuditLogPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
    await loadEntries(user)

    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument())
  })
})
