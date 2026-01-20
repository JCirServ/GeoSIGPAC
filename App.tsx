
import React from 'react';
import { InspectionCard } from './components/InspectionCard';
import { KmlUploader } from './components/KmlUploader';
import { useProjectStore } from './store/useProjectStore';
import { Folder, X } from 'lucide-react';

const App: React.FC = () => {
  const { expedientes, addExpediente, removeExpediente, loading } = useProjectStore();

  if (loading) return null;

  return (
    <div className="min-h-screen bg-[#0a0b10] flex flex-col">
      {/* HEADER ESTILO CAPTURA */}
      <header className="p-5 flex justify-between items-center mb-4">
        <div className="flex items-center gap-3">
          <div className="text-yellow-400">
            <Folder size={24} fill="currentColor" />
          </div>
          <h1 className="text-white font-bold text-xl tracking-tight">Proyectos</h1>
        </div>
        <button className="w-10 h-10 flex items-center justify-center bg-white/5 rounded-full text-white/60 hover:bg-white/10 transition-colors">
          <X size={20} />
        </button>
      </header>
      
      <main className="flex-1 overflow-y-auto custom-scrollbar">
        {/* Importador Punteado */}
        <KmlUploader onDataParsed={addExpediente} />

        {/* Lista de Tarjetas Estilo Neon */}
        <div className="space-y-1">
          {expedientes.map(exp => (
            <InspectionCard 
              key={exp.id} 
              expediente={exp} 
              onDelete={removeExpediente} 
            />
          ))}
        </div>

        {expedientes.length === 0 && (
          <div className="px-8 py-10 text-center">
             <p className="text-gray-600 text-sm">No hay proyectos cargados.</p>
          </div>
        )}
      </main>

      {/* Footer oculto o minimalista */}
      <div className="h-20" />
    </div>
  );
};

export default App;
