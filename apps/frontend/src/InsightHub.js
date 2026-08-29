import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';

/**
 * Phase 1 Mentor Agent panel.
 *
 * One self-contained component that serves BOTH roles off a single data load:
 *   - MEMBER section (always): ask an investing question, watch your questions,
 *     see the answer appear once the mentor approves.
 *   - MENTOR section (role === 'MENTOR'): a small setup strip (create hub / add
 *     members) plus the approval queue — approve, edit & approve, reject, or
 *     answer directly.
 *
 * Delivery is live: while mounted, the question list auto-refreshes every 15s
 * (paused when the tab is hidden), so a member sees their answer land without
 * reloading. Failures on a background poll are swallowed; only explicit actions
 * (ask / approve / reject / answer) surface an error.
 *
 * Card rendering keys off which fields the server returned, not the role flag:
 * draftText present -> mentor view of that card; finalText present -> answer
 * shown. This mirrors the backend's own field gating (members never receive a
 * draft), so the member view is safe by construction.
 *
 * Backend contract (MentorController, all under /api):
 *   GET  /insight-hubs                      -> [{id, name, role, mentorEmail}]
 *   POST /insight-hubs            {name}     -> caller becomes MENTOR
 *   POST /insight-hubs/{id}/members {email}  -> 404 if that email never signed in
 *   POST /insight-hubs/{id}/questions {text} -> agent drafts (RAG) or escalates
 *   GET  /insight-hubs/{id}/questions        -> [question]
 *   POST /questions/{qid}/approve  {text?}   -> DRAFTED only; text = edited answer
 *   POST /questions/{qid}/reject             -> no body
 *   POST /questions/{qid}/answer   {text}    -> NEEDS_MENTOR direct answer
 *
 * question: {id, insightHubId, text, status, origin, draftText, finalText,
 *            sources:["Title|url"], askedByEmail, createdAt}
 * status: DRAFTED | NEEDS_MENTOR | APPROVED | DELIVERED | REJECTED
 */
