
import React, { useState, useMemo } from 'react';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { ProjectDetails } from './components/ProjectDetails';
import { useProjectStore } from './store/useProjectStore';
import { auditFullExpediente } from './services/ai';
import { Folder, X, ShieldCheck, BarChart3, Info, BrainCircuit } from 'lucide-react';

const App: React.FC = () => {
  const { expedientes, addExpediente, removeExpediente, loading } = useProjectStore();
  const [selectedExpedienteId, setSelectedExpedienteId] = useState<string | null>(null);
  const [auditReport, setAuditReport] = useState<Record<string, string>>({});
  const [isAuditing, setIsAuditing] = useState<string | null>(null);

  const selectedExpediente = expedientes.find(e => e.id === selectedExpedienteId);

  // Estadísticas globales
  const stats = useMemo(() => {
    const totalArea = expedientes.reduce((acc, exp) => 
      acc + exp.parcelas.reduce((pAcc, p) => pAcc + p.area, 0), 0
    );
    const totalParcelas = expedientes.reduce((acc, exp) => acc + exp.parcelas.length, 0);
    const conformes = expedientes.reduce((acc, exp) => 
      acc + exp.parcelas.filter(p => p.status === 'conforme').length, 0
    );
    return { totalArea, totalParcelas, conformes };
  }, [expedientes]);

  const handleAudit = async (exp: any) => {
    setIsAuditing(exp.id);
    const report = await auditFullExpediente(exp);
    setAuditReport(prev => ({ ...prev, [exp.id]: report }));
    setIsAuditing(null);
  };

  if (loading) return null;

  if (selectedExpediente) {
    return (
      <ProjectDetails 
        expediente={selectedExpediente} 
        onBack={() => setSelectedExpedienteId(null)} 
      />
    );
  }

  return (
    <div className="h-screen bg-[#07080d] flex flex-col font-sans overflow-hidden">
      <header className="p-5 flex justify-between items-center flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="text-emerald-400">
            <ShieldCheck size={26} />
          </div>
          <h1 className="text-white font-bold text-xl tracking-tight uppercase">Manager</h1>
        </div>
        <div className="flex gap-2">
            <div className="bg-white/5 px-3 py-1.5 rounded-full border border-white/10 flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-[10px] font-black text-white/60 tracking-widest uppercase">Live Sync</span>
            </div>
        </div>
      </header>
      
      <main className="flex-1 overflow-y-auto custom-scrollbar touch-pan-y">
        {/* DASHBOARD EJECUTIVO */}
        {expedientes.length > 0 && (
          <section className="px-5 mb-8">
            <div className="glass-panel rounded-[32px] p-6 border border-white/10 grid grid-cols-3 gap-4">
               <StatBox icon={<BarChart3 size={16}/>} label="Area Total" value={`${stats.totalArea.toFixed(1)} ha`} />
               <StatBox icon={<Folder size={16}/>} label="Recintos" value={stats.totalParcelas} />
               <StatBox icon={<ShieldCheck size={16}/>} label="Validados" value={`${stats.conformes}`} highlight />
            </div>
          </section>
        )}

        <KmlUploader onDataParsed={addExpediente} />

        <div className="px-5 mb-4 flex items-center justify-between">
            <h2 className="text-[11px] font-black text-gray-500 uppercase tracking-[0.2em]">Expedientes Cargados</h2>
            <div className="h-px flex-1 bg-white/5 ml-4" />
        </div>

        <div className="space-y-4 pb-24">
          {expedientes.map(exp => (
            <div key={exp.id} className="group">
                <InspectionCard 
                    expediente={exp} 
                    onDelete={removeExpediente}
                    onSelect={() => setSelectedExpedienteId(exp.id)}
                />
                
                {/* BOTÓN Y PANEL DE AUDITORÍA IA */}
                <div className="px-8 -mt-2 mb-4">
                    {!auditReport[exp.id] ? (
                        <button 
                            onClick={() => handleAudit(exp)}
                            disabled={isAuditing === exp.id}
                            className="flex items-center gap-2 text-[10px] font-bold text-emerald-400/60 hover:text-emerald-400 transition-colors uppercase tracking-widest"
                        >
                            {isAuditing === exp.id ? <LoaderSmall /> : <BrainCircuit size={14} />}
                            {isAuditing === exp.id ? 'Auditando...' : 'Solicitar Auditoría IA'}
                        </button>
                    ) : (
                        <div className="bg-emerald-500/5 border border-emerald-500/20 rounded-2xl p-4 animate-in fade-in slide-in-from-top-2">
                            <div className="flex items-center gap-2 mb-1">
                                <BrainCircuit size={12} className="text-emerald-400" />
                                <span className="text-[9px] font-black text-emerald-400 uppercase tracking-tighter">Informe de Riesgos IA</span>
                            </div>
                            <p className="text-[11px] text-gray-300 italic leading-relaxed">"{auditReport[exp.id]}"</p>
                        </div>
                    )}
                </div>
            </div>
          ))}
        </div>

        {expedientes.length === 0 && (
          <div className="px-8 py-20 text-center flex flex-col items-center">
             <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center mb-4">
                <Info className="text-gray-600" />
             </div>
             <p className="text-gray-500 text-sm font-medium">No hay proyectos activos en esta sesión.</p>
          </div>
        )}
      </main>
    </div>
  );
};

const StatBox = ({ icon, label, value, highlight = false }: any) => (
    <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2 text-gray-500">
            {icon}
            <span className="text-[9px] font-black uppercase tracking-tighter">{label}</span>
        </div>
        <span className={`text-sm font-bold ${highlight ? 'text-emerald-400' : 'text-white'}`}>{value}</span>
    </div>
);

const LoaderSmall = () => (
    <div className="w-3 h-3 border-2 border-emerald-400 border-t-transparent rounded-full animate-spin" />
);

export default App;
