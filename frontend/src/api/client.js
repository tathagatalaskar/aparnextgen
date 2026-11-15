import axios from 'axios'

// Vercel routes /api/* to the Spring Boot service. Vite uses the same path
// locally and proxies it to localhost:8080 (see vite.config.js).
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('apar_token')
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('apar_token')
      localStorage.removeItem('apar_user')
      if (window.location.pathname !== '/login') {
        window.location.replace('/login')
      }
    }
    return Promise.reject(err)
  }
)

export default client
