import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { AuthContext, AuthProvider } from '../auth/AuthContext'
import type { AuthContextValue } from '../auth/AuthContext'
import { DashboardPage } from './DashboardPage'

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

function renderDashboard(authValue: AuthContextValue = baseAuthValue) {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthContext.Provider value={authValue}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/" element={<DashboardPage />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

async function selectCompanyAndMonth(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
  await user.selectOptions(screen.getByLabelText('Company'), 'Wood Lanka')
  const monthInput = screen.getByLabelText('Month') as HTMLInputElement
  await user.clear(monthInput)
  await user.type(monthInput, '2026-04')
}

describe('DashboardPage', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('populates the company dropdown from GET /api/companies', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(companiesResponse)))

    renderDashboard()

    await waitFor(() => {
      expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: 'DCH Plywood' })).toBeInTheDocument()
    })
  })

  it('calls the correctly-shaped calculate URL and renders rows in the correct columns', async () => {
    const rows = [
      {
        employeeCode: 'EMP-001',
        name: 'Vijitha Bandara',
        availableWorkingDays: 23,
        daysWorked: 18,
        otHours: 2.5,
        otPay: 500,
        gross: 24000,
        epfEmployee: 1920,
        epfEmployer: 2880,
        etf: 720,
        allowance: 1000,
        salaryAdvance: 200,
        festivalAdvance: 100,
        takeHome: 25180,
        workingDaysNotSet: false,
      },
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(rows))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderDashboard()
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url] = fetchMock.mock.calls[1]
    expect(url).toContain('/api/payroll/calculate/2026-04?companyId=1')

    const dataRow = (await screen.findByText('EMP-001')).closest('tr') as HTMLElement
    const cells = within(dataRow).getAllByRole('cell')
    expect(cells.map((c) => c.textContent)).toEqual([
      'EMP-001',
      'Vijitha Bandara',
      '23',
      '18',
      '2.5',
      'Rs. 500.00',
      'Rs. 24,000.00',
      'Rs. 1,920.00',
      'Rs. 2,880.00',
      'Rs. 720.00',
      'Rs. 1,000.00',
      'Rs. 200.00',
      'Rs. 100.00',
      'Rs. 25,180.00',
    ])
  })

  it('totals row arithmetic matches a manually summed mocked response', async () => {
    const rows = [
      {
        employeeCode: 'EMP-A',
        name: 'Employee A',
        availableWorkingDays: 23,
        daysWorked: 18,
        otHours: 0,
        otPay: 0,
        gross: 24000,
        epfEmployee: 1920,
        epfEmployer: 2880,
        etf: 720,
        allowance: 1500,
        salaryAdvance: 800,
        festivalAdvance: 300,
        takeHome: 22480,
        workingDaysNotSet: false,
      },
      {
        employeeCode: 'EMP-B',
        name: 'Employee B',
        availableWorkingDays: 23,
        daysWorked: 18,
        otHours: 0,
        otPay: 0,
        gross: 24000,
        epfEmployee: 1920,
        epfEmployer: 2880,
        etf: 720,
        allowance: 0,
        salaryAdvance: 0,
        festivalAdvance: 0,
        takeHome: 22080,
        workingDaysNotSet: false,
      },
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(rows))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderDashboard()
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Calculate' }))

    await screen.findByText('EMP-A')
    const totalsRow = screen.getByText('Total').closest('tr') as HTMLElement
    const totalCells = within(totalsRow).getAllByRole('cell')
    // [0] is the "Total" label cell (colSpan over the 5 non-money columns)
    expect(totalCells[1].textContent).toBe('Rs. 0.00') // OT Pay
    expect(totalCells[2].textContent).toBe('Rs. 48,000.00') // Gross
    expect(totalCells[3].textContent).toBe('Rs. 3,840.00') // EPF Employee
    expect(totalCells[4].textContent).toBe('Rs. 5,760.00') // EPF Employer
    expect(totalCells[5].textContent).toBe('Rs. 1,440.00') // ETF
    expect(totalCells[6].textContent).toBe('Rs. 1,500.00') // Allowance
    expect(totalCells[7].textContent).toBe('Rs. 800.00') // Salary Advance
    expect(totalCells[8].textContent).toBe('Rs. 300.00') // Festival Advance
    expect(totalCells[9].textContent).toBe('Rs. 44,560.00') // Take-Home
  })

  it('renders N/A in every money column with distinct styling for a WORKING_DAYS_NOT_SET row', async () => {
    const rows = [
      {
        employeeCode: 'EMP-NOWD',
        name: 'No Working Days Worker',
        availableWorkingDays: 0,
        daysWorked: 5,
        otHours: 0,
        otPay: null,
        gross: null,
        epfEmployee: null,
        epfEmployer: null,
        etf: null,
        allowance: null,
        salaryAdvance: null,
        festivalAdvance: null,
        takeHome: null,
        workingDaysNotSet: true,
      },
    ]
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(rows))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderDashboard()
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Calculate' }))

    const dataRow = (await screen.findByText('EMP-NOWD')).closest('tr') as HTMLElement
    const naCells = within(dataRow).getAllByText('N/A')
    expect(naCells).toHaveLength(9)
    expect(dataRow).toHaveStyle({ backgroundColor: '#fdecea' })
  })

  it('shows a loading state while the calculate fetch is pending', async () => {
    let resolveCalculate!: (value: Response) => void
    const pending = new Promise<Response>((resolve) => {
      resolveCalculate = resolve
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockReturnValueOnce(pending)
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderDashboard()
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Calculate' }))

    expect(await screen.findByText('Loading payroll data…')).toBeInTheDocument()

    resolveCalculate(jsonResponse([]))
    await waitFor(() => expect(screen.queryByText('Loading payroll data…')).not.toBeInTheDocument())
  })

  it('renders a generic error state for a non-401 error response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderDashboard()
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Calculate' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load payroll data')
  })

  it('triggers 6A redirect-to-login behavior on a 401 calculate response', async () => {
    // Uses the real AuthProvider (not the fake AuthContext.Provider used by the
    // other tests above) so its 401 -> logout -> navigate('/login') wiring from
    // 6A actually runs, proving apiFetch's global handler fires from this screen.
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse)) // companies on mount
      .mockResolvedValueOnce(new Response(null, { status: 401 })) // calculate
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/" element={<DashboardPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Calculate' }))

    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument())
  })

  it('export button fetches the correct export URL and triggers a download', async () => {
    const anchorClickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const createObjectURLSpy = vi.fn(() => 'blob:mock-url')
    const revokeObjectURLSpy = vi.fn()
    // jsdom doesn't implement these by default.
    URL.createObjectURL = createObjectURLSpy
    URL.revokeObjectURL = revokeObjectURLSpy

    const blob = new Blob(['fake xlsx content'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const exportResponse = new Response(blob, {
      status: 200,
      headers: {
        'Content-Disposition': "attachment; filename*=UTF-8''Wood%20Lanka%20Payroll%202026-04.xlsx",
      },
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(exportResponse)
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderDashboard()
    await selectCompanyAndMonth(user)
    await user.click(screen.getByRole('button', { name: 'Export to Excel' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url] = fetchMock.mock.calls[1]
    expect(url).toContain('/api/payroll/export/2026-04?companyId=1')

    await waitFor(() => expect(createObjectURLSpy).toHaveBeenCalledTimes(1))
    expect(anchorClickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url')
  })
})
