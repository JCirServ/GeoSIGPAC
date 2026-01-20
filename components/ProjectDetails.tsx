
import React from 'react';
import { Expediente } from '../types';
import { ArrowLeft, X, ChevronRight, MapPin, Camera, CheckCircle2, AlertTriangle } from 'lucide-react';
import { openNativeCamera } from '../services/bridge';

interface ProjectDetailsProps {
  expediente: Expediente;
  onBack: () => void;
}

export const ProjectDetails: React.FC<ProjectDetailsProps> = ({ expediente, onBack }) => {
  return (
    <div className="h-screen bg-[#07080d] flex flex-col text-white font-sans overflow-hidden">
      {/* HEADER */}
      <header className="px-5 py-6 flex justify-between items-center bg-[#13141f]/80 backdrop-blur-md sticky top-0 z-50 flex-shrink-0">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="text-white/80">
            <ArrowLeft size={24} />
          </button>
          <h1 className="text-white font-bold text-xl">{expediente.titular} (1)</h1>
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
        <span className="text-gray-500 uppercase">Progreso Fotos</span>
        <span className="text-[#00ff88]">0%</span>
      </div>

      {/* LISTA DE RECINTOS */}
      <main className="flex-1 overflow-y-auto px-4 pb-10 space-y-4 custom-scrollbar touch-pan-y">
        {expediente.parcelas.map((parcela, idx) => (
          <div key={parcela.id} className="rounded-[18px] overflow-hidden bg-[#1c202d]">
            <div className="p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <ChevronRight size={18} className="text-gray-500" />
                <MapPin size={18} className="text-red-500 fill-red-500" />
                <span className="text-white font-bold text-sm tracking-tight">
                  46-900-0-0-34-{147 + idx}-1
                </span>
              </div>
              {idx === 2 && (
                <AlertTriangle size={20} className="text-red-400 fill-red-400/20" />
              )}
            </div>

            <div className="px-4 pb-4">
              <button 
                onClick={() => openNativeCamera(parcela.id)}
                className="flex items-center gap-2 px-3 py-1.5 bg-white/5 border border-white/10 rounded-lg text-gray-400 text-[11px] font-bold active:scale-95 transition-transform"
              >
                <Camera size={14} />
                0/2 Fotos
              </button>
            </div>

            {/* BARRA DE COMPATIBILIDAD - Sólo en algunos según la imagen */}
            {idx !== 2 && (
              <div className="bg-[#161a26] px-4 py-2.5 flex items-center gap-2 border-t border-white/5">
                <CheckCircle2 size={16} className="text-[#00ff88]" />
                <p className="text-[#00ff88] text-[11px] font-bold leading-none">
                  Compatible: <span className="text-[#00ff88]/80 font-medium">Cultivo compatible con uso TA.</span>
                </p>
              </div>
            )}
          </div>
        ))}
        {/* Espaciador final para evitar que el último elemento quede tapado */}
        <div className="h-20" />
      </main>
    </div>
  );
};
