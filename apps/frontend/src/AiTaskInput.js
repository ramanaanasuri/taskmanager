import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import useSpeechInput, { micButtonStyle } from './useSpeechInput';
import VoiceHelp, { TASK_VOICE_HELP } from './VoiceHelp';

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
  const autoAddRef = useRef(false);
  const speech = useSpeechInput((finalText) => {
    // Trailing voice command: "... add task" / "... create task" creates it hands-free.
    const m = finalText.match(/(.*?)[,.!]?\s*\b(add|create)\s+(the\s+)?task\b[.!]?\s*$/i);
    const spoken = m ? m[1].trim() : finalText;
    setText((prev) => {
      const next = (prev ? prev + ' ' : '') + spoken;
      if (m && next.trim()) autoAddRef.current = true;
      return next.trim() ? next : prev; // a bare "add task" with no content changes nothing
    });
  });
  const boxRef = useRef(null);
  const shownText = speech.listening && speech.interim
    ? (text ? text + ' ' : '') + speech.interim
    : text;
  useEffect(() => {
    // Auto-grow so long (spoken) sentences stay fully visible for review.
    const el = boxRef.current;
    if (el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 120) + 'px'; }
  }, [shownText]);
  useEffect(() => {
    // Runs after the voice command committed its text: parse, then auto-add.
    if (autoAddRef.current && text.trim() && !busy) {
      autoAddRef.current = false;
      if (speech.listening) speech.toggle();
      parse(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);
  const [message, setMessage] = useState(null); // {kind: 'info'|'warn'|'error', text}

  const parse = async (autoAdd = false) => {
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
        onParsed(task, { autoAdd });
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
      <div style={{ fontWeight: 600, fontSize: '0.9rem', color: '#5b21b6', marginBottom: '8px',
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span>✨ Create with AI</span>
        {speech.supported && <VoiceHelp title="Voice commands — create a task" items={TASK_VOICE_HELP} />}
      </div>
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <textarea
          ref={boxRef}
          rows={1}
          value={shownText}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder='Try: "renew car insurance next friday 6pm, high priority, email me"'
          title={text}
          disabled={busy}
          style={{
            flex: '1 1 200px', minWidth: '160px', padding: '10px 12px', borderRadius: '8px',
            border: '1px solid #c4b5fd', fontSize: '0.9rem', outline: 'none',
            resize: 'none', overflowY: 'auto', maxHeight: '120px',
            fontFamily: 'inherit', lineHeight: 1.4
          }}
        />
        {text && (
          <button
            type="button"
            onClick={() => { if (speech.listening) speech.toggle(); setText(''); setMessage(null); }}
            disabled={busy}
            title="Clear"
            aria-label="Clear text"
            style={{
              width: '42px', minWidth: '42px', height: '42px', borderRadius: '50%',
              border: '1px solid #ddd6fe', background: '#fff', color: '#6d28d9',
              fontSize: '1rem', lineHeight: 1, cursor: busy ? 'not-allowed' : 'pointer'
            }}
          >
            ✕
          </button>
        )}
        {speech.supported && (
          <button
            type="button"
            onClick={speech.toggle}
            disabled={busy}
            title={speech.listening ? 'Stop listening' : 'Speak your task'}
            aria-label={speech.listening ? 'Stop voice input' : 'Start voice input'}
            style={micButtonStyle(speech.listening, busy)}
          >
            {speech.listening ? '🔴' : '🎤'}
          </button>
        )}
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
      {speech.listening && (
        <div style={{ marginTop: '8px', fontSize: '0.82rem', color: '#5b21b6' }}>
          🎤 Listening… tap 🔴 when done (auto-stops after a long pause), then review and press ✨ Fill form.
        </div>
      )}
      {speech.error && (
        <div style={{ marginTop: '8px', fontSize: '0.82rem', color: '#c62828' }}>
          {speech.error}
        </div>
      )}
      {message && (
        <div style={{ marginTop: '8px', fontSize: '0.82rem', color: msgColor[message.kind] }}>
          {message.text}
        </div>
      )}
    </div>
  );
}

export default AiTaskInput;
