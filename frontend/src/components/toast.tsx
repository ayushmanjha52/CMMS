import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';

type Tone = 'success' | 'info';
interface Toast { id: number; message: string; tone: Tone }

const ToastContext = createContext<(message: string, tone?: Tone) => void>(() => {});

/** Confirms that an action took effect. Announced to screen readers via aria-live. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const next = useRef(1);

  const show = useCallback((message: string, tone: Tone = 'success') => {
    const id = next.current++;
    setToasts((t) => [...t.slice(-2), { id, message, tone }]);
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3800);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div aria-live="polite" role="status" className="fixed z-50 bottom-4 right-4 left-4 sm:left-auto flex flex-col gap-2 items-end pointer-events-none">
        {toasts.map((t) => (
          <div
            key={t.id}
            className="toast-in pointer-events-auto flex items-center gap-3 rounded-xl border border-engrave bg-[#141419]/95 backdrop-blur px-4 py-3 shadow-[0_20px_40px_-20px_rgba(0,0,0,0.9)] max-w-sm"
          >
            <span className={`w-2 h-2 rounded-full shrink-0 ${t.tone === 'success' ? 'bg-safe shadow-[0_0_10px_rgba(46,230,166,0.8)]' : 'bg-info'}`} />
            <span className="text-[13.5px]">{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

/** Sets the browser tab title for the current page. */
export function useTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · PlantDesk` : 'PlantDesk · Maintenance management for working plants';
  }, [title]);
}
