
export type InspectionStatus = 'pendiente' | 'en_curso' | 'finalizado' | 'enviado';

export interface Parcela {
  id: string; // UUID o referencia catastral
  referencia: string; // Prov:Mun:Pol:Parc:Rec
  uso: string; // Uso declarado (ej: TA, OV, VI)
  lat: number;
  lng: number;
  area: number; // hectáreas
  status: 'pendiente' | 'conforme' | 'incidencia';
}

export interface Expediente {
  id: string;
  titular: string; // Nombre del agricultor o campaña
  campana: number; // Año (ej: 2024)
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
  importInspectionData: (jsonData: string) => void; // NUEVO: Enviar datos a Room
}

declare global {
  interface Window {
    Android?: AndroidBridge;
    onPhotoCaptured?: (parcelaId: string, photoUri: string) => void;
  }
}
