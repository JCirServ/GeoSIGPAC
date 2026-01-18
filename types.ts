export interface Project {
  id: string;
  name: string;
  description: string;
  lat: number;
  lng: number;
  imageUrl?: string;
  date: string;
  status: 'pending' | 'verified' | 'completed';
}

// Definition of the interface injected by Android WebView
export interface AndroidBridge {
  openCamera: (projectId: string) => void;
  // Renamed from focusMap to match requested API
  onProjectSelected: (lat: number, lng: number) => void; 
  showToast: (message: string) => void;
  getProjects: () => string; // Returns JSON string of projects
}

declare global {
  interface Window {
    Android?: AndroidBridge;
    onPhotoCaptured?: (projectId: string, photoUri: string) => void;
  }
}