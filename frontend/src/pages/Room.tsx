import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

export default function Room() {
  const { roomId } = useParams()
  const [token, setToken] = useState<string>('')
  const [messages, setMessages] = useState<any[]>([])
  const [text, setText] = useState<string>('')
  const evtRef = useRef<EventSource | null>(null)

  useEffect(() => {
    (async () => {
      // reuse localStorage token from App if present
      let t = localStorage.getItem('dev_jwt') || ''
      if (!t) {
        const res = await axios.post(`${API_BASE}/api/v1/auth/dev-token`, { tenantId: 'tenant-dev', displayName: 'Developer' })
        t = res.data.token
        localStorage.setItem('dev_jwt', t)
      }
      setToken(t)
      const init = await axios.get(`${API_BASE}/api/v1/rooms/${roomId}`, { headers: { Authorization: `Bearer ${t}` } })
      setMessages(init.data.messages || [])
      const es = new EventSource(`${API_BASE}/api/v1/rooms/${roomId}/stream?token=${encodeURIComponent(t)}`)
      evtRef.current = es
      es.addEventListener('message', (e: MessageEvent) => {
        const data = JSON.parse(e.data)
        setMessages(prev => [...prev, data])
      })
      es.onerror = () => { /* reconnect handled by browser; keep quiet for dev */ }
      return () => { es.close() }
    })()
  }, [roomId])

  const send = async () => {
    if (!text.trim()) return
    await axios.post(`${API_BASE}/api/v1/rooms/${roomId}/messages`, { text }, { headers: { Authorization: `Bearer ${token}` } })
    setText('')
  }

  return (
    <div style={{ padding: 20 }}>
      <h2>Room: {roomId}</h2>
      <div style={{ border: '1px solid #ddd', padding: 10, height: 400, overflow: 'auto' }}>
        {messages.map((m, i) => (
          <div key={i}>
            <strong>{m.senderType}:{m.senderId}</strong> <em>{new Date(m.ts).toLocaleTimeString()}</em>
            <div>{m.text}</div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 10 }}>
        <input value={text} onChange={e => setText(e.target.value)} placeholder="Type message…" />
        <button onClick={send}>Send</button>
      </div>
    </div>
  )
}
