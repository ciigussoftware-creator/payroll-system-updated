import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../api/client'
import { useAuth } from '../auth/AuthContext'

interface Company {
  id: number
  name: string
}

interface PayrollRow {
  employeeCode: string
  name: string
  availableWorkingDays: number
  daysWorked: number
  otHours: number
  otPay: number | null
  gross: number | null
  epfEmployee: number | null
  epfEmployer: number | null
  etf: number | null
  allowance: number | null
  salaryAdvance: number | null
  festivalAdvance: number | null
  takeHome: number | null
  workingDaysNotSet: boolean
}

type MoneyColumn =
  | 'otPay'
  | 'gross'
  | 'epfEmployee'
  | 'epfEmployer'
  | 'etf'
  | 'allowance'
  | 'salaryAdvance'
  | 'festivalAdvance'
  | 'takeHome'

// Same order as the Excel export's columns/MONEY_COLUMNS, kept in sync deliberately.
const MONEY_COLUMNS: MoneyColumn[] = [
  'otPay',
  'gross',
  'epfEmployee',
  'epfEmployer',
  'etf',
  'allowance',
  'salaryAdvance',
  'festivalAdvance',
  'takeHome',
]

const CALCULATE_ERROR = 'Could not load payroll data. Please try again.'
const EXPORT_ERROR = 'Could not export payroll data. Please try again.'
const NOT_SET_ROW_BACKGROUND = '#fdecea'

const moneyFormatter = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function formatMoney(value: number): string {
  return `Rs. ${moneyFormatter.format(value)}`
}

function parseFilenameFromDisposition(disposition: string | null): string | null {
  if (!disposition) return null

  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1])
    } catch {
      // Malformed percent-encoding — fall through to the plain filename below.
    }
  }

  const plain = disposition.match(/filename="?([^";]+)"?/i)
  return plain ? plain[1] : null
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

function computeTotals(rows: PayrollRow[]): Record<MoneyColumn, number> {
  const totals = Object.fromEntries(MONEY_COLUMNS.map((col) => [col, 0])) as Record<MoneyColumn, number>
  for (const row of rows) {
    if (row.workingDaysNotSet) continue
    for (const col of MONEY_COLUMNS) {
      totals[col] += row[col] ?? 0
    }
  }
  return totals
}

export function DashboardPage() {
  const { username, logout } = useAuth()
  const [companies, setCompanies] = useState<Company[]>([])
  const [companyId, setCompanyId] = useState('')
  const [month, setMonth] = useState('')
  const [rows, setRows] = useState<PayrollRow[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadCompanies() {
      const response = await apiFetch('/api/companies')
      if (!response.ok || cancelled) return
      const data: Company[] = await response.json()
      if (!cancelled) setCompanies(data)
    }

    void loadCompanies()
    return () => {
      cancelled = true
    }
  }, [])

  const canRun = companyId !== '' && month !== ''
  const selectedCompany = companies.find((c) => String(c.id) === companyId)

  async function handleCalculate() {
    if (!canRun) return
    setLoading(true)
    setError(null)
    setRows(null)

    try {
      const response = await apiFetch(`/api/payroll/calculate/${month}?companyId=${companyId}`)
      // A 401 is already handled globally by apiFetch's redirect-to-login behavior.
      if (response.status === 401) return

      if (!response.ok) {
        setError(CALCULATE_ERROR)
        return
      }

      const data: PayrollRow[] = await response.json()
      setRows(data)
    } catch {
      setError(CALCULATE_ERROR)
    } finally {
      setLoading(false)
    }
  }

  async function handleExport() {
    if (!canRun) return
    setExporting(true)
    setExportError(null)

    try {
      const response = await apiFetch(`/api/payroll/export/${month}?companyId=${companyId}`)
      if (response.status === 401) return

      if (!response.ok) {
        setExportError(EXPORT_ERROR)
        return
      }

      const blob = await response.blob()
      const filename =
        parseFilenameFromDisposition(response.headers.get('Content-Disposition')) ??
        `${selectedCompany?.name ?? 'Payroll'} ${month}.xlsx`
      triggerDownload(blob, filename)
    } catch {
      setExportError(EXPORT_ERROR)
    } finally {
      setExporting(false)
    }
  }

  const totals = rows ? computeTotals(rows) : null

  return (
    <main>
      <h1>Payroll Admin</h1>
      <p>Logged in as {username}</p>
      <Link to="/timestamps">Timestamp Corrections</Link>
      <Link to="/ot-management">OT Management</Link>
      <Link to="/notes">Notes</Link>
      <Link to="/audit-log">Audit Log</Link>
      <button type="button" onClick={logout}>
        Logout
      </button>

      <section>
        <div>
          <label htmlFor="company">Company</label>
          <select id="company" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
            <option value="">Select a company</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="month">Month</label>
          <input id="month" type="month" value={month} onChange={(event) => setMonth(event.target.value)} />
        </div>

        <button type="button" onClick={handleCalculate} disabled={!canRun || loading}>
          {loading ? 'Calculating…' : 'Calculate'}
        </button>

        <button type="button" onClick={handleExport} disabled={!canRun || exporting}>
          {exporting ? 'Exporting…' : 'Export to Excel'}
        </button>
      </section>

      {loading && <p>Loading payroll data…</p>}
      {error && <p role="alert">{error}</p>}
      {exportError && <p role="alert">{exportError}</p>}

      {rows && !loading && (
        <table>
          <thead>
            <tr>
              <th>Employee Code</th>
              <th>Name</th>
              <th>Available Working Days</th>
              <th>Days Worked</th>
              <th>OT Hours</th>
              <th>OT Pay</th>
              <th>Gross</th>
              <th>EPF Employee</th>
              <th>EPF Employer</th>
              <th>ETF</th>
              <th>Allowance</th>
              <th>Salary Advance</th>
              <th>Festival Advance</th>
              <th>Take-Home</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={row.employeeCode}
                style={row.workingDaysNotSet ? { backgroundColor: NOT_SET_ROW_BACKGROUND } : undefined}
              >
                <td>{row.employeeCode}</td>
                <td>{row.name}</td>
                <td>{row.availableWorkingDays}</td>
                <td>{row.daysWorked}</td>
                <td>{row.otHours}</td>
                {MONEY_COLUMNS.map((col) => (
                  <td key={col}>{row.workingDaysNotSet ? 'N/A' : formatMoney(row[col] ?? 0)}</td>
                ))}
              </tr>
            ))}
          </tbody>
          {totals && (
            <tfoot>
              <tr>
                <td colSpan={5}>Total</td>
                {MONEY_COLUMNS.map((col) => (
                  <td key={col}>{formatMoney(totals[col])}</td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      )}

      {/* TODO(future sub-phase): MonthlyPayrollInput (allowance/advances) has a PUT endpoint
          from 5C-2 but no editing UI anywhere yet. This dashboard stays read-only for 6B. */}
    </main>
  )
}
