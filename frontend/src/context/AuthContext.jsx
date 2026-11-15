import { createContext, useContext, useState, useCallback } from 'react'
import { authApi } from '../api/api'

const AuthContext = createContext(null)

function readStoredSession() {
  try {
    const token = localStorage.getItem('apar_token')
    const rawUser = localStorage.getItem('apar_user')
    if (!token || !rawUser) throw new Error('Incomplete session')

    const user = JSON.parse(rawUser)
    if (!user || typeof user !== 'object' || !user.role || !user.email) {
      throw new Error('Invalid stored session')
    }
    return user
  } catch {
    localStorage.removeItem('apar_token')
    localStorage.removeItem('apar_user')
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredSession)

  const login = useCallback(async (email, password) => {
    const { data } = await authApi.login(email, password)
    if (!data?.token || !data?.role || !data?.email) {
      throw new Error('The server returned an incomplete login response')
    }

    localStorage.setItem('apar_token', data.token)
    localStorage.setItem('apar_user', JSON.stringify(data))
    setUser(data)
    return data
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('apar_token')
    localStorage.removeItem('apar_user')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
