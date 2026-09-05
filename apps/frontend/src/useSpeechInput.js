import { useEffect, useRef, useState } from 'react';

/**
 * Voice input via the browser's Web Speech API (on-device / OS dictation).
 *
 * No backend, no AI credits: this only turns speech into text. The text then
 * flows through the existing paths (parse-task or the chat agent) exactly as
 * if it had been typed — the human still reviews before anything is created.
 *
 * Chrome, Edge and Safari (incl. iOS) support it; where it's absent
 * (e.g. Firefox) `supported` is false and callers hide the mic entirely.
 *
 * Contract:
 *   const { supported, listening, interim, toggle } = useSpeechInput(onFinal);
 *   - onFinal(text) fires once per finalized utterance segment
 *   - `interim` is the live not-yet-final transcript ('' when idle)
 *   - keeps listening across pauses (continuous); stops when the user taps
 *     the mic again or after ~8s of silence, and commits any leftover
 *     interim words on stop so nothing spoken is lost
 */
export default function useSpeechInput(onFinal) {
  const [supported, setSupported] = useState(false);
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState('');
  const [error, setError] = useState(null);
  const recRef = useRef(null);
  const interimRef = useRef('');       // survives onend so leftovers commit
  const silenceTimer = useRef(null);
  const onFinalRef = useRef(onFinal);
  onFinalRef.current = onFinal;

  const SILENCE_MS = 8000;             // auto-stop after this much quiet

  useEffect(() => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return; // supported stays false -> mic never renders

    // iOS exposes the API inside installed (home-screen) PWAs but its
    // dictation engine ends the session immediately without results —
    // verified on device. Hide the mic on that surface only; Safari tabs
    // on iOS and all other platforms keep it.
    const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent)
      || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches
      || window.navigator.standalone === true;
    if (isIos && isStandalone) return;

    setSupported(true);

    const rec = new SR();
    rec.lang = 'en-US';
    rec.continuous = true;        // keep listening across pauses in speech
    rec.interimResults = true;    // stream words as they're recognized

    const armSilenceTimer = () => {
      clearTimeout(silenceTimer.current);
      silenceTimer.current = setTimeout(() => { try { rec.stop(); } catch (e) { /* noop */ } }, SILENCE_MS);
    };

    rec.onresult = (e) => {
      armSilenceTimer();
      let interimText = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const chunk = e.results[i][0].transcript;
        if (e.results[i].isFinal) {
          if (chunk.trim()) onFinalRef.current(chunk.trim());
        } else {
          interimText += chunk;
        }
      }
      interimRef.current = interimText;
      setInterim(interimText);
    };
    rec.onstart = () => { setListening(true); setError(null); armSilenceTimer(); };
    rec.onend = () => {
      clearTimeout(silenceTimer.current);
      // Words still interim when recognition ends would otherwise vanish.
      if (interimRef.current.trim()) onFinalRef.current(interimRef.current.trim());
      interimRef.current = '';
      setListening(false);
      setInterim('');
    };
    rec.onerror = (e) => {
      // 'no-speech' and 'aborted' are normal outcomes, not errors worth showing
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
        setError('Microphone blocked — allow mic access for this site and try again.');
      } else if (e.error !== 'no-speech' && e.error !== 'aborted') {
        setError('Voice input failed — please type instead.');
      }
      setListening(false);
      setInterim('');
    };

    recRef.current = rec;
    return () => {
      clearTimeout(silenceTimer.current);
      rec.onresult = rec.onend = rec.onerror = rec.onstart = null;
      try { rec.abort(); } catch (e) { /* noop */ }
    };
  }, []);

  const toggle = () => {
    const rec = recRef.current;
    if (!rec) return;
    if (listening) { try { rec.stop(); } catch (e) { /* noop */ } }
    else { setError(null); try { rec.start(); } catch (e) { /* already started */ } }
  };

  return { supported, listening, interim, error, toggle };
}

/** Shared inline style for the round mic button, matching the app's inline-style approach. */
export function micButtonStyle(listening, busy) {
  return {
    width: '42px', minWidth: '42px', height: '42px', borderRadius: '50%',
    border: listening ? '2px solid #dc2626' : '1px solid #c4b5fd',
    background: listening ? '#fee2e2' : '#fff',
    fontSize: '1.05rem', lineHeight: 1,
    cursor: busy ? 'not-allowed' : 'pointer',
    animation: listening ? 'micPulse 1.2s ease-in-out infinite' : 'none',
  };
}
