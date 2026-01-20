
export type InspectionStatus = 'pendiente' | 'en_curso' | 'finalizado' | 'enviado';

export interface Parcela {
  id: string;
  referencia: string; // Ref_SigPac
  uso: string; // USO_SIGPAC
  lat: number;
  lng: number;
  area: number; // DN_SURFACE o similar
  status: 'pendiente' | 'conforme' | 'incidencia';
  aiReport?: string;
  aiConfidence?: number;
  metadata: Record<string, string>; // Todos los campos del ExtendedData
}

export interface Expediente {
  id: string;
  titular: string;
  campana: number;
  fechaImportacion: string;
  descripcion: string;
  status: InspectionStatus;
  parcelas: Parcela[];
}

export interface Project {
  id: string;
  name: string;
  description: string;
  status: 'pending' | 'verified' | 'completed';
  date: string;
  lat: number;
  lng: number;
  imageUrl?: string;
}

export interface AndroidBridge {
  openCamera: (parcelaId: string) => void;
  onProjectSelected: (lat: number, lng: number) => void; 
  showToast: (message: string) => void;
  importInspectionData: (jsonData: string) => void;
}

declare global {
  interface Window {
    Android?: AndroidBridge;
    onPhotoCaptured?: (parcelaId: string, photoUri: string) => void;
  }
}
