import React, { useState, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';

/**
 * Reusable phone verification: verify once, reuse everywhere.
 *
 * Renders three states off a single data load:
 *   - verified   -> shows the confirmed number + "Change"
 *   - enter      -> mobile number + "Send code"
 *   - code sent  -> 6-digit code + "Verify"
 *
 * Reports the confirmed number up via onVerified(phone) (and onVerified('') when
 * the user chooses to change it) so the host form can use it. Backed by the shared
 * /api/reachability/* endpoints — no per-feature logic. Reads the JWT itself so it
 * drops into any surface with no wiring.
 */
export default function PhoneVerification({ onVerified }) {
  const token = localStorage.getItem('jwt_token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };

  const [loading, setLoading] = useState(true);
  const [verified, setVerified] = useState('');   // confirmed number, '' when none
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    axios.get(`${API_BASE_URL}/api/reachability/phone`, auth)
      .then((r) => {
        if (r.data && r.data.verified) {
          setVerified(r.data.phone);
          if (onVerified) onVerified(r.data.phone);
        }
      })
      .catch(() => { /* not verified yet — show the enter state */ })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sendCode = async () => {
    if (!phone.trim()) { setError('Enter your mobile number first.'); return; }
    setBusy(true); setError('');
    try {
      await axios.post(`${API_BASE_URL}/api/reachability/channels`,
        { channelsSelected: ['SMS'], termsVersion: '1' }, auth);
      await axios.post(`${API_BASE_URL}/api/reachability/verify/request`,
        { channel: 'SMS', value: phone.trim() }, auth);
      setCodeSent(true);
    } catch (e) {
      setError(e.response?.data?.message || 'Could not send a code. Please wait a moment and try again.');
    } finally { setBusy(false); }
  };

  const confirm = async () => {
    if (!code.trim()) { setError('Enter the code you received.'); return; }
    setBusy(true); setError('');
    try {
      const r = await axios.post(`${API_BASE_URL}/api/reachability/verify/confirm`,
        { channel: 'SMS', code: code.trim() }, auth);
      if (r.data && r.data.verified) {
        setVerified(phone.trim());
        setCodeSent(false); setCode('');
        if (onVerified) onVerified(phone.trim());
      } else {
        setError('That code is incorrect or has expired. Request a new one.');
      }
    } catch (e) {
      setError(e.response?.data?.message || 'Could not verify. Please try again.');
    } finally { setBusy(false); }
  };

  const change = () => {
    setVerified(''); setPhone(''); setCode(''); setCodeSent(false); setError('');
    if (onVerified) onVerified('');
  };

  const box = { padding: '10px 12px', borderRadius: 8, border: '1px solid #e2e8f0', marginTop: 6 };
  const inp = { flex: 1, padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1', fontSize: '.9rem', boxSizing: 'border-box' };
  const btn = { padding: '8px 14px', borderRadius: 6, border: 'none', background: '#667eea', color: '#fff', cursor: 'pointer', fontSize: '.85rem', fontWeight: 600, whiteSpace: 'nowrap' };
  const ghost = { padding: '6px 12px', borderRadius: 6, border: '1px solid #cbd5e1', background: '#fff', color: '#475569', cursor: 'pointer', fontSize: '.8rem', whiteSpace: 'nowrap' };
  const hint = { display: 'block', color: '#64748b', fontSize: '.78rem', marginTop: 6 };

  if (loading) return <div style={{ color: '#64748b', fontSize: '.85rem' }}>Checking your verified number…</div>;

  if (verified) {
    return (
      <div style={{ ...box, borderColor: '#86efac', background: '#f0fdf4' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
          <span style={{ color: '#047857', fontSize: '.9rem' }}>✓ Texts go to your verified number <strong>{verified}</strong></span>
          <button type="button" style={ghost} onClick={change}>Change</button>
        </div>
      </div>
    );
  }

  return (
    <div style={box}>
      {!codeSent ? (
        <>
          <label style={{ display: 'block', fontSize: '.8rem', color: '#475569', marginBottom: 4 }}>Mobile number (E.164, e.g. +15055550006)</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input type="tel" style={inp} placeholder="+15055550006" value={phone} onChange={(e) => setPhone(e.target.value)} />
            <button type="button" style={btn} disabled={busy} onClick={sendCode}>Send code</button>
          </div>
          <small style={hint}>We'll text a 6-digit code to confirm this is your number. Standard rates apply.</small>
        </>
      ) : (
        <>
          <label style={{ display: 'block', fontSize: '.8rem', color: '#475569', marginBottom: 4 }}>Enter the code sent to {phone}</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input type="text" inputMode="numeric" style={inp} placeholder="123456" value={code} onChange={(e) => setCode(e.target.value)} />
            <button type="button" style={btn} disabled={busy} onClick={confirm}>Verify</button>
          </div>
          <small style={hint}>
            <span style={{ color: '#667eea', cursor: 'pointer' }} onClick={() => { setCodeSent(false); setCode(''); setError(''); }}>Change number</span>
          </small>
        </>
      )}
      {error && <div style={{ color: '#dc2626', fontSize: '.8rem', marginTop: 6 }}>{error}</div>}
    </div>
  );
}
