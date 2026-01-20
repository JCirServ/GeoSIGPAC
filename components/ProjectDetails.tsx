
import React, { useEffect, useState } from 'react';
import { Expediente, Parcela } from '../types';
import { ArrowLeft, X, ChevronDown, ChevronRight, MapPin, Camera, CheckCircle2, AlertTriangle, Loader2 } from 'lucide-react';
import { openNativeCamera } from '../services/bridge';
import { fetchParcelaDetails } from '../services/sigpac';
import { useProjectStore } from '../store/useProjectStore';

interface ProjectDetailsProps {
  expediente: Expediente;
  onBack: () => void;
}

export const ProjectDetails: React.FC<ProjectDetailsProps> = ({ expediente, onBack }) => {
  const { updateParcelaData } = useProjectStore();

  return (
    <div className="h-screen bg-[#07080d] flex flex-col text-white font-sans overflow-hidden">
      {/* HEADER */}
      <header className="px-5 py-6 flex justify-between items-center bg-[#13141f]/80 backdrop-blur-md sticky top-0 z-50 flex-shrink-0">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="text-white/80">
            <ArrowLeft size={24} />
          </button>
          <h1 className="text-white font-bold text-xl">{expediente.titular} ({expediente.parcelas.length})</h1>
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
        {expediente.parcelas.map((parcela) => (
          <RecintoItem 
            key={parcela.id} 
            parcela={parcela} 
            expId={expediente.id}
            onUpdate={(data) => updateParcelaData(expediente.id, parcela.id, data)}
          />
        ))}
        <div className="h-20" />
      </main>
    </div>
  );
};

const RecintoItem: React.FC<{ parcela: Parcela, expId: string, onUpdate: (data: any) => void }> = ({ parcela, onUpdate }) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const [isLoading, setIsLoading] = useState(!parcela.isDataLoaded);

  useEffect(() => {
    if (!parcela.isDataLoaded) {
      const load = async () => {
        setIsLoading(true);
        const data = await fetchParcelaDetails(parcela.referencia);
        onUpdate(data);
        setIsLoading(false);
      };
      load();
    }
  }, [parcela.referencia, parcela.isDataLoaded]);

  return (
    <div className="rounded-[24px] overflow-hidden bg-[#1c202d]/60 border border-white/5 transition-all">
      <div 
        onClick={() => setIsExpanded(!isExpanded)}
        className="p-4 flex items-center justify-between cursor-pointer"
      >
        <div className="flex items-center gap-3">
          {isExpanded ? <ChevronDown size={18} className="text-gray-500" /> : <ChevronRight size={18} className="text-gray-500" />}
          <MapPin size={18} className="text-red-500 fill-red-500" />
          <span className="text-white font-bold text-sm tracking-tight mono">
            {parcela.referencia}
          </span>
        </div>
        <div className="flex items-center gap-3">
            <button 
                onClick={(e) => { e.stopPropagation(); openNativeCamera(parcela.id); }}
                className="flex items-center gap-2 px-3 py-1.5 bg-white/5 border border-white/10 rounded-lg text-gray-400 text-[11px] font-bold active:scale-95 transition-transform"
            >
                <Camera size={14} />
                0/2 Fotos
            </button>
        </div>
      </div>

      {isExpanded && (
        <div className="px-4 pb-4 animate-in fade-in slide-in-from-top-1">
          {/* BARRA DE COMPATIBILIDAD IA (SIMULADA O REAL) */}
          <div className="bg-[#161a26] px-4 py-2.5 flex items-center gap-2 rounded-xl mb-4 border border-[#00ff88]/10">
            <CheckCircle2 size={16} className="text-[#00ff88]" />
            <p className="text-[#00ff88] text-[11px] font-bold leading-none">
              Compatible: <span className="text-[#00ff88]/80 font-medium">Cultivo compatible con uso {parcela.uso}.</span>
            </p>
          </div>

          {/* GRID TÉCNICO */}
          <div className="grid grid-cols-2 gap-px bg-white/5 rounded-xl overflow-hidden border border-white/5">
            {/* COLUMNA RECINTO */}
            <div className="bg-[#13141f] p-4">
              <h4 className="text-[#fbbf24] text-[10px] font-black uppercase tracking-widest mb-4 border-b border-[#fbbf24]/20 pb-1">Recinto (SIGPAC)</h4>
              <div className="space-y-4">
                <DataRow label="Municipio" value={parcela.sigpacData?.municipio || '---'} loading={isLoading} />
                <DataRow label="Pendiente" value={parcela.sigpacData?.pendiente ? `${parcela.sigpacData.pendiente}%` : '---'} loading={isLoading} />
                <DataRow label="Altitud" value={parcela.sigpacData?.altitud ? `${parcela.sigpacData.altitud}m` : '---'} loading={isLoading} />
                <DataRow label="Uso" value={parcela.uso} />
              </div>
            </div>

            {/* COLUMNA DECLARACIÓN */}
            <div className="bg-[#13141f] p-4">
              <h4 className="text-[#22d3ee] text-[10px] font-black uppercase tracking-widest mb-4 border-b border-[#22d3ee]/20 pb-1">Declaración</h4>
              <div className="space-y-4">
                {!isLoading && !parcela.cultivoData ? (
                    <div className="h-full flex flex-col items-center justify-center py-4 text-center">
                        <AlertTriangle size={24} className="text-yellow-500/50 mb-2" />
                        <p className="text-[10px] text-gray-500 font-bold uppercase">Sin declaración activa</p>
                    </div>
                ) : (
                    <>
                        <DataRow label="Exp Num" value={parcela.cultivoData?.expNum || '---'} loading={isLoading} highlight />
                        <DataRow label="Producto" value={parcela.cultivoData?.producto || '---'} loading={isLoading} />
                        <DataRow label="Sist Exp" value={parcela.cultivoData?.sistExp || '---'} loading={isLoading} />
                        <DataRow label="Ayuda SOL" value={parcela.cultivoData?.ayudaSol || '---'} loading={isLoading} />
                    </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const DataRow: React.FC<{ label: string, value: string | number, loading?: boolean, highlight?: boolean }> = ({ label, value, loading, highlight }) => (
  <div>
    <p className="text-[9px] text-gray-500 font-black uppercase tracking-tighter mb-0.5">{label}</p>
    {loading ? (
      <div className="h-4 w-16 bg-white/5 animate-pulse rounded" />
    ) : (
      <p className={`text-[12px] font-bold leading-tight ${highlight ? 'text-indigo-300' : 'text-slate-200'} mono`}>
        {value}
      </p>
    )}
  </div>
);
