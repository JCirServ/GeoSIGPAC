
import React from 'react';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { useProjectStore } from './store/useProjectStore';
import { Folder, X } from 'lucide-react';

const App: React.FC = () => {
  const { expedientes, addExpediente, removeExpediente, loading } = useProjectStore();

  if (loading) {
    return (
      <div className="h-screen w-full bg-[#07080d] flex items-center justify-center">
        <div className="w-8 h-8 border-2 border-[#5c60f5] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="h-screen w-full bg-[#07080d] flex flex-col font-sans overflow-hidden text-white">
      {/* HEADER ESTILO NATIVO */}
      <header className="px-5 pt-8 pb-5 flex justify-between items-center flex-shrink-0 bg-[#07080d]">
        <div className="flex items-center gap-3">
          <div className="text-yellow-400">
            <Folder size={24} fill="currentColor" />
          </div>
          <h1 className="text-white font-bold text-xl tracking-tight">Proyectos</h1>
        </div>
        <button 
          onClick={() => window.Android?.showToast("Cerrando...")}
          className="w-10 h-10 flex items-center justify-center bg-white/10 rounded-full text-white/80 hover:bg-white/20 active:scale-90 transition-all"
        >
          <X size={20} />
        </button>
      </header>
      
      {/* CONTENEDOR DE SCROLL */}
      <main className="flex-1 overflow-y-auto custom-scrollbar">
        <div className="pb-32">
            {/* Importación: Exactamente como en la imagen */}
            <KmlUploader onDataParsed={addExpediente} />

            {/* Lista de Proyectos con el espaciado y bordes neón */}
            <div className="px-4 space-y-4">
              {expedientes.map(exp => (
                <InspectionCard 
                  key={exp.id} 
                  expediente={exp} 
                  onDelete={removeExpediente} 
                />
              ))}
              
              {expedientes.length === 0 && (
                <div className="py-20 text-center flex flex-col items-center opacity-20">
                   <Folder size={48} className="mb-4" />
                   <p className="text-sm font-medium">No hay proyectos activos</p>
                </div>
              )}
            </div>
        </div>
      </main>

      {/* Gradiente inferior decorativo */}
      <div className="h-4 bg-gradient-to-t from-[#07080d] to-transparent pointer-events-none absolute bottom-0 left-0 right-0 z-10" />
    </div>
  );
};

export default App;
