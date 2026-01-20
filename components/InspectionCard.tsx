
import React, { useState } from 'react';
import { Expediente } from '../types';
import { ChevronDown, ChevronUp, Map, Trash2, Sprout, AlertCircle, CheckCircle2 } from 'lucide-react';
import { focusNativeMap } from '../services/bridge';

interface InspectionCardProps {
  expediente: Expediente;
  onDelete: (id: string) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ expediente, onDelete }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const totalParcelas = expediente.parcelas.length;
  const revisadas = expediente.parcelas.filter(p => p.status !== 'pendiente').length;
  const progreso = totalParcelas > 0 ? (revisadas / totalParcelas) * 100 : 0;

  return (
    <div className="glass-panel rounded-2xl overflow-hidden mb-4 border border-white/5 shadow-lg transition-all hover:border-emerald-500/30">
      {/* HEADER TARJETA */}
      <div className="p-5 cursor-pointer" onClick={() => setIsExpanded(!isExpanded)}>
        <div className="flex justify-between items-start mb-3">
            <div className="flex items-center gap-3">
                <div className="bg-emerald-900/30 p-2.5 rounded-xl border border-emerald-500/20 text-emerald-400">
                    <Sprout size={20} />
                </div>
                <div>
                    <h3 className="font-bold text-slate-100 text-base leading-tight">{expediente.titular}</h3>
                    <p className="text-xs text-slate-400 font-medium mt-0.5">Campaña {expediente.campana} • {expediente.fechaImportacion}</p>
                </div>
            </div>
            <button 
                onClick={(e) => { e.stopPropagation(); onDelete(expediente.id); }}
                className="p-2 text-slate-600 hover:text-red-400 hover:bg-red-400/10 rounded-lg transition-colors"
            >
                <Trash2 size={16} />
            </button>
        </div>

        {/* BARRA DE PROGRESO */}
        <div className="mt-4">
            <div className="flex justify-between text-[10px] font-bold uppercase tracking-wider mb-1.5">
                <span className="text-emerald-500">{revisadas} Revisadas</span>
                <span className="text-slate-500">{totalParcelas} Totales</span>
            </div>
            <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                <div 
                    className="h-full bg-emerald-500 transition-all duration-700 ease-out" 
                    style={{ width: `${progreso}%` }}
                />
            </div>
        </div>

        <div className="flex justify-center mt-2">
             {isExpanded ? <ChevronUp size={18} className="text-slate-500" /> : <ChevronDown size={18} className="text-slate-500" />}
        </div>
      </div>

      {/* DETALLE PARCELAS */}
      {isExpanded && (
        <div className="bg-black/20 border-t border-white/5">
            <div className="max-h-[300px] overflow-y-auto p-2 space-y-2 custom-scrollbar">
                {expediente.parcelas.map((parcela, idx) => (
                    <div key={idx} className="flex items-center justify-between p-3 bg-white/5 rounded-xl border border-white/5 hover:bg-white/10 transition-colors group">
                        <div className="flex items-center gap-3">
                            <div className={`w-2 h-10 rounded-full ${
                                parcela.status === 'conforme' ? 'bg-emerald-500' : 
                                parcela.status === 'incidencia' ? 'bg-red-500' : 'bg-slate-600'
                            }`} />
                            <div>
                                <p className="font-bold text-slate-200 text-sm">{parcela.referencia}</p>
                                <p className="text-[10px] text-slate-500 uppercase font-bold tracking-wide">
                                    {parcela.area} ha • {parcela.status}
                                </p>
                            </div>
                        </div>
                        
                        <div className="flex gap-2 opacity-80 group-hover:opacity-100 transition-opacity">
                            <button 
                                onClick={() => focusNativeMap(parcela.lat, parcela.lng)}
                                className="p-2 bg-slate-700 text-white rounded-lg hover:bg-emerald-600 transition-colors active:scale-95 shadow-md"
                            >
                                <Map size={16} />
                            </button>
                        </div>
                    </div>
                ))}
            </div>
        </div>
      )}
    </div>
  );
};
