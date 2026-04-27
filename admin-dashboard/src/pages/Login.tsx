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

  // Backend sirf 10-digit Indian number chahta hai (no +91 prefix)
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
    <div className="min-h-screen bg-gradient-to-br from-[#1E1E35] to-[#2A1A4A] flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-8">
        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-pink-500 to-purple-600 flex items-center justify-center mb-3">
            <Heart size={28} className="text-white fill-white" />
          </div>
          <h1 className="text-2xl font-bold text-slate-800">AuraLink Admin</h1>
          <p className="text-slate-500 text-sm mt-1">Admin access only</p>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm mb-4">
            {error}
          </div>
        )}

        {step === 'mobile' ? (
          <>
            <label className="text-sm font-medium text-slate-700 block mb-1">Mobile Number</label>
            <input
              type="tel"
              value={mobile}
              onChange={e => setMobile(e.target.value)}
              placeholder="9876543210 (10 digits)"
              className="w-full border border-slate-200 rounded-lg px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 mb-4"
            />
            <button
              onClick={sendOtp}
              disabled={loading || !mobile}
              className="w-full bg-gradient-to-r from-pink-500 to-purple-600 text-white font-semibold py-3 rounded-lg hover:opacity-90 transition disabled:opacity-50"
            >
              {loading ? 'Sending…' : 'Send OTP'}
            </button>
          </>
        ) : (
          <>
            <p className="text-sm text-slate-500 mb-3">OTP sent to <strong>{mobile}</strong></p>
            <label className="text-sm font-medium text-slate-700 block mb-1">OTP Code</label>
            <input
              type="text"
              value={otp}
              onChange={e => setOtp(e.target.value)}
              placeholder="Enter 6-digit OTP"
              maxLength={6}
              className="w-full border border-slate-200 rounded-lg px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 mb-4"
            />
            <button
              onClick={verifyOtp}
              disabled={loading || otp.length < 4}
              className="w-full bg-gradient-to-r from-pink-500 to-purple-600 text-white font-semibold py-3 rounded-lg hover:opacity-90 transition disabled:opacity-50"
            >
              {loading ? 'Verifying…' : 'Login'}
            </button>
            <button
              onClick={() => setStep('mobile')}
              className="w-full mt-2 text-sm text-slate-500 hover:text-slate-700"
            >
              ← Change number
            </button>
          </>
        )}
      </div>
    </div>
  )
}
