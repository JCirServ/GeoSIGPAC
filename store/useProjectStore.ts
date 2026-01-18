import { useState, useCallback, useEffect } from 'react';
import { Project } from '../types';
import { getNativeProjects } from '../services/bridge';

const INITIAL_MOCK_PROJECTS: Project[] = [
  {
    id: 'p1',
    name: 'Parcela 102 - Olivos',
    description: 'Revisión de sistema de riego y conteo de árboles.',
    lat: 37.3891,
    lng: -5.9845,
    date: '2023-10-25',
    status: 'pending',
    imageUrl: 'https://picsum.photos/id/112/600/400'
  },
  {
    id: 'p2',
    name: 'Parcela 45 - Viñedos',
    description: 'Inspección fitosanitaria trimestral.',
    lat: 42.4285,
    lng: -2.6280,
    date: '2023-10-26',
    status: 'verified',
    imageUrl: 'https://picsum.photos/id/425/600/400'
  }
];

export const useProjectStore = () => {
  const [projects, setProjects] = useState<Project[]>(INITIAL_MOCK_PROJECTS);

  // Load data from Android on startup (Single Source of Truth)
  useEffect(() => {
    const nativeData = getNativeProjects();
    if (nativeData && nativeData.length > 0) {
      console.log("Hydrating from Android Native:", nativeData);
      setProjects(nativeData);
    }
  }, []);

  const handlePhotoCaptured = useCallback((projectId: string, photoUri: string) => {
    setProjects(prev => prev.map(p => {
      if (p.id === projectId) {
        return { ...p, imageUrl: photoUri };
      }
      return p;
    }));
  }, []);

  useEffect(() => {
    window.onPhotoCaptured = handlePhotoCaptured;
    return () => {
      window.onPhotoCaptured = undefined;
    };
  }, [handlePhotoCaptured]);

  return { projects, setProjects };
};