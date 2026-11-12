import axios from 'axios'

const http = axios.create({
  baseURL: '/alert-console/api',
  timeout: 15000
})

http.interceptors.response.use(
  response => {
    const body = response.data
    if (body && body.success === false) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  error => {
    const message = error.response && error.response.data && error.response.data.message
    return Promise.reject(new Error(message || error.message || '网络请求失败'))
  }
)

export const listApps = () => http.get('/apps')

export const listServices = () => http.get('/services')

export const getConfig = appName => http.get('/config', { params: { appName } })

export const createApp = appName => http.post('/apps', { appName })

export const saveConfig = payload => http.put('/config', payload)

export const refreshLocal = () => http.post('/refresh/local')

export const refreshService = appName => http.post('/refresh', null, {
  params: { appName }
})

export const queryAlerts = params => http.get('/alerts', { params })

export const getAlert = id => http.get(`/alerts/${id}`)

export const getDashboard = range => http.get('/dashboard', { params: { range } })

export const getEffective = (appName, instance) =>
  http.get('/config/effective', { params: { appName, instance } })
