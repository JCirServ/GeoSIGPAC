
export type InspectionStatus = 'planned' | 'in_progress' | 'completed' | 'paused';

export interface Parcela {
  id: string;
  name: string;
  lat: number;
  lng: number;
  area: number; // en hectáreas
  status: 'pending' | 'verified';
  imageUrl?: string;
}

export interface Inspection {
  id: string;
  title: string;
  description: string;
  date: string;
  status: InspectionStatus;
  parcelas: Parcela[];
}

/**
 * Added Project interface to support legacy or specialized components 
 * like ProjectCard and native bridge functions.
 */
export interface Project {
  id: string;
  name: string;
  description: string;
  date: string;
  status: 'pending' | 'verified' | 'completed';
  lat: number;
  lng: number;
  imageUrl?: string;
}

export interface AndroidBridge {
  openCamera: (parcelaId: string) => void;
  onProjectSelected: (lat: number, lng: number) => void; 
  showToast: (message: string) => void;
  getProjects: () => string; 
}

declare global {
  interface Window {
    Android?: AndroidBridge;
    onPhotoCaptured?: (parcelaId: string, photoUri: string) => void;
  }
}