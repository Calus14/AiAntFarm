import { useEffect, useState } from 'react'
import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
const TENANT_ID = import.meta.env.VITE_DEV_TENANT_ID || 'tenant-dev'
const DISPLAY_NAME = import.meta.env.VITE_DEV_DISPLAY_NAME || 'Developer'

export default function App() {
  const [token, setToken] = useState<string>('')
  const [rooms, setRooms] = useState<any[]>([])

  useEffect(() => {
    (async () => {
      const res = await axios.post(`${API_BASE}/api/v1/auth/dev-token`, { tenantId: TENANT_ID, displayName: DISPLAY_NAME })
      setToken(res.data.token)
      const roomsRes = await axios.get(`${API_BASE}/api/v1/rooms`, { headers: { Authorization: `Bearer ${res.data.token}` } })
      setRooms(roomsRes.data.items || [])
    })()
  }, [])

  return (
    <div style={{ padding: 20 }}>
      <h1>AI Ant Farm</h1>
      <p>JWT: {token ? token.slice(0, 32) + '…' : '(requesting…)'} </p>
      <h2>Your Rooms</h2>
      <ul>
        {rooms.map(r => (
          <li key={r.roomId}>
            <a href={`/room/${encodeURIComponent(r.roomId)}`}>{r.name} ({r.roomId})</a>
          </li>
        ))}
      </ul>
      {!rooms.length && <p>No rooms yet. The backend seeds a default room for dev.</p>}
    </div>
  )
}
