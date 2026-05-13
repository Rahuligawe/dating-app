import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { jwtDecode } from 'jwt-decode'
import { Heart } from 'lucide-react'
import { loginSubAdmin } from '../api/adminApi'

type LoginMode = 'choose' | 'otp-mobile' | 'otp-verify' | 'password'

export default function Login() {
  const navigate = useNavigate()
  const [mode,     setMode]     = useState<LoginMode>('choose')
  const [mobile,   setMobile]   = useState('')
  const [otp,      setOtp]      = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  function normalizeMobile(m: string): string {
    return m.replace(/\D/g, '').replace(/^91/, '').slice(-10)
  }

  async function sendOtp() {
    setError('')
    setLoading(true)
    try {
      await axios.post('/api/auth/mobile/send-otp', { mobile: normalizeMobile(mobile) })
      setMode('otp-verify')
    } catch {
      setError('Failed to send OTP. Check the mobile number.')
    } finally {
      setLoading(false)
    }
  }

  async function verifyOtp() {
    setError('')
    setLoading(true)
    try {
      const res = await axios.post('/api/auth/mobile/verify-otp', { mobile: normalizeMobile(mobile), otp })
      const token: string = res.data.accessToken
      const decoded: any = jwtDecode(token)
      if (decoded.role !== 'ADMIN') {
        setError('Access denied. ADMIN role required.')
        return
      }
      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_role', 'ADMIN')
      navigate('/')
    } catch {
      setError('Invalid OTP or access denied.')
    } finally {
      setLoading(false)
    }
  }

  async function loginWithPassword() {
    setError('')
    setLoading(true)
    try {
      const result = await loginSubAdmin(username, password)
      localStorage.setItem('admin_token', result.token)
      localStorage.setItem('admin_role', 'ADMIN')
      navigate('/')
    } catch (e: any) {
      setError(e?.response?.data?.error || 'Invalid credentials.')
    } finally {
      setLoading(false)
    }
  }

  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: '#1C1C28',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 12,
    padding: '12px 16px',
    fontSize: 14,
    color: '#F0EDE8',
    fontFamily: 'inherit',
    outline: 'none',
    marginBottom: 16,
    boxSizing: 'border-box',
  }

  const btnPrimary: React.CSSProperties = {
    width: '100%',
    background: 'linear-gradient(135deg, #C9A96E, #A0784A)',
    border: 'none',
    borderRadius: 12,
    padding: '12px',
    fontSize: 14,
    fontWeight: 600,
    color: '#fff',
    cursor: 'pointer',
    marginBottom: 12,
  }

  const btnDisabled: React.CSSProperties = {
    ...btnPrimary,
    background: 'rgba(201,169,110,0.3)',
    cursor: 'not-allowed',
  }

  const btnGhost: React.CSSProperties = {
    width: '100%',
    background: 'transparent',
    border: 'none',
    fontSize: 12,
    color: '#5C5A6E',
    cursor: 'pointer',
    padding: '8px',
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: 'radial-gradient(ellipse at 30% 40%, #1A1A2E 0%, #0A0A0F 100%)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '16px',
      fontFamily: "'Outfit', 'Inter', sans-serif"
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@500;600&family=Outfit:wght@300;400;500;600&display=swap');
        .login-card { animation: fadeInUp 0.5s ease; }
        @keyframes fadeInUp {
          from { opacity: 0; transform: translateY(20px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .login-card input:focus { border-color: #C9A96E !important; }
      `}</style>

      <div className="login-card" style={{
        background: '#111118',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 28,
        width: '100%',
        maxWidth: 400,
        padding: '36px 32px',
        boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
      }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: 'linear-gradient(135deg, #C9A96E, #A0784A)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 16px',
            boxShadow: '0 8px 20px rgba(201,169,110,0.25)'
          }}>
            <Heart size={30} style={{ fill: '#fff', color: '#fff' }} />
          </div>
          <h1 style={{
            fontFamily: "'Cormorant Garamond', serif",
            fontSize: 32, fontWeight: 600,
            color: '#F0EDE8', margin: 0, letterSpacing: '-0.5px'
          }}>AuraLink Admin</h1>
          <p style={{ fontSize: 13, color: '#5C5A6E', marginTop: 6 }}>Secure access only</p>
        </div>

        {error && (
          <div style={{
            background: 'rgba(224,92,107,0.12)',
            border: '1px solid rgba(224,92,107,0.25)',
            borderRadius: 12, padding: '10px 14px', marginBottom: 20,
            fontSize: 12, color: '#E05C6B'
          }}>
            {error}
          </div>
        )}

        {/* Mode chooser */}
        {mode === 'choose' && (
          <>
            <button
              onClick={() => { setMode('otp-mobile'); setError('') }}
              style={btnPrimary}
            >
              Super Admin Login (OTP)
            </button>
            <button
              onClick={() => { setMode('password'); setError('') }}
              style={{
                ...btnPrimary,
                background: 'rgba(155,127,232,0.15)',
                border: '1px solid rgba(155,127,232,0.3)',
                color: '#C4B0F5',
              }}
            >
              Admin Login (Username + Password)
            </button>
          </>
        )}

        {/* OTP — mobile step */}
        {mode === 'otp-mobile' && (
          <>
            <label style={{ fontSize: 12, fontWeight: 500, color: '#8A8799', display: 'block', marginBottom: 6 }}>
              Mobile Number
            </label>
            <input
              type="tel"
              value={mobile}
              onChange={e => setMobile(e.target.value)}
              placeholder="9876543210"
              style={inputStyle}
            />
            <button
              onClick={sendOtp}
              disabled={loading || !mobile}
              style={loading || !mobile ? btnDisabled : btnPrimary}
            >
              {loading ? 'Sending...' : 'Send OTP'}
            </button>
            <button onClick={() => setMode('choose')} style={btnGhost}>← Back</button>
          </>
        )}

        {/* OTP — verify step */}
        {mode === 'otp-verify' && (
          <>
            <p style={{ fontSize: 13, color: '#8A8799', marginBottom: 16 }}>
              OTP sent to <strong style={{ color: '#C9A96E' }}>{mobile}</strong>
            </p>
            <label style={{ fontSize: 12, fontWeight: 500, color: '#8A8799', display: 'block', marginBottom: 6 }}>
              Verification Code
            </label>
            <input
              type="text"
              value={otp}
              onChange={e => setOtp(e.target.value)}
              placeholder="Enter 6-digit OTP"
              maxLength={6}
              style={{ ...inputStyle, textAlign: 'center', letterSpacing: '4px' }}
            />
            <button
              onClick={verifyOtp}
              disabled={loading || otp.length < 4}
              style={loading || otp.length < 4 ? btnDisabled : btnPrimary}
            >
              {loading ? 'Verifying...' : 'Login to Dashboard'}
            </button>
            <button onClick={() => setMode('otp-mobile')} style={btnGhost}>← Change mobile number</button>
          </>
        )}

        {/* Password login */}
        {mode === 'password' && (
          <>
            <label style={{ fontSize: 12, fontWeight: 500, color: '#8A8799', display: 'block', marginBottom: 6 }}>
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="Enter username"
              style={inputStyle}
              autoComplete="username"
            />
            <label style={{ fontSize: 12, fontWeight: 500, color: '#8A8799', display: 'block', marginBottom: 6 }}>
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="Enter password"
              style={inputStyle}
              autoComplete="current-password"
              onKeyDown={e => e.key === 'Enter' && !loading && username && password && loginWithPassword()}
            />
            <button
              onClick={loginWithPassword}
              disabled={loading || !username || !password}
              style={loading || !username || !password ? btnDisabled : btnPrimary}
            >
              {loading ? 'Logging in...' : 'Login'}
            </button>
            <button onClick={() => setMode('choose')} style={btnGhost}>← Back</button>
          </>
        )}
      </div>
    </div>
  )
}
