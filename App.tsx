
import React from 'react';
import { Header } from './components/Header';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { AIAssistant } from './components/AIAssistant';
import { useProjectStore } from './store/useProjectStore';
import { LayoutGrid, PieChart, Activity, Info } from 'lucide-react';

const App: React.FC = () => {
  const { inspections, addInspection, removeInspection, removeParcela, updateInspectionStatus, loading } = useProjectStore();

  const totalArea = inspections.reduce((acc, ins) => 
    acc + ins.parcelas.reduce((pAcc, p) => pAcc + p.area, 0), 0
  );
  const totalRecintos = inspections.reduce((acc, ins) => acc + ins.parcelas.length, 0);

  if (loading) return null;

  return (
    <div className="min-h-screen pb-24">
      <Header />
      
      <main className="px-5 py-6 max-w-2xl mx-auto">
        {/* Dashboard Stats */}
        <section className="grid grid-cols-2 gap-4 mb-8">
          <div className="glass-panel p-4 rounded-3xl border border-white/5">
            <div className="flex items-center gap-2 text-emerald-400 mb-2">
              <Activity size={16} />
              <span className="text-[10px] font-bold uppercase tracking-widest">Área Total</span>
            </div>
            <div className="text-3xl font-black text-slate-100">{totalArea.toFixed(1)}</div>
            <div className="text-[10px] text-slate-500 font-bold uppercase">Hectáreas Controladas</div>
          </div>
          <div className="glass-panel p-4 rounded-3xl border border-white/5">
            <div className="flex items-center gap-2 text-blue-400 mb-2">
              <LayoutGrid size={16} />
              <span className="text-[10px] font-bold uppercase tracking-widest">Recintos</span>
            </div>
            <div className="text-3xl font-black text-slate-100">{totalRecintos}</div>
            <div className="text-[10px] text-slate-500 font-bold uppercase">En {inspections.length} Expedientes</div>
          </div>
        </section>

        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-black text-slate-100 uppercase tracking-tighter">Expedientes Activos</h2>
          <PieChart size={20} className="text-slate-500" />
        </div>

        {/* Uploader Section */}
        <KmlUploader onDataParsed={addInspection} />

        {inspections.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-600 bg-white/5 rounded-3xl border border-dashed border-white/10">
            <Info size={40} className="mb-4 opacity-20" />
            <p className="font-bold text-sm">Carga un archivo KML para comenzar</p>
            <p className="text-xs opacity-60">Gestiona tus inspecciones de campo aquí</p>
          </div>
        ) : (
          <div className="space-y-2">
            {inspections.map(inspection => (
              <InspectionCard 
                key={inspection.id} 
                inspection={inspection} 
                onDelete={removeInspection}
                onRemoveParcela={removeParcela}
                onStatusChange={updateInspectionStatus}
              />
            ))}
          </div>
        )}
      </main>

      <AIAssistant />

      <footer className="text-center py-10 px-6 mt-6">
        <div className="inline-flex items-center gap-2 px-3 py-1 bg-white/5 rounded-full text-[9px] font-black text-slate-500 uppercase tracking-[0.2em] mb-3 border border-white/5">
            <div className={`w-1.5 h-1.5 rounded-full ${window.Android ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-amber-500'}`}></div>
            {window.Android ? 'Native Bridge Connected' : 'Simulated Environment'}
        </div>
        <p className="text-[9px] text-slate-600 font-bold uppercase tracking-tighter">GeoSIGPAC Field Pro v4.2 • Precision Agriculture</p>
      </footer>
    </div>
  );
};

export default App;
