
export type InspectionStatus = 'pendiente' | 'en_curso' | 'finalizado' | 'enviado';

export interface SigpacInfo {
  pendiente: number;
  altitud: number;
  municipio?: string;
  poligono?: string;
  parcela?: string;
  recinto?: string;
  provincia?: string;
}

export interface CultivoInfo {
  expNum: string;
  producto: string;
  sistExp: string;
  ayudaSol: string;
  superficie: number;
}

export interface Parcela {
  id: string;
  referencia: string; // Prov:Mun:Pol:Parc:Rec
  uso: string;
  lat: number;
  lng: number;
  area: number;
  status: 'pendiente' | 'conforme' | 'incidencia';
  aiReport?: string;
  aiConfidence?: number;
  sigpacData?: SigpacInfo; // Datos técnicos
  cultivoData?: CultivoInfo; // Declaración
  isDataLoaded?: boolean;
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
