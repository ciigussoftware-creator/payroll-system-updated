import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { AuthContext, AuthProvider } from '../auth/AuthContext'
import type { AuthContextValue } from '../auth/AuthContext'
import { OtManagementPage } from './OtManagementPage'

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

const FUTURE_DATE = '2026-08-20'
const PAST_DATE = '2020-01-01'

const dayLevelResponse = { isAllStaffOt: false, dayType: 'WEEKDAY', setAt: null, setBy: null }
const employeeAuthsResponse = [
  { employeeCode: 'EMP-001', authDate: FUTURE_DATE, authorized: true, setAt: '2026-08-15T10:00:00Z', setBy: 'admin' },
]

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderOtManagement(authValue: AuthContextValue = baseAuthValue) {
  return render(
    <MemoryRouter initialEntries={['/ot-management']}>
      <AuthContext.Provider value={authValue}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/ot-management" element={<OtManagementPage />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

async function selectCompanyAndDate(user: ReturnType<typeof userEvent.setup>, date: string) {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
  await user.selectOptions(screen.getByLabelText('Company'), 'Wood Lanka')
  const dateInput = screen.getByLabelText('Date') as HTMLInputElement
  await user.clear(dateInput)
  await user.type(dateInput, date)
}

async function loadWithDate(user: ReturnType<typeof userEvent.setup>, date: string) {
  await selectCompanyAndDate(user, date)
  await user.click(screen.getByRole('button', { name: 'Load' }))
}

describe('OtManagementPage', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('loads and renders both the day-level and per-employee sections from mocked GET responses', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(dayLevelResponse))
      .mockResolvedValueOnce(jsonResponse(employeeAuthsResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderOtManagement()
    await loadWithDate(user, FUTURE_DATE)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))
    expect(fetchMock.mock.calls[1][0]).toContain(`/api/ot-config/day-level/1/${FUTURE_DATE}`)
    expect(fetchMock.mock.calls[2][0]).toContain(`/api/ot-config/employee-authorizations/1/${FUTURE_DATE}`)

    expect(await screen.findByText('Day-Level OT Configuration')).toBeInTheDocument()
    expect(screen.getByText('Per-Employee OT Authorization')).toBeInTheDocument()
    expect(screen.getByText('EMP-001')).toBeInTheDocument()
  })

  it('day-level save calls PUT with the correct body shape and reloads afterward', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(dayLevelResponse))
      .mockResolvedValueOnce(jsonResponse(employeeAuthsResponse))
      .mockResolvedValueOnce(jsonResponse({ isAllStaffOt: true, dayType: 'WEEKDAY', setAt: '2026-08-15T10:00:00Z', setBy: null }))
      .mockResolvedValueOnce(jsonResponse({ isAllStaffOt: true, dayType: 'WEEKDAY', setAt: '2026-08-15T10:00:00Z', setBy: null }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderOtManagement()
    await loadWithDate(user, FUTURE_DATE)
    await screen.findByText('Day-Level OT Configuration')

    await user.click(screen.getByLabelText('All Staff OT'))
    await user.click(screen.getByRole('button', { name: 'Save Day-Level OT' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5))
    const [putUrl, putInit] = fetchMock.mock.calls[3]
    expect(putUrl).toContain(`/api/ot-config/day-level/1/${FUTURE_DATE}`)
    expect(putInit.method).toBe('PUT')
    const body = JSON.parse(putInit.body as string)
    expect(body).toEqual({ isAllStaffOt: true, dayType: 'WEEKDAY', reason: '' })

    const [reloadUrl] = fetchMock.mock.calls[4]
    expect(reloadUrl).toContain(`/api/ot-config/day-level/1/${FUTURE_DATE}`)
  })

  it('per-employee save calls PUT with the correct body shape and reloads afterward', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(dayLevelResponse))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ employeeCode: 'EMP-777', authDate: FUTURE_DATE, authorized: true, setAt: null, setBy: 'admin' }))
      .mockResolvedValueOnce(jsonResponse([{ employeeCode: 'EMP-777', authDate: FUTURE_DATE, authorized: true, setAt: null, setBy: 'admin' }]))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderOtManagement()
    await loadWithDate(user, FUTURE_DATE)
    await screen.findByText('Per-Employee OT Authorization')

    await user.type(screen.getByLabelText('Employee Code'), 'EMP-777')
    await user.click(screen.getByRole('button', { name: 'Save Employee Authorization' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5))
    const [putUrl, putInit] = fetchMock.mock.calls[3]
    expect(putUrl).toContain(`/api/ot-config/employee-authorization/1/EMP-777/${FUTURE_DATE}`)
    expect(putInit.method).toBe('PUT')
    const body = JSON.parse(putInit.body as string)
    expect(body).toEqual({ authorized: true, reason: '' })

    const [reloadUrl] = fetchMock.mock.calls[4]
    expect(reloadUrl).toContain(`/api/ot-config/employee-authorizations/1/${FUTURE_DATE}`)
  })

  it('disables save buttons when a reason is required for a retroactive date and none is given', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(dayLevelResponse))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderOtManagement()
    await loadWithDate(user, PAST_DATE)

    await screen.findByText('Day-Level OT Configuration')
    expect(screen.getByRole('button', { name: 'Save Day-Level OT' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Save Employee Authorization' })).toBeDisabled()

    await user.type(screen.getByLabelText('Reason (required for retroactive changes)', { selector: '#dayLevelReason' }), 'Backdated fix')
    expect(screen.getByRole('button', { name: 'Save Day-Level OT' })).toBeEnabled()
  })

  it('shows a generic error state for a non-401 error response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderOtManagement()
    await loadWithDate(user, FUTURE_DATE)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load OT configuration')
  })

  it('triggers the 6A redirect-to-login behavior on a 401 response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(
      <MemoryRouter initialEntries={['/ot-management']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/ot-management" element={<OtManagementPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
    await loadWithDate(user, FUTURE_DATE)

    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument())
  })
})
