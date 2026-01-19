
import React from 'react';
import { Header } from './components/Header';
import { ProjectCard } from './components/ProjectCard';
import { AIAssistant } from './components/AIAssistant';
import { useProjectStore } from './store/useProjectStore';
import { Plus, LayoutGrid, Info } from 'lucide-react';
import { showNativeToast } from './services/bridge';

const App: React.FC = () => {
  const { projects } = useProjectStore();

  const handleFabClick = () => {
    showNativeToast("Añadir nueva parcela: Función habilitada en versión Pro.");
  };

  return (
    <div className="min-h-screen bg-slate-50 pb-32">
      <Header />
      
      <main className="px-4 py-6 max-w-2xl mx-auto">
        <div className="flex items-end justify-between mb-6">
          <div>
            <h2 className="text-2xl font-black text-slate-900 tracking-tight">Expedientes</h2>
            <p className="text-sm text-slate-500 font-medium">Gestionando {projects.length} parcelas activas</p>
          </div>
          <div className="bg-white p-2 rounded-lg shadow-sm border border-slate-200">
            <LayoutGrid size={20} className="text-primary" />
          </div>
        </div>

        {projects.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-slate-400">
            <Info size={48} className="mb-4 opacity-20" />
            <p className="font-medium">No hay datos sincronizados</p>
          </div>
        ) : (
          <div className="space-y-4">
            {projects.map(project => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        )}
      </main>

      {/* Floating Action Button Principal */}
      <div className="fixed bottom-24 right-6 z-50">
        <button 
          onClick={handleFabClick}
          className="w-16 h-16 bg-primary text-white rounded-2xl shadow-2xl shadow-primary/40 flex items-center justify-center hover:scale-105 transition-all active:scale-95 group"
          aria-label="Nuevo Proyecto"
        >
          <Plus size={32} className="group-hover:rotate-90 transition-transform duration-300" />
        </button>
      </div>

      {/* Nuevo Asistente IA */}
      <AIAssistant />

      <footer className="text-center py-10 px-6 border-t border-slate-200 mt-10">
        <div className="inline-flex items-center gap-2 px-3 py-1 bg-slate-200 rounded-full text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-2">
            <div className={`w-2 h-2 rounded-full ${window.Android ? 'bg-green-500 animate-pulse' : 'bg-orange-500'}`}></div>
            {window.Android ? 'Conexión Nativa Activa' : 'Modo Simulación'}
        </div>
        <p className="text-[10px] text-slate-400 font-medium uppercase tracking-tighter">GeoSIGPAC AI Engine v3.0 • Powered by Gemini</p>
      </footer>
    </div>
  );
};

export default App;
