
import React, { useState } from 'react';
import { Map, Camera, Calendar, CheckCircle, Clock, AlertCircle, Sparkles, BrainCircuit } from 'lucide-react';
import { Project } from '../types';
import { focusNativeMap, openNativeCamera } from '../services/bridge';
import { analyzeProjectWithAI } from '../services/ai';

interface ProjectCardProps {
  project: Project;
}

export const ProjectCard: React.FC<ProjectCardProps> = ({ project }) => {
  const [aiAnalysis, setAiAnalysis] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);

  const getStatusColor = (status: Project['status']) => {
    switch (status) {
      case 'completed': return 'bg-green-100 text-green-800 border-green-200';
      case 'verified': return 'bg-blue-100 text-blue-800 border-blue-200';
      default: return 'bg-yellow-100 text-yellow-800 border-yellow-200';
    }
  };

  const handleAiAnalyze = async () => {
    setIsAnalyzing(true);
    const result = await analyzeProjectWithAI(project);
    setAiAnalysis(result);
    setIsAnalyzing(false);
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-4 transition-all">
      <div className="relative h-32 w-full bg-gray-200">
        {project.imageUrl ? (
          <img src={project.imageUrl} alt={project.name} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center text-gray-400 bg-gray-100">
            <Camera size={32} className="mb-2 opacity-50" />
            <span className="text-xs font-medium">Sin imagen</span>
          </div>
        )}
        
        <div className={`absolute top-2 right-2 px-2 py-1 rounded-full text-xs font-bold uppercase flex items-center gap-1 border ${getStatusColor(project.status)}`}>
          {project.status === 'pending' ? <Clock size={12}/> : <CheckCircle size={12}/>}
          {project.status === 'pending' ? 'Pendiente' : project.status === 'verified' ? 'Verificado' : 'Completado'}
        </div>
      </div>

      <div className="p-4">
        <h3 className="font-bold text-gray-800 text-lg mb-1">{project.name}</h3>
        <p className="text-gray-500 text-sm mb-4 line-clamp-2">{project.description}</p>
        
        {/* Sección de Análisis IA */}
        {aiAnalysis && (
          <div className="mb-4 p-3 bg-gradient-to-r from-green-50 to-emerald-50 border border-green-100 rounded-lg animate-in fade-in slide-in-from-top-2">
            <div className="flex items-center gap-2 mb-1 text-emerald-700 font-bold text-xs uppercase tracking-wider">
              <Sparkles size={14} />
              Recomendación IA
            </div>
            <p className="text-sm text-gray-700 italic">"{aiAnalysis}"</p>
          </div>
        )}

        <div className="flex items-center text-xs text-gray-400 mb-4 gap-4">
           <div className="flex items-center gap-1"><Calendar size={12} /><span>{project.date}</span></div>
           <div className="flex items-center gap-1"><AlertCircle size={12} /><span>ID: {project.id}</span></div>
        </div>

        <div className="flex flex-wrap gap-2">
          <button 
            onClick={() => focusNativeMap(project.lat, project.lng)}
            className="flex-1 min-w-[100px] flex items-center justify-center gap-2 py-2 px-3 bg-slate-100 text-slate-700 rounded-lg font-medium text-sm active:scale-95 transition-transform"
          >
            <Map size={16} /> Localizar
          </button>
          
          <button 
            onClick={() => openNativeCamera(project.id)}
            className="flex-1 min-w-[100px] flex items-center justify-center gap-2 py-2 px-3 bg-primary text-white rounded-lg font-medium text-sm active:scale-95 transition-transform"
          >
            <Camera size={16} /> Foto
          </button>

          <button 
            onClick={handleAiAnalyze}
            disabled={isAnalyzing}
            className={`w-full flex items-center justify-center gap-2 py-2 px-3 ${isAnalyzing ? 'bg-slate-200' : 'bg-emerald-600'} text-white rounded-lg font-bold text-sm transition-all shadow-sm active:scale-95`}
          >
            {isAnalyzing ? (
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
            ) : (
              <BrainCircuit size={18} />
            )}
            {isAnalyzing ? 'Consultando Gemini...' : 'Analizar Parcela con IA'}
          </button>
        </div>
      </div>
    </div>
  );
};
