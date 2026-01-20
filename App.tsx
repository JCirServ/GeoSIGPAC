import React from 'react';
import { useProjectStore } from './store/useProjectStore';
import { KmlUploader } from './components/KmlUploader';
import { InspectionCard } from './components/InspectionCard';
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
      {/* HEADER: Fondo oscuro, icono carpeta amarilla y botón cerrar */}
      <header className="px-6 pt-10 pb-6 flex justify-between items-center bg-[#07080d] z-20">
        <div className="flex items-center gap-3">
          <div className="text-yellow-400">
            <Folder size={28} fill="currentColor" />
          </div>
          <h1 className="text-white font-bold text-2xl tracking-tight">Proyectos</h1>
        </div>
        <button 
          className="w-11 h-11 flex items-center justify-center bg-white/10 rounded-full text-white/70 hover:bg-white/20 active:scale-90 transition-all"
        >
          <X size={22} />
        </button>
      </header>

      {/* ÁREA DE CONTENIDO SCROLLABLE */}
      <main className="flex-1 overflow-y-auto custom-scrollbar px-5">
        <div className="pb-32 space-y-6">
          {/* Componente de Carga de KML */}
          <KmlUploader onDataParsed={addExpediente} />

          {/* Listado de expedientes */}
          <div className="space-y-4">
            {expedientes.map((exp) => (
              <InspectionCard 
                key={exp.id} 
                expediente={exp} 
                onDelete={removeExpediente} 
              />
            ))}
            
            {expedientes.length === 0 && (
              <div className="py-20 text-center flex flex-col items-center opacity-20">
                <Folder size={48} className="mb-4" />
                <p className="text-sm font-medium">No hay proyectos importados</p>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Margen de seguridad inferior para el nav de Android */}
      <div className="h-4 bg-[#07080d] flex-shrink-0" />
    </div>
  );
};

export default App;