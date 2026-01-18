import React from 'react';
import { Map, Camera, Calendar, CheckCircle, Clock, AlertCircle } from 'lucide-react';
import { Project } from '../types';
import { focusNativeMap, openNativeCamera } from '../services/bridge';

interface ProjectCardProps {
  project: Project;
}

export const ProjectCard: React.FC<ProjectCardProps> = ({ project }) => {
  const getStatusColor = (status: Project['status']) => {
    switch (status) {
      case 'completed': return 'bg-green-100 text-green-800 border-green-200';
      case 'verified': return 'bg-blue-100 text-blue-800 border-blue-200';
      default: return 'bg-yellow-100 text-yellow-800 border-yellow-200';
    }
  };

  const getStatusIcon = (status: Project['status']) => {
    switch (status) {
      case 'completed': return <CheckCircle size={14} />;
      case 'verified': return <CheckCircle size={14} />; // distinct icon if needed
      default: return <Clock size={14} />;
    }
  };

  const handleMapClick = (e: React.MouseEvent) => {
    e.preventDefault(); // Prevent accidental navigation if we add links later
    focusNativeMap(project.lat, project.lng);
  };

  const handleCameraClick = (e: React.MouseEvent) => {
    e.preventDefault();
    openNativeCamera(project.id);
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-4 transition-transform active:scale-[0.99] duration-150">
      <div className="relative h-32 w-full bg-gray-200">
        {project.imageUrl ? (
          <img 
            src={project.imageUrl} 
            alt={project.name} 
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center text-gray-400 bg-gray-100">
            <Camera size={32} className="mb-2 opacity-50" />
            <span className="text-xs font-medium">Sin imagen</span>
          </div>
        )}
        
        <div className={`absolute top-2 right-2 px-2 py-1 rounded-full text-xs font-bold uppercase flex items-center gap-1 border ${getStatusColor(project.status)}`}>
          {getStatusIcon(project.status)}
          {project.status === 'pending' ? 'Pendiente' : project.status === 'verified' ? 'Verificado' : 'Completado'}
        </div>
      </div>

      <div className="p-4">
        <h3 className="font-bold text-gray-800 text-lg mb-1">{project.name}</h3>
        <p className="text-gray-500 text-sm mb-4 line-clamp-2">{project.description}</p>
        
        <div className="flex items-center text-xs text-gray-400 mb-4 gap-4">
           <div className="flex items-center gap-1">
             <Calendar size={12} />
             <span>{project.date}</span>
           </div>
           <div className="flex items-center gap-1">
             <AlertCircle size={12} />
             <span>ID: {project.id}</span>
           </div>
        </div>

        <div className="flex gap-2">
          <button 
            onClick={handleMapClick}
            className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-surface text-primary rounded-lg font-medium text-sm hover:bg-green-100 transition-colors"
          >
            <Map size={18} />
            Localizar
          </button>
          
          <button 
            onClick={handleCameraClick}
            className="flex-1 flex items-center justify-center gap-2 py-2.5 px-4 bg-primary text-white rounded-lg font-medium text-sm shadow-md hover:bg-primary/90 transition-colors"
          >
            <Camera size={18} />
            Foto
          </button>
        </div>
      </div>
    </div>
  );
};