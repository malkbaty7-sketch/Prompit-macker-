import React, { Component, ErrorInfo, ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Forgeprom Studio ErrorBoundary caught error:', error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6 text-center font-sans" dir="rtl">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 max-w-md w-full space-y-4 shadow-2xl">
            <div className="w-12 h-12 rounded-xl bg-purple-950 border border-purple-800 flex items-center justify-center text-purple-400 mx-auto font-bold text-xl">
              F
            </div>
            <h1 className="text-lg font-bold text-white">حدث استثناء في واجهة الاستوديو</h1>
            <p className="text-xs text-slate-400 leading-relaxed">
              تم حصر الخطأ بنجاح وحماية مسوداتك المحفوظة محلياً. يمكنك إعادة التحميل لمتابعة العمل بشكل طبيعي.
            </p>
            {this.state.error && (
              <div className="bg-slate-950 p-2.5 rounded border border-slate-800 text-rose-300 font-mono text-[11px] text-right overflow-x-auto">
                {this.state.error.message || 'Script error'}
              </div>
            )}
            <button
              onClick={this.handleReset}
              className="w-full py-2.5 bg-purple-700 hover:bg-purple-600 active:bg-purple-800 text-white font-semibold rounded-lg text-xs transition-colors cursor-pointer"
            >
              إعادة تشغيل الاستوديو
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>
);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Service worker registration optional in restricted preview sandboxes
    });
  });
}

