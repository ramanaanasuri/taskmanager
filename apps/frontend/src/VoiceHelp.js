import React, { useEffect, useRef, useState } from 'react';

/**
 * Small ⓘ button + popover with the voice commands relevant to the surface
 * it sits on (task creation vs. agent chat). Context-sensitive help beats a
 * manual: it's one tap away exactly where the question arises.
 */
export default function VoiceHelp({ items, title }) {
  const [open, setOpen] = useState(false);
  const boxRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const close = (e) => {
      if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('touchstart', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('touchstart', close);
    };
  }, [open]);

  return (
    <span ref={boxRef} style={{ position: 'relative', display: 'inline-flex' }}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        title="Voice commands"
        aria-label="Voice command help"
        aria-expanded={open}
        style={{
          width: '22px', height: '22px', borderRadius: '50%', padding: 0,
          border: '1px solid #ddd6fe', background: open ? '#ede9fe' : '#fff',
          color: '#6d28d9', fontSize: '0.75rem', fontWeight: 700,
          lineHeight: 1, cursor: 'pointer', alignSelf: 'center'
        }}
      >
        i
      </button>
      {open && (
        <div
          role="dialog"
          aria-label={title}
          style={{
            position: 'absolute', top: '28px', right: 0, zIndex: 50,
            width: 'min(300px, 82vw)', background: '#fff',
            border: '1px solid #ddd6fe', borderRadius: '10px',
            boxShadow: '0 8px 24px rgba(76, 29, 149, 0.18)',
            padding: '12px 14px', fontSize: '0.8rem', color: '#3b3b4f',
            textAlign: 'left'
          }}
        >
          <div style={{ fontWeight: 700, color: '#5b21b6', marginBottom: '6px' }}>{title}</div>
          <ul style={{ margin: 0, paddingLeft: '16px', display: 'grid', gap: '5px' }}>
            {items.map(([phrase, effect]) => (
              <li key={phrase}>
                <span style={{ fontStyle: 'italic' }}>&ldquo;{phrase}&rdquo;</span>
                {' '}&mdash; {effect}
              </li>
            ))}
          </ul>
          <div style={{ marginTop: '8px', color: '#6b7280' }}>
            Tap 🎤 to start &middot; tap 🔴 or pause ~8s to stop &middot; ✕ clears the draft.
          </div>
        </div>
      )}
    </span>
  );
}

export const TASK_VOICE_HELP = [
  ['renew car insurance friday 6pm, high priority', 'dictates — review, then press ✨'],
  ['… email me — add task', 'creates the task hands-free'],
  ['add task (after a pause)', 'creates whatever is in the box'],
];

export const CHAT_VOICE_HELP = [
  ['what do I have overdue — send', 'lists overdue tasks'],
  ['postpone the PGE call to tuesday 5pm — send', 'moves a due date'],
  ['mark the AT&T bill as done — send', 'completes a task'],
  ['yes, go ahead — send', 'confirms a proposed bulk change'],
];
