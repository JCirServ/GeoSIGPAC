
import React from 'react';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { useProjectStore } from './store/useProjectStore';
import { Folder, X } from 'lucide-react';

const App: React.FC = () => {
  const { expedientes, addExpediente, removeExpediente, loading } = useProjectStore();

  if (loading) return null;

  return (
    <div className="h-screen w-full bg-[#07080d] flex flex-col font-sans overflow-hidden">
      {/* HEADER: Fijo en la parte superior */}
      <header className="px-6 pt-8 pb-4 flex justify-between items-center flex-shrink-0">
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
      
      {/* ÁREA DE SCROLL: flex-1 permite que ocupe todo el espacio y overflow-y-auto habilita el scroll */}
      <main className="flex-1 overflow-y-auto custom-scrollbar">
        <div className="pb-24"> {/* Padding inferior para que la última tarjeta no quede cortada por el menú de Android */}
            {/* Sección de Importación */}
            <KmlUploader onDataParsed={addExpediente} />

            {/* Listado de proyectos */}
            <div className="space-y-1">
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
        </div>
      </main>

      {/* Margen de seguridad inferior para gestos de navegación de Android */}
      <div className="h-2 bg-[#07080d] flex-shrink-0" />
    </div>
  );
};

export default App;
