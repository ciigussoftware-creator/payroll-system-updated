import type { Company } from '../hooks/useCompanies'

interface CompanyPickerProps {
  companies: Company[]
  value: string
  onChange: (companyId: string) => void
}

// Hidden when there is exactly one company: useCompanies has already selected it.
export function CompanyPicker({ companies, value, onChange }: CompanyPickerProps) {
  if (companies.length === 1) return null

  return (
    <div className="field">
      <label className="field__label" htmlFor="company">
        Company
      </label>
      <select id="company" className="select" value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">Select a company</option>
        {companies.map((company) => (
          <option key={company.id} value={company.id}>
            {company.name}
          </option>
        ))}
      </select>
    </div>
  )
}
