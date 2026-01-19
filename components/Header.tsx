
import React from 'react';
import { ClipboardCheck, Settings } from 'lucide-react';

export const Header: React.FC = () => {
  return (
    <header className="sticky top-0 z-30 glass-panel p-4 flex items-center justify-between border-b border-white/5">
      <div className="flex items-center gap-3">
        <div className="bg-emerald-500/20 p-2 rounded-xl border border-emerald-500/30">
          <ClipboardCheck size={22} className="text-emerald-400" />
        </div>
        <div>
          <h1 className="text-sm font-black text-slate-100 leading-tight uppercase tracking-tighter">GeoSIGPAC Inspecciones</h1>
          <p className="text-[10px] text-emerald-400/80 font-bold uppercase tracking-widest">Digital Field Manager</p>
        </div>
      </div>
      <button className="w-10 h-10 flex items-center justify-center bg-white/5 rounded-full text-slate-400 active:scale-90 transition-all">
        <Settings size={20} />
      </button>
    </header>
  );
};
