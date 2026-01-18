import React from 'react';
import { Header } from './components/Header';
import { ProjectCard } from './components/ProjectCard';
import { useProjectStore } from './store/useProjectStore';
import { Plus } from 'lucide-react';
import { showNativeToast } from './services/bridge';

const App: React.FC = () => {
  const { projects } = useProjectStore();

  const handleFabClick = () => {
    showNativeToast("Crear nuevo proyecto no implementado en demo.");
  };

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      <Header />
      
      <main className="p-4 max-w-xl mx-auto">
        <div className="mb-4">
          <h2 className="text-xl font-bold text-gray-800">Proyectos Activos</h2>
          <p className="text-sm text-gray-500">Sincronizados con SIGPAC</p>
        </div>

        <div className="flex flex-col gap-2">
          {projects.map(project => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      </main>

      <div className="fixed bottom-6 right-6">
        <button 
          onClick={handleFabClick}
          className="w-14 h-14 bg-tertiary text-white rounded-2xl shadow-lg flex items-center justify-center hover:bg-tertiary/90 transition-all active:scale-95"
          aria-label="Nuevo Proyecto"
        >
          <Plus size={28} />
        </button>
      </div>

      <div className="text-center p-8 text-xs text-gray-400">
        <p>GeoSIGPAC Hybrid v1.0.0</p>
        <p>Connected to Native Interface: {window.Android ? 'Yes' : 'No (Dev Mode)'}</p>
      </div>
    </div>
  );
};

export default App;