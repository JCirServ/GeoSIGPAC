
import React, { useEffect, useState, useMemo } from 'react';
import { Expediente, Parcela } from '../types';
import { ArrowLeft, X, ChevronDown, ChevronRight, MapPin, Camera, CheckCircle2, AlertTriangle, Loader2, Database } from 'lucide-react';
import { openNativeCamera, focusNativeMap } from '../services/bridge';
import { fetchParcelaDetails } from '../services/sigpac';
import { useProjectStore } from '../store/useProjectStore';

interface ProjectDetailsProps {
  expediente: Expediente;
  onBack: () => void;
}

export const ProjectDetails: React.FC<ProjectDetailsProps> = ({ expediente, onBack }) => {
  const { updateParcelaData } = useProjectStore();
  
  const hydratedCount = useMemo(() => 
    expediente.parcelas.filter(p => p.isDataLoaded).length
  , [expediente.parcelas]);

  const progressPercent = Math.round((hydratedCount / expediente.parcelas.length) * 100);

  return (
    <div className="h-screen bg-[#07080d] flex flex-col text-white font-sans overflow-hidden">
      {/* HEADER PRINCIPAL */}
      <header className="px-5 py-4 flex justify-between items-center bg-[#13141f]/90 backdrop-blur-xl border-b border-white/5 sticky top-0 z-50 flex-shrink-0">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-white/10 transition-colors">
            <ArrowLeft size={20} className="text-white/80" />
          </button>
          <h1 className="text-white font-bold text-lg tracking-tight">{expediente.titular} (1)</h1>
        </div>
        <button 
          onClick={onBack}
          className="w-10 h-10 flex items-center justify-center bg-white/5 rounded-full text-white/40"
        >
          <X size={20} />
        </button>
      </header>

      {/* PANEL DE PROGRESO */}
      <div className="bg-[#07080d] px-6 py-4 flex flex-col gap-3 flex-shrink-0 border-b border-white/5">
        <div className="flex justify-between items-center">
            <span className="text-gray-500 text-[10px] font-black uppercase tracking-widest">Progreso Fotos</span>
            <span className="text-[#00ff88] text-[10px] font-bold">0%</span>
        </div>
        <div className="h-1 w-full bg-white/5 rounded-full overflow-hidden">
            <div className="h-full bg-emerald-500 w-0 transition-all duration-500" />
        </div>

        <div className="flex justify-between items-center mt-1">
            <div className="flex items-center gap-2">
                <Database size={10} className="text-indigo-400" />
                <span className="text-gray-500 text-[10px] font-black uppercase tracking-widest">Sincronización OGC</span>
            </div>
            <span className="text-indigo-400 text-[10px] font-bold">{progressPercent}%</span>
        </div>
        <div className="h-1 w-full bg-white/5 rounded-full overflow-hidden">
            <div 
                className="h-full bg-indigo-500 shadow-[0_0_10px_rgba(99,102,241,0.5)] transition-all duration-500" 
                style={{ width: `${progressPercent}%` }}
            />
        </div>
      </div>

      {/* LISTA DE RECINTOS */}
      <main className="flex-1 overflow-y-auto px-4 py-4 space-y-4 custom-scrollbar touch-pan-y">
        {expediente.parcelas.map((parcela) => (
          <RecintoItem 
            key={parcela.id} 
            parcela={parcela} 
            expId={expediente.id}
            onUpdate={(data) => updateParcelaData(expediente.id, parcela.id, data)}
          />
        ))}
        <div className="h-24" />
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
        // Pequeño delay artificial para ver la evolución de la carga si es muy rápido
        const data = await fetchParcelaDetails(parcela.referencia);
        onUpdate(data);
        setIsLoading(false);
      };
      load();
    }
  }, [parcela.referencia, parcela.isDataLoaded]);

  return (
    <div className="rounded-[28px] overflow-hidden bg-[#1c202d]/40 border border-white/5 transition-all shadow-xl">
      <div 
        onClick={() => setIsExpanded(!isExpanded)}
        className="p-4 flex items-center justify-between cursor-pointer"
      >
        <div className="flex items-center gap-3">
          <div className="text-gray-600">
            {isExpanded ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
          </div>
          <MapPin size={18} className="text-red-500 fill-red-500/20" />
          <span className="text-white font-bold text-sm tracking-tighter font-mono">
            {parcela.referencia}
          </span>
        </div>
        <div className="flex items-center gap-3">
            <button 
                onClick={(e) => { e.stopPropagation(); openNativeCamera(parcela.id); }}
                className="flex items-center gap-2 px-3 py-1.5 bg-white/5 border border-white/10 rounded-xl text-gray-400 text-[10px] font-black uppercase tracking-tight active:scale-95 transition-transform"
            >
                <Camera size={14} />
                0/2 Fotos
            </button>
        </div>
      </div>

      {isExpanded && (
        <div className="px-4 pb-5 animate-in fade-in slide-in-from-top-1">
          {/* BARRA DE COMPATIBILIDAD */}
          <div className="bg-[#161a26] px-4 py-2.5 flex items-center gap-2 rounded-2xl mb-4 border border-[#00ff88]/10 shadow-inner">
            <CheckCircle2 size={16} className="text-[#00ff88]" />
            <p className="text-[#00ff88] text-[11px] font-bold leading-none">
              Compatible: <span className="text-[#00ff88]/80 font-medium">Cultivo compatible con uso {parcela.uso}.</span>
            </p>
          </div>

          {/* GRID TÉCNICO DOS COLUMNAS */}
          <div className="grid grid-cols-2 gap-px bg-white/5 rounded-[20px] overflow-hidden border border-white/5">
            {/* COLUMNA RECINTO */}
            <div className="bg-[#13141f] p-4">
              <h4 className="text-[#fbbf24] text-[10px] font-black uppercase tracking-[0.1em] mb-4 border-b border-[#fbbf24]/20 pb-1.5">Recinto (SIGPAC)</h4>
              <div className="space-y-4">
                <DataRow label="Municipio" value={parcela.sigpacData?.municipio || (parcela.referencia.split(/[-:]/)[1])} loading={isLoading} />
                <DataRow label="Pendiente" value={parcela.sigpacData?.pendiente !== undefined ? `${parcela.sigpacData.pendiente}` : '---'} loading={isLoading} />
                <DataRow label="Coef Regadio" value="100" />
                <div className="pt-2">
                    <p className="text-[9px] text-gray-500 font-black uppercase tracking-tighter mb-1">Incidencias</p>
                    <ul className="text-[10px] text-gray-400 font-medium space-y-1">
                        <li>• 21 - Catastrado de arroz</li>
                        <li>• 196 - Visto en campo 2022</li>
                    </ul>
                </div>
              </div>
            </div>

            {/* COLUMNA DECLARACIÓN */}
            <div className="bg-[#13141f] p-4">
              <h4 className="text-[#22d3ee] text-[10px] font-black uppercase tracking-[0.1em] mb-4 border-b border-[#22d3ee]/20 pb-1.5">Declaración</h4>
              <div className="space-y-4">
                {!isLoading && !parcela.cultivoData ? (
                    <div className="h-full flex flex-col items-center justify-center py-6 text-center opacity-40">
                        <AlertTriangle size={20} className="text-yellow-500 mb-2" />
                        <p className="text-[9px] text-gray-400 font-black uppercase leading-tight">Sin declaración<br/>activa</p>
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

          <div className="mt-4 flex gap-2">
            <button 
              onClick={() => focusNativeMap(parcela.lat, parcela.lng)}
              className="flex-1 py-3 bg-white/5 border border-white/10 rounded-2xl text-[11px] font-bold text-gray-300 active:scale-95 transition-all"
            >
              LOCALIZAR EN MAPA
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

const DataRow: React.FC<{ label: string, value: string | number, loading?: boolean, highlight?: boolean }> = ({ label, value, loading, highlight }) => (
  <div className="group">
    <p className="text-[9px] text-gray-500 font-black uppercase tracking-tighter mb-0.5 group-hover:text-gray-400 transition-colors">{label}</p>
    {loading ? (
      <div className="h-4 w-20 bg-white/5 animate-pulse rounded-md" />
    ) : (
      <p className={`text-[12px] font-bold leading-tight ${highlight ? 'text-indigo-400' : 'text-slate-100'} font-mono`}>
        {value}
      </p>
    )}
  </div>
);
