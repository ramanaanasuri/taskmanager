import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import API_BASE_URL from './config';
import useSpeechInput, { micButtonStyle } from './useSpeechInput';
import VoiceHelp, { CHAT_VOICE_HELP } from './VoiceHelp';

/**
 * Tier 2: conversational agent panel.
 *
 * REUSABLE by design — everything app-specific arrives via props, so a
 * future application embeds the same component with a different endpoint,
 * title, and suggestions:
 *
 *   <AgentChat authToken={jwt}
 *              endpoint="/api/ai/chat"
 *              title="Task Assistant"
 *              placeholder="Ask me about your tasks…"
 *              suggestions={["What's overdue?", "Plan my week"]}
 *              onActionsApplied={refetch}
 *              onLimitReached={openPlansModal} />
 *
 * Contract with the backend (AiController /api/ai/chat):
 *  - the CLIENT holds conversation history and resends it each turn
 *  - "confirmed": true is sent when the user's message is an approval
 *    (yes / go ahead / confirm…), completing the bulk-change guardrail loop
 *  - a reply with actions.length > 0 means data changed -> onActionsApplied
 */
function AgentChat({
  authToken,
  endpoint = '/api/ai/chat',
  title = 'Task Assistant',
  placeholder = 'Ask me about your tasks…',
  suggestions = ["What do I have overdue?", "What's due this week?"],
  onActionsApplied,
  onLimitReached,
}) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]); // {role:'user'|'assistant', content}
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const bottomRef = useRef(null);
  const autoSendRef = useRef(false);
  const speech = useSpeechInput((finalText) => {
    // Trailing voice command: "... send" / "send it" / "send message" submits the turn.
    const m = finalText.match(/(.*?)[,.!]?\s*\bsend(\s+it|\s+message)?\b[.!]?\s*$/i);
    const spoken = m ? m[1].trim() : finalText;
    setInput((prev) => {
      const next = (prev ? prev + ' ' : '') + spoken;
      if (m && next.trim()) autoSendRef.current = true;
      return next.trim() ? next : prev;
    });
  });
  useEffect(() => {
    // Runs after the voice command committed its text: submit the turn.
    if (autoSendRef.current && input.trim() && !busy) {
      autoSendRef.current = false;
      if (speech.listening) speech.toggle();
      send();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [input]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, busy, open]);

  const isApproval = (text) =>
    /^(yes|y|yeah|yep|ok|okay|sure|go ahead|proceed|confirm(ed)?|approved?|do it)\b/i.test(text.trim());

  const send = async (rawText) => {
    const text = (rawText ?? input).trim();
    if (!text || busy) return;
    const history = [...messages, { role: 'user', content: text }];
    setMessages(history);
    setInput('');
    setBusy(true);
    setError(null);
    try {
      const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const res = await axios.post(
        `${API_BASE_URL}${endpoint}`,
        { messages: history, confirmed: isApproval(text), timezone },
        { headers: { Authorization: `Bearer ${authToken}` } }
      );
      setMessages([...history, { role: 'assistant', content: res.data.reply }]);
      console.log('🤖 [AgentChat] actions:', res.data.actions, 'usage:', res.data.aiRequests);
      if (res.data.actions?.length > 0 && onActionsApplied) {
        onActionsApplied();
      }
    } catch (err) {
      const status = err.response?.status;
      if (status === 402) {
        setError("You've used all AI requests in your plan — upgrade to keep going.");
        if (onLimitReached) onLimitReached();
      } else if (status === 503) {
        setError('The assistant is unavailable right now — please try again shortly.');
      } else {
        console.error('❌ [AgentChat] Error:', err);
        setError('Something went wrong — please try again.');
      }
      // keep the user's message in the transcript so a retry resends context
    } finally {
      setBusy(false);
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  // ---------- styles (inline: no App.css changes) ----------
  const purple = 'linear-gradient(135deg, #7c3aed, #6d28d9)';
  const S = {
    fab: {
      position: 'fixed', bottom: '22px', right: '22px', zIndex: 1000,
      width: '56px', height: '56px', borderRadius: '50%', border: 'none',
      background: purple, color: '#fff', fontSize: '1.5rem', cursor: 'pointer',
      boxShadow: '0 4px 14px rgba(109,40,217,.45)',
    },
    panel: {
      position: 'fixed', bottom: '90px', right: '22px', zIndex: 1000,
      width: 'min(380px, calc(100vw - 32px))', height: 'min(520px, 70vh)',
      background: '#fff', borderRadius: '14px', display: 'flex', flexDirection: 'column',
      boxShadow: '0 10px 40px rgba(15,23,42,.25)', border: '1px solid #e5e7eb',
      overflow: 'hidden',
    },
    header: {
      background: purple, color: '#fff', padding: '12px 16px',
      fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    },
    body: { flex: 1, overflowY: 'auto', padding: '14px', background: '#faf9ff' },
    bubbleUser: {
      background: purple, color: '#fff', padding: '8px 12px', borderRadius: '14px 14px 4px 14px',
      margin: '6px 0 6px auto', maxWidth: '85%', width: 'fit-content',
      fontSize: '.88rem', lineHeight: 1.5, whiteSpace: 'pre-wrap',
    },
    bubbleBot: {
      background: '#fff', color: '#1f2937', padding: '8px 12px', borderRadius: '14px 14px 14px 4px',
      margin: '6px auto 6px 0', maxWidth: '85%', width: 'fit-content',
      fontSize: '.88rem', lineHeight: 1.5, whiteSpace: 'pre-wrap',
      border: '1px solid #e9e5f8',
    },
    chip: {
      display: 'inline-block', margin: '4px 6px 0 0', padding: '6px 12px',
      borderRadius: '16px', border: '1px solid #c4b5fd', background: '#f5f3ff',
      color: '#5b21b6', fontSize: '.8rem', cursor: 'pointer',
    },
    inputRow: { display: 'flex', gap: '8px', padding: '10px', borderTop: '1px solid #eee', background: '#fff' },
    input: {
      flex: 1, padding: '10px 12px', borderRadius: '10px',
      border: '1px solid #c4b5fd', fontSize: '.9rem', outline: 'none', resize: 'none',
    },
    sendBtn: (disabled) => ({
      padding: '0 16px', borderRadius: '10px', border: 'none',
      background: disabled ? '#c4b5fd' : purple, color: '#fff',
      fontWeight: 600, cursor: disabled ? 'default' : 'pointer',
    }),
    error: { color: '#c62828', fontSize: '.8rem', padding: '0 14px 8px' },
  };

  return (
    <>
      <button style={S.fab} onClick={() => setOpen(!open)}
              title={open ? 'Close assistant' : 'Open task assistant'}>
        {open ? '✕' : '🤖'}
      </button>

      {open && (
        <div style={S.panel}>
          <div style={S.header}>
            <span>🤖 {title}</span>
            <span style={{ fontSize: '.72rem', fontWeight: 400, opacity: .85, display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
              AI · plan-metered
              {speech.supported && <VoiceHelp title="Voice commands — Task Assistant" items={CHAT_VOICE_HELP} />}
            </span>
          </div>

          <div style={S.body}>
            {messages.length === 0 && (
              <div>
                <div style={S.bubbleBot}>
                  Hi! I can list, create, update and complete your tasks. Try one of these:
                </div>
                {suggestions.map((s) => (
                  <span key={s} style={S.chip} onClick={() => send(s)}>{s}</span>
                ))}
              </div>
            )}
            {messages.map((m, i) => (
              <div key={i} style={m.role === 'user' ? S.bubbleUser : S.bubbleBot}>{m.content}</div>
            ))}
            {busy && <div style={S.bubbleBot}>…thinking</div>}
            <div ref={bottomRef} />
          </div>

          {error && <div style={S.error}>{error}</div>}

          <div style={S.inputRow}>
            {input && (
              <button
                type="button"
                onClick={() => { if (speech.listening) speech.toggle(); setInput(''); }}
                disabled={busy}
                title="Clear"
                aria-label="Clear text"
                style={{
                  width: '42px', minWidth: '42px', height: '42px', borderRadius: '50%',
                  border: '1px solid #ddd', background: '#fff', color: '#555',
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
                title={speech.listening ? 'Stop listening' : 'Speak your message'}
                aria-label={speech.listening ? 'Stop voice input' : 'Start voice input'}
                style={micButtonStyle(speech.listening, busy)}
              >
                {speech.listening ? '🔴' : '🎤'}
              </button>
            )}
            <textarea
              rows={1}
              style={S.input}
              value={speech.listening && speech.interim ? (input ? input + ' ' : '') + speech.interim : input}
              placeholder={placeholder}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              disabled={busy}
            />
            <button style={S.sendBtn(busy || !input.trim())} onClick={() => send()}
                    disabled={busy || !input.trim()}>
              Send
            </button>
          </div>
        </div>
      )}
    </>
  );
}

export default AgentChat;
