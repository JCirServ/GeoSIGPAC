
import React, { useState } from 'react';
import { Expediente, Parcela } from '../types';
import { ArrowLeft, X, ChevronRight, MapPin, Camera, CheckCircle2, AlertTriangle, ChevronDown } from 'lucide-react';
import { openNativeCamera, focusNativeMap } from '../services/bridge';

interface ProjectDetailsProps {
  expediente: Expediente;
  onBack: () => void;
}

const ParcelaItem: React.FC<{ parcela: Parcela }> = ({ parcela }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-[18px] overflow-hidden bg-[#1c202d] transition-all">
      <div 
        className="p-4 flex items-center justify-between cursor-pointer active:bg-white/5"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center gap-3">
          <ChevronDown 
            size={18} 
            className={`text-gray-500 transition-transform ${expanded ? 'rotate-180' : ''}`} 
          />
          <MapPin size={18} className="text-red-500 fill-red-500" />
          <span className="text-white font-bold text-sm tracking-tight">
            {parcela.referencia}
          </span>
        </div>
        {parcela.status === 'incidencia' && (
          <AlertTriangle size={20} className="text-red-400 fill-red-400/20" />
        )}
      </div>

      <div className="px-4 pb-4 flex gap-3">
        <button 
          onClick={(e) => { e.stopPropagation(); openNativeCamera(parcela.id); }}
          className="flex items-center gap-2 px-3 py-1.5 bg-white/5 border border-white/10 rounded-lg text-gray-400 text-[11px] font-bold active:scale-95 transition-transform"
        >
          <Camera size={14} />
          0/2 Fotos
        </button>
        <button 
          onClick={(e) => { e.stopPropagation(); focusNativeMap(parcela.lat, parcela.lng); }}
          className="flex items-center gap-2 px-3 py-1.5 bg-white/5 border border-white/10 rounded-lg text-indigo-400 text-[11px] font-bold active:scale-95 transition-transform"
        >
          Localizar
        </button>
      </div>

      {expanded && (
        <div className="bg-black/20 p-4 border-t border-white/5 grid grid-cols-2 gap-2">
          {Object.entries(parcela.metadata).map(([key, value]) => (
            <div key={key} className="flex flex-col">
              <span className="text-[10px] text-gray-500 uppercase font-bold">{key.replace(/_/g, ' ')}</span>
              <span className="text-xs text-gray-300 truncate" title={value}>{value || '-'}</span>
            </div>
          ))}
        </div>
      )}

      {parcela.uso && !expanded && (
        <div className="bg-[#161a26] px-4 py-2.5 flex items-center gap-2 border-t border-white/5">
          <CheckCircle2 size={16} className="text-[#00ff88]" />
          <p className="text-[#00ff88] text-[11px] font-bold leading-none">
            Uso: <span className="text-[#00ff88]/80 font-medium">{parcela.uso}</span>
            {parcela.area > 0 && <span className="ml-2">• {parcela.area.toFixed(4)} ha</span>}
          </p>
        </div>
      )}
    </div>
  );
};

export const ProjectDetails: React.FC<ProjectDetailsProps> = ({ expediente, onBack }) => {
  return (
    <div className="h-screen bg-[#07080d] flex flex-col text-white font-sans overflow-hidden">
      {/* HEADER */}
      <header className="px-5 py-6 flex justify-between items-center bg-[#13141f]/80 backdrop-blur-md sticky top-0 z-50 flex-shrink-0">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="text-white/80">
            <ArrowLeft size={24} />
          </button>
          <div className="flex flex-col">
            <h1 className="text-white font-bold text-lg leading-none">{expediente.titular}</h1>
            <span className="text-[10px] text-gray-500 font-bold mt-1 uppercase tracking-wider">
               {expediente.parcelas.length} Recintos importados
            </span>
          </div>
        </div>
        <button 
          onClick={onBack}
          className="w-10 h-10 flex items-center justify-center bg-white/10 rounded-full text-white/70"
        >
          <X size={20} />
        </button>
      </header>

      {/* PROGRESO FOTOS */}
      <div className="px-6 py-4 flex justify-between items-center text-[11px] font-bold tracking-wider flex-shrink-0">
        <span className="text-gray-500 uppercase">Progreso Inspección</span>
        <span className="text-[#00ff88]">0%</span>
      </div>

      {/* LISTA DE RECINTOS */}
      <main className="flex-1 overflow-y-auto px-4 pb-10 space-y-4 custom-scrollbar touch-pan-y">
        {expediente.parcelas.map((parcela) => (
          <ParcelaItem key={parcela.id} parcela={parcela} />
        ))}
        <div className="h-24" />
      </main>
    </div>
  );
};