function InsightHub({ authToken, onLimitReached }) {
  const [hubs, setHubs] = useState([]);
  const [hubId, setHubId] = useState(null);
  const [role, setRole] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [loadingHubs, setLoadingHubs] = useState(true);

  const [ask, setAsk] = useState('');
  const [newHub, setNewHub] = useState('');
  const [memberEmail, setMemberEmail] = useState('');
  const [editingId, setEditingId] = useState(null);   // DRAFTED card in inline-edit
  const [inputs, setInputs] = useState({});            // per-card textarea text {qid: text}

  const [busy, setBusy] = useState(false);             // any explicit action in flight
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);          // small success line (add member)

  const inFlight = useRef(false);                       // guards overlapping polls

  const authHeader = { headers: { Authorization: `Bearer ${authToken}` } };
  const hub = hubs.find((h) => h.id === hubId) || null;

  // ---------- data ----------
  const loadHubs = async () => {
    setLoadingHubs(true);
    try {
      const res = await axios.get(`${API_BASE_URL}/api/insight-hubs`, authHeader);
      setHubs(res.data);
      if (res.data.length > 0) {
        const first = res.data[0];
        setHubId(first.id);
        setRole(first.role);
      }
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setLoadingHubs(false);
    }
  };

  // used by both the 15s poll and by post-action refreshes; stays silent so a
  // dropped poll never blows away the UI — actions set their own errors.
  const loadQuestions = async () => {
    if (!hubId || inFlight.current) return;
    inFlight.current = true;
    try {
      const res = await axios.get(
        `${API_BASE_URL}/api/insight-hubs/${hubId}/questions`, authHeader);
      setQuestions(res.data);
    } catch {
      /* silent on poll */
    } finally {
      inFlight.current = false;
    }
  };

  useEffect(() => { loadHubs(); /* eslint-disable-next-line */ }, []);

  // auto-refresh delivery: fetch on hub load, then every 15s while the tab is
  // visible; clean up on unmount / hub change.
  useEffect(() => {
    if (!hubId) return;
    loadQuestions();
    const id = setInterval(() => {
      if (document.visibilityState === 'visible') loadQuestions();
    }, 15000);
    return () => clearInterval(id);
    // eslint-disable-next-line
  }, [hubId]);

  // ---------- actions ----------
  const errMsg = (err) => {
    const s = err.response?.status;
    if (s === 402) { if (onLimitReached) onLimitReached(); return "You've used all AI requests in your plan — upgrade to keep going."; }
    if (s === 503) return 'The mentor service is unavailable right now — please try again shortly.';
    if (s === 404) return err.response?.data?.message || 'Not found.';
    return err.response?.data?.message || 'Something went wrong — please try again.';
  };

  const run = async (fn) => {
    if (busy) return;
    setBusy(true); setError(null); setNotice(null);
    try { await fn(); await loadQuestions(); }
    catch (err) { setError(errMsg(err)); }
    finally { setBusy(false); }
  };

  const createHub = () => run(async () => {
    const res = await axios.post(`${API_BASE_URL}/api/insight-hubs`, { name: newHub.trim() }, authHeader);
    setNewHub('');
    setHubs([res.data]); setHubId(res.data.id); setRole(res.data.role);
  });

  const addMember = () => run(async () => {
    await axios.post(`${API_BASE_URL}/api/insight-hubs/${hubId}/members`, { email: memberEmail.trim() }, authHeader);
    setNotice(`Added ${memberEmail.trim()} to the circle.`);
    setMemberEmail('');
  });

  const submitAsk = () => run(async () => {
    await axios.post(`${API_BASE_URL}/api/insight-hubs/${hubId}/questions`, { text: ask.trim() }, authHeader);
    setAsk('');
  });

  const approve = (qid, text) => run(async () => {
    await axios.post(`${API_BASE_URL}/api/questions/${qid}/approve`,
      text != null ? { text } : {}, authHeader);
    setEditingId(null);
  });

  const reject = (qid) => run(async () => {
    await axios.post(`${API_BASE_URL}/api/questions/${qid}/reject`, {}, authHeader);
  });

  const answer = (qid) => run(async () => {
    await axios.post(`${API_BASE_URL}/api/questions/${qid}/answer`, { text: (inputs[qid] || '').trim() }, authHeader);
    setInputs((p) => ({ ...p, [qid]: '' }));
  });

  const setInput = (qid, v) => setInputs((p) => ({ ...p, [qid]: v }));

  // ---------- styles (inline: no App.css changes) ----------
  const purple = 'linear-gradient(135deg, #7c3aed, #6d28d9)';
  const S = {
    wrap: { maxWidth: '720px', margin: '0 auto', fontSize: '.92rem', color: '#1f2937' },
    card: { background: '#fff', border: '1px solid #e9e5f8', borderRadius: '14px', overflow: 'hidden', boxShadow: '0 6px 24px rgba(15,23,42,.06)' },
    header: { background: purple, color: '#fff', padding: '14px 18px', fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
    body: { padding: '16px 18px', background: '#faf9ff' },
    section: { marginBottom: '20px' },
    h: { fontWeight: 700, color: '#5b21b6', margin: '0 0 10px', fontSize: '.95rem' },
    row: { display: 'flex', gap: '8px', marginBottom: '8px' },
    input: { flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid #c4b5fd', fontSize: '.9rem', outline: 'none', resize: 'vertical', fontFamily: 'inherit' },
    btn: (disabled) => ({ padding: '10px 16px', borderRadius: '10px', border: 'none', background: disabled ? '#c4b5fd' : purple, color: '#fff', fontWeight: 600, cursor: disabled ? 'default' : 'pointer', whiteSpace: 'nowrap' }),
    ghost: { padding: '8px 14px', borderRadius: '10px', border: '1px solid #c4b5fd', background: '#f5f3ff', color: '#5b21b6', fontWeight: 600, cursor: 'pointer' },
    danger: { padding: '8px 14px', borderRadius: '10px', border: '1px solid #fecaca', background: '#fef2f2', color: '#b91c1c', fontWeight: 600, cursor: 'pointer' },
    qcard: { background: '#fff', border: '1px solid #e9e5f8', borderRadius: '12px', padding: '12px 14px', marginBottom: '10px' },
    qtext: { fontWeight: 600, marginBottom: '8px' },
    draft: { background: '#f5f3ff', border: '1px solid #e9e5f8', borderRadius: '10px', padding: '10px 12px', whiteSpace: 'pre-wrap', lineHeight: 1.5, marginBottom: '8px' },
    src: { display: 'inline-block', margin: '2px 6px 2px 0', padding: '4px 10px', borderRadius: '14px', border: '1px solid #c4b5fd', background: '#f5f3ff', color: '#5b21b6', fontSize: '.78rem', textDecoration: 'none' },
    badge: (bg, fg) => ({ display: 'inline-block', padding: '2px 10px', borderRadius: '12px', background: bg, color: fg, fontSize: '.72rem', fontWeight: 700, letterSpacing: '.02em' }),
    btnRow: { display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '4px' },
    error: { color: '#b91c1c', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px', padding: '8px 12px', margin: '0 0 12px', fontSize: '.85rem' },
    notice: { color: '#166534', background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '8px', padding: '8px 12px', margin: '0 0 12px', fontSize: '.85rem' },
    muted: { color: '#6b7280', fontSize: '.85rem' },
    refreshTag: { fontSize: '.72rem', fontWeight: 400, opacity: .85 },
  };

  const STATUS = {
    DRAFTED:      S.badge('#ede9fe', '#5b21b6'),
    NEEDS_MENTOR: S.badge('#fef3c7', '#92400e'),
    APPROVED:     S.badge('#dbeafe', '#1e40af'),
    DELIVERED:    S.badge('#dcfce7', '#166534'),
    REJECTED:     S.badge('#fee2e2', '#991b1b'),
  };
  const memberLabel = { DRAFTED: 'Your mentor is reviewing this', NEEDS_MENTOR: 'Waiting on your mentor', APPROVED: 'Approved — delivering', DELIVERED: 'Answered', REJECTED: 'Not answered' };

  const renderSources = (sources) => (sources || []).map((s, i) => {
    const bar = s.indexOf('|');
    const title = bar >= 0 ? s.slice(0, bar) : s;
    const url = bar >= 0 ? s.slice(bar + 1) : null;
    return url
      ? <a key={i} href={url} target="_blank" rel="noreferrer" style={S.src}>🔗 {title}</a>
      : <span key={i} style={S.src}>🔗 {title}</span>;
  });

  // ---------- render ----------
  if (loadingHubs) {
    return <div style={S.wrap}><div style={S.card}><div style={S.header}>💡 InsightHub</div><div style={S.body}><span style={S.muted}>Loading…</span></div></div></div>;
  }

  // no hub yet: offer to start one (caller becomes the mentor)
  if (!hub) {
    return (
      <div style={S.wrap}>
        <div style={S.card}>
          <div style={S.header}>💡 InsightHub</div>
          <div style={S.body}>
            {error && <div style={S.error}>{error}</div>}
            <div style={S.h}>Start a hub</div>
            <p style={S.muted}>Create a hub to mentor others. You’ll be the mentor; add learners by email, and their questions come to you for approval.</p>
            <div style={S.row}>
              <input style={S.input} placeholder="Hub name (e.g. Investing)"
                value={newHub} onChange={(e) => setNewHub(e.target.value)} />
              <button style={S.btn(busy || !newHub.trim())} disabled={busy || !newHub.trim()} onClick={createHub}>Create</button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const isMentor = role === 'MENTOR';
  const queue = questions.filter((q) => q.status === 'DRAFTED' || q.status === 'NEEDS_MENTOR');

  return (
    <div style={S.wrap}>
      <div style={S.card}>
        <div style={S.header}>
          <span>📈 {hub.name}</span>
          <span style={S.refreshTag}>{isMentor ? 'Mentor' : 'Learner'} · live</span>
        </div>
        <div style={S.body}>
          {error && <div style={S.error}>{error}</div>}
          {notice && <div style={S.notice}>{notice}</div>}

          {/* Mentor setup: add members */}
          {isMentor && (
            <div style={S.section}>
              <div style={S.h}>Add a learner</div>
              <div style={S.row}>
                <input style={S.input} placeholder="their email (they must have signed in once)"
                  value={memberEmail} onChange={(e) => setMemberEmail(e.target.value)} />
                <button style={S.btn(busy || !memberEmail.trim())} disabled={busy || !memberEmail.trim()} onClick={addMember}>Add</button>
              </div>
            </div>
          )}

          {/* Member: ask a question (always shown) */}
          <div style={S.section}>
            <div style={S.h}>Ask a question</div>
            <div style={S.row}>
              <textarea rows={2} style={S.input} placeholder="e.g. What’s the difference between a covered call and a cash-secured put?"
                value={ask} onChange={(e) => setAsk(e.target.value)} />
            </div>
            <button style={S.btn(busy || !ask.trim())} disabled={busy || !ask.trim()} onClick={submitAsk}>Ask</button>
          </div>

          {/* Mentor: approval queue */}
          {isMentor && (
            <div style={S.section}>
              <div style={S.h}>Approval queue {queue.length > 0 && `(${queue.length})`}</div>
              {queue.length === 0 && <p style={S.muted}>Nothing waiting. New questions appear here automatically.</p>}
              {queue.map((q) => (
                <div key={q.id} style={S.qcard}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', marginBottom: '6px' }}>
                    <span style={STATUS[q.status]}>{q.status.replace('_', ' ')}</span>
                    <span style={S.muted}>{q.askedByEmail}</span>
                  </div>
                  <div style={S.qtext}>{q.text}</div>

                  {/* DRAFTED: agent draft + sources, approve / edit / reject */}
                  {q.status === 'DRAFTED' && (
                    <>
                      {editingId === q.id ? (
                        <textarea rows={8} style={{ ...S.input, width: '100%', marginBottom: '8px' }}
                          value={inputs[q.id] ?? q.draftText ?? ''} onChange={(e) => setInput(q.id, e.target.value)} />
                      ) : (
                        <div style={S.draft}>{q.draftText}</div>
                      )}
                      {q.sources?.length > 0 && editingId !== q.id && <div style={{ marginBottom: '8px' }}>{renderSources(q.sources)}</div>}
                      <div style={S.btnRow}>
                        {editingId === q.id ? (
                          <>
                            <button style={S.btn(busy)} disabled={busy} onClick={() => approve(q.id, inputs[q.id] ?? q.draftText)}>Save & approve</button>
                            <button style={S.ghost} onClick={() => { setEditingId(null); setInput(q.id, ''); }}>Cancel</button>
                          </>
                        ) : (
                          <>
                            <button style={S.btn(busy)} disabled={busy} onClick={() => approve(q.id)}>Approve</button>
                            <button style={S.ghost} onClick={() => { setEditingId(q.id); setInput(q.id, q.draftText || ''); }}>Edit &amp; approve</button>
                            <button style={S.danger} onClick={() => reject(q.id)}>Reject</button>
                          </>
                        )}
                      </div>
                    </>
                  )}

                  {/* NEEDS_MENTOR: no draft — mentor answers directly */}
                  {q.status === 'NEEDS_MENTOR' && (
                    <>
                      <p style={S.muted}>Outside the knowledge base — answer this one yourself.</p>
                      <textarea rows={5} style={{ ...S.input, width: '100%', marginBottom: '8px' }} placeholder="Write your answer…"
                        value={inputs[q.id] || ''} onChange={(e) => setInput(q.id, e.target.value)} />
                      <div style={S.btnRow}>
                        <button style={S.btn(busy || !(inputs[q.id] || '').trim())} disabled={busy || !(inputs[q.id] || '').trim()} onClick={() => answer(q.id)}>Send answer</button>
                        <button style={S.danger} onClick={() => reject(q.id)}>Reject</button>
                      </div>
                    </>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Member view of their questions (also shown to mentor as a running log) */}
          <div style={S.section}>
            <div style={S.h}>Questions</div>
            {questions.length === 0 && <p style={S.muted}>No questions yet.</p>}
            {questions.map((q) => (
              <div key={q.id} style={S.qcard}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', marginBottom: '6px' }}>
                  <span style={STATUS[q.status]}>{isMentor ? q.status.replace('_', ' ') : memberLabel[q.status]}</span>
                  <span style={S.muted}>{new Date(q.createdAt).toLocaleDateString()}</span>
                </div>
                <div style={S.qtext}>{q.text}</div>
                {q.status === 'DELIVERED' && q.finalText && (
                  <>
                    <div style={S.draft}>{q.finalText}</div>
                    {q.sources?.length > 0 && <div>{renderSources(q.sources)}</div>}
                  </>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default InsightHub;
