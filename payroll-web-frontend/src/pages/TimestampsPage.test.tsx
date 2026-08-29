import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { AuthContext, AuthProvider } from '../auth/AuthContext'
import type { AuthContextValue } from '../auth/AuthContext'
import { TimestampsPage } from './TimestampsPage'

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

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderTimestamps(authValue: AuthContextValue = baseAuthValue) {
  return render(
    <MemoryRouter initialEntries={['/timestamps']}>
      <AuthContext.Provider value={authValue}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/timestamps" element={<TimestampsPage />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

async function fillFormAndLoad(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
  await user.selectOptions(screen.getByLabelText('Company'), 'Wood Lanka')
  await user.type(screen.getByLabelText('Employee Code'), 'EMP-001')
  const dateInput = screen.getByLabelText('Date') as HTMLInputElement
  await user.clear(dateInput)
  await user.type(dateInput, '2026-06-20')
  await user.click(screen.getByRole('button', { name: 'Load' }))
}

describe('TimestampsPage', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("loads and renders a day's records for an employee from a mocked GET response", async () => {
    const records = [
      { id: 10, scanDatetime: '2026-06-20T08:30:00', scanType: 'ENTRY', missingClockOut: false },
      { id: 11, scanDatetime: '2026-06-20T17:00:00', scanType: 'EXIT', missingClockOut: false },
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(records))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderTimestamps()
    await fillFormAndLoad(user)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url] = fetchMock.mock.calls[1]
    expect(url).toContain('/api/attendance/1/EMP-001/2026-06-20')

    expect(await screen.findByText('ENTRY')).toBeInTheDocument()
    expect(screen.getByText('EXIT')).toBeInTheDocument()
  })

  it('renders a MISSING_CLOCK_OUT record with distinct styling', async () => {
    const records = [
      { id: 10, scanDatetime: '2026-06-20T08:30:00', scanType: 'ENTRY', missingClockOut: true },
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(records))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderTimestamps()
    await fillFormAndLoad(user)

    const row = (await screen.findByText('ENTRY')).closest('tr') as HTMLElement
    expect(row).toHaveClass('row--flagged')
    expect(within(row).getByText(/Missing Clock Out/)).toBeInTheDocument()
  })

  it('disables the Correct button while the reason field is empty', async () => {
    const records = [{ id: 10, scanDatetime: '2026-06-20T08:30:00', scanType: 'ENTRY', missingClockOut: false }]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(records))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderTimestamps()
    await fillFormAndLoad(user)

    await screen.findByText('ENTRY')
    const correctButton = screen.getByRole('button', { name: 'Correct' })
    expect(correctButton).toBeDisabled()

    await user.type(screen.getByLabelText('Reason'), 'Clock was slow')
    expect(correctButton).toBeEnabled()
  })

  it('calls PUT with the correct body shape and reloads the records afterward', async () => {
    const records = [{ id: 10, scanDatetime: '2026-06-20T08:30:00', scanType: 'ENTRY', missingClockOut: false }]
    const correctedRecords = [
      { id: 10, scanDatetime: '2026-06-20T08:40:00', scanType: 'ENTRY', missingClockOut: false },
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(records))
      .mockResolvedValueOnce(jsonResponse({ id: 10, scanDatetime: '2026-06-20T08:40:00', scanType: 'ENTRY', missingClockOut: false }))
      .mockResolvedValueOnce(jsonResponse(correctedRecords))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderTimestamps()
    await fillFormAndLoad(user)
    await screen.findByText('ENTRY')

    await user.type(screen.getByLabelText('Reason'), 'Clock was slow')
    await user.click(screen.getByRole('button', { name: 'Correct' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4))
    const [putUrl, putInit] = fetchMock.mock.calls[2]
    expect(putUrl).toContain('/api/attendance/10')
    expect(putInit.method).toBe('PUT')
    const body = JSON.parse(putInit.body as string)
    expect(body).toEqual({
      companyId: 1,
      newScanDatetime: '2026-06-20T08:30',
      reason: 'Clock was slow',
    })

    const [reloadUrl] = fetchMock.mock.calls[3]
    expect(reloadUrl).toContain('/api/attendance/1/EMP-001/2026-06-20')
  })

  it('shows a generic error state for a non-401 error response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderTimestamps()
    await fillFormAndLoad(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load attendance records')
  })

  it('triggers the 6A redirect-to-login behavior on a 401 GET response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(
      <MemoryRouter initialEntries={['/timestamps']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/timestamps" element={<TimestampsPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
    await fillFormAndLoad(user)

    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument())
  })
})
