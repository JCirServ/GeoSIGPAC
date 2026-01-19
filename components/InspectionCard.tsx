
import React, { useState } from 'react';
import { Inspection, Parcela } from '../types';
import { ChevronDown, ChevronUp, MapPin, Camera, Trash2, Calendar, LayoutGrid, CheckCircle2 } from 'lucide-react';
import { focusNativeMap, openNativeCamera } from '../services/bridge';

interface InspectionCardProps {
  inspection: Inspection;
  onDelete: (id: string) => void;
  onRemoveParcela: (insId: string, pId: string) => void;
  onStatusChange: (id: string, status: Inspection['status']) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ 
  inspection, onDelete, onRemoveParcela, onStatusChange 
}) => {
  const [expanded, setExpanded] = useState(false);

  const totalArea = inspection.parcelas.reduce((acc, p) => acc + p.area, 0);
  const completedCount = inspection.parcelas.filter(p => p.status === 'verified').length;

  const getStatusColor = (status: Inspection['status']) => {
    switch(status) {
      case 'completed': return 'border-emerald-500 text-emerald-400 bg-emerald-500/10';
      case 'in_progress': return 'border-blue-500 text-blue-400 bg-blue-500/10';
      case 'paused': return 'border-amber-500 text-amber-400 bg-amber-500/10';
      default: return 'border-slate-500 text-slate-400 bg-slate-500/10';
    }
  };

  return (
    <div className="glass-panel rounded-2xl overflow-hidden mb-4 border border-white/5 transition-all">
      <div className="p-4" onClick={() => setExpanded(!expanded)}>
        <div className="flex justify-between items-start mb-2">
          <div className={`status-badge ${getStatusColor(inspection.status)}`}>
            {inspection.status.replace('_', ' ')}
          </div>
          <button 
            onClick={(e) => { e.stopPropagation(); onDelete(inspection.id); }}
            className="text-slate-500 hover:text-red-400 transition-colors p-1"
          >
            <Trash2 size={16} />
          </button>
        </div>

        <h3 className="text-lg font-bold text-slate-100">{inspection.title}</h3>
        <p className="text-xs text-slate-400 mb-4 line-clamp-1">{inspection.description}</p>

        <div className="grid grid-cols-3 gap-2 text-[10px] font-bold uppercase tracking-tight text-slate-500">
          <div className="flex flex-col gap-1 bg-white/5 p-2 rounded-lg border border-white/5">
            <Calendar size={12} className="text-emerald-500" />
            <span>{inspection.date}</span>
          </div>
          <div className="flex flex-col gap-1 bg-white/5 p-2 rounded-lg border border-white/5">
            <LayoutGrid size={12} className="text-emerald-500" />
            <span>{inspection.parcelas.length} Recintos</span>
          </div>
          <div className="flex flex-col gap-1 bg-white/5 p-2 rounded-lg border border-white/5">
            <CheckCircle2 size={12} className="text-emerald-500" />
            <span>{totalArea.toFixed(1)} ha</span>
          </div>
        </div>

        <div className="mt-4 flex items-center justify-between">
           <div className="w-full bg-slate-800 h-1.5 rounded-full mr-4 overflow-hidden">
             <div 
              className="bg-emerald-500 h-full transition-all duration-500" 
              style={{ width: `${(completedCount / inspection.parcelas.length) * 100 || 0}%` }}
             />
           </div>
           {expanded ? <ChevronUp size={20} className="text-slate-400" /> : <ChevronDown size={20} className="text-slate-400" />}
        </div>
      </div>

      {expanded && (
        <div className="border-t border-white/5 bg-black/20 p-4 space-y-3 animate-in slide-in-from-top-2">
          {inspection.parcelas.map(p => (
            <div key={p.id} className="flex items-center justify-between p-3 bg-white/5 rounded-xl border border-white/5">
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-slate-200">{p.name}</span>
                  {p.status === 'verified' && <CheckCircle2 size={14} className="text-emerald-500" />}
                </div>
                <span className="text-[10px] text-slate-500 font-bold uppercase tracking-widest">{p.area} ha</span>
              </div>
              <div className="flex gap-2">
                <button 
                  onClick={() => focusNativeMap(p.lat, p.lng)}
                  className="w-10 h-10 flex items-center justify-center bg-slate-700 rounded-lg text-white"
                >
                  <MapPin size={18} />
                </button>
                <button 
                  onClick={() => openNativeCamera(p.id)}
                  className="w-10 h-10 flex items-center justify-center bg-emerald-600 rounded-lg text-white"
                >
                  <Camera size={18} />
                </button>
                <button 
                  onClick={() => onRemoveParcela(inspection.id, p.id)}
                  className="w-10 h-10 flex items-center justify-center text-slate-500"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          ))}
          
          <div className="pt-2 flex gap-2 overflow-x-auto pb-2">
            {(['planned', 'in_progress', 'completed', 'paused'] as Inspection['status'][]).map(s => (
              <button
                key={s}
                onClick={() => onStatusChange(inspection.id, s)}
                className={`px-3 py-1 rounded-lg text-[10px] font-bold uppercase border whitespace-nowrap ${inspection.status === s ? 'bg-emerald-500 text-white border-emerald-500' : 'bg-transparent text-slate-400 border-white/10'}`}
              >
                Set {s.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
