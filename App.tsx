
import React from 'react';
import { Header } from './components/Header';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { useProjectStore } from './store/useProjectStore';
import { FolderOpen } from 'lucide-react';

const App: React.FC = () => {
  const { expedientes, addExpediente, removeExpediente, loading } = useProjectStore();

  if (loading) return null;

  return (
    <div className="min-h-screen pb-24 bg-bg-dark">
      <Header />
      
      <main className="px-4 py-6 max-w-lg mx-auto">
        
        {/* Sección Importador */}
        <KmlUploader onDataParsed={addExpediente} />

        {/* Título Sección */}
        <div className="flex items-center gap-2 mb-4 px-1">
            <FolderOpen size={18} className="text-emerald-500" />
            <h2 className="text-sm font-black text-slate-300 uppercase tracking-widest">Mis Expedientes</h2>
            <span className="ml-auto bg-slate-800 text-slate-400 text-[10px] font-bold px-2 py-0.5 rounded-full">
                {expedientes.length}
            </span>
        </div>

        {/* Lista Expedientes */}
        {expedientes.length === 0 ? (
          <div className="text-center py-12 px-6 rounded-3xl border border-dashed border-slate-800 bg-slate-900/50">
            <p className="text-slate-500 font-medium text-sm">No hay expedientes activos.</p>
            <p className="text-slate-600 text-xs mt-1">Importa un archivo KMZ para comenzar.</p>
          </div>
        ) : (
          <div className="space-y-4 animate-in fade-in slide-in-from-bottom-4 duration-500">
            {expedientes.map(exp => (
              <InspectionCard 
                key={exp.id} 
                expediente={exp} 
                onDelete={removeExpediente} 
              />
            ))}
          </div>
        )}
      </main>

      {/* Footer Indicador */}
      <footer className="fixed bottom-0 left-0 w-full p-4 bg-bg-dark/80 backdrop-blur-md border-t border-white/5 text-center">
        <p className="text-[9px] text-slate-600 font-bold uppercase tracking-widest">
            GeoSIGPAC Field Manager • v3.0
        </p>
      </footer>
    </div>
  );
};

export default App;
