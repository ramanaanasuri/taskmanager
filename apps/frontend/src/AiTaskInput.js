import React, { useState } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';

/**
 * Tier 1a: natural-language task creation.
 *
 * The user types plain English; the backend (/api/ai/parse-task) extracts
 * structured fields; this component hands them to the parent via onParsed,
 * which pre-fills the normal Create New Task form. The user reviews and
 * presses Add Task — the AI proposes, the human confirms, the existing
 * create path executes.
 *
 * Props:
 *   authToken       - JWT for the API call
 *   onParsed(task)  - receives {title, priority, scheduledDate, notify{...}, confidence}
 *   onLimitReached()- called on HTTP 402 so the parent can open the plans modal
 */
function AiTaskInput({ authToken, onParsed, onLimitReached }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null); // {kind: 'info'|'warn'|'error', text}

  const parse = async () => {
    if (!text.trim() || busy) return;
    setBusy(true);
    setMessage(null);
    try {
      const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const res = await axios.post(
        `${API_BASE_URL}/api/ai/parse-task`,
        { text: text.trim(), timezone },
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      const task = res.data.task;
      console.log('✨ [AiTaskInput] Parsed:', task, 'usage:', res.data.aiRequests);
      if (task.confidence < 0.6) {
        setMessage({ kind: 'warn', text: "I didn't quite get that — try rephrasing with a bit more detail." });
      } else {
        onParsed(task);
        setText('');
        setMessage({ kind: 'info', text: '✓ Form filled below — review and press Add Task.' });
      }
    } catch (err) {
      const status = err.response?.status;
      if (status === 402) {
        setMessage({ kind: 'error', text: "You've used all AI requests in your plan — upgrade to keep going." });
        if (onLimitReached) onLimitReached();
      } else if (status === 503) {
        setMessage({ kind: 'error', text: 'AI is unavailable right now — please use the form below.' });
      } else {
        console.error('❌ [AiTaskInput] Error:', err);
        setMessage({ kind: 'error', text: 'Something went wrong — please use the form below.' });
      }
    } finally {
      setBusy(false);
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault(); // don't submit the surrounding form
      parse();
    }
  };

  const msgColor = { info: '#2e7d32', warn: '#b26a00', error: '#c62828' };

  return (
    <div style={{
      background: 'linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%)',
      border: '1px solid #ddd6fe',
      borderRadius: '10px',
      padding: '14px 16px',
      marginBottom: '20px'
    }}>
      <div style={{ fontWeight: 600, fontSize: '0.9rem', color: '#5b21b6', marginBottom: '8px' }}>
        ✨ Create with AI
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder='Try: "renew car insurance next friday 6pm, high priority, email me"'
          disabled={busy}
          style={{
            flex: 1, padding: '10px 12px', borderRadius: '8px',
            border: '1px solid #c4b5fd', fontSize: '0.9rem', outline: 'none'
          }}
        />
        <button
          type="button"
          onClick={parse}
          disabled={busy || !text.trim()}
          style={{
            padding: '10px 18px', borderRadius: '8px', border: 'none',
            background: busy ? '#a78bfa' : 'linear-gradient(135deg, #7c3aed, #6d28d9)',
            color: '#fff', fontWeight: 600, cursor: busy ? 'wait' : 'pointer',
            whiteSpace: 'nowrap'
          }}
        >
          {busy ? 'Thinking…' : '✨ Fill form'}
        </button>
      </div>
      {message && (
        <div style={{ marginTop: '8px', fontSize: '0.82rem', color: msgColor[message.kind] }}>
          {message.text}
        </div>
      )}
    </div>
  );
}

export default AiTaskInput;
