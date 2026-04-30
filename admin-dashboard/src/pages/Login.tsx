import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { jwtDecode } from 'jwt-decode'
import { Heart } from 'lucide-react'

export default function Login() {
  const navigate = useNavigate()
  const [mobile,  setMobile]  = useState('')
  const [otp,     setOtp]     = useState('')
  const [step,    setStep]    = useState<'mobile' | 'otp'>('mobile')
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')

  function normalizeMobile(m: string): string {
    return m.replace(/\D/g, '').replace(/^91/, '').slice(-10)
  }

  async function sendOtp() {
    setError('')
    setLoading(true)
    try {
      await axios.post('/api/auth/mobile/send-otp', { mobile: normalizeMobile(mobile) })
      setStep('otp')
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
      const role: string = decoded.role || 'USER'

      if (role !== 'ADMIN') {
        setError('Access denied. ADMIN role required.')
        return
      }

      localStorage.setItem('admin_token', token)
      localStorage.setItem('admin_role',  role)
      navigate('/')
    } catch {
      setError('Invalid OTP or access denied.')
    } finally {
      setLoading(false)
    }
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
        .login-card {
          animation: fadeInUp 0.5s ease;
        }
        @keyframes fadeInUp {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>

      <div className="login-card" style={{
        background: '#111118',
        border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 28,
        width: '100%',
        maxWidth: 400,
        padding: '36px 32px',
        boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)',
        backdropFilter: 'blur(2px)'
      }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{
            width: 64,
            height: 64,
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #C9A96E, #A0784A)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
            boxShadow: '0 8px 20px rgba(201,169,110,0.25)'
          }}>
            <Heart size={30} style={{ fill: '#fff', color: '#fff' }} />
          </div>
          <h1 style={{
            fontFamily: "'Cormorant Garamond', serif",
            fontSize: 32,
            fontWeight: 600,
            color: '#F0EDE8',
            margin: 0,
            letterSpacing: '-0.5px'
          }}>AuraLink Admin</h1>
          <p style={{ fontSize: 13, color: '#5C5A6E', marginTop: 6 }}>Secure access only</p>
        </div>

        {error && (
          <div style={{
            background: 'rgba(224,92,107,0.12)',
            border: '1px solid rgba(224,92,107,0.25)',
            borderRadius: 12,
            padding: '10px 14px',
            marginBottom: 20,
            fontSize: 12,
            color: '#E05C6B'
          }}>
            {error}
          </div>
        )}

        {step === 'mobile' ? (
          <>
            <label style={{ fontSize: 12, fontWeight: 500, color: '#8A8799', display: 'block', marginBottom: 6 }}>Mobile Number</label>
            <input
              type="tel"
              value={mobile}
              onChange={e => setMobile(e.target.value)}
              placeholder="9876543210"
              style={{
                width: '100%',
                background: '#1C1C28',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 12,
                padding: '12px 16px',
                fontSize: 14,
                color: '#F0EDE8',
                fontFamily: 'inherit',
                outline: 'none',
                marginBottom: 20
              }}
              onFocus={e => e.target.style.borderColor = '#C9A96E'}
              onBlur={e => e.target.style.borderColor = 'rgba(255,255,255,0.08)'}
            />
            <button
              onClick={sendOtp}
              disabled={loading || !mobile}
              style={{
                width: '100%',
                background: loading || !mobile ? 'rgba(201,169,110,0.3)' : 'linear-gradient(135deg, #C9A96E, #A0784A)',
                border: 'none',
                borderRadius: 12,
                padding: '12px',
                fontSize: 14,
                fontWeight: 600,
                color: '#fff',
                cursor: loading || !mobile ? 'not-allowed' : 'pointer',
                transition: 'all 0.2s'
              }}
            >
              {loading ? 'Sending...' : 'Send OTP'}
            </button>
          </>
        ) : (
          <>
            <p style={{ fontSize: 13, color: '#8A8799', marginBottom: 16 }}>
              OTP sent to <strong style={{ color: '#C9A96E' }}>{mobile}</strong>
            </p>
            <label style={{ fontSize: 12, fontWeight: 500, color: '#8A8799', display: 'block', marginBottom: 6 }}>Verification Code</label>
            <input
              type="text"
              value={otp}
              onChange={e => setOtp(e.target.value)}
              placeholder="Enter 6-digit OTP"
              maxLength={6}
              style={{
                width: '100%',
                background: '#1C1C28',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 12,
                padding: '12px 16px',
                fontSize: 14,
                color: '#F0EDE8',
                fontFamily: 'inherit',
                outline: 'none',
                marginBottom: 20,
                textAlign: 'center',
                letterSpacing: '4px'
              }}
              onFocus={e => e.target.style.borderColor = '#C9A96E'}
              onBlur={e => e.target.style.borderColor = 'rgba(255,255,255,0.08)'}
            />
            <button
              onClick={verifyOtp}
              disabled={loading || otp.length < 4}
              style={{
                width: '100%',
                background: loading || otp.length < 4 ? 'rgba(201,169,110,0.3)' : 'linear-gradient(135deg, #C9A96E, #A0784A)',
                border: 'none',
                borderRadius: 12,
                padding: '12px',
                fontSize: 14,
                fontWeight: 600,
                color: '#fff',
                cursor: loading || otp.length < 4 ? 'not-allowed' : 'pointer',
                marginBottom: 12
              }}
            >
              {loading ? 'Verifying...' : 'Login to Dashboard'}
            </button>
            <button
              onClick={() => setStep('mobile')}
              style={{
                width: '100%',
                background: 'transparent',
                border: 'none',
                fontSize: 12,
                color: '#5C5A6E',
                cursor: 'pointer',
                padding: '8px'
              }}
            >
              ← Change mobile number
            </button>
          </>
        )}
      </div>
    </div>
  )
}