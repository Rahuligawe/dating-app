import { useMemo } from 'react'
import { jwtDecode } from 'jwt-decode'

export interface Permissions {
  isSuperAdmin: boolean
  dashboard: { viewStats: boolean; viewRevenue: boolean; viewCharts: boolean; viewAds: boolean }
  users: { view: boolean; create: boolean; delete: boolean; viewChats: boolean; managePoints: boolean }
  revenue: boolean
  canManageAdmins: boolean
}

const FULL: Permissions = {
  isSuperAdmin:  true,
  dashboard:     { viewStats: true, viewRevenue: true, viewCharts: true, viewAds: true },
  users:         { view: true, create: true, delete: true, viewChats: true, managePoints: true },
  revenue:       true,
  canManageAdmins: true,
}

function parsePermissions(json: string | null | undefined): Permissions {
  if (!json) return FULL
  try {
    const p = JSON.parse(json)
    return {
      isSuperAdmin:  false,
      dashboard:     {
        viewStats:   p.dashboard?.viewStats   ?? true,
        viewRevenue: p.dashboard?.viewRevenue ?? false,
        viewCharts:  p.dashboard?.viewCharts  ?? true,
        viewAds:     p.dashboard?.viewAds     ?? false,
      },
      users: {
        view:         p.users?.view         ?? true,
        create:       p.users?.create       ?? false,
        delete:       p.users?.delete       ?? false,
        viewChats:    p.users?.viewChats    ?? false,
        managePoints: p.users?.managePoints ?? false,
      },
      revenue:         p.revenue          ?? false,
      canManageAdmins: p.canManageAdmins  ?? false,
    }
  } catch {
    return FULL
  }
}

export function usePermissions(): Permissions {
  return useMemo(() => {
    const token = localStorage.getItem('admin_token')
    if (!token) return FULL
    try {
      const decoded: any = jwtDecode(token)
      if (!decoded.sub_admin_id) return FULL  // super admin — no sub_admin_id
      return parsePermissions(decoded.permissions)
    } catch {
      return FULL
    }
  }, [])
}
