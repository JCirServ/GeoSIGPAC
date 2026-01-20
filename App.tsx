
import React from 'react';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { useProjectStore } from './store/useProjectStore';
import { Folder, X } from 'lucide-react';

const App: React.FC = () => {
  const { expedientes, addExpediente, removeExpediente, loading } = useProjectStore();

  if (loading) return null;

  return (
    <div className="min-h-screen bg-[#07080d] flex flex-col font-sans selection:bg-indigo-500/30">
      {/* HEADER: Proyectos + Carpeta Amarilla + X */}
      <header className="px-6 py-5 flex justify-between items-center">
        <div className="flex items-center gap-3">
          <div className="text-yellow-400">
            <Folder size={24} fill="currentColor" />
          </div>
          <h1 className="text-white font-bold text-xl tracking-wide">Proyectos</h1>
        </div>
        <button className="w-10 h-10 flex items-center justify-center bg-white/5 rounded-full text-white/70 hover:bg-white/10 active:scale-90 transition-all">
          <X size={20} />
        </button>
      </header>
      
      <main className="flex-1 overflow-y-auto custom-scrollbar pt-2">
        {/* Sección de Importación con caja punteada */}
        <KmlUploader onDataParsed={addExpediente} />

        {/* Listado de proyectos (Tarjetas Neón) */}
        <div className="space-y-0.5 pb-24">
          {expedientes.map(exp => (
            <InspectionCard 
              key={exp.id} 
              expediente={exp} 
              onDelete={removeExpediente} 
            />
          ))}
          
          {expedientes.length === 0 && (
            <div className="px-10 py-20 text-center opacity-30">
               <p className="text-white text-sm">No hay proyectos importados</p>
            </div>
          )}
        </div>
      </main>

      {/* Margen inferior para navegación nativa */}
      <div className="h-4" />
    </div>
  );
};

export default App;
