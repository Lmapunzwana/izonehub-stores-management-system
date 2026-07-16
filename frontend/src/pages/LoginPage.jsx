import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../api';
import { useAppData } from '../context/AppDataContext';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setUser, refreshAll } = useAppData();

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await apiFetch('/api/auth/login', {
        method: 'POST',
        body: { email, password },
      });
      const me = await apiFetch('/api/auth/me');
      setUser(me);
      await refreshAll(); // Load all data for the newly authenticated user
      navigate('/');
    } catch (err) {
      setError(err.message || 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f8fafc',
      }}
    >
      <div className="card" style={{ width: 360, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <img src="/logo.jpeg" alt="Logo" style={{ width: '120px', height: 'auto', marginBottom: '16px' }} />
        <h2 className="card-title" style={{ textAlign: 'center', margin: '0 0 16px 0' }}>
          Sign in to NSV Stores
        </h2>

        {error && (
          <p style={{ color: '#dc2626', fontSize: 13, marginTop: -8, marginBottom: 16, textAlign: 'center' }}>
            {error}
          </p>
        )}

        <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <label>Email</label>
            <input
              type="email"
              className="input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="username"
            />
          </div>
          <div>
            <label>Password</label>
            <input
              type="password"
              className="input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%' }}>
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}
