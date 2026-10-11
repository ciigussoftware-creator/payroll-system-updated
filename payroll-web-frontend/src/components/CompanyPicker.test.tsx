import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { useCompanies } from '../hooks/useCompanies'
import { CompanyPicker } from './CompanyPicker'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function Harness() {
  const { companies, companyId, setCompanyId } = useCompanies()
  return (
    <>
      <CompanyPicker companies={companies} value={companyId} onChange={setCompanyId} />
      <output data-testid="selected">{companyId}</output>
    </>
  )
}

describe('useCompanies + CompanyPicker', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('auto-selects the only company and hides the picker', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([{ id: 7, name: 'Wood Lanka' }]))
    vi.stubGlobal('fetch', fetchMock)

    render(<Harness />)

    await waitFor(() => expect(screen.getByTestId('selected')).toHaveTextContent('7'))
    expect(screen.queryByLabelText('Company')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toContain('/api/companies')
  })

  it('shows the picker with nothing selected when there are two companies', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse([
          { id: 1, name: 'Wood Lanka' },
          { id: 2, name: 'DCH Plywood' },
        ]),
      ),
    )
    const user = userEvent.setup()

    render(<Harness />)

    await waitFor(() => expect(screen.getByRole('option', { name: 'DCH Plywood' })).toBeInTheDocument())
    const select = screen.getByLabelText('Company') as HTMLSelectElement
    expect(select.value).toBe('')
    expect(screen.getByTestId('selected')).toBeEmptyDOMElement()

    await user.selectOptions(select, 'DCH Plywood')
    expect(screen.getByTestId('selected')).toHaveTextContent('2')
  })
})
