import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'

export interface Company {
  id: number
  name: string
}

// Loads the company list once. A single-company deployment has nothing to pick,
// so its one company is selected automatically.
export function useCompanies() {
  const [companies, setCompanies] = useState<Company[]>([])
  const [companyId, setCompanyId] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadCompanies() {
      const response = await apiFetch('/api/companies')
      if (!response.ok || cancelled) return
      const data: Company[] = await response.json()
      if (cancelled) return
      setCompanies(data)
      if (data.length === 1) setCompanyId(String(data[0].id))
    }

    void loadCompanies()
    return () => {
      cancelled = true
    }
  }, [])

  return { companies, companyId, setCompanyId }
}
