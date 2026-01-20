
import React, { useState } from 'react';
import { Expediente, Parcela } from '../types';
import { ChevronDown, ChevronUp, Map, Trash2, Sprout, AlertCircle, CheckCircle2, BrainCircuit, ScanSearch } from 'lucide-react';
import { focusNativeMap, openNativeCamera } from '../services/bridge';
import { analyzeParcelaCompliance } from '../services/ai';
import { useProjectStore } from '../store/useProjectStore';

interface InspectionCardProps {
  expediente: Expediente;
  onDelete: (id: string) => void;
}

export const InspectionCard: React.FC<InspectionCardProps> = ({ expediente, onDelete }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [analyzingId, setAnalyzingId] = useState<string | null>(null);
  const { setParcelaReport } = useProjectStore();

  const totalParcelas = expediente.parcelas.length;
  const revisadas = expediente.parcelas.filter(p => p.status !== 'pendiente').length;
  const progreso = totalParcelas > 0 ? (revisadas / totalParcelas) * 100 : 0;

  // Manejador del Análisis IA
  const handleAnalyze = async (e: React.MouseEvent, parcela: Parcela) => {
    e.stopPropagation();
    setAnalyzingId(parcela.id);
    const report = await analyzeParcelaCompliance(parcela);
    setParcelaReport(expediente.id, parcela.id, report);
    setAnalyzingId(null);
  };

  return (
    <div className="glass-panel rounded-3xl overflow-hidden mb-5 border border-white/10 shadow-xl transition-all hover:border-emerald-500/40 hover:shadow-emerald-900/20">
      
      {/* --- HEADER PRINCIPAL --- */}
      <div 
        className="p-5 cursor-pointer bg-gradient-to-b from-white/5 to-transparent hover:bg-white/5 transition-colors" 
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="flex justify-between items-start mb-4">
            <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shadow-[0_0_15px_rgba(16,185,129,0.2)]">
                    <Sprout size={24} />
                </div>
                <div>
                    <h3 className="font-black text-slate-100 text-lg leading-none tracking-tight">{expediente.titular}</h3>
                    <div className="flex items-center gap-2 mt-1.5">
                        <span className="px-2 py-0.5 rounded-md bg-slate-800 border border-slate-700 text-[10px] font-bold text-slate-400 uppercase">
                            Campaña {expediente.campana}
                        </span>
                        <span className="text-xs text-slate-500 font-medium">{expediente.fechaImportacion}</span>
                    </div>
                </div>
            </div>
            
            <button 
                onClick={(e) => { e.stopPropagation(); onDelete(expediente.id); }}
                className="p-2 text-slate-600 hover:text-red-400 hover:bg-red-500/10 rounded-xl transition-colors"
            >
                <Trash2 size={18} />
            </button>
        </div>

        {/* --- BARRA DE PROGRESO --- */}
        <div className="mt-2">
            <div className="flex justify-between text-[10px] font-bold uppercase tracking-wider mb-2">
                <div className="flex items-center gap-1.5 text-emerald-400">
                    <CheckCircle2 size={12} />
                    <span>{revisadas} Verificadas</span>
                </div>
                <div className="text-slate-500">{totalParcelas} Recintos</div>
            </div>
            <div className="h-1.5 bg-slate-800/80 rounded-full overflow-hidden">
                <div 
                    className="h-full bg-gradient-to-r from-emerald-600 to-emerald-400 transition-all duration-1000 ease-out shadow-[0_0_10px_rgba(52,211,153,0.5)]" 
                    style={{ width: `${progreso}%` }}
                />
            </div>
        </div>
        
        <div className="flex justify-center mt-1 opacity-50">
            {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </div>
      </div>

      {/* --- LISTA DE RECINTOS (EXPANDIDA) --- */}
      {isExpanded && (
        <div className="bg-black/20 border-t border-white/5 animate-in slide-in-from-top-2 duration-300">
            <div className="max-h-[400px] overflow-y-auto p-3 space-y-3 custom-scrollbar">
                {expediente.parcelas.map((parcela, idx) => (
                    <div 
                        key={idx} 
                        className="relative overflow-hidden bg-white/5 rounded-2xl border border-white/5 hover:border-emerald-500/30 hover:bg-white/10 transition-all group"
                    >
                        <div className="flex items-start justify-between p-4">
                            
                            {/* INFO IZQUIERDA */}
                            <div className="flex gap-4">
                                <div className={`mt-1 w-1.5 h-12 rounded-full ${
                                    parcela.status === 'conforme' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 
                                    parcela.status === 'incidencia' ? 'bg-orange-500 shadow-[0_0_8px_rgba(249,115,22,0.5)]' : 'bg-slate-700'
                                }`} />
                                
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        <h4 className="font-bold text-slate-200 text-sm tracking-wide">{parcela.referencia}</h4>
                                        {parcela.status !== 'pendiente' && (
                                            <span className={`text-[9px] px-1.5 py-0.5 rounded border font-bold uppercase ${
                                                parcela.status === 'conforme' ? 'bg-emerald-500/20 border-emerald-500/30 text-emerald-400' :
                                                'bg-orange-500/20 border-orange-500/30 text-orange-400'
                                            }`}>
                                                {parcela.status}
                                            </span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-3 text-xs text-slate-500 font-medium">
                                        <span className="flex items-center gap-1 bg-white/5 px-2 py-0.5 rounded-md text-slate-400">
                                            <ScanSearch size={10} /> {parcela.uso}
                                        </span>
                                        <span>{parcela.area} ha</span>
                                    </div>
                                </div>
                            </div>

                            {/* ACCIONES DERECHA */}
                            <div className="flex flex-col gap-2">
                                <button 
                                    onClick={() => focusNativeMap(parcela.lat, parcela.lng)}
                                    className="p-2 bg-slate-800 text-slate-300 rounded-xl hover:bg-emerald-600 hover:text-white transition-colors shadow-md border border-white/5"
                                    title="Ver en Mapa"
                                >
                                    <Map size={18} />
                                </button>
                                <button 
                                    onClick={(e) => handleAnalyze(e, parcela)}
                                    disabled={analyzingId === parcela.id}
                                    className={`p-2 rounded-xl border transition-all shadow-md ${
                                        parcela.aiReport 
                                        ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' 
                                        : 'bg-indigo-500/10 border-indigo-500/30 text-indigo-300 hover:bg-indigo-500 hover:text-white'
                                    }`}
                                    title="Analizar con IA"
                                >
                                    {analyzingId === parcela.id ? (
                                        <div className="w-[18px] h-[18px] border-2 border-current border-t-transparent rounded-full animate-spin" />
                                    ) : (
                                        <BrainCircuit size={18} />
                                    )}
                                </button>
                            </div>
                        </div>

                        {/* INFORME IA */}
                        {parcela.aiReport && (
                            <div className="px-4 pb-4 animate-in fade-in slide-in-from-top-1">
                                <div className={`p-3 rounded-xl text-xs leading-relaxed border flex gap-3 ${
                                    parcela.status === 'conforme' 
                                    ? 'bg-emerald-900/20 border-emerald-500/20 text-emerald-200' 
                                    : 'bg-orange-900/20 border-orange-500/20 text-orange-200'
                                }`}>
                                    <div className="mt-0.5 shrink-0">
                                        {parcela.status === 'conforme' ? <CheckCircle2 size={14} /> : <AlertCircle size={14} />}
                                    </div>
                                    <div>
                                        <p className="font-bold opacity-90 mb-0.5">Informe Agronómico:</p>
                                        <p className="opacity-80 italic">"{parcela.aiReport}"</p>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </div>
      )}
    </div>
  );
};
