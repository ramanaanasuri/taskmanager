import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';

/**
 * Live Sessions (FE-1 + FE-2): reachability gate, then a role-aware surface with
 * Browse / My sessions / Teaching. The same person can learn and teach — role is
 * per session, so the mentor surface (Teaching) and learner surfaces (Browse,
 * My sessions) coexist. Self-contained, inline-styled feature component.
 */
function LiveSessions({ authToken }) {
  const authHeader = { headers: { Authorization: `Bearer ${authToken}` } };
  const C = { primary: '#6d28d9', primaryDark: '#5b21b6', bg: '#faf9ff', line: '#e5e7eb', mut: '#6b7280' };

  const [reach, setReach] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [view, setView] = useState('browse');   // 'browse' | 'mysessions' | 'teaching'

  // verify gate
  const [channel, setChannel] = useState('EMAIL');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [devCode, setDevCode] = useState(null);
  const [codeSent, setCodeSent] = useState(false);

  // teaching (mentor)
  const [skills, setSkills] = useState([]);
  const [profile, setProfile] = useState(null);
  const [bio, setBio] = useState('');
  const [chosenSkills, setChosenSkills] = useState([]);
  const [offerings, setOfferings] = useState([]);       // sessions I offer
  const [form, setForm] = useState({ skillId: '', title: '', description: '', startTime: '', durationMin: 60, capacity: 1 });
  const [tempStart, setTempStart] = useState('');   // datetime picked but not yet applied (tick confirms it)

  // learner
  const [browse, setBrowse] = useState([]);
  const [skillFilter, setSkillFilter] = useState(null);
  const [mySessions, setMySessions] = useState([]);      // sessions I joined

  // lobby
  const [lobbyId, setLobbyId] = useState(null);
  const [lobby, setLobby] = useState(null);

  // ---- loaders ----
  const loadReach = useCallback(async () => {
    try { const r = await axios.get(`${API_BASE_URL}/api/reachability/self`, authHeader); setReach(r.data); return r.data; }
    catch { setError('Could not load reachability status'); return null; }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  const loadSkills = useCallback(async () => {
    try { const s = await axios.get(`${API_BASE_URL}/api/skills`, authHeader); setSkills(s.data.skills || []); } catch {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  const loadMentor = useCallback(async () => {
    try { const p = await axios.get(`${API_BASE_URL}/api/mentor/profile`, authHeader); setProfile(p.data); setBio(p.data.bio || ''); setChosenSkills(p.data.skillIds || []); }
    catch { setProfile(null); }
    try { const o = await axios.get(`${API_BASE_URL}/api/my/offerings`, authHeader); setOfferings(o.data.offerings || []); } catch {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  const loadBrowse = useCallback(async (skill) => {
    try {
      const url = skill ? `${API_BASE_URL}/api/offerings?skill=${skill}` : `${API_BASE_URL}/api/offerings`;
      const r = await axios.get(url, authHeader); setBrowse(r.data.offerings || []);
    } catch { setBrowse([]); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  const loadMySessions = useCallback(async () => {
    try { const r = await axios.get(`${API_BASE_URL}/api/my/sessions`, authHeader); setMySessions(r.data.sessions || []); } catch { setMySessions([]); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  const loadLobby = useCallback(async (id) => {
    try { const r = await axios.get(`${API_BASE_URL}/api/offerings/${id}/lobby`, authHeader); setLobby(r.data); }
    catch (e) { setError(e.response?.data?.message || 'Could not load lobby'); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authToken]);

  // initial
  useEffect(() => {
    (async () => {
      setLoading(true);
      const r = await loadReach();
      if (r && r.floorMet) { await loadSkills(); }
      setLoading(false);
    })();
  }, [loadReach, loadSkills]);

  // per-view loads
  useEffect(() => {
    if (!reach?.floorMet || lobbyId) return;
    if (view === 'browse') loadBrowse(skillFilter);
    else if (view === 'mysessions') loadMySessions();
    else if (view === 'teaching') { loadMentor(); }
  }, [view, reach, lobbyId, skillFilter, loadBrowse, loadMySessions, loadMentor]);

  // lobby polling (15s)
  useEffect(() => {
    if (!lobbyId) return;
    loadLobby(lobbyId);
    const t = setInterval(() => { if (!document.hidden) loadLobby(lobbyId); }, 15000);
    return () => clearInterval(t);
  }, [lobbyId, loadLobby]);

  const flash = (msg) => { setNotice(msg); setTimeout(() => setNotice(null), 3000); };
  const errOf = (e, fallback) => e.response?.data?.message || e.response?.data?.error || fallback;

  // ---- gate actions ----
  const sendCode = async () => {
    setBusy(true); setError(null); setDevCode(null);
    try {
      await axios.post(`${API_BASE_URL}/api/reachability/channels`, { channelsSelected: [channel], termsVersion: '1' }, authHeader);
      const body = channel === 'SMS' ? { channel, value: phone } : { channel };
      const res = await axios.post(`${API_BASE_URL}/api/reachability/verify/request`, body, authHeader);
      setCodeSent(true); if (res.data.devCode) setDevCode(res.data.devCode); flash('Code sent.');
    } catch (e) { setError(errOf(e, 'Could not send code')); }
    setBusy(false);
  };
  const confirmCode = async () => {
    setBusy(true); setError(null);
    try {
      const res = await axios.post(`${API_BASE_URL}/api/reachability/verify/confirm`, { channel, code: code.trim() }, authHeader);
      if (res.data.verified) { flash('Verified.'); setCode(''); setCodeSent(false); const r = await loadReach(); if (r?.floorMet) await loadSkills(); }
      else setError('That code is not correct or has expired.');
    } catch (e) { setError(errOf(e, 'Verification failed')); }
    setBusy(false);
  };

  // ---- teaching actions ----
  const toggleSkill = (id) => setChosenSkills((p) => p.includes(id) ? p.filter((s) => s !== id) : [...p, id]);
  const saveProfile = async () => {
    setBusy(true); setError(null);
    try { await axios.post(`${API_BASE_URL}/api/mentor/profile`, { bio: bio.trim(), skillIds: chosenSkills }, authHeader); flash('Profile saved.'); await loadMentor(); }
    catch (e) { setError(errOf(e, 'Could not save profile')); } setBusy(false);
  };
  const createOffering = async () => {
    setBusy(true); setError(null);
    try {
      await axios.post(`${API_BASE_URL}/api/offerings`, {
        skillId: Number(form.skillId), title: form.title.trim(), description: form.description.trim(),
        startTime: form.startTime, durationMin: Number(form.durationMin), capacity: Number(form.capacity),
      }, authHeader);
      flash('Session created.'); setForm({ ...form, title: '', description: '', startTime: '' }); setTempStart(''); await loadMentor();
    } catch (e) { setError(errOf(e, 'Could not create session')); } setBusy(false);
  };
  const schedule = async (id) => {
    setBusy(true); setError(null);
    try { await axios.post(`${API_BASE_URL}/api/offerings/${id}/schedule`, {}, authHeader); flash('Scheduled.'); await loadMentor(); }
    catch (e) { setError(errOf(e, 'Could not schedule')); } setBusy(false);
  };

  // ---- learner actions ----
  const join = async (id) => {
    setBusy(true); setError(null);
    try {
      const res = await axios.post(`${API_BASE_URL}/api/offerings/${id}/join`, {}, authHeader);
      flash(res.data.outcome === 'ENROLLED' ? 'Enrolled!' : 'Already enrolled.');
      await loadBrowse(skillFilter);
    } catch (e) {
      if (e.response?.status === 409) setError('That session is full.');
      else setError(errOf(e, 'Could not join'));
    }
    setBusy(false);
  };

  // ---- lobby actions ----
  const openLobby = (id) => { setLobbyId(id); setLobby(null); };
  const closeLobby = () => { setLobbyId(null); setLobby(null); };
  const checkIn = async () => {
    setBusy(true); setError(null);
    try { await axios.post(`${API_BASE_URL}/api/offerings/${lobbyId}/checkin`, {}, authHeader); flash('Checked in.'); await loadLobby(lobbyId); }
    catch (e) { setError(errOf(e, 'Could not check in')); } setBusy(false);
  };
  const sendReach = async (targetEmail) => {
    setBusy(true); setError(null);
    try {
      const body = targetEmail ? { targetEmail } : {};
      const res = await axios.post(`${API_BASE_URL}/api/offerings/${lobbyId}/reach`, body, authHeader);
      flash(res.data.outcome === 'SENT' ? `Reached via ${res.data.channelsUsed.join(', ')}` : 'No verified channel to reach.');
    } catch (e) {
      if (e.response?.status === 429) setError('Already nudged recently — try again shortly.');
      else setError(errOf(e, 'Could not reach'));
    }
    setBusy(false);
  };

  // ---- styles ----
  const card = { background: '#fff', border: `1px solid ${C.line}`, borderRadius: 12, padding: 18, marginBottom: 16 };
  const label = { fontSize: '.72rem', textTransform: 'uppercase', letterSpacing: '.04em', color: C.mut, marginBottom: 4, display: 'block' };
  const input = { width: '100%', padding: '8px 10px', fontSize: '.9rem', border: `1px solid ${C.line}`, borderRadius: 8, marginBottom: 10, boxSizing: 'border-box' };
  const btn = { background: C.primary, color: '#fff', border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: '.9rem', fontWeight: 600, cursor: 'pointer' };
  const btnGhost = { ...btn, background: '#f3f4f6', color: '#374151' };
  const pill = (bg, fg) => ({ fontSize: '.72rem', fontWeight: 700, padding: '2px 9px', borderRadius: 10, background: bg, color: fg });
  const seg = (on) => ({ padding: '7px 16px', fontSize: '.85rem', fontWeight: on ? 700 : 500, color: on ? '#fff' : C.mut, background: on ? C.primary : 'transparent', border: 'none', borderRadius: 8, cursor: 'pointer' });

  if (loading) return <div style={{ color: C.mut }}>Loading…</div>;

  // ---- render ----
  return (
    <div style={{ maxWidth: 720, margin: '0 auto' }}>
      {error && <div style={{ ...card, borderColor: '#fecaca', background: '#fef2f2', color: '#b91c1c' }}>{error}</div>}
      {notice && <div style={{ color: '#047857', fontSize: '.85rem', marginBottom: 10 }}>{notice}</div>}

      {/* GATE */}
      {!reach?.floorMet && (
        <div style={card}>
          <h3 style={{ margin: '0 0 4px', color: C.primaryDark }}>Verify to continue</h3>
          <p style={{ color: C.mut, fontSize: '.88rem', marginTop: 0 }}>To create or join a session you need a verified email or phone. Push is a free extra, not a substitute.</p>
          <label style={label}>Channel</label>
          <select style={input} value={channel} onChange={(e) => { setChannel(e.target.value); setCodeSent(false); setDevCode(null); }}>
            <option value="EMAIL">Email</option><option value="SMS">SMS</option>
          </select>
          {channel === 'SMS' && (<><label style={label}>Phone number</label><input style={input} value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+1 919 555 0142" /></>)}
          {!codeSent ? <button style={btn} disabled={busy} onClick={sendCode}>Send code</button> : (
            <>
              <label style={label}>Enter code</label>
              <input style={input} value={code} onChange={(e) => setCode(e.target.value)} placeholder="6-digit code" />
              {devCode && <p style={{ fontSize: '.8rem', color: C.mut }}>Test code: <b>{devCode}</b></p>}
              <button style={btn} disabled={busy} onClick={confirmCode}>Verify</button>
              <button style={{ ...btnGhost, marginLeft: 8 }} disabled={busy} onClick={() => { setCodeSent(false); setCode(''); }}>Start over</button>
            </>
          )}
        </div>
      )}

      {/* VERIFIED */}
      {reach?.floorMet && !lobbyId && (
        <>
          <div style={{ display: 'inline-flex', gap: 4, background: '#f3f4f6', borderRadius: 10, padding: 4, marginBottom: 16 }}>
            <button style={seg(view === 'browse')} onClick={() => setView('browse')}>Browse</button>
            <button style={seg(view === 'mysessions')} onClick={() => setView('mysessions')}>My sessions</button>
            <button style={seg(view === 'teaching')} onClick={() => setView('teaching')}>Teaching</button>
          </div>

          {/* BROWSE */}
          {view === 'browse' && (
            <>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 12 }}>
                <button style={{ ...pill(skillFilter === null ? '#ede9fe' : '#fff', skillFilter === null ? C.primaryDark : C.mut), border: `1px solid ${C.line}`, cursor: 'pointer' }} onClick={() => setSkillFilter(null)}>All</button>
                {skills.map((s) => (
                  <button key={s.id} style={{ ...pill(skillFilter === s.id ? '#ede9fe' : '#fff', skillFilter === s.id ? C.primaryDark : C.mut), border: `1px solid ${C.line}`, cursor: 'pointer' }} onClick={() => setSkillFilter(s.id)}>{s.name}</button>
                ))}
              </div>
              {browse.length === 0 && <p style={{ color: C.mut, fontSize: '.88rem' }}>No open sessions right now.</p>}
              {browse.map((o) => (
                <div key={o.id} style={card}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                    <div>
                      <div style={{ fontWeight: 600 }}>{o.title}</div>
                      <div style={{ color: C.mut, fontSize: '.8rem', margin: '3px 0' }}>{o.mentorEmail} · {o.startTime?.replace('T', ' ').slice(0, 16)}</div>
                      <span style={pill(o.seatsLeft > 0 ? '#dcfce7' : '#fee2e2', o.seatsLeft > 0 ? '#047857' : '#b91c1c')}>
                        {o.seatsLeft > 0 ? `${o.seatsLeft} seat${o.seatsLeft > 1 ? 's' : ''} left` : 'Full'}{o.capacity === 1 ? ' · 1:1' : ` · Group ${o.capacity}`}
                      </span>
                    </div>
                    <button style={o.seatsLeft > 0 ? btn : { ...btnGhost, cursor: 'not-allowed' }} disabled={busy || o.seatsLeft === 0} onClick={() => join(o.id)}>Join</button>
                  </div>
                </div>
              ))}
            </>
          )}

          {/* MY SESSIONS (joined) */}
          {view === 'mysessions' && (
            <div style={card}>
              <h3 style={{ margin: '0 0 8px', color: C.primaryDark }}>Sessions I joined ({mySessions.length})</h3>
              {mySessions.length === 0 && <p style={{ color: C.mut, fontSize: '.88rem' }}>You haven't joined any sessions yet.</p>}
              {mySessions.map((o) => (
                <div key={o.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderTop: `1px solid ${C.line}` }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: '.9rem' }}>{o.title}</div>
                    <div style={{ color: C.mut, fontSize: '.78rem' }}>{o.mentorEmail} · {o.startTime?.replace('T', ' ').slice(0, 16)}</div>
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    {o.joinUrl && <a href={o.joinUrl} target="_blank" rel="noreferrer" style={{ ...btn, background: '#16a34a', textDecoration: 'none' }}>Join room</a>}
                    <button style={btnGhost} onClick={() => openLobby(o.id)}>Lobby</button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* TEACHING (mentor) */}
          {view === 'teaching' && (
            <>
              <div style={card}>
                <h3 style={{ margin: '0 0 4px', color: C.primaryDark }}>{profile ? 'Mentor profile' : 'Become a mentor'}</h3>
                <label style={label}>Bio</label>
                <textarea style={{ ...input, height: 60 }} value={bio} onChange={(e) => setBio(e.target.value)} placeholder="Tell learners who you are" />
                <label style={label}>Skills you teach</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
                  {skills.map((s) => (
                    <label key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '.85rem', border: `1px solid ${C.line}`, borderRadius: 20, padding: '4px 10px', cursor: 'pointer' }}>
                      <input type="checkbox" checked={chosenSkills.includes(s.id)} onChange={() => toggleSkill(s.id)} />{s.name}
                    </label>
                  ))}
                </div>
                <button style={btn} disabled={busy} onClick={saveProfile}>Save profile</button>
              </div>

              {profile && (
                <div style={card}>
                  <h3 style={{ margin: '0 0 8px', color: C.primaryDark }}>Create a session</h3>
                  <label style={label}>Skill</label>
                  <select style={input} value={form.skillId} onChange={(e) => setForm({ ...form, skillId: e.target.value })}>
                    <option value="">Select a skill…</option>
                    {skills.filter((s) => (profile.skillIds || []).includes(s.id)).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                  <label style={label}>Title</label>
                  <input style={input} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
                  <label style={label}>Description</label>
                  <input style={input} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
                  <div style={{ display: 'flex', gap: 12 }}>
                    <div style={{ flex: 1 }}>
                      <label style={label}>Start</label>
                      <div style={{ display: 'flex', gap: 6, alignItems: 'stretch' }}>
                        <input type="datetime-local" style={{ ...input, marginBottom: 0, flex: 1 }} value={tempStart} onChange={(e) => setTempStart(e.target.value)} />
                        <button type="button" title="Apply selected date" disabled={!tempStart}
                          style={{ ...btn, background: tempStart ? '#16a34a' : '#d1d5db', padding: '0 14px', cursor: tempStart ? 'pointer' : 'not-allowed' }}
                          onClick={() => setForm({ ...form, startTime: tempStart })}>✓</button>
                      </div>
                      {form.startTime
                        ? <div style={{ fontSize: '.75rem', color: '#047857', marginTop: 4 }}>Start set: {form.startTime.replace('T', ' ')}</div>
                        : <div style={{ fontSize: '.75rem', color: C.mut, marginTop: 4 }}>Pick a time, then click ✓ to apply.</div>}
                    </div>
                    <div style={{ width: 110 }}><label style={label}>Duration (min)</label><input type="number" style={input} value={form.durationMin} onChange={(e) => setForm({ ...form, durationMin: e.target.value })} /></div>
                    <div style={{ width: 90 }}><label style={label}>Capacity</label><input type="number" style={input} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} /></div>
                  </div>
                  <button style={btn} disabled={busy || !form.skillId || !form.title || !form.startTime} onClick={createOffering}>Create session</button>
                </div>
              )}

              <div style={card}>
                <h3 style={{ margin: '0 0 8px', color: C.primaryDark }}>Sessions I offer ({offerings.length})</h3>
                {offerings.length === 0 && <p style={{ color: C.mut, fontSize: '.88rem' }}>No sessions yet.</p>}
                {offerings.map((o) => (
                  <div key={o.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 0', borderTop: `1px solid ${C.line}` }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '.9rem' }}>{o.title}</div>
                      <div style={{ color: C.mut, fontSize: '.78rem' }}>{o.capacity === 1 ? '1:1' : `Group · ${o.capacity}`} · {o.startTime?.replace('T', ' ').slice(0, 16)}</div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      {o.status === 'DRAFT' ? <span style={pill('#f3f4f6', '#374151')}>DRAFT</span> : <span style={pill('#dcfce7', '#047857')}>{o.status}</span>}
                      {o.status === 'DRAFT'
                        ? <button style={btn} disabled={busy} onClick={() => schedule(o.id)}>Schedule</button>
                        : <button style={btnGhost} onClick={() => openLobby(o.id)}>Lobby</button>}
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}

      {/* LOBBY */}
      {reach?.floorMet && lobbyId && (
        <div style={card}>
          <button style={{ ...btnGhost, marginBottom: 12 }} onClick={closeLobby}>← Back</button>
          {!lobby ? <div style={{ color: C.mut }}>Loading lobby…</div> : (
            <>
              <h3 style={{ margin: '0 0 2px', color: C.primaryDark }}>Lobby · {lobby.title}</h3>
              <p style={{ color: C.mut, fontSize: '.82rem', marginTop: 0 }}>
                You are the <b>{lobby.role === 'MENTOR' ? 'mentor' : 'learner'}</b> here. Mentor is{' '}
                <span style={pill(lobby.mentorCheckedIn ? '#dcfce7' : '#fef3c7', lobby.mentorCheckedIn ? '#047857' : '#92400e')}>{lobby.mentorCheckedIn ? 'ready' : 'not here yet'}</span>
              </p>

              <button style={btn} disabled={busy} onClick={checkIn}>I'm ready (check in)</button>
              {lobby.joinUrl && <a href={lobby.joinUrl} target="_blank" rel="noreferrer" style={{ ...btn, background: '#16a34a', textDecoration: 'none', marginLeft: 8, display: 'inline-block' }}>Join room</a>}

              {/* mentor sees roster + reach each learner */}
              {lobby.role === 'MENTOR' && (
                <div style={{ marginTop: 14 }}>
                  <div style={label}>Learners ({(lobby.participants || []).length})</div>
                  {(lobby.participants || []).length === 0 && <p style={{ color: C.mut, fontSize: '.85rem' }}>No one has joined yet.</p>}
                  {(lobby.participants || []).map((p) => (
                    <div key={p.email} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: `1px solid ${C.line}` }}>
                      <span style={{ fontSize: '.85rem' }}>{p.email} <span style={pill(p.checkedIn ? '#dcfce7' : '#f3f4f6', p.checkedIn ? '#047857' : '#6b7280')}>{p.checkedIn ? 'ready' : 'not yet'}</span></span>
                      <button style={{ ...btnGhost, padding: '5px 11px' }} disabled={busy} onClick={() => sendReach(p.email)}>Reach</button>
                    </div>
                  ))}
                </div>
              )}

              {/* learner sees self + nudge mentor */}
              {lobby.role === 'LEARNER' && (
                <div style={{ marginTop: 14 }}>
                  <p style={{ fontSize: '.85rem' }}>You are <span style={pill(lobby.self?.checkedIn ? '#dcfce7' : '#f3f4f6', lobby.self?.checkedIn ? '#047857' : '#6b7280')}>{lobby.self?.checkedIn ? 'ready' : 'not checked in'}</span></p>
                  <button style={btnGhost} disabled={busy} onClick={() => sendReach(null)}>Nudge mentor</button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}

export default LiveSessions;
